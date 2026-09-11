# Feature progress and remaining work

Current audit: **11 September 2026**. This is the application's current progress tracker. [featureDevelopmentInfo.md](featureDevelopmentInfo.md) explains the implementation; [requirements.md](requirements.md) supplies numbered requirements; [VALIDATION.md](VALIDATION.md) retains dated test/build evidence. The 11 September PDF reconciliation implementation includes new tests, real-layout validation, schema migration and APK rebuilds; see the dated validation record.

The core ledger, encryption, manual tracking, ingestion/reconciliation and backup flows exist. **The app is not fully release-validated:** the supplied SBI Relationship Summary layout is validated, while other PDF layouts, notification adapters, real SMS/device behavior and the gaps below still require work.

## How to maintain this tracker

Every feature has a stable F-number, requirement reference, scope, implementation status, validation status/evidence, completed work, remaining work and next action. Keep IDs stable; add rows rather than renumbering existing ones. Update both root documents when code or verification changes, following [rules.md](rules.md).

Implementation statuses:

- **Completed:** the row's bounded implementation scope exists; this does not imply physical-device validation or completion of adjacent features.
- **Partial:** useful implementation exists, with specified missing behavior.
- **Unstarted:** in-scope work or required validation has not been performed.
- **Deferred:** optional/future work explicitly outside the current delivery boundary.
- **Intentionally excluded:** prohibited or deliberately absent capability, not a backlog promise.

Validation statuses are independent: **Recorded pass** means the dated run in VALIDATION.md, **Synthetic only** limits parser evidence, **Code inspected** means implementation evidence without a dedicated behavioral check, **Pending** means the stated check remains, and **N/A** applies to deferred/excluded work. Test names identify actual assertions, not inferred comprehensive coverage.

Requirement references use section numbers in requirements.md. M1–M7 refer to the approved implementation milestones: foundation/manual; PDF/basic reconciliation; SMS; matching/review; optional notifications; security/backup; analytics/release. The approved plan overrides older suggestions with Android 11+, multiple accounts initially, PDF-first support, separate verification/outcome/visibility, and replacement-only restore.

## Evidence index

| Key | Code or dated evidence |
| --- | --- |
| CORE | [Finance.kt](app/src/main/java/com/kiz9r/expense_tracker/domain/Finance.kt) and [ExpenseApplication.kt](app/src/main/java/com/kiz9r/expense_tracker/ExpenseApplication.kt). |
| DB | [Entities.kt](app/src/main/java/com/kiz9r/expense_tracker/data/Entities.kt), [LedgerDatabase.kt](app/src/main/java/com/kiz9r/expense_tracker/data/LedgerDatabase.kt), [schema 2](app/schemas/com.kiz9r.expense_tracker.data.LedgerDatabase/2.json) and historical [schema 1](app/schemas/com.kiz9r.expense_tracker.data.LedgerDatabase/1.json). |
| LEDGER | [LedgerRepository.kt](app/src/main/java/com/kiz9r/expense_tracker/data/LedgerRepository.kt) and [LedgerDao.kt](app/src/main/java/com/kiz9r/expense_tracker/data/LedgerDao.kt). |
| UI | [TrackerApp.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/TrackerApp.kt), [TrackerViewModel.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/TrackerViewModel.kt), [LedgerScreens.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/LedgerScreens.kt), [DetailScreen.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/DetailScreen.kt), [SettingsScreens.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/SettingsScreens.kt), [Components.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/Components.kt). |
| SMS | [MessageParsers.kt](app/src/main/java/com/kiz9r/expense_tracker/ingestion/MessageParsers.kt) and [IngestionServices.kt](app/src/main/java/com/kiz9r/expense_tracker/ingestion/IngestionServices.kt). |
| PDF | [StatementParser.kt](app/src/main/java/com/kiz9r/expense_tracker/ingestion/StatementParser.kt), [PdfTextExtractor.kt](app/src/main/java/com/kiz9r/expense_tracker/ingestion/PdfTextExtractor.kt), [StatementJobs.kt](app/src/main/java/com/kiz9r/expense_tracker/ingestion/StatementJobs.kt). |
| REC | [ReconciliationRepository.kt](app/src/main/java/com/kiz9r/expense_tracker/reconciliation/ReconciliationRepository.kt) and [StatementScreens.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/StatementScreens.kt). |
| SEC | [DatabaseKeys.kt](app/src/main/java/com/kiz9r/expense_tracker/security/DatabaseKeys.kt), [MainActivity.kt](app/src/main/java/com/kiz9r/expense_tracker/MainActivity.kt), [manifest](app/src/main/AndroidManifest.xml), [backup exclusions](app/src/main/res/xml). |
| BACKUP | [BackupCrypto.kt](app/src/main/java/com/kiz9r/expense_tracker/backup/BackupCrypto.kt), [BackupSnapshot.kt](app/src/main/java/com/kiz9r/expense_tracker/backup/BackupSnapshot.kt), [BackupValidation.kt](app/src/main/java/com/kiz9r/expense_tracker/backup/BackupValidation.kt), [BackupService.kt](app/src/main/java/com/kiz9r/expense_tracker/backup/BackupService.kt), [BackupDao.kt](app/src/main/java/com/kiz9r/expense_tracker/data/BackupDao.kt). |
| JVM | [FinanceTest.kt](app/src/test/java/com/kiz9r/expense_tracker/FinanceTest.kt); 14 tests recorded passed. |
| ROOM | [LedgerIntegrationTest.kt](app/src/androidTest/java/com/kiz9r/expense_tracker/LedgerIntegrationTest.kt); 17 repository/integration tests recorded passed, chiefly in-memory Room. |
| DEVICE | [PrivacyAndUiTest.kt](app/src/androidTest/java/com/kiz9r/expense_tracker/PrivacyAndUiTest.kt); 3 emulator tests for real encrypted UI, protected PDF and WorkManager staging. |
| PDF-JVM | [RelationshipParserTest.kt](app/src/test/java/com/kiz9r/expense_tracker/RelationshipParserTest.kt); synthetic real-layout structure, 120 rows, page order, columns, warnings and ranking. |
| PDF-ROOM / SAMPLE | [StatementReconciliationTest.kt](app/src/androidTest/java/com/kiz9r/expense_tracker/StatementReconciliationTest.kt); durable review, concurrency, summaries, ignored/repeated rows, refunds and opt-in read-only validation of the supplied PDF using an in-memory ledger. |
| MIGRATION | [StatementMigrationTest.kt](app/src/androidTest/java/com/kiz9r/expense_tracker/StatementMigrationTest.kt); encrypted version-1 database upgrade to version 2. |
| PDF-UI | [StatementUiTest.kt](app/src/androidTest/java/com/kiz9r/expense_tracker/StatementUiTest.kt); Compose resume/review/commit and ignored-row resolution. |
| RUN | [VALIDATION.md](VALIDATION.md); historical PDF run includes private-sample validation. Latest 11 September account-deletion run: 21 JVM passed; Android runner OK (39 tests), comprising 38 executed passes and one opt-in private-PDF skip; lint zero errors / 16 warnings, signed version 1.1 APK rebuilt and verified. No physical phone sign-off. |
| BUILD | [app/build.gradle.kts](app/build.gradle.kts), [version catalog](gradle/libs.versions.toml), [keep rules](app/src/main/keepRules/rules.keep), [README.md](README.md). |

