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
  `MainShell` pages: For You (**Feed**), Messages, Stories, Unsent, **Profile** (own + anyone's, 0.6.0), TikTok (VNC), Users (admin only), Settings, Search —
  on phones the TikTok tab bar (Home · Stories · Inbox · Unsent · Profile; Profile is a bottom sheet with the other
  pages, switch account and log out), on wide screens a 72 dp icon sidebar plus "More" (see *Look*). `data/` holds `AppGraph`
  (OkHttp with pinned trust), `SecureCookieJar` (session cookie encrypted with an Android Keystore AES-GCM key),
  `ServerTrust`, `AuthentikSignIn` (Custom Tab login, `AuthentikRedirectActivity` for `<applicationId>:/authentik`,
  Keystore-encrypted token store), `AppController` (state/`tt` status/events), `Prefs`, `AutoSave`.

## Look (0.5.0)

Styled after TikTok's own app and web UI, in step with the server's web UI (tiktok_logger v1.14, its ARCHITECTURE §5):

- `ui/theme/Theme.kt`: `WaColors` carry the web's tokens (`#161823` text, `#F1F1F2`/`#2F2F2F` fills, `#121212` dark
  background, red `#FE2C55` primary, `accentNav` for the active tab, `tabbar` white/black, own bubbles `bubbleOut` =
  the web's `--out`). `WaType` uses **TikTok Sans** (`res/font/tiktok_sans_*.ttf`, the official v4.000 release's
  12pt cut, SIL OFL — `third_party/TikTokSans-OFL.txt`).
- `ui/theme/WaIcons.kt` is **generated** from the server's `web/src/components/Icon.tsx`: outline icons (2 px round
  stroke) and solid `…Fill` twins for the active tab, plus a few solid native-only extras at the end.
- `MainShell`: `BottomRail` (TikTok's tab bar, labels under the icons, black on For You, hidden inside a chat),
  `SideRail` (folded TikTok sidebar), `MoreSheet` (Profile sheet).
- Inbox (`ChatScreens.ChatListPane`): centred bold "Messages", capsule search, text chips, 56 dp avatars,
  "preview · time". Chat: 44 dp header avatar with name + @username, the sender's 32 dp avatar beside incoming runs,
  8 dp bubbles without shadow and without the time inside (a centred grey line at each day and after an hour's gap),
  bare photos/videos, links/mentions in the text colour on own bubbles. `Composer`: grey 8 dp field with the attach icon
  inside and a red round send arrow. For You: the author's avatar on top of the action column, solid like/comment/share
  icons with bold counts. Login (`AuthShell`): the mark + wordmark top left, one plain centred column, grey borderless
  4 dp fields, 8 dp buttons. Stories: cyan rings.

## Feed

`FeedScreen` + `FeedViewModel`. Unlike a normal infinite feed, **the server owns the feed cursor**: it reads TikTok's
For You in its own browser tab and answers with the video that is on screen. The app therefore:

- loads a **window** on open (`GET /api/feed/window?limit=4`: the video in view plus the next ones) and swipes through it
  in a `VerticalPager` (0.7.0 — before, one `FeedItem` and `POST /api/feed/step` per swipe swapped the video in at once);
- when the pager settles on a video, tells the server with `POST /api/feed/goto {id}` (its tab moves there) and merges
  the answer with `mergeFeedWindow` — known videos refreshed in place, new ones appended, nothing above moves — so the
  list keeps growing ahead of the user; a spinner shows only on the last video while that call runs;
- likes with `POST /api/feed/:id/like` (optimistic, then the server's answer — it clicks TikTok's own button and polls
  it, and answers 409 `feed_moved` if the feed moved on, in which case the app reverts and asks for a refresh);
- opens comments with `GET /api/feed/:id/comments` and posts with `POST /api/feed/:id/comment` (limit 150 characters,
  enforced by the server too);
- sends a video into a chat with the normal `POST /api/messages` path, using the canonical
  `https://www.tiktok.com/@user/video/<id>` link the server returns.

Playback (`VideoPane`, shared with a profile's videos): a pool of three ExoPlayers (`PLAYER_POOL`, `buildPlayer` in
`media/AudioController.kt`, an OkHttp data source so the session cookie is sent) with `REPEAT_MODE_ONE`. Page *p* plays
in slot `poolSlot(p) = p mod 3`, so the settled video and both neighbours are prepared and buffered at once: the next
video's frame is already on the page while the finger drags it in, and it starts the moment the page settles (the others
wait at 0:00). The surface is a `TextureView` (`res/layout/feed_player_view.xml`) so it moves with the page; the poster
covers a page until its slot reports `onRenderedFirstFrame` for that video. A player error drops the slot's load and
reloads it (three tries per video). Caption, action column and player row belong to each page and slide with it; only
the settled page's buttons act (on `VideoActionsModel.item`). Double tap likes. Pitfall: anything the pager or its
settle listener reads from the list must go through `rememberUpdatedState` — a captured `items.size` froze the pager at
the first window once. the video URL is `/api/feed/media/<ref>`, a short-lived reference the server resolves
to the signed CDN URL and streams (no Range support server-side: the server sends the whole body once). Sound is muted
by default, like the web UI.

Scaling: the rail's resize button switches between **fill** (`AspectRatioFrameLayout.RESIZE_MODE_ZOOM`, the default —
TikTok's look, a video that is not 9:16 loses its sides) and **fit** (`RESIZE_MODE_FIT`, the whole frame with black
bars). The poster uses the matching `ContentScale`, and the choice is stored in `Prefs.feedFitVideo` so it survives a
restart. `feedResizeMode()`/`feedContentScale()` are the single mapping from the flag to the two surfaces.

