Build a production-quality Android application for personal use that functions as a local-first expense tracker for an SBI bank account in India.

The application must monitor financial activity using locally available signals such as SBI transaction SMS messages, optionally supported UPI app notifications, and monthly SBI bank statements imported manually by the user.

The application must NOT initiate, receive, approve, schedule, or otherwise participate in financial transactions.

It is strictly a read-only financial tracking and reconciliation application.

The application must never require the user's SBI internet banking password, UPI PIN, debit card PIN, OTP, or other banking credentials.

The main design principle is:

SBI SMS and UPI notifications provide fast provisional transaction information.

The imported SBI bank statement acts as the authoritative reconciliation source.

The application must combine multiple observations of the same real-world transaction into one canonical transaction instead of creating duplicate expenses.

---

# 1. Product Goal

Create a private Android personal finance application that allows the user to:

- Track SBI transactions automatically.
- See transactions shortly after they happen when an SBI SMS or supported UPI notification arrives.
- Import SBI monthly bank statements.
- Reconcile live-detected transactions with official SBI statement transactions.
- Detect transactions that were missed because no SMS or notification arrived.
- Detect duplicate observations.
- Prevent duplicate statement imports.
- Categorize expenses.
- Add notes and tags.
- Rename merchants locally.
- Hide transactions.
- Add, edit, and delete manually created transactions.
- View monthly spending analytics.
- Track income and expenses.
- Search transaction history.
- Track recurring payments and mandates separately.
- Identify reversals, refunds, failed transactions, and mandate events correctly.
- Backup and restore all local financial tracking data.

The app is intended initially for one user and one or more SBI accounts belonging to that user.

No cloud backend is required for the initial version.

---

# 2. Platform

Build this as a native Android application.

Preferred stack:

- Kotlin
- Jetpack Compose
- Room
- SQLite
- MVVM or Clean Architecture
- Kotlin Coroutines
- Flow / StateFlow
- WorkManager
- Hilt for dependency injection
- Android Keystore
- Android Storage Access Framework
- BroadcastReceiver for relevant SMS handling
- NotificationListenerService for optional UPI notification ingestion
- Material 3

The project should be structured cleanly and designed for maintainability.

Target modern Android versions.

Prefer Android 10 or newer unless a technical reason requires broader support.

Do not use React Native, Flutter, or Java unless explicitly changed later.

---

# 3. Core Privacy Model

The app must be local-first.

By default:

- Transaction data stays on the device.
- No remote server stores transaction history.
- No analytics SDK should receive financial transaction data.
- No advertising SDK should be included.
- No SBI credentials should ever be collected.
- No UPI PIN should ever be collected.
- No banking OTP should ever be stored.
- No debit card PIN should ever be collected.
- No accessibility service should be required.
- No screen recording should be required.
- No continuous screenshot capture should occur.
- No camera permission is required.
- No microphone permission is required.
- No location permission is required.
- No contacts permission is required.

Sensitive financial data should be stored in the app's private application storage.

Use Android Keystore for cryptographic key material.

Provide biometric or device-credential based app locking.

---

# 4. Main Data Philosophy

The application must distinguish between:

1. Raw financial observations
2. Canonical transactions
3. User-defined metadata
4. Official bank evidence

Do NOT treat every SMS, notification, or statement row as a separate transaction.

Example:

PhonePe notification:
₹450 paid to Swiggy.

SBI SMS:
₹450 debited via UPI.

SBI statement:
UPI/SWIGGY/... ₹450 DR.

These should result in:

ONE canonical transaction:

Merchant: Swiggy  
Amount: ₹450  
Direction: Debit  
Category: Food  
Status: Verified

Evidence:

- PhonePe notification
- SBI SMS
- SBI statement

---

# 5. Transaction Confidence Model

Transactions should support statuses such as:

- PROVISIONAL
- LIKELY_MATCHED
- VERIFIED
- REVERSED
- FAILED
- NEEDS_REVIEW
- HIDDEN

Suggested visual representation:

Unverified

Likely matched

Verified by SBI statement

Reversed

Needs review

The imported SBI statement should have the highest authority.

---

# 6. Local Database

Use Room.

Do not put the entire application into a single transactions table.

Create a normalized schema.