## Foundation and manual tracking

| ID / feature | Requirement / plan | Scope | Implementation | Validation | Completed work | Remaining work | Next action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F001 Native architecture and toolchain | §§2,47–49,60,70–71; M1 | MVP | Completed | Recorded pass: BUILD, RUN; code inspected CORE/UI | Single Kotlin/Compose module, Hilt, Room, WorkManager, Navigation, repositories and manual use case; screens do not use DAOs. | No separate class for every illustrative use case or analytics package; no need to split merely to mirror suggested names. Android 11 QA is F038. | Preserve repository boundaries and pinned build setup. |
| F002 Encrypted database foundation | §§3,6,41; M1 | MVP | Completed | Recorded pass: DEVICE.manualTrackingFlowRunsOnEncryptedDatabase; SEC | SQLCipher with random Keystore-wrapped key, exported schemas 1 and 2, independent background key access. | Key-loss/device lifecycle QA is F032/F038; broad repository tests use plain in-memory Room. | Retain real SQLCipher persistence regression. |
| F003 Normalized records and field completeness | §§4–10,15,30,55; M1 | MVP | Partial | Code inspected: DB, CORE, REC; ROOM covers core links | Sixteen normalized tables; observations/evidence/metadata separate; status/outcome/visibility distinct; parser versions stored. | Single reference instead of separate UPI/bank/external values; missing evidence confidence/timestamps, account updatedAt and some proposed audit/category/rule fields; no extracted SMS balance/sourcePackage field. Some links live in JSON or application checks. | Decide required field additions, then introduce a tested migration; do not claim every proposed field exists. |
| F004 Schema migrations | §§6,57,58,70; M1 | MVP maintenance | Completed | Recorded pass: MIGRATION encrypted schema-1 to schema-2 test | Version 2 adds nullable import warnings and durable job review fields; explicit migration preserves account/import/job data and SQLCipher encryption. | Future schema changes require their own migration and tests. | Retain both schema exports and migration regression. |
| F005 Accounts and first launch | §§1,7,62,69; M1 | MVP | Completed | Recorded pass: DEVICE manual flow; ROOM.multipleMaskedAccountsRequireAssignment | Nickname/last4/type onboarding and additional SBI accounts; internal IDs; duplicate last4 requires review. | Physical onboarding QA is F038; account editing/deactivation is F006. | Retain collision and first-account regressions. |
| F006 Account maintenance | §7; M1 | MVP refinement | Partial | Code inspected: DB, LEDGER, UI | Active flag exists; account creation/list/selection and confirmed permanent local deletion work (F056). | No account rename/type-edit/deactivation UI or updatedAt field. | Add safe maintenance flow preserving historical account ownership. |
| F007 Manual entries and bank editing rules | §§31–33; M1/M4 | MVP | Partial | Recorded pass: DEVICE manual flow; ROOM.manualMetadataSurvivesVerificationAndCannotBeDeleted | Add debit/credit with date/time/account/category/notes/tags; edit/delete unverified unlinked manual records; verification protects bank facts. | Linked manual records cannot be financially edited/deleted, narrower than broad manual CRUD requirement; no explicit correction/unlink workflow or edit audit. Dedicated edit/delete rollback tests absent. | Define and document safe correction behavior for linked manual records, then test it. |
| F008 Metadata, tags, hiding and evidence detail | §§4,9–10,12–13,32–33,36,64; M1/M4 | MVP | Completed | Recorded pass: ROOM.manualMetadataSurvivesVerificationAndCannotBeDeleted; code inspected UI/LEDGER | Separate merchant/category/notes/tags, many-to-many tags, hide/show-hidden, original narration/evidence and detected/verified indicators. | Full category override and all detail UI actions lack dedicated tests; optional fields are F009. | Extend metadata-preservation assertions without conflating visibility with verification. |
| F009 Additional personal metadata | §12 | Future refinement | Unstarted | Pending; DB/UI inspected | Notes, merchant display and tags provide existing personal annotations. | Separate custom description and favorite merchant absent; excludedFromBudget is optional and belongs with future budgets. | Specify description/favorite behavior; defer budget exclusions with F047. |
| F010 Category management | §§11,38; M1 | MVP | Partial | Code inspected: DB/LEDGER/UI; defaults created in integration setup | All 25 named defaults; custom add/rename/delete-unused; history through transaction filters; rules accessible in Settings. | Category icons/active/audit fields absent; no dedicated category-history navigation from category management. CRUD protection lacks dedicated assertions. | Finish category navigation/refinements and verify system/in-use deletion protection. |
| F011 Local history, search and filters | §§35,39,66; M1 | MVP | Partial | Code inspected: LEDGER/UI; ROOM checks hidden filtering | 50-row chronological pages; account/category/source/verification/direction/date/amount/hidden filters; local merchant/narration/reference/notes/tag/category and numeric search. | No date group headings (Today/Yesterday/exact date); broad search/filter regression and multi-year performance unverified. | Add date grouping and exercise filter combinations with realistic synthetic volume. |
| F012 Dashboard and initial analytics | §§34,40,50–51; M1/M7 | MVP | Completed | Code inspected LEDGER/UI; ROOM refund/failure totals pass; RUN limited visual inspection | Monthly account selection, spending/income/refunds/net/count, category/merchant/daily breakdowns, comparison, recent/largest entries. | Full aggregate/month-boundary/hidden/transfer combinations not individually tested; profiling is F037. | Add targeted cross-month and exclusion aggregate assertions. |
| F013 Money and reporting dates | §§9,50–51; M1 | MVP | Completed | Recorded pass: JVM.moneyIsExactAndRejectsFractionalPaise and parsesDebitCreditAndChannels; CORE | Long paise, exact conversion/formatting, java.time, Asia/Kolkata; transaction/receipt/value dates distinguished where available. | Dedicated date normalization, leap/month boundary and late-receipt tests incomplete; real date-format coverage unverified. | Add boundary cases when extending source formats. |

