package io.github.revocx35.tiktoklogger.ui.screens

import androidx.compose.foundation.background
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

/**
 * TikTok's For You, read through the server's browser (the same tab the web UI's Feed uses).
 *
 * The server drives the feed: this screen shows whatever video the server says is on screen and asks it to move,
 * like, comment or close the comments. Videos stream through the server, so the phone never talks to TikTok.
 */
class FeedViewModel(private val session: ServerSession) : ViewModel() {
    var item by mutableStateOf<FeedItem?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var stepping by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var comments by mutableStateOf<List<FeedComment>?>(null)
        private set
    var commentsBusy by mutableStateOf(false)
        private set
    var posting by mutableStateOf(false)
        private set
    var postError by mutableStateOf<String?>(null)
        private set
    var chats by mutableStateOf<List<ChatSummary>?>(null)
        private set
    var sendState by mutableStateOf<String?>(null)
        private set

    /** Bumped when the video changes so the player reloads it. */
    var generation by mutableStateOf(0)
        private set

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val res = session.api.feed()
                item = res.item
                generation++
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
                if (res.item != null) {
                    item = res.item
                    comments = null
                    generation++
                }
            } catch (e: Exception) {
                error = session.fail("feed", e)
            } finally {
                stepping = false
            }
        }
    }

    fun toggleLike() {
        val cur = item ?: return
        val want = !cur.liked
        // Optimistic, then the server's answer wins (it polls TikTok's own control).
        item = cur.copy(liked = want)
        viewModelScope.launch {
            try {
                val res = session.api.feedLike(cur.id, want)
                item = item?.copy(liked = res.liked)
            } catch (e: Exception) {
                item = cur
                error = session.fail("feed", e)
            }
        }
    }

    fun loadComments() {
        val cur = item ?: return
        commentsBusy = true
        postError = null
        viewModelScope.launch {
            try {
                comments = session.api.feedComments(cur.id).comments
            } catch (e: Exception) {
                postError = session.fail("feed", e)
            } finally {
                commentsBusy = false
            }
        }
    }

    fun post(text: String) {
        val cur = item ?: return
        if (text.isBlank()) return
        posting = true
        postError = null
        viewModelScope.launch {
            try {
                comments = session.api.feedComment(cur.id, text.trim()).comments
                item = item?.copy(stats = (item?.stats ?: io.github.revocx35.tiktoklogger.core.FeedStats()).copy(
                    comments = (itemsCount(comments) ).toLong(),
                ))
            } catch (e: Exception) {
                postError = session.fail("feed", e)
            } finally {
                posting = false
            }
        }
    }

    private fun itemsCount(list: List<FeedComment>?): Int = list?.size ?: 0

    fun closeComments() {
        comments = null
        postError = null
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

private fun compact(n: Long?): String = when {
    n == null -> "—"
    n >= 1_000_000_000 -> String.format("%.1fB", n / 1_000_000_000.0)
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
    else -> n.toString()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FeedScreen(vm: FeedViewModel, session: ServerSession, connected: Boolean, prefs: Prefs) {
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

    val context = LocalContext.current
    val player = remember(session) { buildPlayer(context, session.api.http).apply { repeatMode = Player.REPEAT_MODE_ONE } }
    var muted by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
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

    val item = vm.item
    LaunchedEffect(item?.id, item?.video?.src) {
        val src = item?.video?.src?.let { session.mediaUrl(it) }
        if (src != null && item?.video?.src != null) {
            player.setMediaItem(MediaItem.fromUri(src))
            player.prepare()
            player.playWhenReady = true
        } else {
            player.stop()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (item == null) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (vm.loading || vm.stepping) Spinner(32.dp)
                ErrorNote(vm.error)
                if (!vm.loading) WaButton("Load the feed", { vm.refresh() })
            }
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

            // Tap: play/pause. Swipe up/down: next/previous video (TikTok's own order, driven by the server).
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
                                dragAccum < -140f -> vm.step("next")
                                dragAccum > 140f -> vm.step("prev")
                            }
                            dragAccum = 0f
                        },
                        onVerticalDrag = { _, delta -> dragAccum += delta },
                    )
                },
            )

            // Author, caption and music over a gradient at the bottom.
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                    .padding(start = 14.dp, end = 84.dp, top = 56.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "@" + (item.author.username ?: item.author.name ?: "tiktok"),
                    color = Color.White,
                    style = WaType.chatName,
                    fontWeight = FontWeight.Bold,
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
            }

            // Right rail: like, comments, send, resize (fit/fill), mute, refresh.
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
                onRefresh = { vm.refresh() },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
            )

            // Up/down arrows: same as swiping, easier with one hand.
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FeedArrow("↑", enabled = !vm.stepping) { vm.step("prev") }
                FeedArrow("↓", enabled = !vm.stepping) { vm.step("next") }
            }

            if (vm.stepping) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(34.dp) }
            }
            ErrorNote(vm.error, Modifier.align(Alignment.TopCenter).padding(top = 70.dp, start = 16.dp, end = 16.dp))
        }
    }

    val comments = vm.comments
    if (comments != null) {
        CommentsDialog(comments, vm.posting, vm.postError, vm::post) { vm.closeComments() }
    }
    if (sending) {
        SendDialog(vm.chats, vm.sendState, vm::send) { if (vm.sendState == null) sending = false else vm.clearSendState() }
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
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FeedAction(WaIcons.heart, compact(likes), liked, if (liked) Color(0xFFFE2C55) else Color.White, onLike, "Like")
        FeedAction(WaIcons.comment, compact(commentsCount), false, Color.White, onComments, "Comments")
        FeedAction(WaIcons.forward, "Send", false, Color.White, onSend, "Send this video to a chat")
        FeedAction(
            icon = if (fit) WaIcons.fillScreen else WaIcons.fitScreen,
            label = if (fit) "Fill" else "Fit",
            active = false,
            tint = Color.White,
            onClick = onToggleFit,
            description = if (fit) "Fill the screen" else "Fit the whole video on the screen",
        )
        FeedAction(WaIcons.mute, if (muted) "Sound" else "Mute", muted, Color.White, onToggleMute, if (muted) "Turn the sound on" else "Mute")
        FeedAction(WaIcons.refresh, "", false, Color.White, onRefresh, "Refresh the feed")
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color(0x66000000)),
            contentAlignment = Alignment.Center,
        ) {
            IconBtn(icon, description, onClick, size = 44.dp, iconSize = 24.dp, tint = tint, active = active)
        }
        if (label.isNotEmpty()) Text(label, color = Color.White, style = WaType.small)
    }
}

