"""PostgreSQL backup/recovery for Farm Management (Python 3.10+, no dependencies).

Usage (run from the repository root):
  python scripts/database_recovery.py backup --database farm_management --backup-dir .test-data/backup-01 --writers-paused
  python scripts/database_recovery.py check-backup --backup-dir .test-data/backup-01
  python scripts/database_recovery.py restore --database farm_recovered --backup-dir .test-data/backup-01
  python scripts/database_recovery.py verify --database farm_recovered --backup-dir .test-data/backup-01

Connection: --host, --port, --username, --postgres-bin; authentication uses the
standard PostgreSQL PGPASSFILE/pgpass.conf or PGPASSWORD (never a CLI password).
Run as a database administrator who can read every table, including RLS tables.
Stop application writers for the entire backup: row data uses an exported MVCC
snapshot, but sequence values and DDL are not protected by that snapshot.

Archives include schema, data, sequences, object ownership and grants, including users,
password hashes and audit history. Store the backup directory in restricted,
encrypted storage outside the repository. Cluster roles/passwords, tablespaces,
server/database configuration and database-level grants, JWT keys and external
files need separate provisioning. Foreign-table rows are not backed up; backups
are refused when foreign tables exist. RPO/retention settings describe the desired
external schedule/storage policy; this tool does not schedule jobs or delete files.
Only restore trusted archives; SHA-256 detects corruption, not malicious edits.
Restore ALWAYS creates a new database and never drops or cleans an existing one.
No backup files or failed restore databases are automatically deleted.
"""

from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time


FORMAT_VERSION = 1
DEFAULT_POSTGRES_BIN = r"C:\Program Files\PostgreSQL\18\bin" if os.name == "nt" else ""
DEFAULT_POLICY = {
    "rpo_hours": 24,
    "rto_minutes": 60,
    "retention_days": 30,
    "scope": "Full database archive: application schemas, rows, sequences, large objects, object ownership and grants",
    "prerequisites": "Pause writers and schema changes; provision cluster roles and tablespaces separately",
}


class RecoveryError(Exception):
    pass


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def identifier(value):
    return '"' + value.replace('"', '""') + '"'


def literal(value):
    return "'" + value.replace("'", "''") + "'"


def database_name(value):
    # Prevent libpq treating a database name as a URI/connection string.
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]{0,62}", value):
        raise RecoveryError("Database name must contain 1-63 ASCII letters, digits or underscores, starting with a letter/underscore")
    return value


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json(path, value):
    # Write completion manifests atomically, without overwriting previous reports.
    path = Path(path)
    if path.exists():
        raise RecoveryError(f"Output already exists: {path}")
    temporary = path.with_name(path.name + ".partial")
    with temporary.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    temporary.rename(path)


