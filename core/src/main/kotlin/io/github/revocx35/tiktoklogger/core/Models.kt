package io.github.revocx35.tiktoklogger.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Kotlin mirror of the server↔client contract in tiktok_logger's `shared/api.d.ts`. Keep both in sync.
 * Timestamps are Unix epoch milliseconds. Unknown enum values fall back to a default
 * (see [ApiJson]) so a newer server never crashes an older app.
 */

val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = false
}

@Serializable
data class ApiErrorBody(val error: ApiErrorDetail? = null)

@Serializable
data class ApiErrorDetail(val code: String = "http_error", val message: String = "", val retryAfter: Int? = null)

/* ------------------------------------------------------------------ state */

@Serializable
enum class TtState {
    @SerialName("idle") Idle,
    @SerialName("starting") Starting,
    @SerialName("login") Login,
    @SerialName("challenge") Challenge,
    @SerialName("syncing") Syncing,
    @SerialName("ready") Ready,
    @SerialName("error") Error,
}

@Serializable
data class TtMe(val id: String, val username: String? = null, val name: String? = null)

@Serializable
data class SyncProgress(val phase: String = "history", val chatsDone: Int = 0, val chatsTotal: Int = 0)

@Serializable
data class TtStatus(
    val state: TtState = TtState.Idle,
    val detail: String? = null,
    val me: TtMe? = null,
    val sync: SyncProgress? = null,
    val mediaQueue: Int = 0,
    val lastEventAt: Long? = null,
    val since: Long = 0,
) {
    val connected: Boolean get() = state == TtState.Ready || state == TtState.Syncing
    val needsLogin: Boolean get() = state == TtState.Login || state == TtState.Challenge
}

@Serializable
data class AppState(
    val hasOwner: Boolean = false,
    val authenticated: Boolean = false,
    val onboardingComplete: Boolean = false,
    val csrfToken: String? = null,
    val username: String? = null,
    val totpEnabled: Boolean? = null,
    val tt: TtStatus? = null,
    /** Authenticated only. The admin (the first account) manages the other users. */
    val role: UserRole = UserRole.User,
)

@Serializable
enum class UserRole {
    @SerialName("admin") Admin,
    @SerialName("user") User,
}

/* -------------------------------------------------------------------- auth */

@Serializable data class SignupRequest(val setupToken: String, val username: String, val password: String)
@Serializable data class SignupResponse(val recoveryKey: String)
@Serializable data class LoginRequest(val username: String, val password: String, val totp: String? = null)
@Serializable
data class LoginResponse(
    val ok: Boolean = false,
    val needTotp: Boolean? = null,
    /** The password was the temporary one an admin issued: the user must choose their own ([ApiClient.claim]). */
    val mustChangePassword: Boolean? = null,
    /** With [mustChangePassword]: a reset of an existing account, so the claim needs its recovery key. */
    val needRecoveryKey: Boolean? = null,
)
@Serializable data class ClaimRequest(val username: String, val password: String, val newPassword: String, val recoveryKey: String? = null)
@Serializable data class ClaimResponse(val recoveryKey: String)
@Serializable data class RecoverRequest(val username: String, val recoveryKey: String, val newPassword: String)
@Serializable data class RecoverResponse(val recoveryKey: String)
@Serializable data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class SessionInfo(
    val id: String,
    val current: Boolean = false,
    val createdAt: Long = 0,
    val lastSeenAt: Long = 0,
    val expiresAt: Long = 0,
    val ip: String? = null,
    val userAgent: String? = null,
)

@Serializable data class TotpSetupResponse(val secret: String, val otpauthUrl: String)
@Serializable data class TotpEnableRequest(val code: String, val password: String)
@Serializable data class TotpDisableRequest(val password: String, val code: String)
@Serializable data class RotateRecoveryKeyRequest(val password: String, val totp: String? = null)
@Serializable data class RotateRecoveryKeyResponse(val recoveryKey: String)
@Serializable data class VncCredentials(val password: String)
@Serializable data class OkResponse(val ok: Boolean = true)
@Serializable data class ForceRequest(val force: Boolean? = null)
/** `POST /api/tt/session`: log in with a session (cookies) copied from the owner's own browser. */
@Serializable data class ImportSessionRequest(val cookies: String)

