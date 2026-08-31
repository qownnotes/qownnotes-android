# ADR 0003: Nextcloud Integration

Status: Accepted for Phase 2

## Decision

The first remote backend uses the Nextcloud Android Single Sign-On library and accounts supplied by the installed Nextcloud Files application. It does not collect or store a user's normal password.

The backend adapter owns SSO and Notes REST API details. It exposes only `core` models and contracts to the rest of the application. API 1.2 or newer is required so updates can use ETag-based optimistic concurrency.

The implementation uses Android Single Sign-On 1.3.4 and its Retrofit/Gson transport. The database stores only the SSO account name and public account metadata; credentials remain owned by the SSO library and Nextcloud Files app. Pulls use collection ETags, `pruneBefore`, and chunk cursors, and the local checkpoint advances only inside the transaction that applies a completed response.

## Consequences

- Initial account setup requires Nextcloud Files for Android.
- Standalone Login Flow v2 remains a later backend/authentication option.
- Screen code remains independent of account and HTTP APIs.
