package io.github.revocx35.tiktoklogger.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import io.github.revocx35.tiktoklogger.core.FeedItem
import io.github.revocx35.tiktoklogger.core.TtProfile
import io.github.revocx35.tiktoklogger.data.Prefs
import io.github.revocx35.tiktoklogger.data.ServerSession
import io.github.revocx35.tiktoklogger.ui.components.BtnStyle
import io.github.revocx35.tiktoklogger.ui.components.ErrorNote
import io.github.revocx35.tiktoklogger.ui.components.IconBtn
import io.github.revocx35.tiktoklogger.ui.components.MutedText
import io.github.revocx35.tiktoklogger.ui.components.Spinner
import io.github.revocx35.tiktoklogger.ui.components.WaButton
import io.github.revocx35.tiktoklogger.ui.theme.Wa
import io.github.revocx35.tiktoklogger.ui.theme.WaIcons
import io.github.revocx35.tiktoklogger.ui.theme.WaType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The lists a profile can show, in TikTok's order, with their tab icon and empty text. */
internal data class ProfileTabInfo(val key: String, val label: String, val icon: ImageVector, val empty: String)

internal val PROFILE_TABS = listOf(
    ProfileTabInfo("videos", "Videos", WaIcons.grid, "No videos yet."),
    ProfileTabInfo("reposts", "Reposts", WaIcons.repost, "No reposts yet."),
    ProfileTabInfo("favorites", "Favorites", WaIcons.bookmark, "Nothing saved to Favorites yet."),
    ProfileTabInfo("liked", "Liked", WaIcons.heart, "No liked videos yet."),
)

/** "Friends" when both follow each other, "Following", else "Follow" — TikTok's button text. */
internal fun followLabel(p: TtProfile): String = when {
    p.friends -> "Friends"
    p.following == true -> "Following"
    else -> "Follow"
}

/**
 * A TikTok profile (server v1.15): the owner's own ("me") or anyone's, read through the server's browser. A tapped
 * video opens the list in the same player as For You ([VideoPane]); everything done there (like, repost, follow…)
 * is mirrored back into the grid's list.
 */
class ProfileViewModel(session: ServerSession, val username: String) : VideoActionsModel(session) {
    var profile by mutableStateOf<TtProfile?>(null)
        private set
    var profileError by mutableStateOf<String?>(null)
        private set
    var tab by mutableStateOf("videos")
        private set
    var items by mutableStateOf<List<FeedItem>>(emptyList())
        private set
    var more by mutableStateOf(false)
        private set
    var listLoading by mutableStateOf(false)
        private set
    var listError by mutableStateOf<String?>(null)
        private set
    var followBusy by mutableStateOf(false)
        private set
    private var cursor: String? = null
    private var index = -1
    private var listJob: Job? = null

    init {
        load()
    }

    fun load() {
        profileError = null
        viewModelScope.launch {
            try {
                profile = session.api.profile(username)
                selectTab(tab, force = true)
            } catch (e: Exception) {
                profileError = session.fail("profile", e)
            }
        }
    }

    fun selectTab(key: String, force: Boolean = false) {
        if (key == tab && !force) return
        tab = key
        items = emptyList()
        cursor = null
        more = false
        listJob?.cancel()
        listJob = null
        loadMore()
    }

    fun loadMore() {
        val p = profile ?: return
        if (listJob?.isActive == true || tab !in p.tabs) return
        val t = tab
        listLoading = true
        listError = null
        listJob = viewModelScope.launch {
            try {
                val page = session.api.profileVideos(if (p.me) "me" else p.username, t, cursor)
                if (t != tab) return@launch
                items = items + page.items.filter { n -> items.none { it.id == n.id } }
                cursor = page.cursor
                more = page.more
            } catch (e: Exception) {
                listError = session.fail("profile", e)
            } finally {
                listLoading = false
            }
        }
    }

    fun toggleProfileFollow() {
        val p = profile ?: return
        if (p.me || followBusy) return
        val want = p.following != true
        followBusy = true
        viewModelScope.launch {
            try {
                val r = session.api.follow(p.username, want)
                val delta = if (r.following == (p.following == true)) 0L else if (r.following) 1L else -1L
                profile = p.copy(following = r.following, friends = r.following && p.friends, stats = p.stats.copy(followers = p.stats.followers?.let { it + delta }))
                onFollowChanged(p.username, r.following)
            } catch (e: Exception) {
                profileError = session.fail("profile", e)
            } finally {
                followBusy = false
            }
        }
    }

    /** Opens the player on the tapped video. */
    fun open(i: Int) {
        val it = items.getOrNull(i) ?: return
        index = i
        show(it)
    }

    fun close() {
        index = -1
        show(null)
    }

    /** Swipes inside the loaded list; the next page loads when the end comes close. */
    fun step(dir: String) {
        if (index < 0) return
        val next = if (dir == "next") index + 1 else index - 1
        if (next < 0) return
        if (next >= items.size - 3 && more) loadMore()
        val it = items.getOrNull(next) ?: return
        index = next
        show(it)
    }

    override fun update(next: FeedItem) {
        super.update(next)
        items = items.map { if (it.id == next.id) next else it }
    }

    override fun onFollowChanged(username: String, following: Boolean) {
        items = items.map { if (it.author.username == username) it.copy(author = it.author.copy(following = following)) else it }
        val p = profile
        if (p != null && p.username == username && !p.me) profile = p.copy(following = following)
    }
}