@Composable
private fun FeedArrow(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (enabled) Color(0x66000000) else Color(0x33000000)),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) {
            IconBtn(WaIcons.back, symbol, onClick, size = 38.dp, iconSize = 18.dp, tint = Color.White)
        } else {
            Text(symbol, color = Color(0x88FFFFFF))
        }
    }
}

@Composable
private fun CommentsDialog(
    comments: List<FeedComment>,
    posting: Boolean,
    error: String?,
    onPost: (String) -> Unit,
    onClose: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    WaDialog("Comments", onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (comments.isEmpty()) MutedText("No comments yet.")
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                comments.forEach { c ->
                    CommentRow(c, 0)
                    c.replies.forEach { r -> CommentRow(r, 1) }
                }
            }
            ErrorNote(error)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    WaTextField(text, { text = it }, null, placeholder = "Add a comment…", singleLine = true)
                }
                WaButton("Post", {
                    val t = text
                    text = ""
                    scope.launch { onPost(t) }
                }, enabled = text.isNotBlank() && !posting, busy = posting, style = io.github.revocx35.tiktoklogger.ui.components.BtnStyle.Primary)
            }
        }
    }
}

@Composable
private fun CommentRow(c: FeedComment, depth: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = (depth * 18).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "@" + (c.author.username ?: c.author.name ?: "tiktok"),
                color = Wa.colors.text2,
                style = WaType.small,
                fontWeight = FontWeight.SemiBold,
            )
            Text(c.text, style = WaType.base)
        }
        if ((c.likes ?: 0) > 0) {
            Text(compact(c.likes), color = Wa.colors.text2, style = WaType.small)
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
