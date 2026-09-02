# Supply-chain security

Log Doctor release hardening combines build provenance, SBOM metadata, checksums and pull-request dependency review.

## Release provenance

Tag-triggered releases attest packaged JARs with GitHub artifact attestations. Consumers can verify that a downloaded artifact was produced by this repository and its release workflow.

```bash
gh attestation verify log-doctor-<version>.jar --repo mathias82/log-doctor
```

## SBOM

The Maven Central publish workflow generates a CycloneDX JSON SBOM for the packaged release and creates a separate SBOM attestation for the release JAR. The SBOM is also uploaded with the release integrity workflow artifact together with `SHA256SUMS`.

The SBOM describes packaged dependencies and licenses; it is not a vulnerability scan and does not replace dependency patching or runtime security controls.

## Pull-request dependency review

Every pull request targeting `main` runs GitHub dependency review. The check fails when a newly introduced dependency has a known vulnerability rated `high` or `critical`, or when a new dependency uses a denied copyleft license (`GPL-3.0` or `AGPL-3.0`).

This check evaluates dependency changes introduced by the pull request rather than treating the existing dependency graph as a one-time security certification.

## Verification layers

The release pipeline intentionally uses independent signals:

1. Maven/GPG signing authenticates published Maven artifacts.
2. GitHub artifact provenance binds release binaries to the repository/workflow/commit that produced them.
3. CycloneDX SBOM metadata records the dependency composition of the release.
4. SHA-256 checksums provide a simple integrity check for downloaded release files.
5. Pull-request dependency review blocks newly introduced high-risk dependency changes before merge.

None of these controls alone proves that a release is vulnerability-free. Together they make provenance, composition and dependency-risk changes auditable.