/* -------------------------------------------------------------- chats etc. */

@Serializable
enum class ChatKind {
    @SerialName("user") User,
    @SerialName("group") Group,
}

@Serializable
data class ChatSummary(
    val id: String,
    val kind: ChatKind = ChatKind.User,
    val name: String = "",
    /** The other person's TikTok username in one-to-one chats. */
    val username: String? = null,
    val avatarUrl: String? = null,
    val lastTs: Long? = null,
    val lastMessage: MessagePreview? = null,
    /** Message requests and other non-primary folders. */
    val folder: String? = null,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val removed: Boolean = false,
    val messageCount: Int = 0,
    val deletedCount: Int = 0,
)

@Serializable
data class MessagePreview(
    val id: String,
    val type: MessageType = MessageType.Unknown,
    val fromMe: Boolean = false,
    val senderName: String? = null,
    val text: String? = null,
    val deleted: Boolean = false,
    val ts: Long = 0,
)

/** text · image · video · gif · voice · sticker · share (post/reel/story/profile/link) · music · system · view_once · unknown */
@Serializable
enum class MessageType {
    @SerialName("text") Text,
    @SerialName("image") Image,
    @SerialName("video") Video,
    @SerialName("gif") Gif,
    @SerialName("voice") Voice,
    @SerialName("sticker") Sticker,
    @SerialName("share") Share,
    @SerialName("music") Music,
    @SerialName("system") System,
    @SerialName("view_once") ViewOnce,
    @SerialName("unknown") Unknown,
}

@Serializable
enum class MediaStatus {
    @SerialName("none") None,
    @SerialName("pending") Pending,
    @SerialName("downloaded") Downloaded,
    @SerialName("failed") Failed,
    @SerialName("too_large") TooLarge,
    @SerialName("skipped") Skipped,
    @SerialName("view_once") ViewOnce,
    @SerialName("unavailable") Unavailable,
}

@Serializable
data class MediaInfo(
    val status: MediaStatus = MediaStatus.None,
    val url: String? = null,
    val mime: String? = null,
    val filename: String? = null,
    val size: Long? = null,
    val width: Double? = null,
    val height: Double? = null,
    val durationSec: Double? = null,
    /** Not sent by tiktok_logger (kept so the shared media UI compiles; always null). */
    val thumbDataUrl: String? = null,
)

@Serializable
data class QuotedSnapshot(
    val id: String? = null,
    val senderName: String? = null,
    val fromMe: Boolean = false,
    val type: MessageType = MessageType.Unknown,
    val text: String? = null,
)

@Serializable data class Reaction(val senderId: String, val senderName: String? = null, val emoji: String, val ts: Long = 0, val fromMe: Boolean = false)

/** A shared post/reel/story/profile/link (TikTok's "XMA" attachments). */
@Serializable
data class ShareInfo(
    val kind: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val caption: String? = null,
    val url: String? = null,
    val eyebrow: String? = null,
)

@Serializable data class MusicInfo(val title: String? = null, val artist: String? = null)
@Serializable data class Mention(val id: String, val name: String? = null)
@Serializable data class AlbumPos(val index: Int = 0, val count: Int = 1)

@Serializable
data class Message(
    val id: String,
    val chatId: String,
    val senderId: String? = null,
    val senderName: String? = null,
    val senderUsername: String? = null,
    val fromMe: Boolean = false,
    val ts: Long = 0,
    val type: MessageType = MessageType.Unknown,
    val text: String? = null,
    val media: MediaInfo? = null,
    val quoted: QuotedSnapshot? = null,
    val share: ShareInfo? = null,
    val music: MusicInfo? = null,
    val mentions: List<Mention> = emptyList(),
    val forwarded: Boolean = false,
    val album: AlbumPos? = null,
    /** Disappearing message: when TikTok removes it. */
    val expiresAt: Long? = null,
    /** 1 sent · 3 seen (own messages only). */
    val ack: Int? = null,
    val reactions: List<Reaction> = emptyList(),
    val edited: Boolean = false,
    val editedAt: Long? = null,
    val editCount: Int = 0,
    /** Unsent by the sender (or gone from TikTok): the content above is the logged copy. */
    val deletedAt: Long? = null,
    /** "sender" (unsent live) · "missing" (gone, noticed by a re-check). */
    val deletedBy: String? = null,
    val source: String = "live",
    val capturedAt: Long = 0,
)

