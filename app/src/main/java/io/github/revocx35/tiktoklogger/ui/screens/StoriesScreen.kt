package io.github.revocx35.tiktoklogger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import io.github.revocx35.tiktoklogger.core.StoryAuthor
import io.github.revocx35.tiktoklogger.core.StoryItem
import io.github.revocx35.tiktoklogger.core.StoryStatus
import io.github.revocx35.tiktoklogger.data.ServerSession
import io.github.revocx35.tiktoklogger.media.MediaOps
import io.github.revocx35.tiktoklogger.media.buildPlayer
import io.github.revocx35.tiktoklogger.ui.components.Avatar
import io.github.revocx35.tiktoklogger.ui.components.BtnStyle
import io.github.revocx35.tiktoklogger.ui.components.EmptyState
import io.github.revocx35.tiktoklogger.ui.components.ErrorNote
import io.github.revocx35.tiktoklogger.ui.components.IconBtn
import io.github.revocx35.tiktoklogger.ui.components.MutedText
import io.github.revocx35.tiktoklogger.ui.components.PageHeader
import io.github.revocx35.tiktoklogger.ui.components.Spinner
import io.github.revocx35.tiktoklogger.ui.components.WaButton
import io.github.revocx35.tiktoklogger.ui.components.WaIcon
import io.github.revocx35.tiktoklogger.ui.theme.Wa
import io.github.revocx35.tiktoklogger.ui.theme.WaIcons
import io.github.revocx35.tiktoklogger.ui.theme.WaType
import kotlinx.coroutines.launch

/**
 * The logged stories, like the web UI's Stories page: the accounts whose stories were captured, a grid of the stories
 * themselves, and a full-screen viewer. The capture runs on the server (`tt/stories.ts`) — this screen reads what was
 * stored, offers "Check now", and saves a story to the phone's gallery.
 */
class StoriesViewModel(private val session: ServerSession) : ViewModel() {
    var status by mutableStateOf<StoryStatus?>(null)
        private set
    var authors by mutableStateOf<List<StoryAuthor>>(emptyList())
        private set
    var items by mutableStateOf<List<StoryItem>>(emptyList())
        private set
    var nextBefore by mutableStateOf<Long?>(null)
        private set
    var author by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var loadingOlder by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun load(who: String? = author, before: Long? = null) {
        val first = before == null
        if (first) loading = true else loadingOlder = true
        if (first) error = null
        viewModelScope.launch {
            try {
                if (first) {
                    status = session.api.storiesStatus()
                    authors = session.api.storiesAuthors()
                }
                val page = session.api.stories(user = who, before = before)
                items = if (before == null) page.items else items + page.items
                nextBefore = page.nextBefore
            } catch (e: Exception) {
                error = session.fail("stories", e)
            } finally {
                loading = false
                loadingOlder = false
            }
        }
    }

    fun chooseAuthor(id: String?) {
        if (author == id) return
        author = id
        items = emptyList()
        nextBefore = null
        load(who = id)
    }

    /** Runs a capture pass now (the server does the work; nothing is marked as seen). */
    fun checkNow() {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                status = session.api.storiesCheck()
                authors = session.api.storiesAuthors()
                val page = session.api.stories(user = author, before = null)
                items = page.items
                nextBefore = page.nextBefore
            } catch (e: Exception) {
                error = session.fail("stories", e)
            } finally {
                busy = false
            }
        }
    }

    fun chosen(): StoryAuthor? = author?.let { id -> authors.find { it.id == id } }
}

