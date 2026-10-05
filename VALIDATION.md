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

## 14 September 2026 — SMS system update (version 1.2)

Implemented version 2 conservative SMS extraction, opt-in/permission/unlock intake, bounded same-sender multipart assembly, idempotent encrypted observation writes, recoverable background processing and explicit review. Settings now reflects actual SMS permission, diagnostic counts, receipt time and pending retry. Added boot-after-unlock/app-update recovery; no historical SMS permission or scan. Observation JSON gains nullable balanceMinor and parseWarning; Room schema remains version 2.

Executed:

- **33 JVM tests passed**: FinanceTest 14, RelationshipParserTest 7, SmsParserTest 12.
- **Default Android run: OK (49 tests), 54.102 seconds**. Of these, 47 executed successfully; the private-PDF test and external-SMS fixture were skipped because their opt-in arguments were absent.
- **Opt-in SmsReceiverDeliveryTest passed**, 22.323 seconds, on the Android 17 disposable emulator. RECEIVE_SMS was granted only on that emulator. Long synthetic text from AD-SBIINB traveled through Android multipart assembly/SMS_RECEIVED, the actual receiver, encrypted intake and WorkManager. Repeated delivery produced one provisional transaction, full assembled text was retained as evidence, and a synthetic OTP was not persisted. Its synthetic account was deleted and tracking disabled by test cleanup.
- Eight new Room tests cover off/denied/pre-unlock gates; concurrent multipart retries and statement verification; uncertain/pending review with account-free ignore; mask collision and target persistence; malformed stored data followed by valid SMS; mandate cancellation versus late creation; failure → success → late failure; and statement-first/late SMS evidence.
- Compose review test confirms uncertain SMS cannot be blindly committed, exposes manual entry, and permits explicit ignore.
- Debug/test installation, unit tests, lint and signed release assembly passed. Final lint: **zero errors, 16 warnings**. The newly introduced URI KTX warning was fixed.
- The complete default suite ran before the final URI KTX substitution and receiver-fixture-only correction; the final source was rebuilt/unit-tested/linted and its actual receiver test passed. No unnecessary repeat of the unchanged financial suite was performed.

Build command:

    .\gradlew.bat :app:testDebugUnitTest :app:installDebug :app:installDebugAndroidTest :app:lintDebug :app:assembleRelease --offline

Default Android command:

    adb -s emulator-5554 shell am instrument -w com.kiz9r.expense_tracker.test/androidx.test.runner.AndroidJUnitRunner

Opt-in delivery:

    adb -s emulator-5554 shell pm grant com.kiz9r.expense_tracker android.permission.RECEIVE_SMS
    adb -s emulator-5554 shell am instrument -w -r -e class com.kiz9r.expense_tracker.SmsReceiverDeliveryTest -e sms_delivery true com.kiz9r.expense_tracker.test/androidx.test.runner.AndroidJUnitRunner

After the test reports sms_fixture=ready, run the synthetic [fixture](app/src/androidTest/sms-emulator-fixture.py) from a second terminal:

    python app/src/androidTest/sms-emulator-fixture.py --adb <absolute-path-to-adb>

Evidence: [final build](build/sms-build-delivery.txt), [default Android run](build/sms-device.txt), [successful SMS broadcast run](build/sms-broadcast-device-final.txt), [unit reports](app/build/reports/tests/testDebugUnitTest), [lint](app/build/reports/lint-results-debug.html).

The [initial broadcast attempt](build/sms-broadcast-device.txt) timed out because the emulator accepted raw-PDU console commands without delivering them. A plain-text probe did deliver; switching the fixture to the emulator SMS text command with an alphanumeric origin and a message longer than 160 characters produced the passing test. This was a fixture/console-path correction; sender filtering and receiver permissions were retained.

Artifact: [app-release.apk](app/build/outputs/apk/release/app-release.apk), **version 1.2 / code 3**, **17,978,833 bytes**. SHA-256: **8C11A7A129526591A0CFA3CD75FFBE82A1814A1DA965C3DE8E1DDEC26B594013**. APK v2 signature verified with the existing personal signer, certificate SHA-256 **CAE7C0C84FB2D31A72FD32C8376294BBB77107DE173FC8B67E21B07B0C4EE152**. No signing material was changed.

