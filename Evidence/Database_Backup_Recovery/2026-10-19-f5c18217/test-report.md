# Database Backup & Recovery Test Report

Status: PASS
Completed: 2026-10-19T02:03:37.626710+00:00
Duration: 28.589 seconds

Environment: isolated PostgreSQL 18 cluster; synthetic farm data; no production database access.

| Check | Result |
|---|---|
| Full database backup with populated farm fixtures | PASS |
| Offline archive validation without a database connection | PASS |
| Restore archive into a new database and compare all data | PASS |
| Independent restored-data consistency verification | PASS |
| Restored farm relationships, Unicode, numeric precision, binary data and RLS | PASS |
| Existing recovery database is never overwritten | PASS |
| Corrupted backup rejected before creating a database | PASS |
| Detect changed data when row count stays unchanged | PASS |
| Detect changed identity sequence state | PASS |
| Detect a real constraint change after normalizing equivalent SQL | PASS |
| Detect a missing table after restoration | PASS |
| Restore failure rolls back all schema/data changes | PASS |

Coverage is functional restoration/consistency on a small fixture, not production-scale RTO, scheduling, retention or offsite recovery.