class PostgreSQL:
    def __init__(self, args):
        self.host = args.host
        self.port = args.port
        self.username = args.username
        self.database = database_name(args.database)
        self.bin = Path(args.postgres_bin) if args.postgres_bin else None
        self.env = dict(os.environ, PGCLIENTENCODING="UTF8", PGCONNECT_TIMEOUT="15", PGAPPNAME="farm-backup-recovery")
        # Stable row serialization for timestamps, bytea, floats and intervals.
        self.env["PGOPTIONS"] = "-c timezone=UTC -c datestyle=ISO,YMD -c intervalstyle=postgres -c extra_float_digits=3 -c bytea_output=hex -c row_security=off"

    def executable(self, name):
        return str(self.bin / (name + (".exe" if os.name == "nt" else ""))) if self.bin else name

    def connection(self, database=None):
        return [f"--host={self.host}", f"--port={self.port}", f"--username={self.username}",
                "--no-password", f"--dbname={database or self.database}"]

    def run(self, tool, arguments, input_text=None):
        process = subprocess.run([self.executable(tool), *arguments], input=input_text,
                                 encoding="utf-8", stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=self.env)
        if process.returncode:
            raise RecoveryError(f"{tool} failed (exit {process.returncode}): {process.stderr.strip()}")
        return process.stdout

    def sql(self, sql, snapshot=None, database=None):
        return self.run("psql", [*self.connection(database), "-X", "-qAt", "--set=ON_ERROR_STOP=1"],
                        self.transaction_sql(sql, snapshot))

    @staticmethod
    def transaction_sql(sql, snapshot):
        if snapshot:
            return "BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;\nSET TRANSACTION SNAPSHOT " + literal(snapshot) + ";\n" + sql + ";\nCOMMIT;\n"
        return sql + ";\n"

    @contextmanager
    def snapshot(self):
        # Keep the exporting transaction open until both pg_dump and fingerprints finish.
        with tempfile.TemporaryFile() as errors:
            process = subprocess.Popen([self.executable("psql"), *self.connection(), "-X", "-qAt", "--set=ON_ERROR_STOP=1"],
                                       stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=errors, env=self.env,
                                       encoding="utf-8", bufsize=1)
            try:
                process.stdin.write("BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;\nSELECT pg_export_snapshot();\n")
                process.stdin.flush()
                snapshot = process.stdout.readline().strip()
                if not re.fullmatch(r"[0-9A-Fa-f]+-[0-9A-Fa-f]+-[0-9]+", snapshot):
                    raise RecoveryError("Cannot acquire database snapshot; check connection and privileges")
                yield snapshot
            finally:
                if process.poll() is None:
                    try:
                        process.stdin.write("ROLLBACK;\n\\q\n")
                        process.stdin.flush()
                        process.wait(timeout=10)
                    except (OSError, subprocess.TimeoutExpired):
                        process.kill()
                        process.wait()
                process.stdin.close()
                process.stdout.close()

    def row_fingerprint(self, schema, table, snapshot):
        qualified = identifier(schema) + "." + identifier(table)
        # Server sorting makes the digest independent of physical row order;
        # COPY escapes embedded newlines/tabs, so each physical line is one row.
        query = f'COPY (SELECT row_to_json(t)::text AS row FROM {qualified} t ORDER BY row_to_json(t)::text COLLATE "C") TO STDOUT'
        digest = hashlib.sha256()
        count = 0
        with tempfile.TemporaryFile() as errors:
            process = subprocess.Popen([self.executable("psql"), *self.connection(), "-X", "-qAt", "--set=ON_ERROR_STOP=1"],
                                       stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=errors, env=self.env)
            try:
                process.stdin.write(self.transaction_sql(query, snapshot).encode("utf-8"))
                process.stdin.close()
                for line in process.stdout:
                    # Normalize only psql's OS line ending, not COPY-escaped values.
                    digest.update(line[:-2] + b"\n" if line.endswith(b"\r\n") else line)
                    count += 1
                process.stdout.close()
                if process.wait():
                    errors.seek(0)
                    raise RecoveryError("Cannot fingerprint " + qualified + ": " + errors.read().decode("utf-8", errors="replace"))
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait()
        return {"rows": count, "sha256": digest.hexdigest()}

    def inventory(self, snapshot):
        catalog = self.sql("""
SELECT COALESCE(json_agg(item ORDER BY item->>'schema', item->>'name'), '[]'::json)
FROM (
 SELECT json_build_object('schema', n.nspname, 'name', c.relname,
   'kind', c.relkind, 'populated', c.relispopulated,
   'columns', (SELECT COALESCE(json_agg(json_build_object(
      'name', a.attname, 'type', format_type(a.atttypid,a.atttypmod),
      'not_null', a.attnotnull, 'identity', a.attidentity, 'generated', a.attgenerated,
      'default', pg_get_expr(d.adbin,d.adrelid)) ORDER BY a.attnum), '[]'::json)
     FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
     WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped),
   'constraints', (SELECT COALESCE(json_agg(json_build_object('name', conname,
      'definition', pg_get_constraintdef(oid, false)) ORDER BY conname), '[]'::json)
     FROM pg_constraint WHERE conrelid=c.oid)
 ) AS item
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE c.relkind IN ('r','p','m') AND n.nspname !~ '^pg_' AND n.nspname <> 'information_schema'
) tables
""", snapshot)
        tables = json.loads(catalog)
        for table in tables:
            table["data"] = self.row_fingerprint(table["schema"], table["name"], snapshot) if table["populated"] else None
        sequence_names = json.loads(self.sql("""
SELECT COALESCE(json_agg(json_build_object('schema', schemaname, 'name', sequencename,
 'type', data_type, 'start', start_value, 'min', min_value, 'max', max_value,
 'increment', increment_by, 'cycle', cycle, 'cache', cache_size)
 ORDER BY schemaname, sequencename), '[]'::json)
FROM pg_sequences WHERE schemaname !~ '^pg_' AND schemaname <> 'information_schema'
""", snapshot))
        for sequence in sequence_names:
            qualified = identifier(sequence["schema"]) + "." + identifier(sequence["name"])
            sequence["state"] = json.loads(self.sql(f"SELECT row_to_json(s) FROM (SELECT last_value, is_called FROM {qualified}) s", snapshot))
        large_objects = self.row_fingerprint("pg_catalog", "pg_largeobject", snapshot)
        large_object_owners = json.loads(self.sql("""
SELECT COALESCE(json_agg(json_build_object('oid', oid, 'owner', pg_get_userbyid(lomowner),
 'acl', lomacl::text) ORDER BY oid), '[]'::json) FROM pg_largeobject_metadata
""", snapshot))
        return {"tables": tables, "sequences": sequence_names,
                "large_objects": large_objects, "large_object_owners": large_object_owners}