@Serializable
data class MessagePage(val messages: List<Message> = emptyList(), val nextBefore: String? = null, val nextAfter: String? = null)

@Serializable data class MessageEdit(val body: String? = null, val capturedAt: Long = 0)

@Serializable data class ChatRef(val id: String, val name: String = "", val kind: ChatKind = ChatKind.User)
@Serializable data class DeletedFeedItem(val chat: ChatRef, val message: Message)
@Serializable data class DeletedFeedPage(val items: List<DeletedFeedItem> = emptyList(), val nextBefore: String? = null)
@Serializable data class MediaFeedItem(val chat: ChatRef, val message: Message)
@Serializable data class MediaFeedPage(val items: List<MediaFeedItem> = emptyList(), val cursor: String = "0", val more: Boolean = false)
@Serializable data class SearchHit(val chat: ChatRef, val message: Message)
@Serializable data class SearchResponse(val hits: List<SearchHit> = emptyList(), val truncated: Boolean = false)

/* ------------------------------------------------------------------ events */

@Serializable
sealed class ServerEvent {
    @Serializable @SerialName("ig_state")
    data class TtStateChanged(val status: TtStatus) : ServerEvent()

    @Serializable @SerialName("message")
    data class NewMessage(val chatId: String, val messageId: String) : ServerEvent()

    @Serializable @SerialName("message_update")
    data class MessageUpdate(val chatId: String, val messageId: String, val reason: String = "") : ServerEvent()

    @Serializable @SerialName("chat_update")
    data class ChatUpdate(val chatId: String) : ServerEvent()

    @Serializable @SerialName("sync_progress")
    data class SyncProgressChanged(val sync: SyncProgress? = null) : ServerEvent()

    @Serializable @SerialName("session_revoked")
    data object SessionRevoked : ServerEvent()
}

/* ---------------------------------------------------------------- settings */

@Serializable
data class Settings(
    val downloadHistoryMedia: Boolean = true,
    val mediaMaxMb: Int = 100,
    val historyPerChat: Int = 100,
    val readReceipts: Boolean = false,
    val sessionRefresh: Boolean = true,
    val sessionRefreshHours: Int = 2,
    /** Re-read the newest conversations this often (minutes) to notice messages deleted on TikTok. */
    val reconcileMinutes: Int = 10,
)

@Serializable
data class SettingsPatch(
    val downloadHistoryMedia: Boolean? = null,
    val mediaMaxMb: Int? = null,
    val historyPerChat: Int? = null,
    val readReceipts: Boolean? = null,
    val sessionRefresh: Boolean? = null,
    val sessionRefreshHours: Int? = null,
    val reconcileMinutes: Int? = null,
)

/* ----------------------------------------------------------------- sending */

@Serializable data class SendTextRequest(val text: String, val quotedId: String? = null)
@Serializable data class SendResult(val id: String? = null)
@Serializable data class UploadResponse(val uploadId: String, val size: Long = 0)

/** auto: photo/video/audio by type · voice: voice message */
@Serializable
enum class SendMediaKind {
    @SerialName("auto") Auto,
    @SerialName("voice") Voice,
}

@Serializable data class SendMediaRequest(val uploadId: String, val kind: SendMediaKind, val quotedId: String? = null)
@Serializable data class ReactRequest(val emoji: String)
@Serializable data class EditRequest(val text: String)

@Serializable data class AuditEntry(val id: Long, val ts: Long = 0, val event: String = "", val ip: String? = null, val detail: String? = null)
@Serializable data class WipeRequest(val password: String, val totp: String? = null)


