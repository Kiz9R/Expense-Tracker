# SBI Expense Tracker

A native Android personal expense ledger built with Kotlin, Compose, Room/SQLCipher, Hilt and WorkManager. All normal operation is offline. There is no payment initiation, banking login, analytics SDK or application network permission.

## Implemented features

Current implementation gaps, experimental support and pending validation are tracked in [leftout.md](leftout.md). The list below describes available capabilities, not full release sign-off.

- Multiple masked SBI accounts; manual debit/credit entries; categories, tags, notes, merchant display names, hiding and search.
- Monthly dashboard with income, spending, refunds, net cash flow, breakdowns, comparisons, largest expenses and recurring-payment suggestions.
- New SBI SMS ingestion with versioned classification, background processing, duplicate protection and a review queue.
- Password-protected SBI Relationship Summary PDF import, durable review drafts, reconciliation summaries, exact/logical duplicate detection, atomic commits and later resolution of ignored rows.
- Canonical transactions with separate source evidence, bank facts, user metadata, verification, payment outcome and visibility.
- Partial/full refunds and reversals; failed transactions and mandate creation excluded from spending.
- Optional experimental PhonePe/Google Pay notification adapters.
- Keystore-wrapped SQLCipher key, optional biometric/device-credential lock and screenshot protection.
- Portable password-encrypted backup, validation preview and transactional replacement restore.

To remove an account, open **Settings → Manage SBI accounts → Delete**, then confirm the named account. This permanently deletes its local transactions, linked evidence, statements and pending imports, including verified and hidden records. Export an encrypted backup first if you need recovery. Other accounts, shared categories/tags/rules and unassigned observations remain. Existing backups are unchanged; your actual SBI bank account is unaffected. Deleting the final account returns to onboarding.

## Setup and builds

Open this directory in Android Studio. Install Android SDK platform 37, accept the SDK licenses, and configure local.properties with your SDK path. The existing project uses Gradle 9.6.0, AGP 9.4.0 and a Java 25 Gradle daemon; Android source compatibility remains Java 11. Minimum device version is Android 11 (API 30).

From PowerShell:

    .\gradlew.bat :app:assembleDebug
    .\gradlew.bat :app:testDebugUnitTest :app:lintDebug
    .\gradlew.bat :app:connectedDebugAndroidTest
    .\gradlew.bat :app:assembleRelease

Connected tests need an emulator or disposable test device. The UI regression creates synthetic records in the installed app and enables screenshots for test inspection; do not run that test against your personal ledger.

Debug APK: app/build/outputs/apk/debug/app-debug.apk.
Signed release APK, when local signing configuration is present: app/build/outputs/apk/release/app-release.apk.

The local, gitignored signing.properties and personal-release.jks are signing material created for this personal build. Keep secure copies: future APK updates need the same key. Do not publish either file or its contents. Financial backups do not contain APK signing material.

On another development machine, supply private signing.properties with storeFile, storePassword, keyAlias, and keyPassword. Without it, release assembly produces an unsigned APK. A debug-signed installation cannot be updated with the personal release key; use encrypted backup/restore when switching signatures.

## First use

1. Install the APK and add an account nickname, last four digits and account type. No full account number is required.
2. Add manual entries or import a statement. Select the correct account before previewing a PDF.
3. In Settings, optionally enable new-SMS tracking. Android may require granting SMS permission through its installer/runtime permission flow. Denying access leaves manual tracking and import available.
4. Enable UPI notification tracking only if wanted, then grant notification access in Android Settings. Unknown accounts and ambiguous matches go to Needs Review.
5. Enable app lock after configuring a device screen lock. Screenshot capture is blocked by default.
6. Export an encrypted backup before uninstalling or moving phones.

## PDF support and boundaries

**Validated layout: SBI Relationship Summary for a single savings account.** The supplied four-page encrypted PDF was validated with the Android extractor: all 41 rows and running/closing balances reconcile. Other layouts are not automatically supported.

The validated layout has a masked savings-account block, Credit–Debit–Balance columns (including integer zeros), repeated page headers, and dated opening/closing balance labels. UPI/DR and UPI/CR references are extracted separately from merchant names. PDFs containing multiple account blocks are rejected; use one statement per account.