At minimum include the following entities.

---

# 7. Account Entity

Fields:

- id
- bankName
- accountLast4
- accountType
- currency
- nickname
- createdAt
- updatedAt
- active

Example:

bankName = SBI  
accountLast4 = 4821  
accountType = Savings  
currency = INR

Do not store full account numbers unless technically necessary.

Prefer storing only masked account information.

---

# 8. RawEvent Entity

RawEvent represents something observed by the app.

Fields:

- id
- source
- sourcePackage
- receivedAt
- eventTimestamp
- rawContent
- normalizedContent
- eventType
- parsingStatus
- parserVersion
- processed
- ignored
- linkedTransactionId
- createdAt

Possible sources:

- SBI_SMS
- GPAY_NOTIFICATION
- PHONEPE_NOTIFICATION
- PAYTM_NOTIFICATION
- BHIM_NOTIFICATION
- AMAZON_PAY_NOTIFICATION
- SBI_STATEMENT
- MANUAL

Possible event types:

- DEBIT
- CREDIT
- REFUND
- REVERSAL
- FAILED_TRANSACTION
- MANDATE_CREATED
- MANDATE_CANCELLED
- MANDATE_EXECUTED
- CASH_WITHDRAWAL
- TRANSFER
- INTEREST
- FEE
- UNKNOWN
- IGNORE

Do not permanently retain irrelevant personal SMS messages.

Messages determined to be unrelated to financial activity should be discarded rather than stored.

---

# 9. Canonical Transaction Entity

Fields:

- id
- accountId
- amountMinor
- currency
- direction
- transactionDate
- transactionTimestamp
- valueDate
- merchantOriginal
- merchantDisplay
- bankNarration
- paymentChannel
- upiReference
- bankReference
- externalReference
- categoryId
- status
- verifiedByStatement
- hidden
- manuallyCreated
- createdAt
- updatedAt

Store currency amounts using integer minor units if possible.

For INR:

₹450.25 should be stored as:

45025 paise.

Avoid floating-point money calculations.

Possible directions:

- DEBIT
- CREDIT

Possible payment channels:

- UPI
- ATM
- IMPS
- NEFT
- RTGS
- CARD
- ECS
- NACH
- CASH
- INTEREST
- BANK_FEE
- INTERNAL_TRANSFER
- UNKNOWN

---

# 10. Transaction Evidence Entity

Fields:

- id
- transactionId
- rawEventId
- source
- confidenceScore
- referenceNumber
- matchedAt
- matchMethod
- verified
- createdAt

Example:

Transaction #382

Evidence 1:
PHONEPE_NOTIFICATION

Evidence 2:
SBI_SMS

Evidence 3:
SBI_STATEMENT

All evidence must point to the same transaction.

---

# 11. Category Entity

Fields:

- id
- name
- icon
- systemCategory
- active
- createdAt

Default categories should include:

- Food & Dining
- Groceries
- Shopping
- Transportation
- Fuel
- Rent
- Utilities
- Bills
- Subscriptions
- Entertainment
- Healthcare
- Education
- Travel
- Cash Withdrawal
- Transfer
- Investments
- Insurance
- EMI / Loan
- Bank Charges
- Salary
- Income
- Interest
- Refund
- Gifts
- Other

Allow custom categories.

---

# 12. User Transaction Metadata

Keep user-editable metadata conceptually separate from authoritative bank information.

User-editable fields should include:

- merchantDisplay
- category
- notes
- tags
- custom description
- hidden
- favorite merchant
- excludedFromBudget if desired

Bank-authoritative fields such as:

- original amount
- transaction direction
- official bank reference
- statement transaction date

should not be silently overwritten once verified.

---

# 13. Tag Entity

Support multiple tags per transaction.

Examples:

- Work
- Personal
- Vacation
- Family
- Reimbursable
- Weekend

Implement a many-to-many transaction-tag relationship.

---

# 14. Merchant Rules

Create a rule system for automatic categorization.

Example:

IF normalized merchant contains SWIGGY  
THEN category = Food & Dining.

IF normalized merchant contains UBER  
THEN category = Transportation.

Fields:

- id
- matchType
- matchValue
- merchantRename
- categoryId
- priority
- enabled
- createdAt

Possible match types:

