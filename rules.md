# Development documentation and progress tracking

Maintain exactly one root-level [featureDevelopmentInfo.md](featureDevelopmentInfo.md) for the entire application and one root-level [leftout.md](leftout.md) for current feature progress. Do not create development-info files inside feature or package folders.

Before changing implementation or verification, read the relevant sections of both root documents and [requirements.md](requirements.md).

## Application development reference

Keep featureDevelopmentInfo.md synchronized with the actual implementation. Include:

- A table of contents, architecture, package structure and file responsibilities.
- Feature purpose, data flow and execution flow across screens, ViewModels, use cases, repositories, database, parsers, services and utilities.
- Dependencies and important Android APIs, including why they are used.
- Database schema, relationships, migrations, state management and business validation.
- Ingestion, background work, permissions, reconciliation, security and backup behavior.
- Tests, important decisions, assumptions, edge cases and known limitations.

Preserve accurate useful detail, remove repetition and obsolete information, and update file links when files move. Describe existing behavior honestly; do not present planned behavior as implemented.

## Current feature tracker

Keep leftout.md synchronized with requirements, the approved plan, code, tests and validation evidence. Every feature must have:

- A stable ID, requirement reference and scope (MVP, optional, future or excluded).
- An implementation status: Completed, Partial, Unstarted, Deferred or Intentionally excluded.
- A separate validation status supported by a code/test reference or dated executed evidence.
- Completed work, remaining work and a concrete next action.

Never renumber existing feature IDs. Add new rows for new scope, preserve deferred/excluded items, and keep all requirement areas and the 12 acceptance scenarios represented. Mark experimental parsers and pending physical-device checks explicitly. A feature can be implemented while its validation remains pending; passing a build or a synthetic fixture does not prove real-world support.

## Update discipline and evidence

Whenever implementation is added, removed, refactored or modified, or verification changes, update both root documents in the same change. Reflect the affected behavior, evidence and remaining work at the appropriate level of detail. Do not replace useful documentation with a generic template.

Retain [VALIDATION.md](VALIDATION.md) as dated execution evidence. Preserve prior results and limitations; append clearly dated records when checks are actually run. Use leftout.md as the current progress tracker, including failures and unresolved checks. Do not imply tests were rerun during a documentation-only change.

Keep [README.md](README.md) links and user-facing support limitations consistent with these documents. Verify local documentation links and ensure exactly one featureDevelopmentInfo.md remains after structural changes. No app rebuild is needed for changes confined to documentation; do not claim app validation from link checks.

Maintain progress through ordinary accompanying documentation updates. No background automation is required.