The older generic text-table adapter remains experimental. It expects explicit account/period/balances and decimal debit/credit columns; an amount/balance variant requires an exact running-balance delta. Scanned PDFs and unknown layouts are rejected.

Scanned PDFs, missing/unknown layouts, CSV and XLSX are unsupported. Password-protected PDFs are decrypted only in memory; interrupted extraction requires password reentry. Passwords are never saved in Room, WorkManager input, preferences, logs or backups. Java/PDFBox creates temporary immutable strings that cannot be explicitly zeroed.

Imports are limited to 20 MB and 250 pages. Parsed content/jobs are encrypted locally; original PDFs are not retained. Completed parsing can resume after process death. Review choices survive restart in encrypted storage. If the ledger changes, refresh matches and review again; stale choices are cleared. Filenames are retained, and cancellation discards the staged import.

Identical files and logically identical statements create no additional transactions. Overlapping rows use account/date/reference/narration/amount/direction/running balance. Ambiguity needs confirmation. A changed ledger invalidates an old preview. Preview and history show row counts, statement versus linked-ledger debit/credit totals, and calculated versus official closing balance. Balance discrepancies and ignored rows remain visible exceptions and are never marked fully reconciled. Open an ignored row in statement history to match or create a transaction; the original ignore decision remains in the audit trail. Official balance discrepancies cannot be dismissed.

SMS and notification formats differ across providers/releases. Included parsers are tested with synthetic fixtures, not every real SBI/provider format. Unknown financial messages are retained locally for review; OTP/PIN/promotional and unrelated messages are discarded before persistence. Notification parsing is optional and experimental.

## Financial conventions

- Money is positive integer paise plus debit/credit direction; there are no floating-point financial calculations.
- Reporting uses Asia/Kolkata and preserves event timestamps separately from receipt and statement dates.
- Statements verify bank facts; display name/category/notes/tags change only local metadata.
- Dashboard includes provisional/manual entries, clearly labeled. Hidden, failed and confirmed owned-account transfers are excluded.
- Refund/reversal credits reduce spending in their posting month. The original debit remains, preventing double subtraction.
- Refund links support partial returns. The current detail UI links a credit using the original debit's displayed local ID.
- Mandates are authorizations, not expenses. Only execution debits affect spending.
- Merchant rules support exact, contains and starts-with matching; manual metadata overrides take priority.
- Android force-stop, installer restrictions and manufacturer background limits can interrupt SMS detection. Monthly reconciliation recovers gaps.

## Backup and recovery

Version-2 .etbackup snapshots (with version-1 restore compatibility) use AES-256-GCM and PBKDF2-HMAC-SHA256 (600,000 iterations), with fresh salt/nonce and a minimum 12-character password. They are portable across installations and independent of Keystore keys.

Restore authenticates the file and validates its version, identities, money/dates and record relationships before showing a preview. Replacement requires confirmation and executes in a Room transaction; failure preserves existing data. Re-enable SMS/notifications afterward. Backups are limited to 64 MB. Automatic Android cloud and device-transfer backup are disabled/excluded.

## Architecture and maintenance

One Gradle module with domain, data, ingestion, reconciliation, security, backup and ui/features packages. Read the single root [featureDevelopmentInfo.md](featureDevelopmentInfo.md) for architecture, file responsibilities, flows, dependencies, schema and limitations. Maintain it and the root [leftout.md](leftout.md) whenever implementation or verification changes, following [rules.md](rules.md). Do not create package-level development documents.

Compose -> ViewModel/use case -> repository -> encrypted Room. Background services reuse the same repositories and matching engine. Screens never access a DAO. The exported version-2 schema and historical version 1 are in app/schemas. Migration 1 to 2 preserves existing data and has an encrypted integration test; future versions require explicit tested migrations.

Tests cover deterministic parsers/matching, 120-row synthetic statements, duplicates/overlaps, concurrent ingestion, account suffix collisions, metadata preservation, failures/mandates, refunds, backup integrity/replacement, PDF passwords and a real Compose transaction flow with encrypted persistence. The root [synthetic-statement.txt](synthetic-statement.txt) is a fictional extracted-text fixture, not a real bank record or an importable PDF. Previously executed results remain in [VALIDATION.md](VALIDATION.md); [leftout.md](leftout.md) tracks current feature status, all requirement areas, the 12 acceptance scenarios and remaining checks.