- exact
- contains
- startsWith
- regex

When the user repeatedly reclassifies the same merchant, offer to create a merchant rule.

Do not use AI categorization initially unless added later.

Prefer deterministic rules.

---

# 15. Statement Import Entity

Fields:

- id
- accountId
- fileName
- fileHash
- logicalFingerprint
- startDate
- endDate
- openingBalance
- closingBalance
- transactionCount
- importedAt
- reconciliationStatus
- parserVersion

Use SHA-256.

---

# 16. Duplicate Statement Protection

Prevent accidental duplicate imports.

Perform two levels of duplicate checking.

First:

SHA-256 of exact imported file bytes.

If the same exact file has already been imported, warn the user and prevent duplicate processing.

Second:

Generate a logical statement fingerprint based on normalized statement information such as:

- account
- statement period
- opening balance
- closing balance
- transaction count
- normalized transaction hashes

This protects against SBI generating another PDF with different PDF metadata but identical financial contents.

If the same logical statement is imported repeatedly:

Import #1:
transactions imported.

Import #2:
0 duplicate transactions created.

Import #3:
0 duplicate transactions created.

Never duplicate transactions merely because the same statement was imported again.

---

# 17. Statement Import Formats

Design the statement importer as an interface.

Example:

StatementParser

implementations:

- SbiCsvStatementParser
- SbiXlsxStatementParser
- SbiPdfStatementParser

Prefer structured formats such as CSV or XLSX over PDF whenever possible.

PDF support should still be included if SBI's available statements require it.

Do not use OCR if the PDF contains machine-readable text.

Support password-protected SBI PDFs if technically feasible.

The password should be used only in memory for opening the document.

Never permanently store the PDF password.

---

# 18. Statement Import Flow

UI flow:

Import Statement

Select file

Identify statement format

Validate SBI statement

Identify account

Identify date range

Extract transactions

Check duplicate statement

Normalize transactions

Compare with existing canonical transactions

Show reconciliation preview

Ask user to review ambiguous matches

Commit import

Mark matched transactions as verified

Add transactions that were missed by SMS or notification tracking

Do not immediately commit ambiguous matches without review.

---

# 19. SBI SMS Ingestion

Implement SBI SMS parsing as a modular parser system.

Do not use one giant regex for everything.

Create an interface such as:

MessageParser

Methods conceptually:

canParse()

parse()

Create specific parser implementations.

Examples:

- SbiDebitParser
- SbiCreditParser
- SbiUpiDebitParser
- SbiCardParser
- SbiAtmParser
- SbiCashWithdrawalParser
- SbiReversalParser
- SbiRefundParser
- SbiMandateCreatedParser
- SbiMandateCancelledParser
- SbiFailedTransactionParser
- SbiBankFeeParser

The parser should extract, where available:

- amount
- direction
- accountLast4
- date
- time
- reference
- UPI reference
- merchant
- channel
- balance
- event type

---

# 20. SMS Classification

Do not create a transaction merely because an SMS came from SBI.

Classify first.

Examples:

"₹850 debited from account..."
=> Debit transaction.

"₹5,000 credited..."
=> Credit transaction.

"UPI mandate for ₹999 created..."
=> Mandate created.
Do not count as spending.

"Mandate cancelled..."
=> Mandate cancelled.
Do not count as spending.

"Transaction declined..."
=> Failed transaction.
Do not count as spending.

"₹1,200 reversed..."
=> Reversal.
Link to original transaction if possible.

"OTP 123456..."
=> Ignore.

"Your statement is ready..."
=> Ignore.

"Promotional SBI message..."
=> Ignore.

Store only relevant parsed information.

---

# 21. Missing SMS Handling

The system must assume that SBI SMS messages can occasionally:

- arrive late
- not arrive
- be duplicated
- contain different formats
- be deleted
- fail due to carrier issues

Therefore:

SMS is not authoritative.

An SMS-created transaction should initially be provisional.

Example:

₹850  
Swiggy  
Source: SBI SMS  
Status: Provisional

After statement reconciliation:

₹850  
Swiggy  
Sources: SBI SMS + SBI Statement  
Status: Verified

If the monthly statement contains a transaction never observed before:

Create it during reconciliation.

Example:

Statement transaction exists.

