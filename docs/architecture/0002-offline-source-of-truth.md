# ADR 0002: Offline Source Of Truth

Status: Accepted

## Decision

Room is the source of truth for Nextcloud-backed accounts. Screens observe repository `Flow` values, and user edits are persisted locally before network work starts. Each note has a stable local UUID and explicit synchronization state.

Remote writes must use the last known ETag. Applying a network result must verify that it does not replace a newer local edit. The last synchronized values are retained for conflict detection.

## Consequences

- Offline creation and editing are normal behavior rather than error cases.
- Process recreation can recover persisted edits.
- Network failures leave local content intact and visible.
