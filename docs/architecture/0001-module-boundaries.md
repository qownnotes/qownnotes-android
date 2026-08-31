# ADR 0001: Module Boundaries

Status: Accepted

## Decision

Keep portable models and policies in `core`. Keep Room in `data`, Nextcloud integration in `backend-nextcloud`, Android Markdown widgets in `markdown-android`, and composition/UI in `app`.

The UI depends on `core` contracts and does not call Room, HTTP clients, SSO APIs, or document providers directly. Constructor injection through a small application component is preferred over an Android-specific dependency injection framework until the object graph requires one.

## Consequences

- Backend and Android framework details cannot leak into portable policy code.
- The core can be extracted to Kotlin Multiplatform after its contracts stabilize.
- A module may remain small until its boundary has production behavior.