| F056 Permanent local account deletion | User request 11 September 2026; §7; M1 | MVP refinement | Completed | Recorded pass: LedgerIntegrationTest account cleanup/rollback and AccountDeletionUiTest confirmation; 11 September account-deletion run in RUN | Settings account deletion with explicit scope and backup reminder; atomic cleanup of owned records, ignored-row decisions and drafts; preserves other accounts and shared data; clears invalid selection/preview; last account returns to onboarding; stale jobs cannot recreate data. | Physical-phone confirmation/accessibility QA pending; unassigned observations remain because ownership is uncertain; prior exported backups unchanged. | Verify confirmation and final-account onboarding on a physical phone; retain deletion/rollback regressions. |

## Statements and reconciliation

| ID / feature | Requirement / plan | Scope | Implementation | Validation | Completed work | Remaining work | Next action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F014 PDF boundary, SAF and passwords | §§17–18,43,54; M2 | MVP | Completed | Recorded pass: protected PDF, scanned/invalid/size/password-clear tests; SAMPLE Android extraction | PDFBox content-order extraction, SAF filenames, memory-only passwords, cancellation checks and 20 MB/250-page limits. | Phone-specific SAF provider and interruption behavior still needs device QA. | Run the same flow on the user's phone; preserve source PDFs. |
| F015 SBI statement layout | §§15,17–18,27,55,59; M2 | MVP | Completed | Recorded pass: SAMPLE; PDF-JVM 120-row/page-order fixtures | Supplied encrypted four-page SBI Relationship Summary parses all 41 rows; running/closing balances match; in-memory commit verifies all rows and repeat import is duplicate. Dedicated credit-first/integer-zero/date-boundary parser and UPI/DR/CR reference extraction. | Other SBI layouts are not certified; generic text-table adapter remains experimental; multi-account documents are rejected. | Add another layout only with a representative sample and synthetic regression fixtures. |
| F016 Resumable encrypted import jobs | §§18,46,54; M2 | MVP | Completed | Recorded pass: background staging plus PDF-ROOM draft/recreation/stale/cancellation/atomic cleanup tests | SAF filename retained; review token/choices persisted encrypted; unchanged preview resumes choices, changed candidates clear stale drafts; commit removes staging atomically; cancel prevents job recreation. | Interrupted extraction intentionally requires file/password reentry; physical process-death/provider QA remains F038. | Retain lifecycle regressions and verify phone resume/cancel. |
| F017 Duplicate and overlapping statements | §§16,25,53; M2/M4 | MVP | Completed | Recorded pass: JVM/ROOM duplicate tests; PDF-ROOM repeated-identical rows and ignored overlap; SAMPLE repeat | File/logical hashes retain multiplicity, official rows remain one-to-one, duplicate/overlap retries do not silently collapse genuine purchases. | Additional layout variants need fixtures as introduced. | Keep repeated/ignored overlap cases in the release suite. |
| F018 Strong-reference evidence matching | §§4,21,23–25,53,73; M4 | MVP | Completed | Recorded pass: JVM.exactReferenceIsOnlyAutomaticMerge; ROOM.smsNotificationStatementProduceOneVerifiedTransaction | Shared match/new/review engine; unique compatible reference attachment; conflicting financial facts reviewed; one canonical record with multiple evidence sources. | Reference types remain combined (F003); real source coverage remains F015/F024/F029. | Preserve conflict/account/currency guards in future parser changes. |
| F019 Fuzzy ranking and observation review | §§5,24,54,56; M4 | MVP | Completed | Recorded pass: PDF-JVM ranking; ROOM repeated-purchase/account collision tests; UI code inspected | Suggestions rank date/time/merchant/channel/narration with stable ties; candidate labels show date/time/source/reference/channel/verification; fuzzy choices remain explicit. | Phone usability/accessibility QA is F038. LIKELY_MATCHED denotes combined provisional evidence, not permission to auto-merge fuzzy matches. | Preserve explicit confirmation and test real-source ambiguity. |
| F020 Atomic import and persisted decisions | §§18,21,32,56–57; M2/M4 | MVP | Completed | Recorded pass: ROOM stale/conflict rollback; PDF-ROOM concurrent import/ingestion, truncated preview, decision replay and staging cleanup | Decisions and official rows commit atomically; refreshed candidates and row counts are checked; statement-only recovery and manual category/name/notes/tags preserve provenance. | Physical interruption QA remains F038. | Keep rollback and concurrent-writer regressions. |
| F021 Balance validation and exception status | §§26–27,33; M2/M4 | MVP | Completed | Recorded pass: PDF-JVM arithmetic; PDF-ROOM warning reload/hiding/backup; SAMPLE balances | Every official row participates in arithmetic; exact warnings persist, ignored rows prevent reconciliation, hidden/transfer exclusions do not alter statement totals. | Genuine bank balance errors remain visible until a corrected statement is obtained. | Never add a dismiss-to-reconciled shortcut for financial discrepancies. |
| F022 Reconciliation summaries and statement history | §§15,26,37,56; M2/M7 | MVP | Completed | Recorded pass: PDF-ROOM summaries/audit; PDF-UI full Compose flow | Preview/history show row decisions, refund/reversal counts, official versus linked debit/credit totals, calculated/official balances and detailed warnings; ignored rows resolve with confirmation and retained decision revisions. | Physical phone usability/accessibility QA remains F038. | Retain the full UI regression and verify the same flow on phone. |
| F023 Refunds, reversals and owned transfers | §§28–29,40; M4/M7 and defaults | MVP | Partial | Recorded pass: ROOM partial/full/generic-credit tests; PDF-ROOM cross-month reversal and late original debit | Both movements retained, bounded partial/full links, original outcome/category preserved; statement refunds net in posting month, including late original debit. Manual owned-transfer exclusion and ATM category exist. | Manual refund selection still uses original UUID; no paired owned-transfer workflow. | Improve manual credit-to-debit selection and verify transfer exclusions separately. |

