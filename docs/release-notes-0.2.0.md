# Log Doctor 0.2.0

Log Doctor 0.2.0 turns the project from a CLI-only diagnostic tool into a local-first diagnosis experience with an embedded web dashboard, structured results and CI-backed verification.

## Highlights

- Embedded dashboard served from the same shaded JAR
- Structured diagnosis model shared by CLI and web flows
- Deterministic rules remain the source of truth for high-confidence incidents
- Local Ollama remains the fallback for unknown or ambiguous failures
- Browser-local recent-analysis history without server-side log persistence
- Configurable local web port
- Hardened HTTP handling and security headers
- Java 21 CI with unit and API tests

## Run the dashboard

```bash
mvn clean verify
java -jar target/log-doctor-0.2.0.jar --web
```

Open `http://localhost:8080`.

Custom port:

```bash
java -jar target/log-doctor-0.2.0.jar --web --port 9090
```

## CLI compatibility

The CLI remains available:

```bash
java -jar target/log-doctor-0.2.0.jar --file examples/app.log
```

## Upgrade notes

There are no intended breaking CLI changes from 0.1.0. The web API now exposes the structured diagnosis object directly, so clients built against the earlier temporary `{ "diagnosis": "..." }` response shape should migrate to the structured fields.

## Suggested Git tag

After this release-readiness PR is merged and CI is green:

```bash
git tag -a v0.2.0 -m "Log Doctor 0.2.0"
git push origin v0.2.0
```