/* --------------------------------------------------------------------- feed */
// TikTok's For You, read through the server's browser. Video URLs are app URLs (/api/feed/media/<ref>) that stream
// through the server: the phone never talks to TikTok or its CDN.

@Serializable
data class FeedAuthor(val username: String? = null, val name: String? = null, val avatar: String? = null)

@Serializable data class FeedStats(val likes: Long? = null, val comments: Long? = null, val shares: Long? = null, val plays: Long? = null)

@Serializable
data class FeedVideo(val src: String? = null, val poster: String? = null, val muted: Boolean = false, val playing: Boolean = false)

@Serializable
data class FeedItem(
    val id: String,
    val url: String = "",
    val author: FeedAuthor = FeedAuthor(),
    val caption: String? = null,
    val music: String? = null,
    val stats: FeedStats = FeedStats(),
    val liked: Boolean = false,
    val video: FeedVideo? = null,
)

@Serializable data class FeedCommentAuthor(val username: String? = null, val name: String? = null)

@Serializable
data class FeedComment(
    val id: String,
    val author: FeedCommentAuthor = FeedCommentAuthor(),
    val text: String = "",
    val likes: Long? = null,
    val replies: List<FeedComment> = emptyList(),
)

@Serializable data class FeedItemResponse(val item: FeedItem? = null)
@Serializable data class FeedStepRequest(val dir: String)
@Serializable data class FeedLikeRequest(val like: Boolean)
@Serializable data class FeedLikeResponse(val liked: Boolean = false)
@Serializable data class FeedCommentRequest(val text: String)
@Serializable data class FeedCommentPage(val comments: List<FeedComment> = emptyList())

/* --------------------------------------------------------- user management */
// Served by the gateway under /api/admin/* (admin only): names, status and ports, never anyone's messages.

@Serializable
enum class UserStatus {
    @SerialName("pending") Pending,
    @SerialName("reset") Reset,
    @SerialName("active") Active,
    @SerialName("unknown") Unknown,
}

@Serializable
data class AdminUser(
    val id: Int,
    val username: String,
    val role: UserRole = UserRole.User,
    val disabled: Boolean = false,
    /** Port the user signs in on; null = the default port. */
    val port: Int? = null,
    val createdAt: Long = 0,
    val lastLoginAt: Long? = null,
    val status: UserStatus = UserStatus.Unknown,
    /** State of the user's TikTok link; null when the user's worker could not be asked. */
    val tt: TtState? = null,
)

@Serializable
data class AdminPort(
    val port: Int,
    /** The browser origin on this port; null = derived from the Host header. */
    val origin: String? = null,
    val usernames: List<String> = emptyList(),
)

@Serializable
data class AdminOverview(
    val currentUserId: Int,
    val users: List<AdminUser> = emptyList(),
    val defaultPort: Int = 0,
    val defaultOrigin: String? = null,
    val ports: List<AdminPort> = emptyList(),
    val availablePorts: List<Int> = emptyList(),
    val maxUsers: Int = 0,
)

@Serializable data class CreateUserResponse(val user: AdminUser, val temporaryPassword: String)

@Serializable
enum class ResetMode {
    /** Keep the data: the user needs their recovery key. */
    @SerialName("recovery") Recovery,
    /** Delete everything of the account and start over. */
    @SerialName("wipe") Wipe,
}

/** 'new' = the user had not signed in yet, so it was only a fresh temporary password. */
@Serializable
enum class ResetOutcome {
    @SerialName("recovery") Recovery,
    @SerialName("wipe") Wipe,
    @SerialName("new") New,
}

@Serializable data class ResetUserResponse(val temporaryPassword: String, val mode: ResetOutcome = ResetOutcome.New)

@Serializable
data class AdminAuditEntry(
    val id: Long = 0,
    val ts: Long = 0,
    val event: String = "",
    val actor: String? = null,
    val target: String? = null,
    val ip: String? = null,
    val detail: String? = null,
)