## Incoming sources and recurring activity

| ID / feature | Requirement / plan | Scope | Implementation | Validation | Completed work | Remaining work | Next action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F024 Incoming SBI SMS | §§8,19–21,43–46,55; M3 | MVP | Partial | Synthetic only: JVM parsesDebitCreditAndChannels, financialKindsAreClassifiedBeforeSpending, discard/masking tests; code inspected SMS | New multipart receiver, allowlist, versioned modular classification; debit/credit/UPI/card/ATM/fee/refund/reversal/failure/mandate handling; unknown review. | Real SBI formats/installer delivery unvalidated; shared extractor lacks SMS balance and distinct reference types; no broad per-format fixture corpus. | Validate sanitized actual formats and incoming delivery on the user's phone. |
| F025 Idempotent background processing | §§21,46,53,59,65; M3/M5 | MVP | Completed | Recorded pass: JVM.duplicateSmsIdentityIsStableButTimestampMatters; ROOM.concurrentDuplicateIngestionIsIdempotent; code inspected SMS | Stable observation IDs, unique events, bounded receiver IO, worker retry/startup processing, first-unlock guards and identifier-only work requests. | Duplicate multipart broadcasts, late/out-of-order delivery, listener updates, receiver timeout/failure, force-stop/OEM behavior unverified. | Execute delivery/retry/reboot matrix in F038. |
| F026 Failed payments | §§20,29; M3/M4 | MVP | Completed | Recorded pass: ROOM.failedPaymentsAndMandatesDoNotIncreaseSpending and verifiedStatementCanConfirmPreviouslyFailedPayment | Failed outcome retained and excluded from spending; later compatible statement can confirm posted payment. | Actual provider failed-versus-pending language needs source fixtures. | Retain outcome-transition regression as formats expand. |
| F027 Mandates and execution | §§20,30,40; M3/M7 | MVP | Partial | Synthetic only: JVM create/cancel/execute classification; ROOM mandate non-spending assertion | Separate mandate records/list, creation/cancellation status, execution debit classification, reminder held unknown. | Paused/expired/unknown lifecycle, schedule/frequency display and execution-to-mandate linkage absent; cancellation/execution spending path lacks full integration coverage. | Define full mandate lifecycle and add create/cancel/execute flow tests. |
| F028 Deterministic recurring suggestions | §§40,62; M7 | MVP enhancement | Completed | Code inspected LEDGER/UI; no dedicated recurring assertion | Same merchant/amount across at least three months becomes a dashboard suggestion; no money movement scheduled. | False-positive/negative behavior unvalidated; advanced subscription/forecast scope is F047. | Test three-month threshold, account isolation and exclusion behavior. |
| F029 PhonePe / Google Pay notifications | §§8,22–23,43–46,53; M5 | Optional | Partial | Synthetic only: JVM.notificationParsersRecognizeProvidersAndIgnoreOtp; ROOM multi-source evidence test | Explicit packages, NotificationListenerService, shared extractor, stable keys and uncertain account review. | Real provider-specific layouts and notification update semantics unvalidated; adapters share generic extraction. | Validate separate provider fixtures and update/repost sequences before declaring support. |
| F030 Other UPI notification providers | §§8,22; M5 | Optional / future | Deferred | N/A; SMS parser inventory inspected | Extensible parser interface. | Paytm, BHIM and Amazon Pay adapters absent. | Add a provider only with validated fixtures after core reconciliation readiness. |

