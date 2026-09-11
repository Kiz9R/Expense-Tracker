# Validation record

This document retains dated execution evidence. See [leftout.md](leftout.md) for current progress and outstanding checks, and [featureDevelopmentInfo.md](featureDevelopmentInfo.md) for implementation details. Documentation consolidation did not rerun the checks recorded below.

Validated on 10 September 2026 using the existing Gradle 9.6.0 / AGP 9.4.0 / Java 25 toolchain and a hidden, read-only Medium Phone Android 17 emulator.

## Executed checks

- 14 JVM tests passed, with zero failures.
- 18 Android instrumentation tests passed, with zero failures/errors/skips.
- Android lint completed with zero errors and 25 warnings. Warnings concern dependency updates, existing unused template resources, synchronous key-storage style, and unused transitive PDFBox/BouncyCastle TLS helpers. The merged application manifest has no INTERNET permission.
- Debug assembly and R8-optimized release assembly succeeded.
- APK signature verification succeeded with APK Signature Scheme v2 and the private personal RSA-3072 signer.
- The real Compose flow exercised account onboarding, manual transaction entry, saved notes and SQLCipher persistence. A raw database-header assertion confirms it is not plaintext SQLite.
- WorkManager successfully parsed an encrypted staged statement and cleared its raw text.
- A password-protected PDF was generated on-device, extracted with the correct password, and rejected with the wrong password.
- An encrypted backup file was restored into an independent fresh database with matching transactions/evidence. Wrong-password and invalid-snapshot attempts preserved data.
- Dashboard and Settings were visually inspected using only synthetic account data. Screenshot capture was explicitly enabled on that disposable test installation.
- The final signed, optimized release installed successfully and cold-launched on the emulator.

The full final test/build command was:

    .\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleRelease

The successful build log is build/acceptance-final.txt. Detailed test reports are in app/build/reports/tests and app/build/reports/androidTests. Lint output is app/build/reports/lint-results-debug.html.

## Signed artifact

- File: app/build/outputs/apk/release/app-release.apk
- Size: 17,896,817 bytes
- SHA-256: 2EC046E36387B7CF48F4D24321462054091A875258B592004B5A376FDD0E8140
- Signer certificate SHA-256: CAE7C0C84FB2D31A72FD32C8376294BBB77107DE173FC8B67E21B07B0C4EE152

Retain the gitignored signing.properties and personal-release.jks privately for future signed updates.

## Remaining release validation

- Validate a representative SBI PDF from the user's actual statement workflow. Only synthetic layouts have been tested; PDF support remains explicitly experimental.
- Validate real SBI SMS and PhonePe/Google Pay notification formats on the user's phone, including installer permission grants and manufacturer background behavior.
- Complete physical-device biometric/device-credential, TalkBack, large-font, Android 11 compatibility and battery-restriction checks.
- Exercise uninstall/reinstall recovery on a physical device. Independent fresh-database recovery is automated, but it is not a substitute for device-specific Keystore/lifecycle testing.
- Confirm signed-APK updates using the retained private signer before adopting the app as the sole local ledger.

No real financial credentials or transaction records were used or committed.
## 11 September 2026 — PDF reconciliation completion

This dated run supersedes the earlier pending representative-PDF validation for the supported SBI Relationship Summary savings-account layout. Other PDF formats are not thereby certified. The original 10 September evidence above is retained.

### Executed checks

- 21 JVM tests passed: 14 existing financial tests and 7 Relationship Summary/parser/summary/ranking regressions.
- 36 Android instrumentation tests passed with no failures or skips in the final combined run. This includes the opt-in supplied-PDF test, encrypted schema migration, persistent drafts and stale-draft reset, concurrent ingestion/import, repeated and ignored overlaps, atomic staging cleanup, warning persistence through hiding/backup, cross-month reversals, late original debit, and the Compose resume/review/commit/ignored-row resolution flow.
- The supplied encrypted four-page SBI Relationship Summary was opened using the Android PDFBox extractor. All 41 transaction rows were extracted with valid UPI references; every running balance and the closing balance agreed. An isolated in-memory ledger received 41 verified transactions, and repeated import was detected as a duplicate. Real financial rows were not inserted into the installed app's persistent ledger or copied into test fixtures.
- The original PDF was read-only throughout validation; its SHA-256 remained unchanged. The opening password was used only for the local validation invocation, never embedded in source, Gradle properties, app settings, logs or fixtures.
- PDFBox position sorting was found to interleave hidden null table placeholders with the opening-balance label. Content-stream extraction preserves that label; the real-layout parser and synthetic content-order regression verify this behavior.
- SQLCipher schema-1 migration to schema 2 passed with retained account/import/job data and an encrypted file-header assertion.
- The existing manual UI test now waits for the loaded merchant field rather than only the screen container. The PDF UI test uses stable resume identifiers, loaded-state waits and native semantics actions for transient controls. A fixed-height global progress slot keeps controls stationary during background writes.
- Android lint passed with zero errors and 16 warnings.
- Debug/test APK installation and optimized signed release assembly succeeded.
- APK Signature Scheme v2 verification succeeded with the existing RSA-3072 personal signer.