def load_backup(directory):
    directory = Path(directory).resolve()
    archive = directory / "database.dump"
    manifest_path = directory / "manifest.json"
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        if manifest["format_version"] != FORMAT_VERSION:
            raise RecoveryError("Unsupported backup manifest version")
        if manifest["status"] != "COMPLETE" or not isinstance(manifest["inventory"]["tables"], list) or not isinstance(manifest["inventory"]["sequences"], list):
            raise RecoveryError("Incomplete backup manifest")
        if not isinstance(manifest["inventory"]["large_objects"], dict) or not isinstance(manifest["inventory"]["large_object_owners"], list):
            raise RecoveryError("Missing large object inventory")
        if not isinstance(manifest["database_settings"], dict):
            raise RecoveryError("Missing source database settings")
        for field in ("encoding", "collate", "ctype", "provider"):
            if not isinstance(manifest["database_settings"][field], str):
                raise RecoveryError("Invalid source database settings")
        if manifest["database_settings"]["provider"] in ("i", "b") and not isinstance(manifest["database_settings"].get("locale"), str):
            raise RecoveryError("Missing source locale")
        for field in ("rto_minutes", "rpo_hours", "retention_days"):
            value = manifest["policy"][field]
            if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value <= 0:
                raise RecoveryError("Invalid recovery policy")
        if datetime.fromisoformat(manifest["started_at"]).utcoffset() is None:
            raise RecoveryError("Backup timestamp must contain a timezone")
        for category in ("tables", "sequences"):
            seen = set()
            for item in manifest["inventory"][category]:
                if not isinstance(item["schema"], str) or not isinstance(item["name"], str):
                    raise RecoveryError("Invalid inventory object name")
                key = (item["schema"], item["name"])
                if key in seen:
                    raise RecoveryError("Duplicate inventory object")
                seen.add(key)
        if manifest["archive_bytes"] != archive.stat().st_size or manifest["archive_sha256"] != sha256_file(archive):
            raise RecoveryError("Backup checksum/size mismatch; restore refused")
    except (OSError, ValueError, KeyError, TypeError) as error:
        raise RecoveryError("Invalid or missing backup archive/manifest") from error
    return archive, manifest


def inspect_archive(pg, archive):
    # Listing the TOC alone does not read compressed table data. Render the full
    # archive to the OS null device to detect decompression/read errors without
    # executing SQL or opening any database connection.
    pg.run("pg_restore", ["--list", str(archive)])
    pg.run("pg_restore", [f"--file={os.devnull}", str(archive)])


def check_backup(pg, directory):
    started = time.monotonic()
    archive, manifest = load_backup(directory)
    inspect_archive(pg, archive)
    return {"status": "PASS", "checked_at": utc_now(), "backup_dir": str(Path(directory).resolve()),
            "archive_bytes": manifest["archive_bytes"], "archive_sha256": manifest["archive_sha256"],
            "checks": {"manifest": "PASS", "archive_size": "PASS", "sha256": "PASS",
                       "archive_catalog": "PASS", "full_archive_read": "PASS"},
            "duration_seconds": round(time.monotonic() - started, 3),
            "restore_validated": False,
            "limitation": "Archive integrity only; database restoration and restored-data consistency require restore/verify"}


def backup(pg, directory, policy):
    if pg.sql("SELECT EXISTS (SELECT 1 FROM pg_foreign_table)").strip() != "f":
        raise RecoveryError("Foreign tables are not supported: back up remote data separately before using this tool")
    directory = Path(directory).resolve()
    # Exclusive directory creation prevents overwriting a prior backup.
    directory.mkdir(parents=True, exist_ok=False)
    started = time.monotonic()
    manifest = {"format_version": FORMAT_VERSION, "status": "COMPLETE", "started_at": utc_now(),
                "source_database": pg.database, "policy": policy,
                "source_server_version": pg.sql("SHOW server_version").strip()}
    # PostgreSQL 18 catalog: preserve locale provider so collation-sensitive
    # constraints/indexes retain the source semantics on the recovery database.
    manifest["database_settings"] = json.loads(pg.sql("""
SELECT json_build_object('encoding', pg_encoding_to_char(encoding),
 'collate', datcollate, 'ctype', datctype, 'provider', datlocprovider,
 'locale', datlocale) FROM pg_database WHERE datname=current_database()
"""))
    archive = directory / "database.dump"
    with pg.snapshot() as snapshot:
        pg.run("pg_dump", [*pg.connection(), "--format=custom", "--compress=6", f"--snapshot={snapshot}", f"--file={archive}"])
        manifest["inventory"] = pg.inventory(snapshot)
    # Publish the completion manifest only after the entire archive is readable.
    inspect_archive(pg, archive)
    manifest.update(archive_bytes=archive.stat().st_size, archive_sha256=sha256_file(archive),
                    completed_at=utc_now(), duration_seconds=round(time.monotonic() - started, 3))
    write_json(directory / "manifest.json", manifest)
    return {"status": "PASS", "backup_dir": str(directory), "archive_sha256": manifest["archive_sha256"],
            "tables": len(manifest["inventory"]["tables"]), "duration_seconds": manifest["duration_seconds"]}


