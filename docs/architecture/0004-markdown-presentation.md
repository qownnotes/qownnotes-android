# ADR 0004: Android Markdown Presentation

Status: Accepted for Phase 2 and Phase 3

## Decision

Use Markwon behind interfaces in `markdown-android`. Compose hosts mature Android text widgets through `AndroidView`: a text view for rendered Markdown and an editable text widget with Markwon editor spans for source editing.

The concrete Markwon artifacts will be selected and license-checked when Markdown work begins. Portable preprocessing and internal-link parsing remain in `core`.

## Consequences

- Rendering and editor highlighting can evolve independently.
- Highlighting cannot alter source text, cursor position, or selection.
- Android widget behavior does not enter the portable core.
