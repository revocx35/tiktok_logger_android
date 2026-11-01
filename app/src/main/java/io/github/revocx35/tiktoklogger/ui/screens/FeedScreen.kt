package io.github.revocx35.tiktoklogger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import io.github.revocx35.tiktoklogger.media.MediaOps
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import io.github.revocx35.tiktoklogger.core.ChatSummary
import io.github.revocx35.tiktoklogger.core.FeedComment
import io.github.revocx35.tiktoklogger.core.FeedItem
import io.github.revocx35.tiktoklogger.data.Prefs
import io.github.revocx35.tiktoklogger.data.ServerSession
import io.github.revocx35.tiktoklogger.media.buildPlayer
import io.github.revocx35.tiktoklogger.ui.components.ErrorNote
import io.github.revocx35.tiktoklogger.ui.components.IconBtn
import io.github.revocx35.tiktoklogger.ui.components.MutedText
import io.github.revocx35.tiktoklogger.ui.components.Spinner
import io.github.revocx35.tiktoklogger.ui.components.WaButton
import io.github.revocx35.tiktoklogger.ui.components.WaDialog
import io.github.revocx35.tiktoklogger.ui.components.WaTextField
import io.github.revocx35.tiktoklogger.ui.theme.Wa
import io.github.revocx35.tiktoklogger.ui.theme.WaIcons
import io.github.revocx35.tiktoklogger.ui.theme.WaType
import kotlinx.coroutines.launch

/** A reply thread opened in the comments: its loaded replies and where the next page starts (null = all there). */
data class ReplyThread(val replies: List<FeedComment> = emptyList(), val cursor: String? = null, val loading: Boolean = false)

/** What the comment box answers: [top] = the thread's comment, [toReply] = the reply answered inside it. */
data class ReplyTarget(val top: String, val toReply: String?, val who: String)

/**
 * Everything one can do with the video on screen (server v1.15), shared by For You and a profile's videos: like,
 * repost, follow its author, comments with threads and comment likes, send to a chat. Every action goes to the server,
 * which does it as the logged-in account; the item shows what TikTok answered, not what was asked.
 */
open class VideoActionsModel(protected val session: ServerSession) : ViewModel() {
    var item by mutableStateOf<FeedItem?>(null)
        protected set
    /** Bumped when the video changes so the player reloads it. */
    var generation by mutableStateOf(0)
        protected set
    var error by mutableStateOf<String?>(null)
        protected set
    var notice by mutableStateOf<String?>(null)
        protected set
    var comments by mutableStateOf<List<FeedComment>?>(null)
        private set
    var commentsBusy by mutableStateOf(false)
        private set
    var posting by mutableStateOf(false)
        private set
    var postError by mutableStateOf<String?>(null)
        private set
    var threads by mutableStateOf<Map<String, ReplyThread>>(emptyMap())
        private set
    var replyTo by mutableStateOf<ReplyTarget?>(null)
        private set
    var chats by mutableStateOf<List<ChatSummary>?>(null)
        private set
    var sendState by mutableStateOf<String?>(null)
        private set
    var reposting by mutableStateOf(false)
        private set
    var following by mutableStateOf(false)
        private set

    /** Replaces the item on screen; a list-backed model (a profile) mirrors it into its list. */
    protected open fun update(next: FeedItem) {
        item = next
    }

    private fun patch(id: String, fn: (FeedItem) -> FeedItem) {
        val cur = item ?: return
        if (cur.id == id) update(fn(cur))
    }

    /** A new video: drop what belonged to the previous one. */
    protected fun show(next: FeedItem?) {
        item = next
        comments = null
        threads = emptyMap()
        replyTo = null
        generation++
    }

    fun clearNotice() {
        notice = null
    }

    fun toggleLike() {
        val cur = item ?: return
        val want = !cur.liked
        // Optimistic, then the server's answer wins (it reads TikTok's own control).
        patch(cur.id) { it.copy(liked = want, stats = it.stats.copy(likes = it.stats.likes?.let { n -> (n + if (want) 1 else -1).coerceAtLeast(0) })) }
        viewModelScope.launch {
            try {
                val res = session.api.feedLike(cur.id, want)
                patch(cur.id) { it.copy(liked = res.liked) }
            } catch (e: Exception) {
                patch(cur.id) { it.copy(liked = cur.liked, stats = cur.stats) }
                error = session.fail("feed", e)
            }
        }
    }