## Security, backup and release quality

| ID / feature | Requirement / plan | Scope | Implementation | Validation | Completed work | Remaining work | Next action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F031 Offline privacy and permission onboarding | §§3,41,43–45,63,65,69; M1/M3/M6 | MVP | Completed | Code inspected SEC/UI; RUN merged manifest no INTERNET; JVM OTP/promotion tests | Private storage, backup/transfer exclusions, opt-in explained SMS/notification access, SAF, no login or broad permissions; manual/import remain independent of grants. | Denial/revocation/installer behavior and complete offline flow checks pending on phone. | Execute permission/offline checklist in F038; preserve minimal manifest. |
| F032 App lock and screenshot protection | §§3,41,43,67; M6 | MVP | Partial | Code inspected SEC/UI; RUN Settings inspection only | Optional Android biometric/device credentials, onStop relock, fail-closed loading, FLAG_SECURE default and configurable screenshots, first-unlock-independent ingestion. | Physical lock lifecycle/enrollment/SAF return testing pending; missing Keystore material throws without a verified in-app recovery path. | Test hardware lifecycle and provide actionable non-destructive key-loss recovery. |
| F033 Portable encrypted backup export | §§41–42; M6 | MVP | Completed | Recorded pass: JVM.backupAuthenticatesPasswordAndEveryByte; ROOM fresh-database encrypted-file test | Versioned full logical snapshot excluding transient jobs/device keys, AES-GCM authenticated header, PBKDF2, password policy and SAF output. | All entity types/settings together and large archive/failed-output behavior not comprehensively tested; memory scaling is F037. | Add an archive fixture containing every persistent record type. |
| F034 Archive validation and replacement restore | §§42,54,57; M6 | MVP | Partial | Recorded pass: ROOM snapshot/fresh-database restore; PDF-ROOM warning and statement-history backup round trip | Version-2 snapshots retain warnings and decision revisions; versions 1/2 restore; authentication, IDs, review targets, statement dates/sequence and linked facts checked before atomic replacement. | Full all-record semantic validation, interrupted/concurrent restore and physical recovery remain broader backup work. | Complete the archive matrix and failure-injection checks before recovery sign-off. |
| F035 Physical uninstall/reinstall recovery | §§42,58,72 scenario 12; M6 | Required release validation | Unstarted | Pending physical test; automated independent fresh Room database is narrower | Portable file round trip and fresh-database replacement pass as supporting evidence. | Real uninstall/reinstall or phone-change restoration, regenerated SQLCipher key, all metadata/evidence/settings comparison and permission reestablishment. | Use a disposable synthetic ledger on a physical device; record dated recovery results. |
| F036 Merchant normalization and rules | §§14,38,52; M7 | MVP | Partial | Recorded pass: JVM.merchantRulesAreDeterministic tests predicates; code inspected LEDGER/UI | Original narration retained; exact/contains/prefix rename/category rules, explicit priority, stable tie-break and user overrides; detail can offer rule creation. | Regex and automatic repeated-reclassification suggestions absent; no comprehensive rule priority/tie/user-override integration test. | Test precedence and add repeated-reclassification suggestion; keep regex deferred unless selected. |
| F037 Multi-year performance | §§40,46,66; M1/M7 | Required release validation | Partial | Code inspected LEDGER/PDF/BACKUP; synthetic 120-row parser test only | Indexed ledger and 50-row history, SQL dashboard aggregates, bounded PDF/backup inputs. | No multi-year benchmarks; statement/review/import lists and backup snapshots can load full collections; screen paging is not database paging for official rows. | Profile realistic multi-year synthetic data and paginate/stream measured bottlenecks. |
| F038 Android/device, permissions and accessibility QA | §§2–3,41,43–46,65–67,69; M3/M6/M7 | Required release validation | Partial | RUN Android 17 emulator, DEVICE UI flow; code inspected M3 theme and labels | Emulator manual flow, SQLCipher, worker and password-PDF tests; limited Dashboard/Settings visuals; light/dark and text status support in code. | Physical Android 11+, biometric/PIN, reboot before/after first unlock, lock with ingestion, permission deny/revoke, OEM battery/force-stop, notification access, offline SAF, TalkBack, large fonts, contrast/touch targets not signed off. | Run and date each check on emulator and user's phone; retain failures as open rows. |
| F039 Automated acceptance and edge-case suite | §§58–59,72; all milestones | MVP validation | Partial | Recorded pass: RUN 21 JVM and 36 Android tests, including PDF repository/migration/real-sample and complete Compose flow | New layout, 120-row and page-order fixtures, repeated/ignored overlaps, concurrency, drafts, cancellation, warnings/backup, encrypted migration and refund dates covered. | Remaining non-PDF source/device/backup coverage must remain explicit. | Retain the PDF run and continue broader device/source acceptance checks. |
| F040 Release APK and installation | §§2,70–71; M7 | MVP deliverable | Partial | Recorded pass: RUN version 1.1 debug/test install, release assembly and v2 signing verification; 21 JVM/36 Android tests | Optimized signed APK with schema-2 migration; README and supported PDF layout updated. | 16 lint warnings remain; physical install/update, recovery and accessibility are unverified (F035/F038). | Verify phone install/update/recovery and resolve actionable warnings. |
| F041 Development documentation and tracking | §70; current documentation plan | Maintenance | Completed | Documentation-only audit: one root reference, seven package copies removed, local links and coverage checked | Unified architecture/flows/schema/file responsibilities/limitations; stable feature tracker; updated rules/README; VALIDATION retained as dated evidence. | Ongoing updates whenever implementation or verification changes; no background automation. | Maintain both root documents in the same change and append dated evidence when checks run. |