No SMS.

No UPI notification.

Result:

Add transaction.

Mark:

Source: SBI Statement  
Status: Verified.

---

# 22. UPI Notification Support

UPI notification support is optional but desirable.

Initial supported apps may include:

- Google Pay
- PhonePe
- Paytm
- BHIM
- Amazon Pay

Implement each as its own notification parser.

Do not assume all notification formats are identical.

Do not require the application screen to remain visible.

Use Android's notification listener APIs.

The app must never use screen scraping.

Do not use accessibility services.

Do not inspect arbitrary screen content.

---

# 23. UPI Notification Example

PhonePe notification:

₹420 paid to Domino's.

Create a provisional financial observation.

Fields may include:

amount = ₹420  
merchant = Domino's  
direction = DEBIT  
source = PHONEPE_NOTIFICATION

Later SBI SMS arrives:

₹420 debited via UPI.

Do not create a second canonical transaction if the matcher determines it is the same payment.

Instead attach the SBI SMS as additional evidence.

Then the statement arrives later.

Attach the statement row as authoritative evidence.

Result:

One transaction.

Three evidence sources.

---

# 24. Transaction Matching Engine

Create a dedicated reconciliation and transaction matching engine.

Do not mix matching logic into the UI.

The engine should support deterministic and fuzzy matching.

Highest-confidence match criteria:

- exact bank reference
- exact UPI reference
- exact UTR
- exact transaction identifier

These should outweigh all fuzzy heuristics.

Fallback matching may consider:

- amount
- direction
- account
- timestamp difference
- transaction date
- merchant similarity
- narration similarity
- payment channel
- balance progression if available

Example scoring model:

Exact reference:
+100

Exact amount:
+40

Same direction:
+10

Same account:
+10

Within 2 minutes:
+25

Within 10 minutes:
+15

Same merchant or strong merchant similarity:
+15

Same payment channel:
+5

Thresholds may be refined through testing.

Example:

score >= 100:
definite match.

score 70-99:
likely match.

score below threshold:
do not automatically merge.

Avoid false-positive merging.

It is safer to show two transactions for review than to incorrectly merge two genuine payments.

---

# 25. Duplicate Transaction Handling

Be especially careful with legitimate repeated transactions.

Example:

Coffee shop
₹100 at 10:00

Coffee shop
₹100 at 10:04

These may be two real purchases.

Do not merge merely because amount and merchant match.

Prefer:

- reference number
- timestamps
- unique identifiers
- statement sequence
- account balance progression

---

# 26. Reconciliation Engine

Monthly statement reconciliation should produce a summary.

Example:

September 2026

Statement transactions: 92

Matched: 84

New statement-only transactions: 5

Possible duplicates: 1

Reversals: 1

Needs review: 1

Statement debit total: ₹58,394

Tracked debit total: ₹58,394

Opening balance: ₹92,830

Closing balance: ₹71,203

Status:
Reconciled

The user should be able to open each unresolved item.

---

# 27. Balance Validation

When the statement includes balances, use them to strengthen reconciliation.

Validate:

Opening balance  
+ credits  
- debits  
= closing balance

Allow statement fees, reversals, and interest transactions.

If the calculated balance does not match the statement:

Show a reconciliation warning.

---

# 28. Reversals and Refunds

Reversals must not simply be treated as unrelated income.

Where possible, link the reversal to the original debit.

Example:

₹1,200 debit

later

₹1,200 reversal

Represent:

Original transaction:
REVERSED.

Associated reversal event.

Monthly spending analytics should reflect the net financial effect appropriately.

Refunds should be distinguishable from regular income.

---

# 29. Failed Transactions

Failed or declined transactions should not affect actual expense totals.

Optionally show them in transaction history with:

FAILED

but exclude them from:

- spending totals
- budgets
- monthly expense calculations

unless later confirmed by statement evidence.

---

# 30. Mandates

Create a separate Mandates section.

Mandate creation is NOT an expense.

Example:

Netflix  
₹649 monthly  
Status: Active

Possible mandate states:

- ACTIVE
- CANCELLED
- EXPIRED
- PAUSED
- UNKNOWN

Mandate execution may create an actual debit transaction.

The mandate record and the financial debit should remain conceptually separate.

---

