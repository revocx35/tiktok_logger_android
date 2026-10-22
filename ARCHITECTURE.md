# tiktok_logger for Android — architecture

Three Gradle modules: `core` (Kotlin/JVM: API client, models, authentik interceptor, VNC/RFB client — unit-testable
without Android), `app` (Compose UI, Android-only pieces), `media` code inside `app` (ExoPlayer, audio, auto-save).

Phone ⇄ tiktok_logger server only. The server talks to TikTok through its own browser (the messages tab and, for the
feed, a second tab); the app never contacts TikTok or its CDN. Media URLs are server-relative (`/api/media/<id>`,
`/api/feed/media/<ref>`, `/api/vnc`, …) and fetched with the session cookie in `SecureCookieJar`.

## Modules and flow

- **core/**: `ApiClient` (OkHttp, JSON via kotlinx-serialization, every call with `Origin` + `X-CSRF-Token`),
  `Models` (mirrors `shared/api.d.ts` of the server), `EventStream` (SSE `/api/events`), `Authentik` (forward-auth
  interceptor: OAuth2 + PKCE in the system browser, bearer per request, `X-App-Authorization` swap for app tokens),
  `vnc/` (RFB client: DES for VNC auth, raw/zlib/hextile decoding).
- **app/**: `AppRoot` routes server → signup → login/recover → recovery key → TikTok login → main;
  `MainShell` tabs: Chats, **Feed**, Unsent, Search, TikTok (VNC), Settings, Users (admin only). `data/` holds `AppGraph`
  (OkHttp with pinned trust), `SecureCookieJar` (session cookie encrypted with an Android Keystore AES-GCM key),
  `ServerTrust`, `AuthentikSignIn` (Custom Tab login, `AuthentikRedirectActivity` for `<applicationId>:/authentik`,
  Keystore-encrypted token store), `AppController` (state/`tt` status/events), `Prefs`, `AutoSave`.

## Feed

`FeedScreen` + `FeedViewModel`. Unlike a normal infinite feed, **the server owns the feed cursor**: it reads TikTok's
For You in its own browser tab and answers with the video that is on screen. The app therefore:

- calls `GET /api/feed` on open and keeps exactly one `FeedItem`;
- moves with `POST /api/feed/step {dir}` (swipe up/down or the arrows), showing a spinner until the new item arrives;
- likes with `POST /api/feed/:id/like` (optimistic, then the server's answer — it clicks TikTok's own button and polls
  it, and answers 409 `feed_moved` if the feed moved on, in which case the app reverts and asks for a refresh);
- opens comments with `GET /api/feed/:id/comments` and posts with `POST /api/feed/:id/comment` (limit 150 characters,
  enforced by the server too);
- sends a video into a chat with the normal `POST /api/messages` path, using the canonical
  `https://www.tiktok.com/@user/video/<id>` link the server returns.

Playback: one shared ExoPlayer (`buildPlayer` in `media/AudioController.kt`, an OkHttp data source so the session cookie
is sent) with `REPEAT_MODE_ONE`; the video URL is `/api/feed/media/<ref>`, a short-lived reference the server resolves
to the signed CDN URL and streams (no Range support server-side: the server sends the whole body once). Sound is muted
by default, like the web UI.

## Chats and messages

`ChatScreens`/`ChatViewModel` page through `GET /api/messages` (`before`/`after`/`around`), render `MessageType`s
(text, photo, video, voice, audio, sticker, share, music, system) with `MessageBubble`/`MessageMedia`, and mark
deleted messages (the server flags them; the app draws the red frame and the *Unsent* tab lists them).
Sending goes through `POST /api/messages` (text) or upload + `POST /api/messages/media`; the server types it into
TikTok's composer and confirms by re-reading the chat, so a failure surfaces as an error, never a silent no-op.

## TikTok screen (VNC)

`ui/vnc/VncView` connects a WebSocket to `/api/vnc`, then speaks RFB to the server's bridge, which tunnels to the
tenant's x11vnc inside the chromium container (the app is the only client that may open that tunnel). Tap = click,
drag = scroll, pinch = zoom, plus a keyboard and a view-only switch. `POST /api/tt/restart` restarts the browser.

## authentik

`core/Authentik.kt` is shared with the owner's other clients. When the proxy answers with authentik's sign-in
redirect, the app shows **Sign in with authentik**, runs OAuth2 + PKCE against the public client, keeps the refresh
token encrypted in the Keystore, and sends `Authorization: Bearer <access token>` on every request to that server
(the outpost validates it by introspection). The server's own session stays as it is; see `docs/authentik.md`.

## Tests

- `core`: JSON/API contract (`ApiClientTest`, including the feed), authentik flow (`AuthentikTest`), RFB/DES
  (`RfbTest`), text/link handling (`TtTextTest`), formatting, and a live-server test that is skipped unless a server
  address is provided.
- `app`: view-model selection logic, auto-save policy, and screenshot render tests (Roborazzi) for the main screens.
- CI runs `:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