## Deferred and intentionally excluded scope

Deferred items below are not MVP release blockers. Multiple SBI accounts and basic recurring suggestions were originally future examples but are already implemented under F005/F028. Excluded actions remain absent even though §61 also lists some as things that can wait: the stronger read-only rules in §§3,45,63 and the approved plan govern.

| ID / feature | Requirement / plan | Scope | Implementation | Validation | Completed work | Remaining work | Next action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F042 CSV / XLSX statement import | §17; M2 defaults | Future | Deferred | N/A; PDF parser interface inspected | StatementParser boundary exists; PDF selected for first release. | Structured-format implementations and fixtures absent. | Revisit only after PDF/core reconciliation validation. |
| F043 Other banks and credit cards | §62 | Future | Deferred | N/A | Accounts and ingestion boundaries provide extension points. | Non-SBI rules, card statement/balance semantics and UI absent. | Define independent supported formats before expanding. |
| F044 Multiple currencies | §§7,9,50,62 | Future | Deferred | N/A | INR stored explicitly; exact minor-unit arithmetic. | Currency precision, conversion/reporting and validation beyond INR absent. | Specify currency semantics before schema changes. |
| F045 Cloud sync / encrypted cloud backup | §§1,3,61–62,65 | Future | Deferred | N/A | Portable encrypted local backup exists. | Remote storage, key strategy, conflict handling and user consent absent; no INTERNET permission. | Keep offline default; require a separate approved design. |
| F046 AI categorization | §§14,61; defaults | Future | Deferred | N/A | Deterministic rules implemented. | No model/service or AI classification. | Reconsider only under an explicit future scope change. |
| F047 Budgets, savings, subscriptions and forecasts | §§12,40,61–62 | Future | Deferred | N/A | Basic recurring suggestions and mandate records exist. | Budgets/targets, subscription analysis, recurring forecasts, budget exclusion metadata absent. | Define a bounded enhancement after core release validation. |
| F048 Multiple users and family profiles | §§1,61–62 | Future | Deferred | N/A | Multiple owned accounts for one person supported. | User/profile separation, shared data and multiuser workflows absent. | Keep one-user scope until separately approved. |
| F049 Web dashboard and iOS | §§2,61 | Future | Deferred | N/A | Native Android project only. | Other clients absent. | No work before a separate platform plan. |
| F050 Account Aggregator / bank API | §§61–62; defaults | Future | Deferred | N/A | Parser/reconciliation boundaries reusable in principle. | Consented read-only integrations absent; no automatic bank access. | Require a separate design; do not add login scraping. |
| F051 Investment tools, tax filing and financial advice | §61 | Beyond initial tracker | Deferred | N/A | Investments is only a transaction category. | No investment analysis/execution, tax workflow or advice feature. | Keep out of current delivery; any execution remains excluded by F053. |
| F052 Historical SMS scan, OCR and merge restore | §§17,19,42,45; approved defaults | Outside approved scope | Intentionally excluded | Code inspected SMS/PDF/BACKUP | Incoming-only SMS, text-only PDF, replacement-only restore. | No historical scan, scanned-PDF/phone-screen OCR or merge restore planned. | Preserve boundaries; ordinary unsupported-file messages should remain actionable. |
| F053 Payments and banking credentials | §§3,61,63; defaults | Never part of tracker | Intentionally excluded | Code inspected manifest/SEC/SMS; JVM ignored OTP tests | No fund transfer, UPI sending, bill payment, mandate approval, SBI login/password, UPI/card PIN or OTP collection. | No implementation work intended; APK/local edits must never claim to change bank records or impersonate SBI. | Preserve read-only behavior in every new feature. |
| F054 Screen monitoring and unrelated data collection | §§3,43,45,63; defaults | Never part of tracker | Intentionally excluded | Code inspected manifest/SMS; RUN no INTERNET | No accessibility scraping, screenshots as ingestion, bank-site scraping, broad storage, contacts/location/camera/microphone, ads or remote financial analytics. | No implementation work intended. | Preserve explicit package/sender filtering and minimal permissions. |
| F055 Parser reprocessing and richer rule syntax | §§14,55 | Optional / future | Deferred | N/A; version fields and rule types inspected | Parser versions stored; deterministic basic rule types available. | No version-selective reprocess/migration workflow or regex rule support. | Design safe evidence-preserving reprocessing when a validated parser update requires it. |

