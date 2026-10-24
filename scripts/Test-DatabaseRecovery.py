"""Run real restoration/consistency checks in a disposable PostgreSQL 18 cluster.

python scripts/Test-DatabaseRecovery.py
Optional: --postgres-bin PATH
No existing server/database is used. Data, logs and JSON/Markdown reports are kept
under .test-data/database-recovery-<unique-id>. The test server is always stopped.
"""

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import traceback
from types import SimpleNamespace
import uuid

import database_recovery as recovery


ROOT = Path(__file__).resolve().parent.parent
MIGRATIONS = ["00_Identity_Schema.sql", "05_Authentication_Sessions.sql", "06_User_Role_Management.sql",
              "07_Farm_Plot_Crop.sql", "08_Material_Warehouse.sql", "09_Management_Integrity.sql",
              "10_Farm_Expense_Management.sql", "11a_Material_Usage_Warehouse.sql",
              "12_Farm_Tenancy.sql", "13_Crop_Season_Enhancement.sql"]


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--postgres-bin", default=recovery.DEFAULT_POSTGRES_BIN)
    args = parser.parse_args()
    run_dir = ROOT / ".test-data" / ("database-recovery-" + uuid.uuid4().hex)
    run_dir.mkdir(parents=True)
    cluster = run_dir / "pgdata"
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    connection = dict(host="127.0.0.1", port=port, username="postgres", postgres_bin=args.postgres_bin)
    source = recovery.PostgreSQL(SimpleNamespace(database="recovery_source", **connection))
    restored = recovery.PostgreSQL(SimpleNamespace(database="recovery_restored", **connection))
    archive = run_dir / "backup"
    results = []
    started = time.monotonic()
    server_started = False

    def control_server(arguments):
        # PostgreSQL on Windows can keep inherited pipe handles open after
        # pg_ctl exits. Use a file instead of PIPE so communicate cannot hang.
        with (run_dir / "pg_ctl.log").open("ab") as log:
            completed = subprocess.run([source.executable("pg_ctl"), "-D", str(cluster), *arguments],
                                       stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                                       env=source.env, timeout=45)
        require(completed.returncode == 0, "pg_ctl failed; see " + str(run_dir / "pg_ctl.log"))

    def cli(action, database=None, backup_dir=archive, expected=0, extra=()):
        command = [sys.executable, str(ROOT / "scripts" / "database_recovery.py"), action,
                   "--backup-dir", str(backup_dir), "--host", "127.0.0.1", "--port", str(port),
                   "--username", "postgres", "--postgres-bin", args.postgres_bin, *extra]
        if database:
            command += ["--database", database]
        completed = subprocess.run(command, capture_output=True, encoding="utf-8", timeout=180,
                                   env=dict(os.environ, PYTHONIOENCODING="utf-8"))
        if action == "restore" and expected == 0 and completed.returncode != 0:
            # Preserve exact catalog differences to diagnose round-trip issues.
            target = recovery.PostgreSQL(SimpleNamespace(database=database, **connection))
            with target.snapshot() as snapshot:
                actual = target.inventory(snapshot)
            baseline = json.loads((Path(backup_dir) / "manifest.json").read_text(encoding="utf-8"))["inventory"]
            diagnostic = {"expected": baseline, "actual": actual}
            (run_dir / "restore-diagnostic.json").write_text(json.dumps(diagnostic, ensure_ascii=False, indent=2), encoding="utf-8")
        require(completed.returncode == expected,
                f"Unexpected exit {completed.returncode}: {completed.stdout}\n{completed.stderr}")
        value = json.loads(completed.stdout)
        require(value["status"] == ("PASS" if expected == 0 else "FAIL"), str(value))
        return value

    def case(name, action):
        case_started = time.monotonic()
        try:
            detail = action()
            results.append(dict(name=name, status="PASS", details=detail,
                                duration_seconds=round(time.monotonic() - case_started, 3)))
            print("PASS: " + name, flush=True)
        except Exception as error:
            results.append(dict(name=name, status="FAIL", error=str(error), traceback=traceback.format_exc(),
                                duration_seconds=round(time.monotonic() - case_started, 3)))
            print("FAIL: " + name + ": " + str(error), flush=True)
            raise

    try:
        source.run("initdb", ["-D", str(cluster), "-U", "postgres", "-A", "trust", "--encoding=UTF8", "--no-locale"])
        control_server(["-l", str(run_dir / "postgres.log"),
                        "-o", f"-h 127.0.0.1 -p {port}", "-w", "start"])
        server_started = True
        source.sql("CREATE DATABASE recovery_source", database="postgres")
        for migration in MIGRATIONS:
            source.run("psql", [*source.connection(), "-X", "--set=ON_ERROR_STOP=1", "-f", str(ROOT / "database" / migration)])
        source.run("psql", [*source.connection(), "-X", "--set=ON_ERROR_STOP=1", "-f",
                            str(ROOT / "src" / "test" / "resources" / "database-recovery-fixture.sql")])

        case("Full database backup with populated farm fixtures",
             lambda: cli("backup", source.database, extra=["--writers-paused", "--report", str(run_dir / "backup-report.json")]))
        case("Offline archive validation without a database connection",
             lambda: cli("check-backup", extra=["--port", "1"]))
        case("Restore archive into a new database and compare all data",
             lambda: cli("restore", restored.database, extra=["--report", str(run_dir / "restore-report.json")]))
        case("Independent restored-data consistency verification",
             lambda: cli("verify", restored.database, extra=["--report", str(run_dir / "consistency-report.json")]))

        def business_checks():
            checks = {
                "unicode": restored.sql("SELECT farm_name FROM farm WHERE farm_id=1").strip() == "Trang trại khôi phục",
                "farm_count": restored.sql("SELECT count(*) FROM farm").strip() == "2",
                "inventory_precision": restored.sql("SELECT quantity::text FROM warehouse_inventory WHERE inventory_id=1").strip() == "125.375",
                "expense_relation": restored.sql("SELECT count(*) FROM farm_expense e JOIN crop_season s USING(season_id) JOIN plot p USING(plot_id) WHERE e.farm_id=p.farm_id AND e.amount=123456.78").strip() == "1",
                "binary_and_null": restored.sql("SELECT count(*) FROM recovery_probe WHERE binary_value=decode('000aff805c','hex') OR text_value IS NULL").strip() == "2",
                "large_object": restored.sql("SELECT count(*) FROM pg_largeobject_metadata WHERE octet_length(lo_get(oid))=5000").strip() == "1",
                "rls_isolation": restored.sql("SET row_security=on; SET ROLE farm_app; SET app.user_id='1'; SET app.farm_id='1'; SELECT count(*) FROM farm").strip() == "1",
            }
            require(all(checks.values()), str(checks))
            return checks
        case("Restored farm relationships, Unicode, numeric precision, binary data and RLS", business_checks)

        def refuse_existing():
            before = restored.sql("SELECT count(*) FROM recovery_probe").strip()
            result = cli("restore", restored.database, expected=1)
            require("already exists" in result.get("error", ""), str(result))
            require(restored.sql("SELECT count(*) FROM recovery_probe").strip() == before, "Existing data changed")
            require(cli("verify", restored.database)["status"] == "PASS", "Existing DB changed")
            return result
        case("Existing recovery database is never overwritten", refuse_existing)

        def refuse_corrupt():
            corrupt = run_dir / "corrupt-backup"
            shutil.copytree(archive, corrupt)
            with (corrupt / "database.dump").open("r+b") as stream:
                stream.seek(-1, 2)
                byte = stream.read(1)
                stream.seek(-1, 2)
                stream.write(bytes([byte[0] ^ 0xFF]))
            result = cli("restore", "must_not_be_created", backup_dir=corrupt, expected=1)
            require("checksum/size mismatch" in result.get("error", ""), str(result))
            require(source.sql("SELECT count(*) FROM pg_database WHERE datname='must_not_be_created'", database="postgres").strip() == "0", "Corrupt archive created a database")
            return result
        case("Corrupted backup rejected before creating a database", refuse_corrupt)

        def changed_rows():
            restored.sql("UPDATE recovery_probe SET amount=amount+1 WHERE id=1")
            result = cli("verify", restored.database, expected=1)
            require(any("recovery_probe" in value and "data" in value for value in result["differences"]), str(result))
            restored.sql("UPDATE recovery_probe SET amount=amount-1 WHERE id=1")
            require(cli("verify", restored.database)["status"] == "PASS", "Data repair did not restore baseline")
            return result
        case("Detect changed data when row count stays unchanged", changed_rows)

        def changed_sequence():
            restored.sql("SELECT nextval(pg_get_serial_sequence('public.recovery_probe','id'))")
            result = cli("verify", restored.database, expected=1)
            require(any("sequences" in value and "recovery_probe" in value for value in result["differences"]), str(result))
            restored.sql("SELECT setval(pg_get_serial_sequence('public.recovery_probe','id'),3,true)")
            return result
        case("Detect changed identity sequence state", changed_sequence)

        def changed_constraint():
            restored.sql("ALTER TABLE recovery_probe ADD CONSTRAINT probe_amount_limit CHECK (amount < 1000)")
            result = cli("verify", restored.database, expected=1)
            require(any("recovery_probe" in value and "constraints" in value for value in result["differences"]), str(result))
            restored.sql("ALTER TABLE recovery_probe DROP CONSTRAINT probe_amount_limit")
            return result
        case("Detect a real constraint change after normalizing equivalent SQL", changed_constraint)

        def missing_table():
            restored.sql("DROP TABLE public.recovery_probe")
            result = cli("verify", restored.database, expected=1)
            require(any("Missing tables: public.recovery_probe" in value for value in result["differences"]), str(result))
            return result
        case("Detect a missing table after restoration", missing_table)

        def transaction_rollback():
            # CREATE ROLE has a cluster-wide effect and cannot be included in the
            # archive. Omitting an owner on this separate test archive forces an
            # ownership failure during restore; all restored SQL must roll back.
            source.sql("CREATE ROLE missing_restore_owner; CREATE TABLE public.owner_probe(id INTEGER); ALTER TABLE public.owner_probe OWNER TO missing_restore_owner")
            failure_archive = run_dir / "owner-backup"
            cli("backup", source.database, backup_dir=failure_archive, extra=["--writers-paused"])
            source.sql("ALTER TABLE public.owner_probe OWNER TO postgres; DROP ROLE missing_restore_owner")
            result = cli("restore", "rollback_target", backup_dir=failure_archive, expected=1)
            target = recovery.PostgreSQL(SimpleNamespace(database="rollback_target", **connection))
            count = target.sql("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','S')").strip()
            require(count == "0", "Partial schema survived transactional restore: " + count)
            return result
        case("Restore failure rolls back all schema/data changes", transaction_rollback)
    except Exception as error:
        if not results or results[-1]["status"] != "FAIL":
            results.append(dict(name="Cluster/schema setup", status="FAIL", error=str(error), traceback=traceback.format_exc()))
    finally:
        if server_started:
            try:
                control_server(["-m", "fast", "-w", "stop"])
            except Exception as error:
                results.append(dict(name="Stop isolated test server", status="FAIL", error=str(error)))
        report = dict(status="PASS" if results and all(item["status"] == "PASS" for item in results) else "FAIL",
                      completed_at=datetime.now(timezone.utc).isoformat(), test_cluster_port=port,
                      production_database_accessed=False, fixtures="Synthetic data with current application migrations 00 through 13",
                      duration_seconds=round(time.monotonic() - started, 3), results=results)
        (run_dir / "test-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        lines = ["# Database Backup & Recovery Test Report", "", f"Status: {report['status']}",
                 f"Completed: {report['completed_at']}", f"Duration: {report['duration_seconds']} seconds", "",
                 "Environment: isolated PostgreSQL 18 cluster; synthetic farm data; no production database access.", "",
                 "| Check | Result |", "|---|---|"]
        lines += [f"| {item['name']} | {item['status']} |" for item in results]
        lines += ["", "Coverage is functional restoration/consistency on a small fixture, not production-scale RTO, scheduling, retention or offsite recovery."]
        (run_dir / "test-report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
        print("Report: " + str(run_dir / "test-report.md"), flush=True)
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