## Stories

`StoriesScreen` + `StoriesViewModel` mirror the web UI's Stories page over `GET /api/stories/status`, `/api/stories/authors`
and `/api/stories?user=&before=&limit=` (plus `POST /api/stories/check` for "Check now"). The capture itself is the
server's (`tt/stories.ts`); the app only reads what was stored and can save a story.

- a row of authors (`Avatar` + a count badge, the row the web shows in gradient rings), a `LazyVerticalGrid` of tiles and a
  full-screen `HorizontalPager` viewer: videos play through one ExoPlayer (silent, `REPEAT_MODE_OFF`) and move on at the
  end, photos hold five seconds — the web's behaviour;
- a **video story has no stored cover**, so its tile is a dark square with a play badge instead of a frame (the browser
  shows a paused `<video>`; MediaMetadataRetriever cannot authenticate against `/api/media` because it has its own HTTP
  stack and no session cookie, and downloading every video just for a thumbnail is not worth it);
- **download** = `MediaOps.saveStoryToGallery` → MediaStore (`Pictures/` or `Movies/tiktok_logger`), the bytes taken from
  the server's `/api/media/<id>?download=1`, the name built by `MediaOps.storyFileName` exactly like the server's
  `Content-Disposition` (`story_@user_2026-10-23_05-37.jpg`). Android 10+; a story whose media is still downloading says so
  instead of saving an empty file.

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

## Multiple accounts (0.4.0)

`Prefs.accounts` is the list of accounts (id, server URL, HTTP choice, user name), `Prefs.activeAccountId` the one
being shown; `AppController` owns the switch:

- `open(account)` closes the current `ServerSession` and builds the next one; `ServerSession` gets its own
  `AccountClient` (`AppGraph.accountClient(id)` = encrypted cookie jar + `Authentik` instance + OkHttp client per
  account), so a switch never carries a cookie or bearer token across servers. Clients are cached, so switching back is
  instant and still signed in.
- `beginAddAccount()` sets an "adding" flag and a pending id; `ServerScreen` shows on top of the open account and probes
  with that id's client (`pendingAccountId()`), so an authentik sign-in during the check is stored for the account that
  is about to exist. `connect()` switches to a server already in the list, otherwise saves the account and opens it;
  `cancelAddAccount()` deletes the pending secrets.
- `removeAccount()` signs out on the server (best effort), deletes the cookie and authentik files, drops the entry, and
  opens the next account — or the server screen when none is left. `sweepOrphanSecrets()` removes files of accounts
  that no longer exist at startup.
- Trusted certificates moved from one global set to per-host keys (`certs:<host>`); `ServerTrust.serverHost` reloads
  the anchors of the account (or server being added) whenever it changes.
