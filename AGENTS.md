# AGENTS.md — notes for AI coding agents

Native Android client (Kotlin, Jetpack Compose) for the self-hosted **tiktok_logger** server
(github.com/revocx35/tiktok_logger). The phone talks **only** to that server; it never contacts TikTok or its CDN.
Read [ARCHITECTURE.md](ARCHITECTURE.md) (modules, flows, the *Look* section) and [README.md](README.md) first.

## Layout

| Path | What |
|---|---|
| `core/` | Kotlin/JVM, no Android: `ApiClient` (OkHttp + kotlinx-serialization, `Origin` + `X-CSRF-Token` on every call), `Models.kt` (mirror of the server's `shared/api.d.ts`), `EventStream` (SSE), `Authentik` (OAuth2 + PKCE interceptor), `vnc/` (RFB client) |
| `app/src/main/java/.../data/` | `AppGraph` (pinned OkHttp per account), `SecureCookieJar` (Keystore AES-GCM), `ServerTrust`, `AuthentikSignIn`, `AppController` (accounts, state, events), `Prefs` |
| `app/src/main/java/.../ui/MainShell.kt` | Navigation: TikTok tab bar (phones), icon sidebar (wide), Profile/More sheet, routes |
| `app/src/main/java/.../ui/screens/` | Chats (inbox + chat), Feed (For You; `VideoActionsModel` + `VideoPane` are shared with profiles), Profile (0.6.0), Stories, Pages (Unsent, Search), TikTok (VNC), Settings, Admin, Auth, Accounts, Server |
| `app/src/main/java/.../ui/components/` | `MessageBubble`, `Composer`, `MessageMedia`, `Avatar`, `WaTextView`, shared `Components.kt` (buttons, fields, chips, search box, `BrandMark`) |
| `app/src/main/java/.../ui/theme/` | `Theme.kt` (colour tokens + type scale = the web's `styles.css`), `WaIcons.kt` (generated from the web's `Icon.tsx`) |
| `app/src/main/res/font/` | TikTok Sans TTFs (OFL, `third_party/TikTokSans-OFL.txt`) |
| `app/src/test/` | Robolectric + Roborazzi screenshot tests (`ScreenshotTest`), feed scaling, selection, accounts |
| `docs/` | `authentik.md`, README screenshots |

## Commands

```bash
echo "sdk.dir=/opt/android-sdk" > local.properties          # on CT 105; local.properties is gitignored
/usr/local/bin/heavy ./gradlew :core:test :app:testDebugUnitTest --max-workers=1   # 35 core + 45 app tests
/usr/local/bin/heavy ./gradlew :app:recordRoborazziDebug --max-workers=1           # renders app/build/screenshots/*.png
/usr/local/bin/heavy ./gradlew :app:lintDebug :app:assembleDebug --max-workers=1
```
On the shared host every Gradle run goes through `/usr/local/bin/heavy` with `--max-workers=1` (an unbounded build
froze the container once). Releases: bump `versionCode`/`versionName` in `app/build.gradle.kts`, push a `v*` tag — the
release workflow signs with the `KEYSTORE_*` secrets and attaches the APK. There is no local release key.

## Conventions

- Server API first: a change to `shared/api.d.ts` on the server goes into `core/Models.kt` in the same task.
- The look follows the server's web UI (which follows TikTok): colours only through `WaColors` tokens, text through
  `WaType`; new icons go into the web's `Icon.tsx` first and are regenerated into `WaIcons.kt` (outline = `stroke(...)`,
  solid = `solid(...)`; nav icons need a solid `…Fill` twin).
- Every web UI change must reach this app in the same task (native screens; there is no WebView).
- Update ARCHITECTURE.md, README.md and this file in the same commit as the change.

## Never

- Talk to TikTok or a TikTok CDN from the phone, or log message text, names, cookies, tokens or media URLs.
- Store the session, authentik tokens or certificates unencrypted; allow cleartext HTTP except for an account the owner
  explicitly set to HTTP; weaken certificate pinning.
- Use TikTok's logo or name as the app's mark (the mark is the server's own shield tile).
- Commit `local.properties`, keystores or `keystore.properties`.

## Gotchas

- `WaIcons` outline icons are strokes: tint works, but `fill` does not — use the `…Fill` twin for a solid look.
- The phone tab bar has room for five slots; anything else goes into the Profile sheet (`MoreSheet`).
- `MessageBubble` has no time inside: the chat list (`ChatPane`) inserts centred day/time rows instead; incoming
  bubbles get a `BubbleAvatar` (shown on the first of a run). Selection mode hides the avatars.
- `FeedActionRail`'s parameters are used by `FeedResizeTest` and `ScreenshotTest`; keep new ones optional.
- `VideoPane` owns a pool of three ExoPlayers (released on dispose) — a profile's viewer and For You never share one.
  It is a `VerticalPager` over the model's list (0.7.0): `PagerState`'s page count and the settle listener read the list
  through `rememberUpdatedState` (a captured `items.size` once froze the pager at the first window). Test clips for the
  emulator must be MPEG-4 Part 2, not H.264 (the goldfish decoder fails with several players) — ARCHITECTURE.
- New chat, chat actions (mute/pin/delete/block), groups and stickers are not in the server yet (waiting for live tests
  with the owner's alt account) — do not add UI for them before the server routes exist.
- Screenshots of the real feed: `screencap` is black for `PlayerView` (SurfaceView) and while FLAG_SECURE is on — see
  ARCHITECTURE "Checking the feed without a TikTok account".
