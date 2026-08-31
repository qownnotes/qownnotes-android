# ADR 0005: Synchronization Scheduling

Status: Accepted for Phase 4

## Decision

Use WorkManager for durable background synchronization, with unique work scoped to each account. Foreground refresh and editor-triggered sync invoke the same coordinator and persist state before scheduling network work.

Draft persistence and Markdown highlighting use independent debounce schedules. Workers classify retryable failures separately from authentication, permission, read-only, and conflict failures.

The WorkManager dependency will be added when synchronization is implemented rather than as unused Phase 1 infrastructure.

## Consequences

- Work survives process death and observes Android background constraints.
- Multiple triggers cannot create uncontrolled duplicate synchronization jobs.
- Synchronization policy remains testable outside the worker implementation.