### Commands and evidence

Build command (offline, using the installed toolchain):

    .\gradlew.bat :app:testDebugUnitTest :app:installDebug :app:installDebugAndroidTest :app:lintDebug :app:assembleRelease --offline

The full Android instrumentation suite ran through adb on the disposable Android 17 emulator. Its supplied-PDF validation received temporary path/password arguments directly; do not copy passwords into reusable commands, repository files or Gradle configuration. Ordinary connected test runs omit that optional local-sample case unless explicitly configured; synthetic tests remain self-contained.

- Build/test/lint/release log: [pdf-reconciliation-build.txt](build/pdf-reconciliation-build.txt).
- Final Android results: [pdf-reconciliation-device.txt](build/pdf-reconciliation-device.txt).
- JVM reports: [unit test reports](app/build/reports/tests/testDebugUnitTest).
- Lint report: [lint-results-debug.html](app/build/reports/lint-results-debug.html).
- Current implementation and remaining work: [leftout.md](leftout.md).

### Signed artifact

- APK: [app-release.apk](app/build/outputs/apk/release/app-release.apk).
- Version name/code: 1.1 / 2.
- Size: 17,945,969 bytes.
- SHA-256: 24A948728B040519D8F744FF11BA7BC81AAEE5AD2576D0532F5E81D7371BB5CD.
- Signer certificate SHA-256: CAE7C0C84FB2D31A72FD32C8376294BBB77107DE173FC8B67E21B07B0C4EE152.

### Remaining boundaries

The Relationship Summary layout prerequisite is satisfied. The generic text-table adapter remains experimental, scanned PDFs/CSV/XLSX remain unsupported, and documents with multiple account blocks require separate per-account statements. Official arithmetic discrepancies remain exceptions and cannot be dismissed as reconciled.

Physical-phone SAF behavior, process-death/reboot/OEM restrictions, Android 11, accessibility, biometric lifecycle, real SMS/notification coverage, uninstall/reinstall recovery and signed phone updates remain pending as tracked in leftout.md. This run does not claim those checks or a new release-APK cold launch. The earlier release cold-launch evidence refers to the 10 September artifact.

## 11 September 2026 — Account deletion

Implemented Settings → Manage SBI accounts → Delete with explicit confirmation identifying the nickname and masked account. Account-owned transactions (including hidden and verified entries), metadata/tag links, linked observations/evidence, statement imports/rows/decisions, mandates, refund links and queued imports are removed atomically. Other accounts, shared categories/tags/rules/settings and unassigned observations remain. No schema migration is required.

Verification:

- 21 JVM tests passed (14 FinanceTest, 7 RelationshipParserTest).
- Final Android runner: **OK (39 tests)** in 32.240 seconds on the disposable Android 17 emulator. This comprises **38 executed passes and one skipped opt-in ProvidedStatementValidationTest**; the private statement/password were not supplied or reread for this change.
- New account tests cover colliding suffix isolation, removal of verified/hidden records and ignored-row decision revisions, retention of shared/unassigned records, final-account deletion, stale-job/preview rejection, SQL-abort rollback, and Compose cancellation/confirmation.
- Existing reconciliation, encrypted migration, manual UI, protected-PDF and WorkManager tests passed. The WorkManager test now creates a real synthetic account instead of using a nonexistent account ID.
- Unit tests, debug/test installation, lint and release assembly succeeded. Lint: zero errors, 16 warnings.
- APK signature verified with the existing signer (v2). No physical phone, signed APK cold launch or phone update test was performed.
- Documentation check: one root featureDevelopmentInfo.md; no broken local documentation paths.

Commands:

    .\gradlew.bat :app:testDebugUnitTest :app:installDebug :app:installDebugAndroidTest :app:lintDebug :app:assembleRelease --offline
    .\gradlew.bat :app:installDebugAndroidTest :app:lintDebug --offline
    adb -s emulator-5554 shell am instrument -w com.kiz9r.expense_tracker.test/androidx.test.runner.AndroidJUnitRunner

Evidence: [main build](build/account-deletion-build.txt), [corrected test build](build/account-deletion-test-build.txt), [final Android results](build/account-deletion-device-final.txt). The initial test compilation missed two nullable arguments, subsequently fixed. A disconnected emulator was restarted. The [first completed Android run](build/account-deletion-device.txt) exposed the outdated nonexistent-account WorkManager fixture; the final run passed after its correction.

Rebuilt artifact: [app-release.apk](app/build/outputs/apk/release/app-release.apk), version 1.1 / code 2, 17,945,969 bytes. SHA-256: **6E1A58361A6307BFC5BAF97134812EBA5A4D50D56084B4A8908D2A372DB2F16D**. This supersedes the earlier PDF-build hash for the current artifact.

Physical-phone confirmation, accessibility/large fonts, final-account onboarding and lifecycle QA remain pending. Deletion affects local records only; exported backups and the actual SBI account are unchanged.