# 31. Manual Transactions

Allow the user to manually add transactions.

Fields:

- amount
- debit/credit
- date
- time
- merchant
- category
- account
- notes
- tags

Manual transactions should be fully editable.

Manual transactions should be deletable.

If a later SBI statement transaction strongly matches a manual transaction, allow it to become verified rather than automatically creating a duplicate.

---

# 32. Editing Bank Transactions

For verified bank transactions:

Allow editing:

- merchant display name
- category
- notes
- tags
- visibility

Do not silently alter:

- official amount
- official debit/credit direction
- bank reference
- UPI reference
- official statement date

If the user needs to override something, preserve the original value separately.

---

# 33. Delete and Hide Behavior

Manual transactions:

Allow permanent deletion.

Imported or verified bank transactions:

Prefer "Hide from tracker" rather than destructive deletion.

Fields should support:

hidden = true.

Provide a "Show hidden transactions" option.

If destructive deletion is provided for imported data, show a strong warning and preserve enough reconciliation metadata to avoid recreating incorrect duplicates unexpectedly.

---

# 34. Dashboard

Create a clean dashboard.

Example:

September

Spent
₹38,420

Income
₹82,000

Net
₹43,580

Then:

Recent Transactions

Swiggy  
₹480  
Food

Uber  
₹235  
Transportation

Amazon  
₹1,299  
Shopping

Then:

Top Categories

Food  
₹8,410

Shopping  
₹12,300

Transportation  
₹4,100

Bills  
₹5,820

Provide monthly navigation.

---

# 35. Transaction List

Support:

- chronological list
- search
- filters
- category filter
- source filter
- verified/unverified filter
- account filter
- debit/credit filter
- amount range
- date range

Group by:

Today

Yesterday

Earlier this week

or by exact date.

---

# 36. Transaction Detail Screen

Example:

Swiggy

₹480

9 September 2026  
8:41 PM

Category:
Food & Dining

Paid via:
UPI

Account:
SBI ••••4821

Evidence:

PhonePe notification  
SBI SMS  
Statement verification pending

Bank Reference:
62518932839

Notes:
Dinner

Tags:
Weekend

Allow category and user metadata editing.

---

# 37. Statements Screen

Example:

Statements

September 2026  
Reconciled

August 2026  
Reconciled

July 2026  
2 items need review

Button:

Import SBI Statement

Each statement should show:

- period
- imported date
- transaction count
- opening balance
- closing balance
- reconciliation status

---

# 38. Categories Screen

Support:

- default categories
- custom categories
- rename custom categories
- delete unused custom categories
- category spending history
- category rules

---

# 39. Search

Search should match:

- merchant
- bank narration
- amount
- reference number
- notes
- tags
- category

Search must remain local.

---

# 40. Analytics

Initial analytics:

- total monthly spend
- total monthly income
- net cash flow
- spending by category
- spending by merchant
- daily spending
- month-to-month comparison
- largest expenses
- recurring expenses
- number of transactions

Later features may include:

- budgets
- savings targets
- subscription analysis
- recurring expense forecasts

Do not make these mandatory for the MVP.

---

# 41. Security

Implement:

- local database protection
- Android Keystore
- app lock
- biometric unlock
- optional device PIN fallback
- secure backup encryption
- no plaintext banking credentials
- no OTP storage
- no network transmission of transaction history by default

Consider disabling screenshots on sensitive screens using Android secure window flags.

However, make this configurable if needed.

---

# 42. Backup and Restore

Because uninstalling an Android application normally removes its private local database, include encrypted backup and restore.

Backup should include:

- accounts
- transactions
- evidence
- categories
- tags
- merchant rules
- statement import history
- mandates
- settings
- reconciliation metadata

Export to a single encrypted backup file.

Example:

expense_tracker_backup_2026_09_09.etbackup

Allow user to save it using Android's Storage Access Framework.

Require a backup password or strong encryption method.

Never store the backup password.

Restore should validate the backup before replacing or merging local data.

---

# 43. Permissions

Request permissions only when needed.

SMS:

Needed for SBI SMS tracking.

Notification access:

Optional and only needed for supported UPI notifications.

Biometric:

Optional for app locking.

File access:

Use Android's Storage Access Framework rather than broad storage permissions.