## Acceptance scenarios

Every scenario from §72 is represented below. Automated evidence uses synthetic inputs; a repository test bypassing the Android receiver is not proof of SMS delivery.

| Scenario | Feature IDs | Implementation evidence | Validation status | Remaining check / next action |
| --- | --- | --- | --- | --- |
| A01 — Debit SMS creates one provisional transaction | F018, F024–F025 | SMS parser -> enqueue/processPending -> provisional canonical record | Partial: JVM debit/identity tests; ROOM concurrentDuplicateIngestionIsIdempotent asserts one event/transaction, but no dedicated end-to-end receiver/provisional assertion | Verify incoming multipart SMS on phone and explicitly assert provisional state before statement verification. |
| A02 — UPI notification and SMS become one canonical transaction | F018, F025, F029 | Shared strong-reference matching and evidence attachment | Recorded pass for synthetic compatible observations: ROOM.smsNotificationStatementProduceOneVerifiedTransaction asserts one record before statement | Validate real provider updates and missing-reference/account review; fuzzy evidence requires user confirmation by design. |
| A03 — Statement verifies the existing transaction | F018, F020 | Statement commit updates bank facts and adds official evidence | Recorded pass: ROOM multi-source verification; SAMPLE validates actual layout in memory | Retain these checks when extending layouts. |
| A04 — Statement recovers a transaction with no SMS | F015, F020 | New official rows create verified canonical transactions | Recorded pass: PDF-ROOM.statementOnlyRowsAreVerifiedAndSameValuePurchasesRemainDistinct asserts verified/source/evidence; SAMPLE verifies 41 statement-only records | Retain statement-only regression. |
| A05 — Reimporting the same statement creates no duplicates | F017 | File and multiplicity-preserving logical fingerprints | Recorded pass: ROOM exact/logical repeats, PDF-ROOM repeated/overlapping rows, SAMPLE actual PDF repeat | Retain duplicate protections for new formats. |
| A06 — Mandate creation does not create spending | F027 | Separate mandate entity; no spending movement on authorization | Recorded pass: ROOM.failedPaymentsAndMandatesDoNotIncreaseSpending and JVM classification | Extend to cancellation/reminder/execution and actual SMS wording. |
| A07 — Failed payment does not increase spending | F026 | Failed outcome excluded; statement can later confirm posted | Recorded pass: ROOM failed/mandate totals and verifiedStatementCanConfirmPreviouslyFailedPayment | Validate real failed/pending wording. |
| A08 — Reversal is linked to and marks the original debit | F023 | Separate credits link to the original debit | Recorded pass: PDF-ROOM cross-month statement refunds asserts final REVERSED outcome, links and posting-month totals; late-debit test asserts REFUNDED and one link | Manual refund-picker refinement remains F023. |
| A09 — Two genuine ₹500 payments stay separate | F017–F019 | Same-value rows retain distinct canonical assignments; fuzzy choices require review | Recorded pass: PDF-ROOM verifies two ₹500 purchases and repeated-identical official rows with one-to-one assignments | Retain reference/time and overlapping-import fixtures. |
| A10 — User renames/categorizes without losing originals | F008, F020, F036 | User metadata remains separate from statement facts | Recorded pass: PDF-ROOM manual category/name and official narration/reference assertions; ROOM notes/tags preservation | Broader merchant-rule precedence testing remains F036. |
| A11 — Hiding a verified transaction retains statement evidence | F008, F021 | Metadata visibility does not change official evidence or balances | Recorded pass: ROOM hidden history/retained row; PDF-ROOM confirms official and linked totals plus warnings survive hiding/backup | Phone show-hidden/evidence UI QA remains F038. |
| A12 — Encrypted backup survives uninstall/reinstall | F033–F035 | Portable encryption and transactional replacement restore | Partial: ROOM.encryptedFileRestoresIntoAnIndependentFreshDatabase plus snapshot rejection/crypto tests; no actual uninstall/reinstall | Restore every persistent entity type on a physical fresh install and compare; record key/permission behavior. |

## Requirement coverage index

This index covers every numbered requirement area. It points to status rows; inclusion is not a completion claim. Cross-cutting §§1,60,70–71,73 depend on all applicable MVP rows.