    fun toggleRepost() {
        val cur = item ?: return
        if (reposting) return
        val want = cur.reposted != true
        reposting = true
        notice = if (want) "Reposting…" else "Removing the repost…"
        viewModelScope.launch {
            try {
                val res = session.api.feedRepost(cur.id, want)
                patch(cur.id) { it.copy(reposted = res.reposted) }
                notice = if (res.reposted) "Reposted to your followers." else "Repost removed."
            } catch (e: Exception) {
                notice = null
                error = session.fail("feed", e)
            } finally {
                reposting = false
            }
        }
    }

    fun toggleFollow() {
        val cur = item ?: return
        val who = cur.author.username ?: return
        if (following) return
        val want = cur.author.following != true
        following = true
        patch(cur.id) { it.copy(author = it.author.copy(following = want)) }
        viewModelScope.launch {
            try {
                val res = session.api.follow(who, want)
                onFollowChanged(who, res.following)
                patch(cur.id) { it.copy(author = it.author.copy(following = res.following)) }
                notice = if (res.following) "Following @$who." else "Unfollowed @$who."
            } catch (e: Exception) {
                patch(cur.id) { it.copy(author = it.author.copy(following = cur.author.following)) }
                error = session.fail("feed", e)
            } finally {
                following = false
            }
        }
    }

    /** Other videos of the same author in a list follow the new state. */
    protected open fun onFollowChanged(username: String, following: Boolean) {}

    fun loadComments() {
        val cur = item ?: return
        commentsBusy = true
        postError = null
        threads = emptyMap()
        replyTo = null
        viewModelScope.launch {
            try {
                comments = session.api.feedComments(cur.id).comments
            } catch (e: Exception) {
                postError = session.fail("feed", e)
                if (comments == null) comments = emptyList()
            } finally {
                commentsBusy = false
            }
        }
    }

    fun loadReplies(c: FeedComment) {
        val cur = item ?: return
        val th = threads[c.id]
        if (th?.loading == true) return
        threads = threads + (c.id to (th ?: ReplyThread()).copy(loading = true))
        viewModelScope.launch {
            try {
                val page = session.api.feedReplies(cur.id, c.id, th?.cursor)
                val old = th?.replies ?: emptyList()
                val merged = old + page.comments.filter { n -> old.none { it.id == n.id } }
                threads = threads + (c.id to ReplyThread(merged, if (page.more) page.cursor else null, false))
            } catch (e: Exception) {
                threads = threads + (c.id to (th ?: ReplyThread()).copy(loading = false))
                postError = session.fail("feed", e)
            }
        }
    }

    private fun setCommentLiked(id: String, liked: Boolean) {
        fun upd(c: FeedComment): FeedComment =
            if (c.id == id) c.copy(liked = liked, likes = c.likes?.let { (it + if (liked) 1 else -1).coerceAtLeast(0) }) else c.copy(replies = c.replies.map(::upd))
        comments = comments?.map(::upd)
        threads = threads.mapValues { (_, t) -> t.copy(replies = t.replies.map(::upd)) }
    }

    fun toggleCommentLike(c: FeedComment) {
        val cur = item ?: return
        val want = !c.liked
        setCommentLiked(c.id, want)
        viewModelScope.launch {
            try {
                session.api.feedCommentLike(cur.id, c.id, want)
            } catch (e: Exception) {
                setCommentLiked(c.id, !want)
                postError = session.fail("feed", e)
            }
        }
    }

    fun startReply(top: FeedComment, to: FeedComment?) {
        val target = to ?: top
        replyTo = ReplyTarget(top.id, to?.id, target.author.username ?: target.author.name ?: "someone")
    }

    fun cancelReply() {
        replyTo = null
    }

    fun post(text: String) {
        val cur = item ?: return
        if (text.isBlank()) return
        val target = replyTo
        posting = true
        postError = null
        viewModelScope.launch {
            try {
                if (target != null) {
                    val page = session.api.feedReply(cur.id, target.top, text.trim(), target.toReply)
                    threads = threads + (target.top to ReplyThread(page.comments, if (page.more) page.cursor else null, false))
                    comments = comments?.map { if (it.id == target.top) it.copy(replyCount = maxOf(it.replyCount ?: 0, page.comments.size.toLong())) else it }
                    replyTo = null
                } else {
                    comments = session.api.feedComment(cur.id, text.trim()).comments
                    patch(cur.id) { it.copy(stats = it.stats.copy(comments = (it.stats.comments ?: 0) + 1)) }
                }
            } catch (e: Exception) {
                postError = session.fail("feed", e)
            } finally {
                posting = false
            }
        }
    }