Do NOT request:

- camera
- microphone
- location
- contacts
- accessibility
- screen capture

unless future requirements explicitly change.

---

# 44. Permission Onboarding

Do not request every permission immediately at first launch.

Explain why each permission is needed.

Example:

SMS Access

Used only to identify SBI financial transaction messages.

Non-financial messages are ignored and should not be permanently stored.

Buttons:

Allow SMS Access

Skip for Now

Then separately:

Notification Access

Allows optional detection of payment notifications from apps such as PhonePe or Google Pay.

Buttons:

Enable Notification Access

Skip

The app should remain usable without notification access.

---

# 45. No Screen Monitoring

The application must NEVER require the user to keep the app open.

It must not continuously watch the screen.

It must not use OCR against the user's phone screen.

It must not use AccessibilityService to scrape UPI apps.

Financial signals should come from:

- SMS APIs
- NotificationListenerService
- manually imported statements
- manual entries

---

# 46. Background Processing

Incoming SMS should trigger lightweight parsing.

Do not perform expensive work in the receiver itself.

Pipeline:

Receive event

Persist necessary raw event

Schedule parsing if appropriate

Parse

Classify

Attempt matching

Update Room database

Notify UI through Flow

Use WorkManager for longer-running operations such as statement parsing or reconciliation.

---

# 47. App Architecture

Use clean separation.

Suggested modules/packages:

app/

data/
database/
entities/
dao/
repositories/
mappers/

domain/
models/
repositories/
usecases/

ingestion/
sms/
parsers/
notifications/
parsers/
statements/
parsers/

reconciliation/
matcher/
scoring/
deduplication/

security/

backup/

analytics/

ui/
dashboard/
transactions/
transactiondetail/
categories/
statements/
reconciliation/
mandates/
settings/
onboarding/

util/

---

# 48. Repository Layer

Do not let Compose screens communicate directly with DAO classes.

Use repositories.

Example:

TransactionRepository

StatementRepository

AccountRepository

CategoryRepository

EvidenceRepository

MandateRepository

BackupRepository

---

# 49. Domain Use Cases

Use cases may include:

AddManualTransaction

UpdateTransactionMetadata

HideTransaction

ImportStatement

ProcessIncomingSms

ProcessNotification

MatchRawEventToTransaction

ReconcileStatement

CategorizeTransaction

CreateMerchantRule

ExportBackup

RestoreBackup

SearchTransactions

GetMonthlySummary

---

# 50. Money Handling

Never use Double or Float for persistent financial calculations.

Use:

Long amountMinor

For INR:

₹1 = 100 paise.

Example:

₹1,249.50

store as:

124950

Create utility methods for display formatting.

---

# 51. Dates and Times

Use modern java.time APIs.

Store timestamps in a consistent format.

Preserve:

- actual event timestamp
- transaction date
- value date
- ingestion time

Do not assume event receipt time equals transaction time.

---

# 52. Merchant Normalization

Bank narration can be messy.

Example:

UPI/625189208332/SWIGGY/YESBANK

Normalize to:

Swiggy.

Store both:

merchantOriginal

merchantDisplay

Never discard original narration.

Merchant normalization rules should be deterministic and editable.

---

# 53. Idempotency

All ingestion pipelines must be idempotent wherever possible.

Repeated processing of the same:

- SMS
- notification
- statement
- statement row

must not create duplicate canonical transactions.

---

# 54. Error Handling

Do not crash because a new SBI SMS format is unknown.

Unknown message:

classify as UNKNOWN.

Optionally log locally for debugging.

Allow the user to inspect unmatched financial events from a "Needs Review" screen.

Unknown statement format:

show:

Unable to recognize this SBI statement format.

Do not partially corrupt existing data.

Use transactional database operations for statement import commits.

---

# 55. Parser Versioning

Store parser version on parsed events and imported statements.

This allows future parser improvements and optional reprocessing.

Example:

parserVersion = 1.2.0

---

# 56. Reconciliation Review Screen

For ambiguous matches show:

Statement:

₹500  
Amazon  
09 Sep  
Ref: ABC123

Possible existing matches:

Transaction A  
₹500  
Amazon  
09 Sep 14:31  
Source: PhonePe

