# Application development reference

This is the single development reference for the SBI Expense Tracker. It consolidates the foundation and all seven former package documents. Read [requirements.md](requirements.md) for product requirements, [leftout.md](leftout.md) for current implementation and verification status, [VALIDATION.md](VALIDATION.md) for dated executed checks, and [rules.md](rules.md) for maintenance rules. Setup, installation and recovery instructions remain in [README.md](README.md).

Last audited: 11 September 2026. This document describes existing code; it does not certify every requirement as complete. The approved plan keeps Android 11+, multiple SBI accounts, INR, English UI, PDF-first import and replacement restore. Notification ingestion is optional. The SBI Relationship Summary savings-account layout is validated against the supplied four-page encrypted sample; the earlier generic text-table parser remains experimental.

## Table of contents

- [Architecture and execution flow](#architecture-and-execution-flow)
- [Build and dependencies](#build-and-dependencies)
- [File responsibilities](#file-responsibilities)
- [Financial domain and invariants](#financial-domain-and-invariants)
- [Database schema and persistence](#database-schema-and-persistence)
- [Screens and state management](#screens-and-state-management)
- [SMS and notification ingestion](#sms-and-notification-ingestion)
- [PDF extraction and staged imports](#pdf-extraction-and-staged-imports)
- [Matching and reconciliation](#matching-and-reconciliation)
- [Security and permissions](#security-and-permissions)
- [Encrypted backup and restore](#encrypted-backup-and-restore)
- [Analytics and merchant rules](#analytics-and-merchant-rules)
- [Testing and release](#testing-and-release)
- [Known limitations and extension boundaries](#known-limitations-and-extension-boundaries)

## Architecture and execution flow

One Gradle app module separates domain, data, ingestion, reconciliation, security, backup and ui/features packages. Analytics currently lives in DAO aggregates, repository queries and dashboard UI; there is no separate analytics package.

ExpenseApplication initializes Hilt. AppModule provides Gson and SQLCipher-backed Room. MainActivity hosts Compose and the biometric/device-credential gate. Background Android components obtain the same repositories through a Hilt entry point.

The normal flow is Compose screen -> TrackerViewModel / SaveManualTransaction -> LedgerRepository or ReconciliationRepository -> Room -> Flow -> StateFlow -> lifecycle-aware Compose collection. Screens never access DAOs. SaveManualTransaction is the explicit manual-entry use case; other application operations are methods on repositories/services rather than separate classes for every suggested use case.

Incoming sources become observations. Reconciliation may attach evidence to an existing canonical transaction, create a transaction, record a mandate, or hold an observation for review. A statement stages official rows, previews decisions, then commits all financial changes atomically. Bank facts, user metadata and original evidence remain separate.

## Build and dependencies

Configuration lives in [app/build.gradle.kts](app/build.gradle.kts), [root build configuration](build.gradle.kts), [version catalog](gradle/libs.versions.toml), [wrapper settings](gradle/wrapper/gradle-wrapper.properties) and [daemon JVM settings](gradle/gradle-daemon-jvm.properties). These files, rather than this table, are authoritative for future version changes.

| Dependency / tool | Existing configuration and purpose |
| --- | --- |
| Gradle / AGP / JVM | Gradle 9.6.0, AGP 9.4.0, Java 25 daemon; Android source compatibility Java 11; compile/target SDK 37, minimum SDK 30. |
| Kotlin / Compose / Material 3 | Compose compiler plugin 2.2.10 and Compose BOM 2026.02.01; native UI, theme and test semantics. |
| Hilt / KSP | Hilt 2.60.1 and KSP 2.3.9 generate injection and Room bindings. |
| Room / SQLite / SQLCipher | Room 2.8.4, AndroidX SQLite 2.6.2, SQLCipher Android 4.17.0; typed persistence and encrypted Room support factory. |
| Navigation / Lifecycle | Navigation Compose 2.9.4 and Lifecycle 2.11.0; routes, ViewModel state and lifecycle-aware Flow subscriptions. |
| WorkManager | 2.11.1; durable event processing and staged statement parsing. Financial payloads are stored in encrypted Room, not WorkManager input. |
| Biometric / Fragment | Biometric 1.1.0 and Fragment 1.8.9; Android-owned biometric/device-credential prompt hosted by MainActivity. |
| PDFBox-Android | 2.0.27.0; machine-readable PDF text extraction and password opening, separated from SBI layout parsing. |
| Gson | 2.13.2; observation payloads, staged parse results and versioned logical backup serialization. |
| Test libraries | JUnit, AndroidX test, Compose UI tests, Room testing and coroutine testing; see the catalog for pins. |

Android Storage Access Framework supplies scoped document selection/export. Coroutines move extraction and ingestion off the UI thread. No banking service or remote API is used.

Room schema export is configured through KSP. Schema version 2 adds nullable statement warning JSON and import-job review-token/choice JSON. MIGRATION_1_2 preserves existing financial records and queued jobs; an instrumentation test opens an encrypted schema-1 database and verifies Room migration and retained data. Every subsequent schema change requires another explicit tested migration. Destructive fallback is forbidden.

Release optimization/R8 is enabled. [Keep rules](app/src/main/keepRules/rules.keep) preserve Gson DTOs and Room data records. The optional PDFBox JPEG2000 decoder is omitted because imports extract text without decoding images.

## File responsibilities

All Kotlin paths below are relative to the application package and link to their actual files.

| File | Responsibility |
| --- | --- |
| [ExpenseApplication.kt](app/src/main/java/com/kiz9r/expense_tracker/ExpenseApplication.kt) | Hilt application and AppModule providers, including encrypted database construction. |
| [MainActivity.kt](app/src/main/java/com/kiz9r/expense_tracker/MainActivity.kt) | Theme, lifecycle locking, secure-window policy and biometric/device-credential prompt. |
| [domain/Finance.kt](app/src/main/java/com/kiz9r/expense_tracker/domain/Finance.kt) | Enums, Observation, MatchCandidate/MatchDecision, MatchingEngine, money/date utilities, hashes, masking and normalization; no Android dependencies. |
| [data/Entities.kt](app/src/main/java/com/kiz9r/expense_tracker/data/Entities.kt) | Normalized Room records and query projections. |
| [data/LedgerDatabase.kt](app/src/main/java/com/kiz9r/expense_tracker/data/LedgerDatabase.kt) | Schema version and DAO registry. |
| [data/LedgerDao.kt](app/src/main/java/com/kiz9r/expense_tracker/data/LedgerDao.kt) | Parameterized queries, writes, reactive history and SQL aggregates. |
| [data/LedgerRepository.kt](app/src/main/java/com/kiz9r/expense_tracker/data/LedgerRepository.kt) | Validation, manual CRUD, metadata/tags, categories, rules, settings, history and SaveManualTransaction. |
| [data/BackupDao.kt](app/src/main/java/com/kiz9r/expense_tracker/data/BackupDao.kt) | Complete logical snapshot reads and ordered replacement writes. |
| [ingestion/MessageParsers.kt](app/src/main/java/com/kiz9r/expense_tracker/ingestion/MessageParsers.kt) | Versioned SBI classification/extraction and allowlisted PhonePe/Google Pay adapters. |
| [ingestion/IngestionServices.kt](app/src/main/java/com/kiz9r/expense_tracker/ingestion/IngestionServices.kt) | SMS receiver, notification listener, ingestion worker, scheduling and Hilt entry point. |
| [ingestion/StatementParser.kt](app/src/main/java/com/kiz9r/expense_tracker/ingestion/StatementParser.kt) | Parser interface, typed statement/row results, SBI parser dispatcher, generic experimental text-table layout, fingerprints and merchant extraction. |
| [ingestion/SbiRelationshipStatementParser.kt](app/src/main/java/com/kiz9r/expense_tracker/ingestion/SbiRelationshipStatementParser.kt) | Validated SBI Relationship Summary layout with credit-first columns, integer zeros, dated balances and repeated page headers. |
| [ingestion/PdfTextExtractor.kt](app/src/main/java/com/kiz9r/expense_tracker/ingestion/PdfTextExtractor.kt) | SAF input limits, in-memory PDF opening, password handling and IO text extraction. |
| [ingestion/StatementJobs.kt](app/src/main/java/com/kiz9r/expense_tracker/ingestion/StatementJobs.kt) | Encrypted staging, WorkManager parse jobs, result persistence, resume and cleanup. |
| [reconciliation/ReconciliationRepository.kt](app/src/main/java/com/kiz9r/expense_tracker/reconciliation/ReconciliationRepository.kt) | Observation processing, account assignment, previews, decisions, atomic commit and refund linking. |
| [reconciliation/StatementReview.kt](app/src/main/java/com/kiz9r/expense_tracker/reconciliation/StatementReview.kt) | Durable review DTOs, import validation, official balance warnings and preview/history summaries. |
| [security/DatabaseKeys.kt](app/src/main/java/com/kiz9r/expense_tracker/security/DatabaseKeys.kt) | Random SQLCipher passphrase and Keystore wrapping; refuses replacement keys for an inaccessible existing ledger. |
| [backup/BackupCrypto.kt](app/src/main/java/com/kiz9r/expense_tracker/backup/BackupCrypto.kt) | Portable password derivation and authenticated encryption format. |
| [backup/BackupSnapshot.kt](app/src/main/java/com/kiz9r/expense_tracker/backup/BackupSnapshot.kt) | Versioned logical archive DTO. |
| [backup/BackupValidation.kt](app/src/main/java/com/kiz9r/expense_tracker/backup/BackupValidation.kt) | Structural/financial validation before replacement. |
| [backup/BackupService.kt](app/src/main/java/com/kiz9r/expense_tracker/backup/BackupService.kt) | Snapshot, SAF export, authenticated inspection and transactional restore. |
| [ui/features/TrackerApp.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/TrackerApp.kt) | Five-tab navigation, onboarding and secondary routes. |
| [ui/features/TrackerViewModel.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/TrackerViewModel.kt) | Shared StateFlow state, subscriptions, actions/errors, import jobs and restore preview. |
| [ui/features/Components.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/Components.kt) | Scrollable layouts, labeled fields/choices, text notices, cards, confirmation and lock dialogs. |
| [ui/features/LedgerScreens.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/LedgerScreens.kt) | Accounts/onboarding, dashboard, transaction filters and manual form. |
| [ui/features/DetailScreen.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/DetailScreen.kt) | Evidence, editable metadata, hiding, transfer classification and manual/refund actions. |
| [ui/features/StatementScreens.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/StatementScreens.kt) | PDF preview, row decisions, history/detail and observation review. |
| [ui/features/SettingsScreens.kt](app/src/main/java/com/kiz9r/expense_tracker/ui/features/SettingsScreens.kt) | Categories, rules, mandates, permissions/security and backup UI. |
| [ui/theme](app/src/main/java/com/kiz9r/expense_tracker/ui/theme) | Theme.kt, Color.kt and Type.kt define Material theme, colors and typography. |
| [AndroidManifest.xml](app/src/main/AndroidManifest.xml) and [XML resources](app/src/main/res/xml) | Component/permission declarations and cloud/device-transfer backup exclusions. |

## Financial domain and invariants

Amounts are positive Long paise; direction supplies the sign. Money uses exact BigDecimal conversion and rejects fractional paise/overflow. Float is used only for visual progress. Reporting uses Asia/Kolkata via java.time; event timestamps, receipt times, transaction dates and statement value dates remain distinct where available. Missing source timestamps fall back to available source/receipt information.

Verification (PROVISIONAL, LIKELY_MATCHED, VERIFIED, NEEDS_REVIEW), payment outcome (POSTED, FAILED, REVERSED, PARTIALLY_REFUNDED, REFUNDED), metadata visibility and owned-transfer classification are independent. Enum availability does not mean every status has its own active workflow: fuzzy observations are normally held in review.

One canonical transaction may have several evidence records. Statements verify bank facts; local names, categories, notes and tags do not modify SBI's ledger. Hiding must not remove official evidence or affect statement balance validation.

Account identity is a generated internal ID, never the last four digits. Automatic suffix assignment requires exactly one active account. Collisions and missing identifiers require review.

SHA-256 supplies stable identities/fingerprints, not source authenticity. Account masking handles labeled full account numbers; it is not a general detector of every sensitive numeric string.

## Database schema and persistence

[Exported schema 2](app/schemas/com.kiz9r.expense_tracker.data.LedgerDatabase/2.json) (with [schema 1](app/schemas/com.kiz9r.expense_tracker.data.LedgerDatabase/1.json) retained for migration tests) records exact fields, indices and foreign keys. Entities.kt is the readable model.

| Table | Ownership, content and constraints |
| --- | --- |
| accounts | Internal ID, nickname, SBI/INR, last4, type, active flag, creation time. Last4 is deliberately nonunique. |
| categories | Unique name and system/custom flag. |
| transactions | Account-owned canonical amount/direction/date/timestamp/value date, original merchant/narration/reference/channel, verification/outcome/kind, manual/owned-transfer flags and audit times. Account deletion is restricted. |
| metadata | One record per transaction; display name, category, notes, hidden and userEdited. Cascades with permitted transaction deletion. |
| events | Unique ingestion identity; source, received time, masked content, parsed Observation JSON, parser version, processed flag and review reason. |
| evidence | Transaction/event linkage, source, match method and verified flag. Unique eventId prevents attaching one observation to multiple canonical records. |
| tags / transaction_tags | Unique tag names and a many-to-many composite-key join, with ownership foreign keys. |
| merchant_rules | Match type/value, rename/category, priority and enabled flag. |
| statement_imports | Unique file hash and logical fingerprint, account, filename, period, balances, row count, parser version, import time, status and nullable warningsJson. |
| statement_rows | Import-owned official sequence/fingerprint/date/value date/narration/reference/amount/direction/balance, optional canonical ID and ignored flag. |
| review_decisions | Unique observationKey; selected action/target/reason and decision time. |
| mandates | Separate authorization records with account, merchant, amount, reference, status and source event. |
| refund_links | Original/credit IDs and linked amount; each refund ID appears once. |
| settings | Local key/value preferences. |
| import_jobs | Transient encrypted text, filename/hash/account, state, parsed result/error, creation time and durable previewToken/resolutionsJson; excluded from backup. |

Transaction indices cover account, date, reference and verification; metadata indexes category. Events and import fingerprints have uniqueness constraints. Some conceptual relationships are checked by repository/backup validation rather than SQL foreign keys.

Repository mutations use Room transactions. Backup replacement clears child records before parents and inserts parents before children. Room serializes writes; no destructive database recreation is used.

Manual creation also records MANUAL observation/evidence. Only unverified, unlinked manual records permit financial edits and permanent deletion. Statement verification protects bank facts while metadata/tags remain editable. Linked refund records cannot be financially edited or deleted; hide them instead. The initial manual observation is not a full edit-history audit.

History uses bounded 50-row offset queries with account, direction, source, category, verification, hidden, date, amount and local text filters. LIKE terms are escaped; numeric searches convert rupees to paise. Dashboard totals aggregate in SQLite. Statement detail pages display 30 rows at a time, but their backing query loads that import's rows; imports/review lists also lack database pagination.

The schema intentionally separates status/metadata rather than reproducing every suggested field literally. Outstanding fields and behavior are tracked in leftout.md, including separate reference types, evidence confidence/audit fields, category icons, and optional metadata.

## Screens and state management

TrackerApp provides Dashboard, Transactions, Statements, Categories and Settings tabs. Secondary routes include transaction add/edit/detail, accounts, Needs Review, rules, mandates and backup. Search is embedded in Transactions; security controls are in Settings.

TrackerViewModel owns account/filter/month/grouping StateFlows, reactive repository subscriptions, busy/error state, statement preview/resolutions and backup preview. Startup waits for the first real account query before selecting onboarding, avoiding a returning user's form disappearing during initial load. Action errors reach a Snackbar; cancellation is preserved. The global progress indicator reserves its height so asynchronous writes do not move active controls.

Account onboarding explains local privacy and collects nickname/last4/type without login. Additional accounts can be created. Account editing/deactivation is not exposed.

Manual forms validate through SaveManualTransaction and use India date/time conventions. Detail displays original narration, source evidence and references alongside editable metadata. It offers explicit merchant-rule creation, hiding and owned-transfer classification. Refund linking currently requires the original debit's local ID displayed on its detail page.

Needs Review keeps unknown/ambiguous source observations out of canonical spending. Users select account and match/new/ignore. Unknown observations without usable financial fields cannot silently create expenses; a manual entry can be added separately.

Categories supports defaults and custom add/rename/delete when unused. Category history is available through transaction filtering. Rules supports exact/contains/prefix, priority, enablement and removal. Mandates displays authorization status separately from expenses.

Important native controls use hierarchical Compose testTag semantics, not React ui-tag attributes. There are no role/access wrappers. Material 3 follows system light/dark/dynamic colors and font scaling; text labels communicate status without relying only on color. Physical TalkBack, large-font, contrast and touch-target checks remain pending.

Nonsensitive form drafts use rememberSaveable where implemented. Passwords are kept in memory, never saved in instance state. Completed parse jobs survive process death; extraction requires file/password reentry if interrupted, and review choices resume from encrypted storage. If the regenerated preview token changes, old choices are cleared with an explanation.

## SMS and notification ingestion

MessageParsers contains modular failure/mandate/reversal/refund/debit/credit classifiers with shared financial extraction. SbiSmsParser validates sender patterns, discards OTP/PIN/promotional/unrelated messages before persistence, extracts available account/amount/date/reference/merchant/channel and versions results. Financial unknowns enter review. Channels include UPI, card, ATM, transfers, fees and interest; real-format coverage remains unvalidated.

SbiSmsReceiver assembles incoming multipart messages using RECEIVE_SMS; it never scans existing history. goAsync bounds receiver IO work, persists eligible observations and queues IngestionWorker. Pending records are retried on startup. The worker processes pending events in batches. Background ingestion uses credential-protected storage and does not run before first device unlock after reboot.

PhonePeParser and GooglePayParser use explicit package allowlists and a shared extractor. UpiNotificationService reuses the observation/reconciliation pipeline and stable notification keys. They are experimental synthetic-format adapters, not validated coverage of all provider notifications or update sequences. Unassignable paying accounts stay in review.

WorkManager receives identifiers/scheduling data, never raw financial payloads. Force-stop, installer permissions and manufacturer battery restrictions can interrupt delivery. Statement reconciliation recovers missed activity; it cannot guarantee real-time SMS/notification delivery.

## PDF extraction and staged imports

Flow: SAF selection -> in-memory PDF decryption/text extraction -> masked text in encrypted Room job -> WorkManager layout parsing -> persisted typed result -> account/duplicate checks and preview -> explicit decisions -> atomic commit.

PdfTextExtractor is a separate boundary from StatementParser and its SBI layout implementation. Extraction runs on Dispatchers.IO with 20 MB / 250 page limits. Scanned PDFs, missing text and unknown layouts return actionable errors. No original PDF copy is retained.

The validated Relationship Summary parser requires one savings-account block, explicit Credit–Debit–Balance headings, integer-zero or decimal amount cells, dated opening/closing balances, and date-led rows. It handles repeated table headers and the empty six-null table artifact. Transaction dates use DD-MM-YY. UPI/DR and UPI/CR references/merchant tokens and NEFT references are extracted without confusing DR/CR with a reference.

PDFBox extracts in content-stream order: sorting by visual position interleaves hidden null placeholders with the supplied statement’s opening-balance label. The parser accepts the opening label appearing after table rows in content order and ignores only recognized section/footer boundaries. Multiple-account statement documents are rejected with an actionable message; select a separate statement for each account.

The earlier experimental parser requires SBI identification, labeled account/period/opening/closing balances, date-led rows and decimal money columns. Explicit debit/credit columns are preferred. Two-money-column rows require exact running-balance deltas to determine direction. Wrapped narration joins the prior row. Unsupported headers/layouts fail rather than partially importing. Opening/closing and running-balance discrepancies become warnings.

Passwords are memory-only; character buffers are cleared. PDFBox requires a temporary immutable String that cannot be explicitly zeroed; it is never persisted or logged. Interrupted password extraction requires reentry.

StatementJobs stores masked extracted text encrypted, schedules StatementParseWorker by random ID, saves results/errors and clears raw text after parsing. Queued/ready/failed jobs can be resumed or discarded. Completion/cancellation removes staging jobs. Review resolutions and a hash of the preview are saved in encrypted jobs. Resume regenerates candidates and restores choices only when that token still agrees. Commit revalidates candidates inside the same write transaction and removes the job atomically with the import. Cancellation interrupts extraction/waiting and discards the job; a late worker cannot recreate it. SAF display names are preserved.

Exact duplicates use SHA-256 of file bytes. Logical fingerprints include account, period, balances, row count and sorted row fingerprints WITH multiplicity. Row fingerprints include date/value date, normalized narration, reference, direction, amount and running balance so repeated genuine rows are not reduced to a set.

The supplied encrypted four-page Relationship Summary was opened with Android PDFBox, parsed into 41 rows, checked against all running/closing balances, reconciled in an isolated in-memory ledger, and recognized as a duplicate on repeat. Real account information and the document password are not test fixtures. Synthetic tests cover 120-row Relationship Summary and generic statements, wrapped narration, integer zeros, and page boundaries. [synthetic-statement.txt](synthetic-statement.txt) contains fictional extracted text, not an official bank record or an importable PDF. The representative Relationship Summary layout prerequisite is satisfied; other SBI layouts require their own fixtures and validation. Do not broaden regexes against invented layouts without fixtures.

## Matching and reconciliation

MatchingEngine first scopes candidates to account and INR. Automatic attachment requires one unique strong-reference candidate with compatible amount/direction. Conflicting financial facts or references and multiple candidates require review. Without strong references, amount/direction and a three-day date window select suggestions, ranked by date, normalized merchant equality, timestamp proximity, channel and narration-token overlap, with stable ID tie-breaking. Scores rank suggestions only; they never authorize fuzzy merging.

ReconciliationRepository shares this engine across manual, SMS, notification and statement observations. It depends on LedgerRepository, Room transactions and Gson. Live processing records mandates separately, excludes failures from spending and leaves unknown/account-ambiguous/fuzzy observations in review. Confirmed actions are persisted.

Statement preview detects duplicate imports, reuses prior official row evidence for overlaps and reserves automatic target IDs one-to-one. Previously ignored overlap rows require explicit review. Users can match, create new or explicitly ignore a row; unresolved review rows block commit.

Commit regenerates the preview inside the Room write transaction and compares candidates/decisions with the displayed version. Stale previews abort. Explicit targets must agree on account/currency/amount/direction and nonempty references; existing verified statement dates cannot silently change. Import records, canonical updates, raw evidence, official rows, review decisions and status commit together, or all roll back.

Statement evidence supersedes provisional narration/date/channel/kind while preserving source timestamps and user metadata. Original events and official rows remain available. Failed provisional payments can become posted if statement evidence confirms them.

Balance validation always uses every official row, independently of hidden items and analytics exclusions. Ignored rows and balance warnings produce EXCEPTIONS, never RECONCILED. History preserves exact balance warnings and official rows. Preview/history summaries show matched/new/ignored/review counts, refund/reversal rows, official debit/credit totals, planned or actual linked-ledger totals, and calculated versus official closing balance. These totals include hidden and owned-transfer items because they describe official evidence.

An ignored row can later be matched or created through a confirmation flow. Its original ignore decision remains in history and the resolution appends a revision. One-to-one assignment and financial compatibility are checked again transactionally. Resolving all ignored rows restores RECONCILED only when no official balance warnings remain. Bank discrepancies cannot be dismissed or edited away; obtain a corrected statement.

Refund links preserve both movements and require compatible same-account debit/credit pairs; linked credits cannot exceed the original debit. Unique strong-reference linking is attempted after creation, including late original debits. Partial/full outcomes update the original. A linked refund retains its classification after generic statement credit verification. Unless the user edited its metadata, the refund takes the original category.

## Security and permissions

DatabaseKeys creates a random 32-byte SQLCipher passphrase, wrapped by an Android Keystore AES-256-GCM key. Wrapped material lives in private preferences excluded from system backup. The wrapping key is not authentication-bound, allowing ingestion after first device unlock independently of app lock.

Missing wrapping material or unavailable aliases for an existing database are errors. Never replace the key over an inaccessible ledger, silently reset data, or introduce destructive migrations. A user-facing key-loss recovery flow has not been validated.

MainActivity applies FLAG_SECURE before rendering; screenshots are blocked unless the user opts in. Security settings fail closed during loading. Optional app lock uses Android biometric/device credentials; the modal cannot be dismissed with Back and onStop locks the session. Composition stays alive behind the gate so SAF callbacks retain state. A device screen lock is required before enabling app lock; the app never collects device PINs.

The manifest declares incoming SMS and biometric capabilities plus protected receiver/listener services. Settings explains optional access and requests RECEIVE_SMS only when tracking is enabled; notification access is granted separately in Android Settings. Manual tracking and imports remain available without those grants.

No application INTERNET, READ_SMS, broad storage, contacts, location, camera, microphone, accessibility-service or screen-capture permission is required. Automatic cloud backup is disabled; cloud/device-transfer rules exclude root, files, databases, preferences and external app files. There is no backend, advertising/financial analytics SDK, banking login, payment initiation or screen scraping.

## Encrypted backup and restore

BackupSnapshot version 2 (restoring versions 1 and 2) includes accounts, transactions, metadata, categories, tags/joins, evidence/events, merchant rules, imports/rows, decisions, mandates, refund links and settings. Transient import jobs and device/signing keys are excluded.

BackupCrypto format ETBACK01 uses magic/header bytes, PBKDF2-HMAC-SHA256 with 600,000 iterations, fresh 16-byte salt, fresh 12-byte nonce and AES-256-GCM. The complete header is authenticated as AAD. Exports require a password of at least 12 characters. The derived key is independent of Android Keystore, permitting portable recovery.

BackupService snapshots Room transactionally, serializes with Gson in process memory, encrypts and writes through SAF. Password/byte buffers are cleared in finally blocks; immutable Gson/String copies cannot be explicitly zeroed. No plaintext archive is written to disk. Backup input is bounded to 64 MB; logical snapshots still load complete archive data into memory.

Statement warning JSON and review-decision revisions are included in backups. Old archives without warning JSON remain readable; warnings can be reconstructed from official rows. Validation also checks statement dates, sequence, linked financial facts and decision targets.

Inspect authenticates the entire file before parsing. BackupValidation checks version, unique IDs, account/currency/money/date validity, links, row counts, tags and refund bounds. UI shows date and account/transaction/import counts before explicit replacement confirmation.

Restore revalidates, discards transient jobs and replaces all records inside one Room transaction. Other writers serialize behind it; failure preserves the previous ledger. SMS/notification settings are disabled after restore because OS grants are device-specific. If no device screen lock exists, an imported app-lock preference is disabled with a notice to review security settings.

There is no merge restore or saved backup password. Device keys are never imported. APK signing material must be retained separately from financial backups.

## Analytics and merchant rules

Dashboard provides month/account selection; gross spending, refunds, net spending, income, cash flow and transaction count; previous-month comparison; category/merchant/day aggregates; recent/largest transactions and recurring suggestions.

Hidden, failed and user-confirmed owned transfers are excluded from combined totals. Refund/reversal credits reduce spending in their posting month and stay separate from regular income. Original debit movements remain, so refunds are subtracted once. ATM withdrawals retain Cash Withdrawal classification. Owned-transfer marking is manual; no automatic paired-transfer detector exists.

Recurring suggestions identify the same merchant and amount across at least three months. They are advisory, not payment schedules or subscription forecasts. Mandate creation/cancellation never increases spending; execution debits are separate financial movements.

Merchant normalization is deterministic and preserves original narration. Rules normalize exact/contains/startsWith patterns, sort by descending priority then stable ID and apply rename/category to new/reconciled records. Explicit user edits take precedence, and rules do not rewrite historical personal decisions. UI offers rule creation from a detail page; automatic repeated-reclassification prompts and regex rules are not implemented.

## Testing and release

| Evidence | Coverage and limits |
| --- | --- |
| [FinanceTest.kt](app/src/test/java/com/kiz9r/expense_tracker/FinanceTest.kt) | Money, classifications, ignored/unknown formats, masking, stable SMS identity, provider fixtures, matching conflicts/repeated purchases, 120-row/logical statement parsing, wrapped narration, rules and authenticated backup round trips/tampering. |
| [LedgerIntegrationTest.kt](app/src/androidTest/java/com/kiz9r/expense_tracker/LedgerIntegrationTest.kt) | Room idempotency, multiple evidence sources, duplicate/overlap imports, account collisions, manual metadata/hiding, failures/mandates, partial/full refunds, stale/conflicting rollback and replacement/fresh-database restore. Most repository tests use in-memory Room, not SQLCipher. |
| [PrivacyAndUiTest.kt](app/src/androidTest/java/com/kiz9r/expense_tracker/PrivacyAndUiTest.kt) | Real Hilt/Compose manual flow and SQLCipher file header; on-device protected PDF extraction; WorkManager encrypted staging cleanup. |
| [RelationshipParserTest.kt](app/src/test/java/com/kiz9r/expense_tracker/RelationshipParserTest.kt) | Synthetic real-layout structure, 120 rows, wrapped/page-order variants, malformed columns, balances, summary exclusions and ranking. |
| [StatementReconciliationTest.kt](app/src/androidTest/java/com/kiz9r/expense_tracker/StatementReconciliationTest.kt) | Statement-only verification, repeated/ignored overlaps, drafts/replay/cancellation, concurrent ingestion, warnings/backups and cross-month/late refunds; opt-in read-only supplied-PDF validation uses an in-memory ledger. |
| [StatementMigrationTest.kt](app/src/androidTest/java/com/kiz9r/expense_tracker/StatementMigrationTest.kt) and [StatementUiTest.kt](app/src/androidTest/java/com/kiz9r/expense_tracker/StatementUiTest.kt) | Encrypted schema-1 migration and Compose resume/review/commit/ignored-row resolution. |
| [VALIDATION.md](VALIDATION.md) | Historical 10 September results retained. The 11 September PDF run passed 21 JVM and 36 Android tests, lint with zero errors/16 warnings, debug/test installation, release assembly and signing verification. Physical phone QA remains pending. |

The 10 September results remain historical; the 11 September PDF implementation run and remaining checks are recorded separately in VALIDATION.md. Stable resume-control tags and waits for loaded content support the asynchronous Compose regression. Test reports/build logs are generated artifacts described in VALIDATION.md. Synthetic tests do not certify actual bank layouts, all delivery conditions or physical-device behavior.

Release signing loads private, gitignored signing.properties and personal-release.jks; missing configuration produces an unsigned release. Never embed or publish signing secrets. Keep the signer for future APK updates. Output is app/build/outputs/apk/release/app-release.apk when configured; README supplies commands and installation instructions.

## Known limitations and extension boundaries

[leftout.md](leftout.md) owns the detailed current backlog, requirement mapping and all 12 acceptance scenarios. Key boundaries are:

- SBI Relationship Summary is validated against the supplied encrypted sample. Other PDF layouts and SMS/provider formats need further coverage. CSV/XLSX and other notification providers are deferred; scanned-PDF OCR is outside the approved MVP.
- Basic schema and UI omit some requested refinements: category icons, separate reference types/audit fields, custom description/favorite metadata, history date group headings, full mandate lifecycle and richer rule suggestions.
- Reconciliation preserves official rows, durable review choices and detailed exceptions. Unchanged previews resume choices; changed candidates require fresh review. Balance discrepancies cannot be dismissed.
- Account maintenance, refund selection by merchant/date instead of local ID, multi-year performance and backup memory behavior need further work or validation.
- Android 11/physical biometrics, permission denial/revocation, reboot/background restrictions, TalkBack/large fonts, interrupted recovery and real uninstall/reinstall/update paths remain unverified.
- Migration 1 to 2 has an encrypted integration test; future migrations also need tests when the schema changes. Current lint warnings remain recorded; passing builds are not release readiness.
- Cloud, other banks/cards/currencies, family profiles, budgets and advanced recurring analysis are future scope. Payments, credential/OTP collection, bank-login scraping and screen monitoring are intentionally excluded.

Maintain this root document and leftout.md together whenever implementation or verification changes. Do not recreate package-level development documents.