    fun closeComments() {
        comments = null
        postError = null
        threads = emptyMap()
        replyTo = null
        viewModelScope.launch { runCatching { session.api.feedCloseComments() } }
    }

    fun loadChats() {
        if (chats != null) return
        viewModelScope.launch {
            chats = runCatching { session.api.chats() }.getOrDefault(emptyList())
        }
    }

    /** Sends the video's canonical link into one of the logged chats (through the normal send path). */
    fun send(chat: ChatSummary) {
        val cur = item ?: return
        sendState = null
        viewModelScope.launch {
            try {
                session.api.sendText(chat.id, cur.url, null)
                sendState = "Sent to ${chat.name ?: chat.username ?: "the chat"}"
            } catch (e: Exception) {
                sendState = session.fail("feed", e)
            }
        }
    }

    fun clearSendState() {
        sendState = null
    }
}

/**
 * TikTok's For You, read through the server's browser (the same tab the web UI's Feed uses).
 *
 * The server drives the feed: this screen shows whatever video the server says is on screen and asks it to move.
 * Videos stream through the server, so the phone never talks to TikTok.
 */
class FeedViewModel(session: ServerSession) : VideoActionsModel(session) {
    var loading by mutableStateOf(false)
        private set
    var stepping by mutableStateOf(false)
        private set

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val res = session.api.feed()
                show(res.item)
                if (res.item == null) error = "TikTok did not show a video. Try again in a moment."
            } catch (e: Exception) {
                error = session.fail("feed", e)
            } finally {
                loading = false
            }
        }
    }

    fun step(dir: String) {
        if (stepping) return
        stepping = true
        error = null
        viewModelScope.launch {
            try {
                val res = session.api.feedStep(dir)
                if (res.item != null) show(res.item)
            } catch (e: Exception) {
                error = session.fail("feed", e)
            } finally {
                stepping = false
            }
        }
    }
}

private fun compact(n: Long?): String = when {
    n == null -> "—"
    n >= 1_000_000_000 -> String.format("%.1fB", n / 1_000_000_000.0)
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
    else -> n.toString()
}