@Composable
fun StoriesScreen(vm: StoriesViewModel, session: ServerSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewer by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { if (vm.items.isEmpty() && !vm.loading) vm.load(before = null) }

    val save: (StoryItem, String?) -> Unit = { story, who ->
        scope.launch { MediaOps.saveStoryToGallery(context.applicationContext, session.api, story, who) }
    }

    if (!vm.loading && vm.status?.logging == false && vm.items.isEmpty()) {
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(28.dp), contentAlignment = Alignment.Center) {
            EmptyState(WaIcons.stories, "Story logging is off") {
                MutedText(
                    "Turn it on in the web UI (Settings → Stories) to keep the stories of the accounts you follow before " +
                        "they disappear. Nothing is marked as seen.",
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PageHeader(WaIcons.stories, "Stories", statusLine(vm.status))
            }
            if (vm.author != null) {
                WaButton("All", { vm.chooseAuthor(null) }, tiny = true, style = BtnStyle.Ghost)
            }
            WaButton("Check now", { vm.checkNow() }, tiny = true, busy = vm.busy, icon = WaIcons.refresh)
        }

        ErrorNote(vm.error)

        if (vm.authors.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(vm.authors, key = { it.id }) { a ->
                    AuthorChip(a, active = vm.author == a.id) { vm.chooseAuthor(if (vm.author == a.id) null else a.id) }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                vm.loading && vm.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(28.dp) }
                vm.items.isEmpty() -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        MutedText(
                            if (vm.authors.isEmpty()) "No stories logged yet. \"Check now\" reads TikTok's stories bar."
                            else "No stories for this account in the log.",
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 132.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(vm.items, key = { _, s -> s.id }) { i, story ->
                        StoryTile(
                            story = story,
                            session = session,
                            onOpen = { viewer = i },
                            onSave = { save(story, story.username) },
                        )
                    }
                    if (vm.nextBefore != null) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.Center) {
                                WaButton("Load older", { vm.load(before = vm.nextBefore) }, busy = vm.loadingOlder, style = BtnStyle.Ghost, tiny = true)
                            }
                        }
                    }
                }
            }
        }
    }

    val at = viewer
    if (at != null && vm.items.getOrNull(at) != null) {
        StoryViewer(
            items = vm.items,
            start = at,
            session = session,
            onSave = save,
            onClose = { viewer = null },
        )
    }
}

private fun statusLine(st: StoryStatus?): String {
    if (st == null) return "Loading…"
    val parts = mutableListOf<String>()
    parts += "${st.items} stor${if (st.items == 1) "y" else "ies"} from ${st.authors} account${if (st.authors == 1) "" else "s"}"
    if (st.pending > 0) parts += "${st.pending} still downloading"
    st.lastCheckAt?.let { parts += "last check ${ago(it)}" }
    st.nextCheckAt?.let { if (it > System.currentTimeMillis()) parts += "next in ${inTime(it - System.currentTimeMillis())}" }
    return parts.joinToString(" · ")
}

@Composable
private fun AuthorChip(a: StoryAuthor, active: Boolean, onClick: () -> Unit) {
    val c = Wa.colors
    Column(
        Modifier
            .width(70.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) c.active else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Avatar(a.id, a.name ?: a.username ?: a.id, a.avatarUrl, size = 54.dp)
            Box(
                Modifier.size(18.dp).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("${a.count}", style = WaType.tiny, color = c.accentText)
            }
        }
        Text(
            "@" + (a.username ?: a.id.takeLast(6)),
            style = WaType.tiny,
            color = if (active) c.accent else c.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One story in the grid: the photo, or a dark tile with a play badge for a video (a story has no stored cover). */
@Composable
private fun StoryTile(story: StoryItem, session: ServerSession, onOpen: () -> Unit, onSave: () -> Unit) {
    val c = Wa.colors
    Box(Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF111111)).clickable(onClick = onOpen)) {
        when {
            !story.ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (story.status == "pending") Spinner(18.dp) else MutedText(story.status, style = WaType.tiny, textAlign = TextAlign.Center)
            }
            story.isVideo -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) { WaIcon(WaIcons.play, 20.dp, Color.White) }
            }
            else -> AsyncImage(
                model = session.mediaUrl(story.url),
                contentDescription = null,
                imageLoader = session.imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                .padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                "@" + (story.username ?: story.userId.takeLast(6)),
                style = WaType.tiny.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(ago(story.takenAt), style = WaType.tiny, color = Color(0xFFDDDDDD), maxLines = 1)
        }
        if (story.ready) {
            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
                SaveButton(onSave, size = 30.dp, tint = Color.White)
            }
        }
    }
}