- The UI is `ui/screens/AccountsScreen.kt`: `AccountSwitcherDialog` (Settings → Account, the rail's account icon, the
  sign-in screens and after adding a server) and `AccountCard` for the Settings header. `MainShell` keys the whole
  shell on the session id so tab state and view models never leak between accounts.
- Migration: the single saved `server_url`/`allow_http`/`trusted_certs` of 0.3.0 become `Account.MAIN`, which keeps the
  old secret file names; `accounts_ready` makes it run once.

## Tests

- `core`: JSON/API contract (`ApiClientTest`, including the feed and the 0.6.0 profile/follow/repost/thread calls), authentik flow (`AuthentikTest`), RFB/DES
  (`RfbTest`), text/link handling (`TtTextTest`), formatting, and a live-server test that is skipped unless a server
  address is provided.
- `app`: view-model selection logic, auto-save policy, the feed pager (`FeedPagerTest`: window merge, player slots), the feed's rail and scaling
  (`FeedResizeTest`: the pref, the `RESIZE_MODE`/`ContentScale` mapping on a real `PlayerView`, clicking the resize
  button both ways), and screenshot render tests (Roborazzi) for the main screens.
- CI runs `:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.

## Video actions and profiles (0.6.0, server v1.15)

`VideoActionsModel` holds everything one can do with the video on screen — like, repost (`POST /api/feed/:id/repost`),
follow its author (`POST /api/profiles/:u/follow`), comments with threads (`…/comments/:cid/replies`, `…/reply`) and
comment likes (`…/comments/:cid/like`), send to a chat — and shows what the server answered (the server clicks TikTok's
own controls for like/repost/follow). `FeedViewModel` (For You) and `ProfileViewModel` (a profile's list) extend it, and
both draw `VideoPane`: the player (one ExoPlayer; TikTok's captions side-loaded as WebVTT `SubtitleConfiguration`s with
the text track disabled until the CC button, `setPlaybackSpeed` for 1/1.5/2/0.5/0.75×, a seek row polled every 250 ms),
`FeedActionRail` (avatar → profile, follow badge, like, comments, repost, share, save, fit/fill, sound) and the
comments dialog. "Save" downloads `/api/feed/:id/download` into the gallery's tiktok_logger album
(`MediaOps.saveFeedVideoToGallery`). `ProfileScreen` (`ProfileRoute("me" | username)`) is the header, the list tabs
(owner: videos, reposts, favorites, liked; others: videos, reposts) and a 3-column grid that loads the next page near
its end; a tile opens the list in `VideoPane`, swipes move through it, and every change is mirrored into the grid.

## Checking the feed without a TikTok account

The Feed only renders when the server reports `tt.state = ready`, and the real server needs a logged-in browser tab,
so a local check uses a throwaway API that answers `/api/state` with `tt.state=ready` plus a canned `/api/feed` item.
Point the app at it with `wal_prefs.xml` (`server_url=http://10.0.2.2:<port>`, `allow_http=true`,
`secure_screen=false`) written through `run-as`, grant `android.permission.ACCESS_LOCAL_NETWORK`, and tap through with
`uiautomator dump` + `input tap` (parse the XML per node: `content-desc` carries the button names, `bounds` the centre).

For the pager, the harness serves `/api/feed/window` + `/api/feed/goto` over a list of numbered clips. Encode test clips
as **MPEG-4 Part 2** (`ffmpeg -c:v mpeg4`), not H.264: the headless emulator's `c2.goldfish.h264.decoder` fails at
`queueInputBuffer` with several players, which looks like a frozen feed. Drive it with `input swipe 540 1700 540 600 120`
(one per ~2 s; faster ones land in the snap animation on a loaded host) and read the caption/time from `uiautomator dump`.
With the TextureView surface, `screencap` shows the video frames.

Two capture limits: `screencap` is black while FLAG_SECURE is on, and it is **also** black for video inside a
`PlayerView` (a SurfaceView), even with the flag off — `screenrecord` in this emulator is no better. To see the
scaling, give the harness item a `poster` image instead of a `video.src`: the poster uses the same flag
(`feedContentScale`) and does show up in a screencap.