@Composable
fun FeedScreen(vm: FeedViewModel, session: ServerSession, connected: Boolean, prefs: Prefs, onOpenProfile: (String) -> Unit = {}) {
    if (!connected) {
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MutedText("TikTok is not connected", style = WaType.chatName, textAlign = TextAlign.Center)
                MutedText(
                    "The feed plays through the server's TikTok login. Log in on the TikTok screen first.",
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    LaunchedEffect(Unit) { if (vm.item == null && !vm.loading) vm.refresh() }

    VideoPane(
        vm = vm,
        session = session,
        prefs = prefs,
        stepping = vm.stepping,
        onStep = vm::step,
        onOpenProfile = onOpenProfile,
        onRefresh = vm::refresh,
        empty = {
            if (vm.loading || vm.stepping) Spinner(32.dp)
            ErrorNote(vm.error)
            if (!vm.loading) WaButton("Load the feed", { vm.refresh() })
        },
    )
}

private fun clock(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** TikTok's speed steps. */
private val SPEEDS = listOf(1f, 1.5f, 2f, 0.5f, 0.75f)

/**
 * One video full screen with TikTok's controls (server v1.15): the action column (author → profile, follow badge,
 * like, comments, repost, send, save to the gallery, fit/fill, sound), the player row (play/pause, seek bar, speed,
 * TikTok's captions) and swipes/arrows for the next one. For You and a profile's videos both use it.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPane(
    vm: VideoActionsModel,
    session: ServerSession,
    prefs: Prefs,
    stepping: Boolean,
    onStep: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onRefresh: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    empty: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember(session) { buildPlayer(context, session.api.http).apply { repeatMode = Player.REPEAT_MODE_ONE } }
    var muted by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }
    var captions by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var seekTo by remember { mutableStateOf<Float?>(null) }
    // Whole frame ("fit") or cropped to the screen ("fill"). Remembered across app restarts in Prefs.
    var fit by remember(prefs) { mutableStateOf(prefs.feedFitVideo) }

    DisposableEffect(player) { onDispose { player.release() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(speed) { player.setPlaybackSpeed(speed) }
    LaunchedEffect(captions) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captions).build()
    }

    val item = vm.item
    LaunchedEffect(item?.id, item?.video?.src, vm.generation) {
        val src = item?.video?.src?.let { session.mediaUrl(it) }
        if (src != null && item?.video?.src != null) {
            // TikTok's own captions ride along as side-loaded WebVTT; the text track is switched off until asked for.
            val subs = (item.video?.captions ?: emptyList()).mapNotNull { t ->
                session.mediaUrl(t.src)?.let { u ->
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(u)).setMimeType(MimeTypes.TEXT_VTT).setLanguage(t.lang.take(2).lowercase()).setLabel(t.lang).build()
                }
            }
            player.setMediaItem(MediaItem.Builder().setUri(src).setSubtitleConfigurations(subs).build())
            player.prepare()
            player.playWhenReady = true
        } else {
            player.stop()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            if (seekTo == null) position = player.currentPosition
            duration = player.duration.takeIf { it > 0 } ?: 0L
            playing = player.playWhenReady
            delay(250)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (item == null) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { empty() }
        } else {
            val poster = item.video?.poster?.let { session.mediaUrl(it) }
            if (item.video?.src != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = feedResizeMode(fit)
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = {
                        it.player = player
                        // The resize button only swaps the scale, so the same surface keeps playing.
                        if (it.resizeMode != feedResizeMode(fit)) it.resizeMode = feedResizeMode(fit)
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (poster != null) {
                AsyncImage(poster, null, imageLoader = session.imageLoader, contentScale = feedContentScale(fit), modifier = Modifier.fillMaxSize())
            }

            // Tap: play/pause. Swipe up/down: next/previous video.
            Box(
                Modifier.fillMaxSize().pointerInput(item.id) {
                    detectTapGestures(onTap = { player.playWhenReady = !player.playWhenReady })
                },
            )
            var dragAccum by remember(item.id) { mutableStateOf(0f) }
            Box(
                Modifier.fillMaxSize().pointerInput(item.id) {
                    detectVerticalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onDragEnd = {
                            when {
                                dragAccum < -140f -> onStep("next")
                                dragAccum > 140f -> onStep("prev")
                            }
                            dragAccum = 0f
                        },
                        onVerticalDrag = { _, delta -> dragAccum += delta },
                    )
                },
            )
            if (!playing && item.video?.src != null) {
                Box(Modifier.align(Alignment.Center).size(84.dp).clip(CircleShape).background(Color(0x55000000)).clickable { player.playWhenReady = true }, contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(WaIcons.play, "Play", tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }

            // Author, caption, music and the player row over a gradient at the bottom.
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                    .padding(start = 14.dp, end = 76.dp, top = 56.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "@" + (item.author.username ?: item.author.name ?: "tiktok"),
                    color = Color.White,
                    style = WaType.chatName,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = item.author.username != null) { item.author.username?.let(onOpenProfile) },
                )
                item.caption?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color.White, style = WaType.base, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                item.music?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color(0xFFDDDDDD), style = WaType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "${compact(item.stats.plays)} plays · ${compact(item.stats.shares)} shares",
                    color = Color(0xFFBBBBBB),
                    style = WaType.small,
                )
                if (item.video?.src != null) {
                    FeedPlayerRow(
                        playing = playing,
                        position = seekTo?.toLong() ?: position,
                        duration = duration,
                        speed = speed,
                        hasCaptions = (item.video?.captions?.isNotEmpty() == true),
                        captions = captions,
                        onPlayPause = { player.playWhenReady = !player.playWhenReady },
                        onSeek = { seekTo = it },
                        onSeekDone = {
                            seekTo?.let { player.seekTo(it.toLong()) }
                            seekTo = null
                        },
                        onSpeed = { speed = SPEEDS[(SPEEDS.indexOf(speed) + 1) % SPEEDS.size] },
                        onCaptions = { captions = !captions },
                    )
                }
            }

            FeedActionRail(
                likes = item.stats.likes,
                commentsCount = item.stats.comments,
                liked = item.liked,
                muted = muted,
                fit = fit,
                onLike = { vm.toggleLike() },
                onComments = { vm.loadComments() },
                onSend = {
                    vm.clearSendState()
                    vm.loadChats()
                    sending = true
                },
                onToggleFit = {
                    fit = !fit
                    prefs.feedFitVideo = fit
                },
                onToggleMute = { muted = !muted },
                onRefresh = null,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 18.dp),
                author = item.author,
                reposted = item.reposted == true,
                canRepost = item.canRepost,
                reposting = vm.reposting,
                onRepost = { vm.toggleRepost() },
                onSave = if (item.video?.download != null) ({ scope.launch { MediaOps.saveFeedVideoToGallery(context, session.api, item) } }) else null,
                onAuthor = item.author.username?.let { u -> { onOpenProfile(u) } },
                onFollow = if (item.author.username != null && item.author.following != null) ({ vm.toggleFollow() }) else null,
            )

            // Top bar: close (a profile's videos) or refresh (For You) on the left, up/down on the right.
            Row(Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 10.dp)) {
                if (onClose != null) {
                    FeedTopButton(WaIcons.close, "Close", onClose)
                } else if (onRefresh != null) {
                    FeedTopButton(WaIcons.refresh, "Refresh the feed", onRefresh)
                }
            }
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FeedArrow("↑", enabled = !stepping) { onStep("prev") }
                FeedArrow("↓", enabled = !stepping) { onStep("next") }
            }

            if (stepping) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(34.dp) }
            }
            Column(Modifier.align(Alignment.TopCenter).padding(top = 60.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorNote(vm.error)
                vm.notice?.let { n ->
                    LaunchedEffect(n) {
                        delay(2600)
                        vm.clearNotice()
                    }
                    Text(n, color = Color.White, style = WaType.small, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xAA000000)).padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }

    val comments = vm.comments
    if (comments != null) {
        CommentsDialog(vm, comments, onOpenProfile) { vm.closeComments() }
    }
    if (sending) {
        SendDialog(vm.chats, vm.sendState, vm::send) { if (vm.sendState == null) sending = false else vm.clearSendState() }
    }
}

@Composable
private fun FeedTopButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0x30FFFFFF)), contentAlignment = Alignment.Center) {
        IconBtn(icon, description, onClick, size = 38.dp, iconSize = 20.dp, tint = Color.White)
    }
}

/** Play/pause, the seek bar with the time, the speed step and TikTok's captions (when it made some). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun FeedPlayerRow(
    playing: Boolean,
    position: Long,
    duration: Long,
    speed: Float,
    hasCaptions: Boolean,
    captions: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekDone: () -> Unit,
    onSpeed: () -> Unit,
    onCaptions: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconBtn(if (playing) WaIcons.pause else WaIcons.play, if (playing) "Pause" else "Play", onPlayPause, size = 30.dp, iconSize = 16.dp, tint = Color.White)
        Slider(
            value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(0f)),
            onValueChange = onSeek,
            onValueChangeFinished = onSeekDone,
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            enabled = duration > 0,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x55FFFFFF)),
            // TikTok's seek bar: a thin white line and a small round thumb (not Material's pill and gap).
            thumb = { Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White)) },
            track = { st ->
                val frac = if (st.valueRange.endInclusive > st.valueRange.start) (st.value - st.valueRange.start) / (st.valueRange.endInclusive - st.valueRange.start) else 0f
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF))) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(3.dp).background(Color.White))
                }
            },
            modifier = Modifier.weight(1f).heightIn(max = 28.dp),
        )
        Text("${clock(position)} / ${clock(duration)}", color = Color(0xDDFFFFFF), style = WaType.tiny)
        Box(
            Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x30FFFFFF)).clickable(onClick = onSpeed).padding(horizontal = 7.dp, vertical = 4.dp),
        ) {
            Text("${if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()}×", color = Color.White, style = WaType.tiny, fontWeight = FontWeight.Bold)
        }
        if (hasCaptions) {
            IconBtn(WaIcons.captions, if (captions) "Hide captions" else "Show captions", onCaptions, size = 30.dp, iconSize = 18.dp, tint = if (captions) Color.White else Color(0x99FFFFFF), active = captions)
        }
    }
}

/**
 * The action column next to the video. The resize entry offers the mode you are **not** in: "Fit" while the video
 * fills the screen (a wide video loses its sides), "Fill" while the whole frame is shown with black bars.
 */
@Composable
internal fun FeedActionRail(
    likes: Long?,
    commentsCount: Long?,
    liked: Boolean,
    muted: Boolean,
    fit: Boolean,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onSend: () -> Unit,
    onToggleFit: () -> Unit,
    onToggleMute: () -> Unit,
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** TikTok puts the author's avatar on top of the column. */
    author: io.github.revocx35.tiktoklogger.core.FeedAuthor? = null,
    reposted: Boolean = false,
    /** The repost entry shows only when the video allows it (and the caller handles it). */
    canRepost: Boolean = false,
    reposting: Boolean = false,
    onRepost: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onAuthor: (() -> Unit)? = null,
    /** TikTok's follow badge under the avatar: "+" to follow, a check once followed. */
    onFollow: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (author != null) {
            Box(Modifier.padding(bottom = 10.dp), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier.size(48.dp).border(1.dp, Color.White, CircleShape).padding(1.dp).clip(CircleShape).clickable(enabled = onAuthor != null) { onAuthor?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    io.github.revocx35.tiktoklogger.ui.components.Avatar(author.username ?: "tiktok", author.name ?: author.username ?: "?", author.avatar, size = 46.dp)
                }
                if (onFollow != null) {
                    val on = author.following == true
                    Box(
                        Modifier
                            .padding(top = 38.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (on) Color.White else Color(0xFFFE2C55))
                            .clickable(onClick = onFollow),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            if (on) WaIcons.check else WaIcons.plus,
                            if (on) "Unfollow @${author.username}" else "Follow @${author.username}",
                            tint = if (on) Color(0xFFFE2C55) else Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        // TikTok's solid icons with bold counts underneath.
        FeedAction(WaIcons.heartFill, compact(likes), liked, if (liked) Color(0xFFFE2C55) else Color.White, onLike, "Like")
        FeedAction(WaIcons.commentFill, compact(commentsCount), false, Color.White, onComments, "Comments")
        if (canRepost && onRepost != null) {
            FeedAction(WaIcons.repost, if (reposted) "Reposted" else "Repost", reposted, if (reposted) Color(0xFFFFC300) else Color.White, { if (!reposting) onRepost() }, if (reposted) "Remove repost" else "Repost")
        }
        FeedAction(WaIcons.shareArrow, "Share", false, Color.White, onSend, "Send this video to a chat")
        if (onSave != null) FeedAction(WaIcons.download, "Save", false, Color.White, onSave, "Save the video to the gallery")
        FeedAction(
            icon = if (fit) WaIcons.fillScreen else WaIcons.fitScreen,
            label = if (fit) "Fill" else "Fit",
            active = false,
            tint = Color.White,
            onClick = onToggleFit,
            description = if (fit) "Fill the screen" else "Fit the whole video on the screen",
        )
        FeedAction(WaIcons.mute, if (muted) "Sound" else "Mute", muted, Color.White, onToggleMute, if (muted) "Turn the sound on" else "Mute")
        if (onRefresh != null) FeedAction(WaIcons.refresh, "", false, Color.White, onRefresh, "Refresh the feed")
    }
}

/** The player's frame mode for the chosen scaling: `fit` shows the whole frame, otherwise the video fills the screen. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun feedResizeMode(fit: Boolean): Int =
    if (fit) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM

/** The poster's scale, so the still frame matches the video that replaces it. */
internal fun feedContentScale(fit: Boolean): ContentScale = if (fit) ContentScale.Fit else ContentScale.Crop

@Composable
private fun FeedAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    tint: Color,
    onClick: () -> Unit,
    description: String = label,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        IconBtn(icon, description, onClick, size = 42.dp, iconSize = 28.dp, tint = tint, active = active)
        if (label.isNotEmpty()) {
            Text(
                label,
                color = Color.White,
                style = WaType.tiny.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, shadow = androidx.compose.ui.graphics.Shadow(Color(0x80000000), blurRadius = 4f)),
            )
        }
    }
}

@Composable
private fun FeedArrow(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (enabled) Color(0x30FFFFFF) else Color(0x18FFFFFF)),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) {
            IconBtn(if (symbol == "↑") WaIcons.arrowUp else WaIcons.arrowDown, symbol, onClick, size = 38.dp, iconSize = 18.dp, tint = Color.White)
        } else {
            Text(symbol, color = Color(0x88FFFFFF))
        }
    }
}