Transaction B  
₹500  
Amazon  
09 Sep 17:42  
Source: SBI SMS

Allow user to choose:

Match A

Match B

Create New Transaction

Ignore Statement Entry

Never silently choose when confidence is insufficient.

---

# 57. Database Transactions

Statement reconciliation commits must use Room transactions.

If an import fails halfway:

Rollback the entire unfinished commit.

Do not leave the database partially reconciled.

---

# 58. Testing Requirements

Testing is critical.

Create extensive unit tests for parsers and reconciliation.

Tests must cover:

SBI debit SMS

SBI credit SMS

UPI debit SMS

ATM withdrawal

card payment

refund

reversal

failed transaction

mandate creation

mandate cancellation

mandate execution

OTP message

promotional SBI message

unknown SBI message

Duplicate same SMS

Duplicate same notification

Same statement imported twice

Same logical statement with different PDF metadata

Notification + SMS for one transaction

Notification + SMS + statement for one transaction

Two genuine same-value transactions close together

No SMS but statement transaction exists

Manual transaction later matched by statement

Reversal matched with original debit

Failed transaction excluded from expense totals

Correct monthly totals

Correct opening/closing balance reconciliation

Backup and restore

---

# 59. Test Data

Create synthetic test data.

Do not include real bank credentials.

Tests should simulate:

100+ transaction statement

duplicate imports

multiple UPI apps

multiple same-value transactions

missing SMS events

out-of-order events

late SMS delivery

refunds

reversals

failed transactions

different SBI SMS message formats

---

# 60. MVP Scope

Version 1.0 should include:

- Kotlin Android application
- Jetpack Compose
- Room local database
- SBI account support
- manual transactions
- SBI SMS detection
- debit parsing
- credit parsing
- UPI parsing
- reversal detection
- failed transaction detection
- mandate filtering
- monthly SBI statement import
- duplicate statement detection
- transaction matching
- reconciliation
- categories
- notes
- tags
- local merchant rename
- hide transaction
- search
- monthly dashboard
- biometric lock
- encrypted backup
- restore

---

# 61. Features That Can Wait

Do not block MVP development on:

- cloud sync
- AI categorization
- multiple users
- web dashboard
- iOS
- automatic Account Aggregator integration
- automatic SBI API integration
- payment initiation
- UPI sending
- bill payment
- investments
- tax filing
- financial advice
- complex budgeting

---

# 62. Future Expansion

Architecture should allow future support for:

- multiple SBI accounts
- other Indian banks
- credit cards
- multiple currencies
- Account Aggregator integrations
- budgets
- recurring expense detection
- subscription tracking
- family profiles
- optional encrypted cloud backup

But do not over-engineer the initial version.

---

# 63. Important Safety Rule

The application is a financial record-keeping application.

It must never:

- transfer funds
- initiate UPI
- approve mandates
- collect UPI PIN
- collect SBI password
- collect OTP
- automatically log into OnlineSBI
- scrape SBI internet banking
- impersonate the SBI application
- claim that locally edited transaction details modify SBI's official ledger

Editing a transaction in this app only changes the user's local representation.

The original bank evidence should remain preserved.

---

# 64. User Experience Principle

The application should make the distinction clear between:

"Detected"

and:

"Verified"

Example:

₹840  
Swiggy

Detected from SBI SMS

versus:

₹840  
Swiggy

Verified against SBI statement.

Avoid technical terminology unless the user opens detailed information.

---

# 65. Offline Operation

Normal expense tracking should function without internet access.

The following should work offline:

- dashboard
- transaction history
- categories
- search
- statement import
- reconciliation
- SMS parsing
- local notification parsing
- backup export
- restore

Internet should not be a requirement for the core application.

---

# 66. Performance

The database may eventually contain several years of transactions.

Design queries and indexes accordingly.

Useful indexes:

- transactionDate
- accountId
- categoryId
- bankReference
- upiReference
- status
- hidden

Do not load the entire transaction history into memory.

Use pagination or efficient lazy loading.

---

# 67. Accessibility and UI

Use Material 3.

Support:

- dark mode
- light mode
- dynamic font scaling
- readable contrast
- TalkBack-friendly labels
- proper touch target sizes

Do not use color alone to communicate transaction status.

---

