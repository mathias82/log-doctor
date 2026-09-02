# Release integrity and provenance

Log Doctor's tag-triggered Maven Central workflow creates integrity evidence for release artifacts before publication.

## Build provenance

The release workflow uses GitHub artifact attestations for packaged JAR artifacts. The attestation binds the artifact digest to GitHub-provided build identity such as the repository, workflow, commit and triggering event.

Attestations improve provenance and tamper detection; they are not a claim that an artifact is vulnerability-free.

## SHA-256 checksums

Each release workflow also creates `SHA256SUMS` as a workflow artifact. This gives consumers and maintainers a simple digest record for packaged release artifacts.

## Verify an attestation

With GitHub CLI installed, verify a downloaded release JAR against this repository:

```bash
gh attestation verify path/to/log-doctor.jar -R mathias82/log-doctor
```

Verification should be performed against the exact artifact a consumer intends to run.

## Security boundary

Release provenance complements, but does not replace:

- Maven/GPG signing
- dependency and vulnerability review
- CI tests
- deterministic Log Doctor safety policies
- review of the source and build workflow

The release workflow intentionally generates provenance only for tag-triggered distributable artifacts rather than every CI build.