/** Comments with TikTok's threads: like a comment, "View N replies", reply to a comment or to a reply inside it. */
@Composable
private fun CommentsDialog(vm: VideoActionsModel, comments: List<FeedComment>, onOpenProfile: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val target = vm.replyTo
    WaDialog("Comments", onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.commentsBusy) Spinner()
            if (!vm.commentsBusy && comments.isEmpty()) MutedText("No comments yet.")
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                comments.forEach { c ->
                    CommentRow(c, 0, { vm.toggleCommentLike(c) }, { vm.startReply(c, null) }, onOpenProfile)
                    val th = vm.threads[c.id]
                    val replies = th?.replies ?: c.replies
                    replies.forEach { r -> CommentRow(r, 1, { vm.toggleCommentLike(r) }, { vm.startReply(c, r) }, onOpenProfile) }
                    val total = c.replyCount ?: c.replies.size.toLong()
                    val left = if (th != null) (if (th.cursor != null) maxOf(1, total - th.replies.size) else 0) else maxOf(0, total - c.replies.size)
                    if (left > 0 || th?.loading == true) {
                        Text(
                            if (th?.loading == true) "Loading…" else if (th != null) "View more replies" else "View ${compact(left)} ${if (left == 1L) "reply" else "replies"}",
                            color = Wa.colors.muted,
                            style = WaType.small,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 36.dp).clickable(enabled = th?.loading != true) { vm.loadReplies(c) },
                        )
                    }
                }
            }
            ErrorNote(vm.postError)
            if (target != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Wa.colors.panel).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MutedText("Replying to @${target.who}", Modifier.weight(1f), style = WaType.small)
                    IconBtn(WaIcons.close, "Cancel reply", { vm.cancelReply() }, size = 28.dp, iconSize = 14.dp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    WaTextField(text, { text = it.take(150) }, null, placeholder = if (target != null) "Reply to @${target.who}…" else "Add a comment…", singleLine = true)
                }
                WaButton("Post", {
                    val t = text
                    text = ""
                    vm.post(t)
                }, enabled = text.isNotBlank() && !vm.posting, busy = vm.posting, style = io.github.revocx35.tiktoklogger.ui.components.BtnStyle.Primary)
            }
        }
    }
}