# 68. Initial Navigation

Recommended bottom navigation:

Dashboard

Transactions

Statements

Categories

Settings

Additional screens accessible through navigation:

Transaction Detail

Add Transaction

Reconciliation

Mandates

Search

Backup

Security

---

# 69. First Launch Experience

Suggested onboarding:

Welcome

"This app keeps your expense data locally on your device."

Add SBI Account

Enter:

- account nickname
- last four digits
- account type

Then optionally enable:

SBI SMS Tracking

UPI Notification Tracking

Biometric Lock

Then land on Dashboard.

Do not require login or account registration.

---

# 70. Developer Deliverables

When implementing this project, provide:

1. Project architecture.

2. Gradle configuration.

3. Room schema.

4. Entities.

5. DAOs.

6. Repositories.

7. Domain models.

8. Use cases.

9. SBI SMS parser framework.

10. Notification parser framework.

11. Statement parser framework.

12. Matching engine.

13. Reconciliation engine.

14. Duplicate detection.

15. Backup and restore.

16. Security layer.

17. Compose UI.

18. Navigation.

19. ViewModels.

20. Unit tests.

21. Integration tests where practical.

22. Sample synthetic data.

23. README explaining setup.

24. Clear comments around financial reconciliation logic.

---

# 71. Development Order

Implement in this sequence.

Phase 1:

Project setup.

Room database.

Accounts.

Transactions.

Categories.

Manual transaction CRUD.

Dashboard.

Search.

Phase 2:

SBI statement import.

Statement entity.

Transaction normalization.

Duplicate statement protection.

Basic reconciliation.

Phase 3:

SBI SMS receiver.

SMS parser architecture.

Debit/credit parsing.

Reversal and failed transaction handling.

Mandate classification.

Phase 4:

Matching engine.

SMS-to-statement reconciliation.

Confidence system.

Review screen.

Phase 5:

UPI notification listener.

PhonePe parser.

Google Pay parser.

Additional UPI parsers as required.

Phase 6:

Security.

Biometric lock.

Backup.

Restore.

Phase 7:

Analytics.

Merchant rules.

UI polish.

---

# 72. Acceptance Criteria

The application is considered functionally successful when all of the following scenarios work.

Scenario 1:

An SBI debit SMS arrives.

Exactly one provisional debit appears in the app.

Scenario 2:

A corresponding UPI notification and SBI SMS arrive.

Exactly one canonical transaction exists.

Scenario 3:

The monthly SBI statement is imported.

The existing provisional transaction is matched and marked verified.

No duplicate is created.

Scenario 4:

A bank transaction occurred but no SMS or notification was received.

Importing the SBI statement creates the missing verified transaction.

Scenario 5:

The same statement is imported twice.

No duplicate transactions are created.

Scenario 6:

A mandate creation SMS arrives.

No expense is created.

The mandate may appear separately.

Scenario 7:

A failed payment notification arrives.

It does not increase expense totals.

Scenario 8:

A debit is later reversed.

The transaction is correctly marked or linked as reversed.

Scenario 9:

Two identical ₹500 purchases happen close together.

The matching engine does not incorrectly merge them if evidence indicates two separate transactions.

Scenario 10:

The user changes:

Amazon

to:

Amazon Electronics

and changes category:

Shopping

to:

Electronics.

The original bank narration remains unchanged.

Scenario 11:

The user hides a statement-verified transaction.

It disappears from normal expense views but bank evidence is preserved.

Scenario 12:

The app is uninstalled after creating an encrypted backup.

After reinstalling and restoring the backup, transaction history, categories, evidence, rules, and reconciliation state are recovered correctly.

---

# 73. Final Engineering Principle

Build the application around this rule:

A transaction is not the same thing as an observation of a transaction.

SMS, UPI notifications, manual entries, and SBI statement rows are observations.

The system should combine those observations into the most accurate canonical representation of the user's real financial activity.

The SBI monthly statement is the strongest reconciliation source.

Live sources should provide speed.

Statement reconciliation should provide accuracy.

The final application should therefore behave as:

Fast during the month.

Accurate after reconciliation.

Private by default.

Offline-first.

Read-only with respect to the bank.

Safe against duplicate ingestion.

Transparent about where each transaction came from.