def canonical_constraint(definition):
    # PostgreSQL 18 can move a varchar[] -> text[] cast into each literal
    # when pg_dump's CHECK definition is parsed during restore. Normalize that
    # specific equivalent form, preserving literal contents and all other casts.
    # Tokenization keeps quoted strings/identifiers intact; never rewrite SQL
    # fragments occurring inside a string literal.
    tokens = re.findall(r"'(?:''|[^'])*'|\"(?:\"\"|[^\"])*\"|::|[A-Za-z_][A-Za-z_0-9$]*|[0-9]+(?:\.[0-9]+)?|[^\s]", definition)
    normalized = []
    index = 0
    while index < len(tokens):
        if tokens[index:index + 3] == ["(", "ARRAY", "["]:
            cursor = index + 3
            values = []
            while cursor + 3 < len(tokens):
                if not tokens[cursor].startswith("'") or tokens[cursor + 1:cursor + 4] != ["::", "character", "varying"]:
                    break
                values.append(tokens[cursor])
                cursor += 4
                if tokens[cursor:cursor + 1] != [","]:
                    break
                cursor += 1
            if values and tokens[cursor:cursor + 6] == ["]", ")", "::", "text", "[", "]"]:
                normalized += ["ARRAY", "["]
                for value_index, value in enumerate(values):
                    if value_index:
                        normalized.append(",")
                    normalized += ["(", value, "::", "character", "varying", ")", "::", "text"]
                normalized.append("]")
                index = cursor + 6
                continue
        normalized.append(tokens[index])
        index += 1
    return " ".join(normalized)


def comparable_item(item, category):
    if category != "tables":
        return item
    return dict(item, constraints=[dict(constraint, definition=canonical_constraint(constraint["definition"]))
                                   for constraint in item["constraints"]])


def compare_inventory(expected, actual):
    differences = []
    for category in ("tables", "sequences"):
        before = {(item["schema"], item["name"]): comparable_item(item, category) for item in expected[category]}
        after = {(item["schema"], item["name"]): comparable_item(item, category) for item in actual[category]}
        for key in sorted(before.keys() | after.keys()):
            label = ".".join(key)
            if key not in before:
                differences.append(f"Unexpected {category}: {label}")
            elif key not in after:
                differences.append(f"Missing {category}: {label}")
            elif before[key] != after[key]:
                fields = sorted(field for field in before[key].keys() | after[key].keys() if before[key].get(field) != after[key].get(field))
                differences.append(f"Changed {category}: {label} ({', '.join(fields)})")
    for category in ("large_objects", "large_object_owners"):
        if expected[category] != actual[category]:
            differences.append(f"Changed {category}")
    return differences


def verify(pg, directory):
    _, manifest = load_backup(directory)
    with pg.snapshot() as snapshot:
        actual = pg.inventory(snapshot)
    differences = compare_inventory(manifest["inventory"], actual)
    return {"status": "FAIL" if differences else "PASS", "database": pg.database,
            "checked_at": utc_now(), "tables_checked": len(actual["tables"]),
            "sequences_checked": len(actual["sequences"]), "differences": differences}