@Composable
private fun CommentRow(c: FeedComment, depth: Int, onLike: () -> Unit, onReply: () -> Unit, onOpenProfile: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = (depth * 22).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "@" + (c.author.username ?: c.author.name ?: "tiktok"),
                color = Wa.colors.text2,
                style = WaType.small,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(enabled = c.author.username != null) { c.author.username?.let(onOpenProfile) },
            )
            Text(c.text, style = WaType.base)
            Text("Reply", color = Wa.colors.muted, style = WaType.tiny, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onReply).padding(vertical = 2.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconBtn(if (c.liked) WaIcons.heartFill else WaIcons.heart, if (c.liked) "Unlike" else "Like", onLike, size = 30.dp, iconSize = 16.dp, tint = if (c.liked) Color(0xFFFE2C55) else Wa.colors.muted)
            if ((c.likes ?: 0) > 0) Text(compact(c.likes), color = Wa.colors.text2, style = WaType.tiny)
        }
    }
}

@Composable
private fun SendDialog(chats: List<ChatSummary>?, state: String?, onSend: (ChatSummary) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    WaDialog("Send this video", onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state != null) {
                MutedText(state)
            } else {
                WaTextField(q, { q = it }, null, placeholder = "Search chats")
                if (chats == null) {
                    Spinner()
                } else {
                    Column(
                        Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val list = chats.filter {
                            q.isBlank() || (it.name ?: "").contains(q.trim(), true) || (it.username ?: "").contains(q.trim(), true)
                        }
                        if (list.isEmpty()) MutedText("No chats match.")
                        list.forEach { chat ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .pointerInput(chat.id) { detectTapGestures(onTap = { onSend(chat) }) }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(Wa.colors.panel), contentAlignment = Alignment.Center) {
                                    Text((chat.name ?: chat.username ?: "?").take(1).uppercase(), style = WaType.small)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(chat.name ?: chat.username ?: chat.id, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    chat.username?.let { MutedText("@$it", style = WaType.small) }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.widthIn(min = 0.dp))
        }
    }
}
