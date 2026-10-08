# Migration cutover assessment and approval

[Documentation index](../README.md) · [Migration workflows](README.md)

## Cutover assessment and approval

Before cutover, `assessCutover(checks, lastVerifiedAt,
maximumVerificationAge)` compares source and target watermarks and rejects stale
verification. Pass a report path to persist the same decision and measurements
as atomic JSON; the overload also accepts `maxCutoverReportFileSizeBytes`.
`getCutoverReportFingerprint()` returns the SHA-256 value of the bytes that were
written, so deployment approval can bind to the exact cutover evidence.
At the deployment boundary, call `approveCutover(reportFile,
expectedReportFingerprint, maximumReportAge, maxCutoverReportFileSizeBytes)`.
It accepts only an exact, recent `READY` report, rejects future timestamps and
expired decisions, and exposes the accepted SHA-256 value through
`getApprovedCutoverReportFingerprint()`. This validation is file-only and does
not open source or target database connections.

### Using a verification report

To avoid supplying verification time manually, use
`assessCutover(checks, verificationReport, maximumVerificationAge,
cutoverReport)`. It requires a successful verification report whose plan
fingerprint still matches the live migration plan, then uses that report's
`generatedAt` as verification time. The advanced overload accepts the expected
verification-report SHA-256 value and independent input/output size limits.
`getApprovedVerificationReportFingerprint()` identifies the exact verification
evidence used by the assessment without changing the existing cutover-report
format.

### Portable cutover evidence

For a portable deployment gate, call
`assessCutoverAndWriteEvidence(checks, verificationReport,
maximumVerificationAge, cutoverReport, evidenceFile)`. The additional evidence
file links the successful verification report, its plan fingerprint, and the
cutover decision by SHA-256 without embedding machine-specific file paths. The
advanced overload adds an expected verification SHA-256 value and independent
size limits for all three files. Use `getCutoverEvidenceFingerprint()` as the
value handed to the deployment stage. That stage calls
`approveCutoverEvidence(evidenceFile, expectedEvidenceFingerprint,
verificationReport, cutoverReport, maximumReportAge, ...)`; approval succeeds
only when the evidence file is exact, both referenced files still have the
recorded bytes and metadata, verification succeeded, and the cutover decision
is recent and `READY`. The accepted SHA values are exposed through
`getApprovedCutoverEvidenceFingerprint()`,
`getApprovedVerificationReportFingerprint()`, and
`getApprovedCutoverReportFingerprint()`. Approval is file-only and does not
open database connections. The simpler cutover APIs remain suitable when an
external system already stores the relationship between the two reports.

### Cutover artifact packages

For CI/CD systems that transfer directories as artifacts,
`assessCutoverPackage(checks, verificationReport, maximumVerificationAge,
packageDirectory)` publishes a new directory containing `verification.json`,
`cutover.json`, and `evidence.json`. Files are prepared in a sibling staging
directory and the completed directory is moved into place; an existing output
directory is rejected instead of being overwritten. The returned
`CutoverPackage.evidenceFingerprint()` is the value to carry in the deployment
approval. A reviewer can call `inspectCutoverPackage(packageDirectory)` to
cross-check and read the evidence, verification summary, cutover measurements,
and package SHA-256 value without approving anything or opening database
connections. `approveCutoverPackage(packageDirectory,
expectedEvidenceFingerprint, maximumReportAge)` verifies the complete package
without database access. It requires exactly the three documented regular
files and rejects missing entries, extra entries, and symbolic-link
substitution before reading report content. Advanced overloads provide the expected input
verification SHA-256 value and independent size limits. This package API is
optional; the individual-report APIs remain available for artifact stores that
manage files separately.
All file-only inspection and approval behavior is implemented by
`MigrationCutoverArtifactService`; applications that do not use the facade can
call that service directly and retain the same package inventory, fingerprint,
timestamp, successful-verification, and `READY` checks.