@Composable
fun ProfileScreen(vm: ProfileViewModel, session: ServerSession, prefs: Prefs, onBack: (() -> Unit)?, onOpenProfile: (String) -> Unit) {
    if (vm.item != null) {
        BackHandler { vm.close() }
        VideoPane(
            vm = vm,
            session = session,
            prefs = prefs,
            stepping = false,
            onStep = vm::step,
            onOpenProfile = { u -> if (u == vm.profile?.username) vm.close() else onOpenProfile(u) },
            onClose = vm::close,
        )
        return
    }
    val p = vm.profile
    if (p == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (vm.profileError == null) Spinner(30.dp)
            ErrorNote(vm.profileError)
            if (vm.profileError != null) {
                WaButton("Try again", { vm.load() })
                if (onBack != null) WaButton("Back", onBack, style = BtnStyle.Ghost)
            }
        }
        return
    }
    ProfileContent(
        p = p,
        items = vm.items,
        tab = vm.tab,
        loading = vm.listLoading,
        more = vm.more,
        error = vm.listError ?: vm.profileError,
        followBusy = vm.followBusy,
        mediaUrl = session::mediaUrl,
        imageLoader = session.imageLoader,
        onBack = onBack,
        onTab = vm::selectTab,
        onFollow = vm::toggleProfileFollow,
        onOpen = vm::open,
        onMore = vm::loadMore,
    )
}

/** The profile page itself, without a session (the screenshot test draws it with sample data). */
@Composable
internal fun ProfileContent(
    p: TtProfile,
    items: List<FeedItem>,
    tab: String,
    loading: Boolean,
    more: Boolean,
    error: String?,
    followBusy: Boolean,
    mediaUrl: (String?) -> String?,
    imageLoader: coil3.ImageLoader?,
    onBack: (() -> Unit)?,
    onTab: (String) -> Unit,
    onFollow: () -> Unit,
    onOpen: (Int) -> Unit,
    onMore: () -> Unit,
) {
    val c = Wa.colors
    val uri = LocalUriHandler.current
    val grid = rememberLazyGridState()
    val nearEnd by remember { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= grid.layoutInfo.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, more, loading) { if (nearEnd && more && !loading) onMore() }
    val tabs = PROFILE_TABS.filter { it.key in p.tabs }

    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize().background(c.bg), state = grid, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onBack != null) {
                    Row(Modifier.fillMaxWidth()) { IconBtn(WaIcons.back, "Back", onBack) }
                }
                Box(Modifier.size(96.dp).clip(CircleShape).background(c.panel2), contentAlignment = Alignment.Center) {
                    val a = mediaUrl(p.avatar)
                    if (a != null && imageLoader != null) {
                        AsyncImage(a, null, imageLoader = imageLoader, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(WaIcons.userFill, null, tint = c.muted, modifier = Modifier.size(48.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.username, style = WaType.chatName, fontWeight = FontWeight.Bold, color = c.text)
                    if (p.verified) Icon(WaIcons.verified, "Verified", tint = Color(0xFF20D5EC), modifier = Modifier.size(18.dp))
                }
                p.name?.let { Text(it, style = WaType.base, color = c.text2, fontWeight = FontWeight.SemiBold) }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Stat(p.stats.following, "Following")
                    Stat(p.stats.followers, "Followers")
                    Stat(p.stats.likes, "Likes")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.me) {
                        WaButton("Open on TikTok", { uri.openUri(p.url) })
                    } else {
                        WaButton(followLabel(p), onFollow, busy = followBusy, style = if (p.following == true) BtnStyle.Default else BtnStyle.Primary)
                        WaButton("Open on TikTok", { uri.openUri(p.url) }, style = BtnStyle.Ghost)
                    }
                }
                p.bio?.let { Text(it, style = WaType.base, color = c.text, textAlign = TextAlign.Center) }
                p.link?.let { l -> Text(l.removePrefix("https://").removePrefix("http://"), style = WaType.small, color = c.link, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { uri.openUri(l) }) }
                ErrorNote(error)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (tabs.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(WaIcons.lock, null, tint = c.muted, modifier = Modifier.size(32.dp))
                    MutedText("This account is private. Follow it to see its videos.", textAlign = TextAlign.Center)
                }
            } else {
                Row(Modifier.fillMaxWidth()) {
                    tabs.forEach { t ->
                        val on = t.key == tab
                        Column(
                            Modifier.weight(1f).clickable { onTab(t.key) }.padding(top = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(t.icon, t.label, tint = if (on) c.text else c.muted, modifier = Modifier.size(20.dp))
                            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(2.dp).background(if (on) c.text else c.border))
                        }
                    }
                }
            }
        }
        itemsIndexed(items, key = { _, it -> it.id }) { i, it ->
            Box(Modifier.aspectRatio(3f / 4f).background(c.panel2).clickable { onOpen(i) }) {
                val poster = mediaUrl(it.video?.poster)
                if (poster != null && imageLoader != null) {
                    AsyncImage(poster, null, imageLoader = imageLoader, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
                Row(Modifier.align(Alignment.BottomStart).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(WaIcons.play, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Text(" " + compactCount(it.stats.plays), color = Color.White, style = WaType.tiny, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                when {
                    loading -> Spinner()
                    items.isEmpty() && tabs.isNotEmpty() && error == null -> MutedText(PROFILE_TABS.first { it.key == tab }.empty)
                }
            }
        }
    }
}

@Composable
private fun Stat(n: Long?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(compactCount(n), style = WaType.base, fontWeight = FontWeight.Bold, color = Wa.colors.text)
        Text(" $label", style = WaType.small, color = Wa.colors.text2)
    }
}

internal fun compactCount(n: Long?): String = when {
    n == null -> "—"
    n >= 1_000_000_000 -> String.format("%.1fB", n / 1_000_000_000.0)
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
    else -> n.toString()
}