def restore(pg, directory):
    started = time.monotonic()
    archive, manifest = load_backup(directory)
    inspect_archive(pg, archive)
    # CREATE DATABASE is the final atomic guard against concurrent creation;
    # there is deliberately no --clean, DROP DATABASE, or existing-target mode.
    exists = pg.sql("SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname=" + literal(pg.database) + ")", database="postgres").strip()
    if exists != "f":
        raise RecoveryError("Target database already exists; choose a NEW database name")
    maintenance = [f"--host={pg.host}", f"--port={pg.port}", f"--username={pg.username}", "--no-password", "--maintenance-db=postgres"]
    settings = manifest["database_settings"]
    provider = {"c": "libc", "i": "icu", "b": "builtin"}.get(settings.get("provider"))
    if not provider:
        raise RecoveryError("Unsupported source locale provider")
    locale_args = [f"--encoding={settings['encoding']}", f"--locale-provider={provider}",
                   f"--lc-collate={settings['collate']}", f"--lc-ctype={settings['ctype']}"]
    if provider == "icu":
        locale_args.append(f"--icu-locale={settings['locale']}")
    elif provider == "builtin":
        locale_args.append(f"--builtin-locale={settings['locale']}")
    pg.run("createdb", [*maintenance, "--template=template0", *locale_args, "--", pg.database])
    try:
        pg.run("pg_restore", [*pg.connection(), "--exit-on-error", "--single-transaction", str(archive)])
        result = verify(pg, directory)
    except Exception as error:
        raise RecoveryError(f"Recovery failed; keep {pg.database} offline for inspection. Restore SQL is transactional. {error}") from error
    elapsed = round(time.monotonic() - started, 3)
    result.update(duration_seconds=elapsed, rto_minutes=manifest["policy"]["rto_minutes"],
                  rto_met=elapsed <= manifest["policy"]["rto_minutes"] * 60)
    age = (datetime.now(timezone.utc) - datetime.fromisoformat(manifest["started_at"])).total_seconds()
    result.update(backup_age_seconds=round(age, 3), rpo_hours=manifest["policy"]["rpo_hours"],
                  rpo_met=0 <= age <= manifest["policy"]["rpo_hours"] * 3600)
    result["data_consistent"] = not result["differences"]
    result["rpo_measurement"] = "Age of the backup snapshot at recovery completion; excludes transactions after that snapshot"
    if not result["rto_met"] or not result["rpo_met"]:
        result["status"] = "FAIL"
    return result


def positive(value):
    value = int(value)
    if value <= 0:
        raise argparse.ArgumentTypeError("Must be a positive integer")
    return value


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("action", choices=["backup", "check-backup", "restore", "verify"])
    parser.add_argument("--database", help="Required for backup/restore/verify; unused by check-backup")
    parser.add_argument("--backup-dir", required=True, help="New directory for backup; existing backup directory for restore/verify")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=positive, default=5432)
    parser.add_argument("--username", default="postgres")
    parser.add_argument("--postgres-bin", default=DEFAULT_POSTGRES_BIN)
    parser.add_argument("--report", help="Write a new JSON report (including failures); never overwrite an old report")
    parser.add_argument("--rpo-hours", type=positive, default=24, help="Documented backup schedule target; scheduling is external")
    parser.add_argument("--rto-minutes", type=positive, default=60)
    parser.add_argument("--retention-days", type=positive, default=30, help="Documented retention target; files are never automatically deleted")
    parser.add_argument("--writers-paused", action="store_true", help="Confirm application writers and DDL are stopped during backup")
    args = parser.parse_args(argv)
    result = {"action": args.action, "started_at": utc_now(), "status": "FAIL"}
    try:
        if args.port > 65535:
            raise RecoveryError("Port must be between 1 and 65535")
        if args.action == "backup" and not args.writers_paused:
            raise RecoveryError("Pause application writers and schema changes, then pass --writers-paused to capture consistent sequence state")
        if args.action != "check-backup" and not args.database:
            raise RecoveryError("--database is required for backup, restore and verify")
        if args.report:
            report = Path(args.report).resolve()
            if report.exists() or report.with_name(report.name + ".partial").exists():
                raise RecoveryError("Report path already exists; choose a new report path")
            if not report.parent.is_dir():
                raise RecoveryError("Report parent directory does not exist")
        if args.action == "check-backup":
            # Placeholder only: the offline check never uses a DB connection.
            args.database = "postgres"
        pg = PostgreSQL(args)
        if args.action == "backup":
            policy = dict(DEFAULT_POLICY, rpo_hours=args.rpo_hours, rto_minutes=args.rto_minutes, retention_days=args.retention_days)
            result.update(backup(pg, args.backup_dir, policy))
        elif args.action == "restore":
            result.update(restore(pg, args.backup_dir))
        elif args.action == "check-backup":
            result.update(check_backup(pg, args.backup_dir))
        else:
            result.update(verify(pg, args.backup_dir))
    except (RecoveryError, OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
        result.update(status="FAIL", error=str(error))
    result["completed_at"] = utc_now()
    if args.report:
        try:
            write_json(args.report, result)
        except (RecoveryError, OSError) as error:
            result.update(status="FAIL", report_error=str(error))
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
