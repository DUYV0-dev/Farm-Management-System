# Material & Warehouse

The connected UI is served by Spring Boot at `/ui/material.html` within the authenticated application at `/`.

Current source: `features/inventory/material.html` and `material.js`; shared styles are in
`shared/`. Production packaging and the current database path are described in
[DEPLOYMENT.md](../docs/DEPLOYMENT.md) and [WORKSPACE.md](../docs/WORKSPACE.md).
The list of preview-only files is in [DEMO_FILES.md](../docs/DEMO_FILES.md).

See [installation, validation rules, permissions and test commands](../docs/MANAGEMENT.md).

This screen now uses the real REST API and PostgreSQL. `material-store.js` and its original tests are retained as legacy demo code and are not packaged or loaded by the application.
