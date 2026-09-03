# ADR 0004: Android Markdown Presentation

Status: Accepted for Phase 2 and Phase 3

## Decision

Use Markwon behind interfaces in `markdown-android`. Compose hosts mature Android text widgets through `AndroidView`: a text view for rendered Markdown and an editable text widget with Markwon editor spans for source editing.

The concrete Markwon artifacts will be selected and license-checked when Markdown work begins. Portable preprocessing and internal-link parsing remain in `core`.

## Consequences

- Rendering and editor highlighting can evolve independently.
- Highlighting cannot alter source text, cursor position, or selection.
- Android widget behavior does not enter the portable core.
- The application theme must stay a `Theme.AppCompat` descendant, because the hosted AppCompat widgets take their default styles from AppCompat theme attributes. A framework-only theme leaves the editor without `focusableInTouchMode` and makes typing impossible.
- The hosted widgets are styled from two independent sources, so Compose colors must be passed into them explicitly.
- Anything that marks up a note for the reader, such as finding text in it, works on the rendered text. Rendering removes the source markers and shifts every offset, so source offsets cannot address the rendered note.
- A rendered note is held in a `SpannableString`, which has no watchers. Spans added to it after rendering must invalidate the view explicitly or they stay invisible.
- The rendered note is selectable text. A text view only offers selection when its movement method reports that it can select arbitrarily, which `LinkMovementMethod` does not, and that method also clears the selection on any touch outside a link. Link tapping is therefore built on top of a selection-capable movement method instead of the other way round.
- Editor device tests must inject real key events, because setting widget text directly bypasses input focus.