Remaining: the user explicitly deferred real SBI SMS samples. Actual bank formats, physical-phone carrier delivery, denied/revoked permission UI, first-unlock/reboot/OEM/force-stop/app-lock lifecycle, accessibility and signed phone-update QA are not certified. Unit/integration unlock and permission gates are simulations; no physical reboot claim is made. No real PDF/password was used or reread during this update. Earlier PDF validation remains dated evidence.

## 14 September 2026 — Supplied SMS XML validation (version 1.2.1)

The user subsequently supplied sms-20260914211524.xml. A secure, opt-in host SAX test streams the XML with external entities/DTDs disabled, selects incoming SBI messages, and checks independent expected classifications and financial fields. Only aggregate results are logged. The original file is unchanged (SHA-256 checked), excluded by /sms-*.xml, and never copied into app storage, the emulator or synthetic fixtures. This adds no historical-SMS/XML import capability.

The corpus contains 2,162 SMS records, of which **299 are incoming SBI messages**. The initial SBI-prefix audit found 272; an independent broader sender audit found another 27 from ATMSBI/CBSSBI. The final allowlist explicitly supports those sender families. Final classification/field audit: **zero mismatches**.

| Observed family | Count | Expected handling |
| --- | ---: | --- |
| UPI debit / compact-date credit | 130 / 33 | Posted financial observations |
| NEFT / linked IMPS credit | 11 / 2 | Posted financial observations |
| Card debit | 7 | Posted observation; account assignment required |
| CBS credit / cheque credit / cash deposit | 3 / 1 / 2 | Posted financial observations |
| Cheque clearing / NACH execution debit | 1 / 5 | Posted financial observations |
| UPI mandate created / cancelled | 11 / 13 | Separate mandate evidence; no spending |
| UMRN mandate issued | 1 | Separate mandate evidence; no spending |
| Scheduled AutoPay / collect request | 29 / 1 | UNKNOWN / Needs Review; no spending |
| Informational, promotional or credential messages | 49 | Discard before persistence |

Parser 2.1.0 fixes observed currency-less amounts, compact dates, counterparties, UMN/UMRN and cheque/card references, AC account labels, and transaction-versus-balance amounts. Card suffixes never identify accounts. Longer masked account suffixes are reduced to four digits in persisted evidence; account labels require a word boundary so merchant text cannot manufacture an account. Existing ingestion identities and Room schema 2 remain unchanged.

Final executed checks:

- **45 JVM tests passed**, no skips: FinanceTest 14, RelationshipParserTest 7, SmsParserTest 12, SbiObservedLayoutsTest 11, ProvidedSmsXmlTest 1. Reusable regressions contain invented values only.
- **Android runner OK (53 tests), 41.324 seconds**, on the disposable Android 17 emulator: 51 executed passes and two opt-in skips (private PDF and external SMS delivery). Twelve SMS Room cases include statement matching, distinct same-value UMN mandates, card-only account review/deduplication, and NACH/cash movement amounts. Earlier external-broadcast and private-PDF evidence remains historical; those fixtures were not rerun here.
- Final unit tests, debug/test installation, lint and signed release assembly succeeded after the account-boundary guard. Lint: **zero errors, 16 warnings**. Build time: 2m 14s.
- Exactly one root featureDevelopmentInfo.md remains. Local links in README, rules, both root tracking/development documents and this evidence file resolve.

Final build command (SBI_SMS_FIXTURE_PATH set to the supplied local XML only in the host test process):

    .\gradlew.bat :app:testDebugUnitTest :app:installDebug :app:installDebugAndroidTest :app:lintDebug :app:assembleRelease --offline

Final Android command:

    adb -s emulator-5554 shell am instrument -w com.kiz9r.expense_tracker.test/androidx.test.runner.AndroidJUnitRunner

Evidence: [final build](build/sms-xml-delivery-build.txt), [final Android run](build/sms-xml-delivery-device.txt), [unit reports](app/build/reports/tests/testDebugUnitTest), [lint](app/build/reports/lint-results-debug.html). Initial audit/regression failures exposed currency-less debit and service filtering gaps, strict comma validation, and test-expectation escaping/trailing-punctuation mistakes; all were resolved before the final passing run.