/** The small round "save to the gallery" control used on a tile and in the viewer. */
@Composable
private fun SaveButton(onSave: () -> Unit, size: androidx.compose.ui.unit.Dp = 38.dp, tint: Color = Wa.colors.text) {
    Box(Modifier.size(size).clip(CircleShape).background(if (tint == Color.White) Color(0x66000000) else Wa.colors.panel2), contentAlignment = Alignment.Center) {
        IconBtn(WaIcons.download, "Download this story", onSave, size = size, iconSize = size * 0.55f, tint = tint)
    }
}

/**
 * Full-screen viewer: swipe left/right, videos play once and move on, photos hold five seconds. The download button
 * saves the story showing to the phone's gallery.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun StoryViewer(
    items: List<StoryItem>,
    start: Int,
    session: ServerSession,
    onSave: (StoryItem, String?) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    val player = remember { buildPlayer(context, session.api.http) }
    val current = items.getOrNull(pager.currentPage)

    DisposableEffect(player) { onDispose { player.release() } }

    // One player for the whole viewer: the current page's story is loaded into it (silent, like a story).
    LaunchedEffect(current?.id) {
        val src = current?.takeIf { it.isVideo }?.url?.let { session.mediaUrl(it) }
        if (src != null) {
            player.setMediaItem(MediaItem.fromUri(src))
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.volume = 0f
            player.prepare()
            player.playWhenReady = true
        } else {
            player.stop()
        }
    }
    // A photo has no "ended": hold it for five seconds like a story does. The last one stays.
    LaunchedEffect(current?.id, current?.isVideo) {
        if (current != null && !current.isVideo) {
            kotlinx.coroutines.delay(5000)
            if (pager.currentPage < items.size - 1) pager.animateScrollToPage(pager.currentPage + 1)
        }
    }
    // A video that ends moves on to the next story.
    DisposableEffect(current?.id) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    scopeNext(pager.currentPage, items.size) { pager.currentPage + 1 }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val story = items[page]
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (story.isVideo) {
                        if (page == pager.currentPage) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        useController = false
                                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                                    }
                                },
                                update = { it.player = player },
                                onRelease = { it.player = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else if (story.url != null) {
                        AsyncImage(
                            model = session.mediaUrl(story.url),
                            contentDescription = null,
                            imageLoader = session.imageLoader,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Spinner(22.dp)
                            MutedText("Still downloading…", style = WaType.small)
                        }
                    }
                }
            }

            // Header: who posted it, when, download, close.
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding()
                    .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "@" + (current?.username ?: current?.userId?.takeLast(6) ?: ""),
                    color = Color.White,
                    style = WaType.chatName.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                current?.takenAt?.let { Text(ago(it), color = Color(0xFFDDDDDD), style = WaType.small) }
                if (current != null && current.ready) {
                    SaveButton({ onSave(current, current.username) }, tint = Color.White)
                }
                IconBtn(WaIcons.close, "Close", onClose, tint = Color.White)
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("${pager.currentPage + 1} / ${items.size}", color = Color.White, style = WaType.small)
                current?.expiresAt?.let {
                    val left = it - System.currentTimeMillis()
                    Text(
                        if (left > 0) "expires in ${inTime(left)}" else "expired",
                        color = Color(0xFFBBBBBB),
                        style = WaType.tiny,
                    )
                }
            }
        }
    }
}

private fun scopeNext(page: Int, size: Int, next: () -> Int) {
    if (page < size - 1) next()
}

/** "7 min ago" / "2 h ago" / "12 Oct" — the same wording as the web UI. */
internal fun ago(ts: Long?): String {
    if (ts == null || ts <= 0) return ""
    val mins = ((System.currentTimeMillis() - ts) / 60_000).toInt()
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 60 * 24 -> "${mins / 60} h ago"
        else -> java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
    }
}

/** "in 12 min" / "in 3 h" — for a moment that has not come yet. */
internal fun inTime(ms: Long): String {
    val mins = (ms / 60_000).toInt()
    return when {
        mins < 1 -> "a moment"
        mins < 60 -> "$mins min"
        mins < 60 * 24 -> "${mins / 60} h"
        else -> "${mins / (60 * 24)} days"
    }
}