| Requirement section | Area | Tracker IDs |
| --- | --- | --- |
| 1 | Product goal | F005–F040, F045, F048 |
| 2 | Platform | F001, F038, F049 |
| 3 | Privacy | F002, F031–F033, F053–F054 |
| 4 | Observations vs canonical vs metadata | F003, F008, F018 |
| 5 | Confidence/status | F003, F008, F019, F026 |
| 6 | Normalized Room database | F002–F004 |
| 7 | Accounts | F003, F005–F006, F044 |
| 8 | Raw events/sources | F003, F024–F025, F029–F030 |
| 9 | Canonical transaction | F003, F008, F013, F044 |
| 10 | Evidence | F003, F008, F018 |
| 11 | Categories | F010 |
| 12 | User metadata | F008–F009, F047 |
| 13 | Tags | F008 |
| 14 | Merchant rules | F036, F046, F055 |
| 15 | Statement import record | F003, F015, F022 |
| 16 | Duplicate statements | F017 |
| 17 | Import formats | F014–F015, F042, F052 |
| 18 | Import flow | F014–F016, F020 |
| 19 | SMS framework | F024, F052 |
| 20 | SMS classification | F024, F026–F027 |
| 21 | Missing/late SMS recovery | F018, F020, F024–F025 |
| 22 | Notification providers | F029–F030 |
| 23 | Notification + SMS + statement | F018, F029; A02–A03 |
| 24 | Matching engine | F018–F019 |
| 25 | Repeated transactions | F017–F019; A09 |
| 26 | Reconciliation summary | F021–F022 |
| 27 | Balance validation | F015, F021 |
| 28 | Refunds/reversals | F023; A08 |
| 29 | Failed payments | F026; A07 |
| 30 | Mandates | F003, F027 |
| 31 | Manual CRUD | F007 |
| 32 | Bank editing rules | F007–F008, F020 |
| 33 | Delete/hide | F007–F008, F021; A11 |
| 34 | Dashboard | F012 |
| 35 | Transaction list | F011 |
| 36 | Detail | F008 |
| 37 | Statements screen | F022 |
| 38 | Categories/rules UI | F010, F036 |
| 39 | Local search | F011 |
| 40 | Analytics | F012, F023, F027–F028, F037, F047 |
| 41 | Security | F002, F031–F034, F038 |
| 42 | Backup/restore | F033–F035, F052; A12 |
| 43 | Permissions | F014, F024, F029, F031–F032, F038, F054 |
| 44 | Permission onboarding | F024, F029, F031, F038 |
| 45 | No screen monitoring | F031, F052, F054 |
| 46 | Background work | F016, F024–F025, F029, F037–F038 |
| 47 | Architecture | F001 |
| 48 | Repository layer | F001 |
| 49 | Use cases | F001 |
| 50 | Money | F012–F013, F044 |
| 51 | Dates/times | F012–F013 |
| 52 | Merchant normalization | F036 |
| 53 | Idempotency | F017–F018, F025, F029 |
| 54 | Errors | F014, F016, F019, F034 |
| 55 | Parser versioning | F003, F015, F024, F055 |
| 56 | Reconciliation review | F019–F020, F022 |
| 57 | Atomic transactions | F004, F020, F034 |
| 58 | Tests | F004, F035, F039 and acceptance matrix |
| 59 | Synthetic data/edge cases | F015, F025, F039 |
| 60 | MVP scope | F001–F040 except optional/deferred rows |
| 61 | Can-wait features | F045–F051, F053 |
| 62 | Future expansion | F005, F028, F043–F045, F047–F048, F050 |
| 63 | Read-only safety | F031, F053–F054 |
| 64 | Detected versus verified UX | F008 |
| 65 | Offline operation | F025, F031, F038, F045 |
| 66 | Performance | F011, F037 |
| 67 | Accessibility/UI | F032, F038 |
| 68 | Navigation | F001, F008, F010–F012, F022, F027, F029, F031–F034 |
| 69 | Onboarding | F005, F031, F038 |
| 70 | Deliverables | F001, F004, F039–F041 and evidence index |
| 71 | Development order | M1–M7 status below |
| 72 | Acceptance | A01–A12 above |
| 73 | Canonical transaction principle | F003, F008, F018–F020, F039 |

## Milestone position and next work

| Milestone | Current position | What prevents sign-off |
| --- | --- | --- |
| M1 Foundation/manual | Usable core implemented; refinements partial | F003/F006–F011 completeness and relevant F037–F039 validation. |
| M2 PDF/basic reconciliation | Supported Relationship Summary parser, durable review and complete summaries implemented | Physical SAF/lifecycle QA; other layouts remain outside certified support. |
| M3 Incoming SMS | Synthetic-tested pipeline | Actual formats/delivery F024 and permission/background QA F038. |
| M4 Matching/review | Core invariants implemented with regressions | PDF ranking/reporting and refund edge cases are covered; manual refund UX F023 and remaining non-PDF acceptance checks persist. |
| M5 Optional notifications | Experimental adapters exist | Provider/update validation F029; does not block core MVP. |
| M6 Security/backup | Encryption and core export/restore implemented | Semantic validation F034 and physical lock/recovery F032/F035/F038. |
| M7 Analytics/release | Initial analytics and signed APK available | Rule/UI refinements F036, scale/accessibility F037–F038, full acceptance and release checks F039–F040. |

Next work, in practical order:

1. Preserve the validated Relationship Summary layout and its passing PDF-UI regression; validate actual SMS formats separately (F024). Keep real records private and commit only synthetic equivalents.
2. Close archive semantic-validation/recovery gaps and prove physical reinstall recovery (F032/F034/F035).
3. Continue remaining manual-refund and core UI refinements (F023/F006–F011); retain the completed durable PDF review and exception-history regressions.
4. Fill the explicit acceptance/edge-case test gaps, run Android 11/phone/permission/background/accessibility checks and profile multi-year data (F037–F039).
5. Resolve actionable lint warnings and verify signed APK update/install using the retained signer (F040). Preserve the dated evidence and update this tracker.
6. Validate optional notification formats separately; leave deferred features outside MVP delivery.