Artifact: [app-release.apk](app/build/outputs/apk/release/app-release.apk), **version 1.2.1 / code 4**, **17,978,833 bytes**. SHA-256: **974C0AD40A95023F0D786C0EB2BC52255FCE9F09782DB242037F856E5E5C502E**. APK v2 signature verified with the existing personal signer, certificate SHA-256 **CAE7C0C84FB2D31A72FD32C8376294BBB77107DE173FC8B67E21B07B0C4EE152**. No signing material changed.

Remaining: observed layouts are validated against this sample, not every SBI format. Other ATM, fee, refund/reversal and notification formats need source fixtures. Carrier delivery, phone installer/permission changes, first-unlock/reboot/OEM/force-stop/app-lock behavior, accessibility and signed phone-update QA remain pending. No physical-phone or historical-ledger-import claim is made.

## 14 September 2026 — Backup and recovery completion (version 1.3)

Implemented database-independent archive authentication and strict field/type decoding, financial/evidence/statement/decision/mandate/rule/settings validation, onboarding restore, invalidated/cancelled preview handling, and startup recovery when the encrypted ledger cannot open. Key-loss recovery requires a validated backup and explicit confirmation, restores into a separate encrypted database, reopens it successfully and then activates it; original inaccessible files remain untouched. Users close/reopen after activation. Normal restore remains a single Room transaction. SMS and notification opt-in checks occur inside their observation-write transactions so stale callbacks cannot bypass settings disabled by restore. Existing ETBACK01 encryption and archive versions 1/2 remain supported; Room schema stays at version 2.

Final executed checks on the disposable Android 17 emulator:

- **47 JVM passes, one opt-in XML skip (48 reported)**. Three new BackupArchiveTest cases cover legacy/current archives, absent and incorrectly typed fields, integer overflow/fractional versions, random salts/nonces and truncated envelopes. Existing finance, PDF layout and SMS unit tests pass.
- **Android runner OK (65 tests), 60.267 seconds**: **62 executed passes and three opt-in skips** (private PDF, externally injected SMS and the separately orchestrated reinstall fixture). Nine BackupRecoveryTest cases cover every persistent record family, password/tamper/malformed-file rejection, failed output/password clearing, inconsistent relationships and review targets, SQL write-failure rollback retaining drafts, cancellation both while queued and after deletion begins, and concurrent enabled SMS/notification intake. Existing reconciliation, migration, UI and SMS regressions pass.
- **BackupUiTest passes**: a fresh empty ledger exposes restore without account creation; an authenticated file preview makes no changes, cancelling confirmation makes no changes, and explicit confirmation restores the records. The SAF activity result is supplied by an instrumentation monitor; real phone/provider interaction remains separate QA.
- **DeviceKeyRecoveryTest passes** with isolated preferences and database paths: removing wrapped key material prevents opening, invalid backup input leaves the original active, valid recovery activates a separately encrypted/reopenable ledger, the original ciphertext hash remains unchanged, and recovery refuses to replace a healthy ledger through that path.
- **Actual emulator uninstall/reinstall proof passes**, using the opt-in BackupReinstallTest in prepare/verify phases. The host retained only encrypted invented-data archives and comparison hashes; the application was uninstalled and reinstalled. The new SQLCipher database-key fingerprint differed, the complete record digest matched, and SMS/notifications remained disabled. Prepare and verify each report OK (1 test); verify completed in 2.474 seconds. This adds one distinct passing Android test beyond the default suite.
- Final build, debug/test installation, unit tests, lint and release assembly pass. **Lint: zero errors, 16 existing warnings.** Durable preference activation explicitly checks commit success rather than using a helper that discards that result. Build completed in 2m 57s.
- The exact signed release installed and cold-launched successfully on the emulator; its UI exposed **Restore an encrypted backup** on onboarding. Cold launch: 1,388 ms. No real account, SMS XML or PDF was imported or used in this work.

Final build command:

    .\gradlew.bat :app:testDebugUnitTest :app:installDebug :app:installDebugAndroidTest :app:lintDebug :app:assembleRelease --offline

Default Android command:

    adb -s emulator-5554 shell am instrument -w com.kiz9r.expense_tracker.test/androidx.test.runner.AndroidJUnitRunner

Reinstall proof (destructive only to the explicitly acknowledged disposable emulator's synthetic app installation; the script rejects physical-device serials):

    python app/src/androidTest/backup-reinstall-fixture.py --adb <absolute-adb-path> --serial emulator-5554 --confirm-disposable

Evidence: [final build](build/backup-notification-final-build.txt), [final Android suite](build/backup-notification-final-device.txt), [reinstall result](build/backup-reinstall-run.txt), [prepare](build/backup-reinstall-proof/prepare.txt), [verify](build/backup-reinstall-proof/verify.txt), [signed cold launch](build/backup-release-launch.txt), [signature](build/backup-signature.txt), [unit reports](app/build/reports/tests/testDebugUnitTest), [lint](app/build/reports/lint-results-debug.html).

Development checks initially found fixture issues: legacy-version digests were compared across different versions, a test returned Boolean instead of Unit, and the SAF monitor lacked a MIME type. The corrected fixture intercepts the intended picker result and waits for the returning Compose hierarchy. These failed attempts remain in [development suite](build/backup-development-device.txt) and [initial UI run](build/backup-ui-device.txt). No app confirmation or validation guard was relaxed to pass the tests.

Artifact: [app-release.apk](app/build/outputs/apk/release/app-release.apk), **version 1.3 / code 5**, **17,995,217 bytes**. SHA-256: **D0DD3A0256AF475A693BF6DE3F4BBEA8E2214C24AC74803FDB40208D4A3E9F34**. APK v2 signature verified with the retained personal signer, certificate SHA-256 **CAE7C0C84FB2D31A72FD32C8376294BBB77107DE173FC8B67E21B07B0C4EE152**. Signing material is unchanged and is not part of the financial archive.

Remaining: physical-phone reinstall/phone-change recovery, real SAF provider interruptions, hardware Keystore/app-lock lifecycle, Android 11 and accessibility checks. Large archives remain bounded to 64 MB but are processed in memory; multi-year memory/performance profiling remains open. A failed SAF output may leave an incomplete destination, which the UI tells the user to remove/retry. No physical-phone recovery or hardware key-invalidation claim is made.

## 2 October 2026 — v1.5.0 foundations and budgets candidate (release blocked)

Implemented version **1.5.0 / code 8**, Room schema **3**, backup format **4** (accepts 1–4). App ID and name remain unchanged. This is an implementation/test record, **not a released update**: the configured signing key does not match the retained v1.4/v1.4.1 APKs.

Final build command:

    .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --console=plain

[Final build](build/v150-accessibility-build.txt): BUILD SUCCESSFUL, 1m33s. JVM reports **60 tests: 59 passed, 1 optional private-SMS fixture skipped, 0 failures/errors**. Lint reports **0 errors / 31 warnings**. Existing warnings include dependency/version and source/resource cleanup recommendations; no dependency upgrades were made for this release.

[Final full Android suite](build/v150-final-accessibility-device.txt): **OK (88 tests)** in 156.463s on the Android 17 Medium_Phone emulator. This is **85 executed passes and 3 optional skips**: private PDF validation, externally injected SMS fixture, and the separate actual-uninstall/reinstall fixture. Earlier private-source and actual-reinstall evidence remains dated history, not a claim that those fixtures were rerun today. One initial attempt immediately after emulator restart exited with “Process crashed” before test discovery; a subsequent complete run passed. The prior complete run also passed before the large-font navigation adjustment.

Coverage includes:
- The two outstanding v1.4.1 regressions: valid multi-category visual fixtures and actual screenshot-window capture/protection/recreation. MainActivity now reads readiness in composition before applying FLAG_SECURE; no lock/screenshot assertions were removed.
- ₹1 sent/₹1 received, ordinary income/refunds/negative totals, independent drilldowns, navigation roots and statement/review flows.
- Encrypted schema1→2→3 and schema2→3 preservation tests, existing encryption/metadata/evidence/ingestion/reconciliation regressions, authenticated archive1–4 decoding/replacement/rollback and planning relationship validation.
- Ten PlanningIntegrationTest cases for checkpoint cutoffs/conflicts, hidden/failed/future movements, split sums and parent totals, proportional refunds/personal overrides, paired transfers, budget scope/exclusions, late facts/rollover, alert baselines/deduplication/corrections, archived accounts, coverage snapshots and backup4 rollback.
- Five PlanningMathTest cases for rollover modes/caps/deficits, zero allocations, checked arithmetic, deterministic paise allocation and thresholds.
- The existing synthetic five-year / 20,000-transaction AnalyticsIntegrationTest workload passed. This checks bounded history and aggregate responsiveness, not physical-device frame rendering or large planning/backup memory behavior.

[Final visual test](build/v150-final-visual-device.txt): **OK (1 test)** in 20.194s, run with PlanningUiTest and v150_visual=true. Six synthetic captures are in [final visual evidence](build/v150-final-visuals): empty/populated dark Plan, budget detail, balances, light Plan and 200% font Plan. The UI creates a ₹5,000 budget against ₹4,200 spending, checks ₹800 remaining and a ₹20,800 calculated balance from a ₹25,000 opening. Final 200% font capture was inspected; bottom navigation uses accessible named icons at large font sizes and the app bar identifies the screen. The Compose test host does not exercise MainActivity's status-bar color handling. Full TalkBack, every dialog at every font size, small physical screens and contrast/touch-target audits remain pending.

### Signing and upgrade blocker

The built candidate is **18,491,789 bytes**, SHA-256 **3D1C035A40D301C89403C6733B209230F29B08C25F9CD5A38CC95B36ADD80449**. apksigner verified its certificate SHA-256 **CAE7C0C84FB2D31A72FD32C8376294BBB77107DE173FC8B67E21B07B0C4EE152**. This is the workspace's older configured personal signer.

The retained [v1.4.1 APK](app/release/app-release.apk), code 7, and the retained v1.4 APK both instead use certificate SHA-256 **A3379A4B38B27921299A0E619D29811D3366B104E5A09EE05A0B82F2104BFCA3**. Installing the candidate with adb install -r failed with **INSTALL_FAILED_UPDATE_INCOMPATIBLE**. No uninstall or key replacement was used to bypass Android's protection.

An isolated disposable emulator (5556, separate build/v150-upgrade-data.img) was seeded through the retained v1.4 UI with account UpgradeFixture, ₹1 debit and ₹1 credit. A cold launch showed two manual transactions, ₹1 spending, ₹1 income and ₹0 cash flow ([before XML](build/v150-upgrade-before.xml)). The same-signer retained v1.4.1 control update succeeded ([baseline XML](build/v150-upgrade-v141-baseline.xml)). The v1.5.0 in-place update and exact release cold launch remain **unverified** until the original signing configuration is provided. Its synthetic data image is retained for continuing this check. The original signing key location has been requested; no passwords are written in this evidence.

### Remaining scope and limitations

- Automatic approval review rejected expanding permanent account deletion to erase the new planning records. The implementation guards such accounts and supports archival/reactivation; paired surviving-account cleanup on permanent deletion remains unimplemented. The existing deletion flow remains for accounts without planning history.
- Budget repository alert/dedup logic is tested; actual platform push permission denial/grant, background notification delivery and restore-to-notification behavior still require device checks. Timing remains OS-controlled.
- Historical balance UI is bounded to the latest twelve months. Wider historical navigation, full phone/TalkBack checks, Android 11 hardware behavior and larger planning/backup profiling remain open.
- No representative private PDF/SMS fixture was modified or rerun today. Optional notification adapters and unseen source layouts retain existing support limits.
- Payment schedules/reminders, forecasts and projected shortfall alerts are subsequent stages.
- Required release sign-off is withheld. Obtain the original signer, rebuild, verify the exact signature, complete the synthetic in-place upgrade/cold launch and then deliver the APK.

## 2 October 2026 — screenshot-reported IDE inspection fixes

Fixed the settings-query keyword inspection by quoting `key` and renaming the bind parameter to settingKey. Removed unused imports, the obsolete CategoriesScreen/deprecated icon and old analytics StateFlows. Constructor qualifiers now use @param:ApplicationContext. Both ViewModels require lifecycle-supplied SavedStateHandle; test fixtures construct it explicitly. Coroutine timeout/delay values use Duration, the redundant Executor SAM wrapper is removed, and test schema assets use the non-deprecated directories API. The activity inherits its unchanged application label. The incoming SMS permission remains with a narrowly scoped, commented SmsAndCallLogPolicy exception for personal sideloading; this does not establish Play Store eligibility.

[Initial build](build/inspection-cleanup-build.txt) passed in 2m12s. [Final build](build/inspection-cleanup-final-build.txt), after the same saved-state correction in AnalyticsViewModel, passed in 58s. Commands: assembleDebug, assembleDebugAndroidTest, testDebugUnitTest and lintDebug. No Kotlin compiler warning/error lines were emitted. Unit results: **59 passed, 1 optional private-fixture skip, 0 failures/errors**. Final lint: **0 errors / 26 warnings**, down from 31. Remaining findings are 15 dependency/version notices, 7 unused resources, 3 dependency trust-manager findings and 1 KTX suggestion on an explicitly checked synchronous encryption-key preference commit; its Boolean failure check is deliberately retained.

[Full Android regression](build/inspection-cleanup-device.txt): **OK (88 tests)** in 149.53s, meaning 85 executed passes and the same 3 optional skips. It covers settings reads, encryption, migrations, backup/rollback, SMS, navigation, planning and screenshot assertions. Following the final AnalyticsViewModel constructor-only adjustment, focused navigation/visual/planning/real-activity/analytics checks passed **OK (12 tests)** in 77.205s: [final focused results](build/inspection-cleanup-final-device.txt).

No schema/backup/version/signing change was made, and no release APK was rebuilt for this cleanup. The previously recorded release-signing blocker remains. Gradle/compiler/lint and emulator checks were run; Android Studio's interactive inspection itself was not automated.

## 3 October 2026 — budget carry-forward clarity and financial colors

The shared FinancialPalette now separates red spending, green credits/refunds and yellow warnings from emerald actions. Zero amounts, account balances and base allocations remain neutral. Notices have explicit Information/Warning/Error/Success severity, icons and theme-specific foreground/container colors. Budget presentation separates base, carried surplus/deficit, effective allocation, net spending and remaining; negative allocations have no percentage progress. The two-way rollover label and explanation include the multi-month deficit example. Existing accounting, budget settings, Room schema, archive format and app version are unchanged.

[Final build and checks](build/financial-colors-final-checks.txt): **BUILD SUCCESSFUL in 49s**, running assembleDebug, assembleDebugAndroidTest, testDebugUnitTest and lintDebug. JVM results: **61 reported, 60 passed, 1 optional private-SMS fixture skipped, 0 failures/errors**. Lint: **0 errors / 26 existing warnings**. The new notice's modifier-order warning was corrected rather than suppressed.

[Full Android regression](build/financial-colors-full-device.txt): **OK (91 tests)** in **146.532s** on emulator-5554: **88 executed passes and 3 optional skips** (private PDF, externally injected SMS and separate uninstall/reinstall fixture). This includes navigation, statement review, screenshot protection, recovery, encrypted migrations, budget repositories and the five-year/20,000-transaction workload. The full run preceded only the final Notice parameter-order/named-argument lint correction; focused post-correction evidence follows below.

Added regressions cover the ₹5,000 allocation / ₹12,000 spending deficit continuing over subsequent months, positive-only caps, unchanged Off/unused-only modes, late refunds correcting earlier deficits, repeated recalculation without extra transactions, and preserved historical revisions. PlanningMathTest now has six tests; PlanningIntegrationTest has eleven. FinancialColorsUiTest checks rendered Home, transaction, detail, budget and chart amounts for debits, credits, refunds, negative net spending and neutral zero in both themes. It also verifies financial text and notice-container contrast of at least 4.5:1 and category graphics against their card surface of at least 3:1.

Synthetic [visual captures](build/financial-visuals) were inspected for dark/light Home, transaction rows, debit/refund charts, category segments, warning/error/success/information notices and budget deficits at 200% font scale. Labels and signs remain present. Category separators distinguish red-family segments. The Compose fixture bypasses MainActivity system-bar styling; its light status-bar icons are not evidence of production system-bar behavior. Physical-phone/TalkBack checks and exhaustive dialog/small-screen coverage remain open.

Two initial UI-fixture failures were corrected without weakening behavior assertions: scroll to the merged Home card before inspecting its child amount, and wait for the budget-save snackbar to disappear before the real pointer click on the lower detail action. The subsequent full suite passed. The retained-release signing-key mismatch remains a release blocker; no signed APK was rebuilt or delivered for this refinement.

[Final post-correction UI checks](build/financial-colors-final-ui-device.txt): **OK (4 tests)** in **36.515s**, covering both FinancialColorsUiTest methods, PlanningUiTest and StatementUiTest against the final rebuilt APKs. Both visual capture flags were enabled. Exactly one root featureDevelopmentInfo.md remains; all local Markdown link targets in the six root reference/tracking documents resolve, and git diff --check passes.

## 3 October 2026 — multi-category budgets and resilient live updates

Implemented revision-linked category sets, parent/child union coverage, overlap validation across revision and frozen-period boundaries, category/account/month labels, and searchable accessible checkbox selection. Revisions default to next month; current-month changes preview spending/remaining and preserve prior snapshots. Amount-only edits retain frozen coverage. Loading/error/stale/retry states are shared by Home and Plan; calculation failures are handled inside each observation event so later invalidations can recover. Background alert invalidation includes category selections. No actual user transaction was modified to address the display report.

Room schema **4** adds budget_revision_categories; encrypted migrations backfill legacy revision selections without rewriting financial facts or monthly coverage. Backup version **5** includes those selections, validates them before transactional replacement and accepts versions 1–5. Version-4 archives reconstruct original single-category roots. Category deletion guards include all revision selections.

[Final production build](build/multi-budget-final-build.txt): **BUILD SUCCESSFUL in 44s**, with assembleDebug, assembleDebugAndroidTest, testDebugUnitTest and lintDebug. [Final test-only rebuild](build/multi-budget-final-test-build.txt) passed in **13s** after correcting two test assertions. JVM reports **61 tests: 60 passed, 1 optional private-SMS skip, 0 failures/errors**. Lint reports **0 errors / 26 existing warnings**. No compiler warnings were emitted in the final production build.

[Focused regressions](build/multi-budget-regressions-device.txt): **OK (18 tests)** in **45.658s**, covering shared category totals, split/refund accounting, revision preservation, scope conflicts, live observation/error recovery, archive compatibility, manual-entry UI/edit/recreation and existing planning regressions. Subsequent added tests cover SMS/statement/restore invalidation and next-month hierarchy conflicts. Encrypted schema-2 and schema-3 checks passed in the earlier focused run; schema-1 upgrade is retained in the full suite.

The first baseline attempt crashed before test discovery during emulator startup. Its retry timed out waiting for transaction-save completion, before asserting budget amounts. The fixture now waits for the budget-save snackbar to disappear before continuing. The exact original phone-specific failure was therefore **not conclusively reproduced**. The real navigation regression verifies ₹5,000 → ₹4,900 after a ₹100 manual expense, ₹4,850 after editing it to ₹150, and ₹4,825 after another ₹25 entry, without restarting. It also exercises saved-state recreation, a deliberately induced calculation error with visible stale results, Retry and automatic recovery after valid configuration is restored.

The initial full run reported 101 tests with two test-only failures: an old backup-version assertion still expected 4, and a stale-message assertion targeted the notice container rather than its text child. Both were corrected; no financial, migration, screenshot or lock assertion was removed. One new coroutine test also needed an explicit Unit return for JUnit discovery in an earlier focused attempt.

Synthetic dark/light picker captures are retained in [multi-category visual evidence](build/multi-budget-visuals). They show selected names/counts, searchable rows and disabled inherited children. Local font overrides do not reliably scale dialog windows; actual Android font-scale evidence is recorded below after its separate check. The existing retained-release signing-key mismatch and physical-phone/TalkBack limitations remain. No release APK was rebuilt or delivered.

[Final full Android run](build/multi-budget-final-device.txt): **OK (101 tests)** in **177.044s**: **98 executed passes and 3 optional skips** (private PDF, externally injected SMS and separate uninstall/reinstall fixture). This includes all seven MultiCategoryBudgetTest cases, both PlanningUiTest cases, the picker test, encrypted migrations from schemas 1/2/3, backup/rollback, canonical reconciliation, navigation, actual screenshot protection and the existing five-year/20,000-transaction workload. No production code changed after the final 44-second build; the subsequent rebuild changed test assertions only.

[Actual Android 200% font test](build/multi-budget-system-font-device.txt): **OK (1 test)** in **8.596s** with system font_scale=2.0. The dark/light dialog captures prefixed system-200 in build/multi-budget-visuals were inspected: labels wrap, inherited-child explanation remains readable, and selection/Done controls remain operable. The emulator setting was restored and verified as **1.0**. The test host's background/status-bar layout is not the production MainActivity layout. Full physical-device/TalkBack and every small-screen/keyboard combination remain unverified.

Documentation verification: one root featureDevelopmentInfo.md; local link targets resolve; git diff --check passes. README and requirements describe shared budgets and schema4/backup5 compatibility. F072 records completed implementation, executed validation and remaining phone/signing limits.

## 5 October 2026 — set-aside money per account

Implemented requirements §86 under v1.5.0/code 8: per-account virtual reserves, explicit add/release, partial/full/change/remove expense funding, Home/Plan integration, paginated history, loading/error/retry, financial-edit/reconciliation guards, schema 5 and backup 6 (restoring 1–6). No app identity/signing change or live bank interaction.

[Final debug/unit/lint build](build/set-aside-final-build.txt) completed successfully in **25s**, following an earlier 41-second build. JVM results: **63 reported, 62 passes, 1 optional private-SMS fixture skip, zero failures/errors**. Lint: **0 errors, 26 existing warnings**. New ReserveMathTest covers checked arithmetic and eligible movements. Debug and Android test APKs were installed in place on the synthetic Medium_Phone emulator; no uninstall was used.

[Initial focused Android run](build/set-aside-focused-device.txt) ran 13 checks: **12 passed, 1 UI fixture failure**. All eight ReserveIntegrationTest cases and all four encrypted upgrades (schemas 1, 2, 3, 4 → 5) passed. Repository coverage includes unchanged transactions/evidence/budgets/balances, partial/full funding, refund/hiding behavior, unknown/negative unreserved balances, concurrent insufficient-fund attempts, stable retries, stale editor rejection, deletion/edit guards, account isolation, SMS/statement evidence, duplicate imports, incompatible statement rollback, backup-6 round trips, legacy defaults, malformed restore, paginated history and error/retry/recovery.

The first UI failure read a lazy Home account card before scrolling it into composition; no financial calculation failed. The fixture now scrolls to the account section before asserting its exact amounts. [Focused UI rerun](build/set-aside-ui-device.txt): **OK (1 test)** in **31.112s**, retaining all assertions. It exercises Plan reserve creation, saved-state recreation, partial funding, Home figures, release, full funding/removal, budget preservation and warning acknowledgement. Screenshots in [reserve visual evidence](build/set-aside-visuals) were inspected for dark/light readability and large text; the local Compose font override does not prove dialog system font scaling. Full-suite and actual system-font follow-up results are recorded below when completed.

Release delivery remains blocked by the retained original signing key mismatch. No release APK was delivered. Physical-phone, TalkBack, OEM behavior and exhaustive small-device checks remain unverified. All new fixtures and captures are synthetic.

[Full Android run](build/set-aside-full-device.txt): **OK (111 tests)** in **142.852s**, with **108 executed passes and 3 optional skips** (private PDF, externally injected SMS and separate reinstall fixture). This includes existing budget, balance, split, navigation, screenshot protection, encrypted recovery, SMS/reconciliation and five-year/20,000-transaction regressions, plus the new reserve and migration cases.

Actual Android font_scale=2.0 testing initially passed functionally, but screenshot inspection found the keyboard obscured lower dialog controls. Both reserve dialogs were adjusted to handle IME insets explicitly without changing financial behavior or weakening assertions. [System-font rerun](build/set-aside-system-font-device.txt): **OK (1 test)** in **18.194s**. Updated system-200 screenshots show Save/Confirm and Cancel above the keyboard; long body content scrolls. The keyboard's own font-change banner is outside app control. Font scale was restored and verified as **1.0**. The full-suite result precedes this two-dialog inset refinement; its final normal-font UI check is recorded separately below.

[Final normal-font UI run](build/set-aside-ui-final-device.txt): **OK (1 test)** in **16.873s**, covering both themes, funding edits/removal, warnings and Home/Plan navigation after the inset refinement. The final 25-second build/unit/lint run passed with unchanged counts above. Final normal-font captures replace the earlier captures in build/set-aside-visuals. [Final debug cold launch](build/set-aside-cold-launch.txt) returned **Status: ok**, **COLD**, **1786 ms**. This is emulator debug evidence, not a signed release upgrade claim.

Final documentation checks: exactly one featureDevelopmentInfo.md, no missing local links in the maintained root documents, and git diff --check passed. Requirements §86 and F073 map SA01–SA08 to the reserve implementation and verification; physical-device and signing limits remain explicit.
