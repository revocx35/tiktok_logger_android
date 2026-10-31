package io.github.revocx35.tiktoklogger.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.github.revocx35.tiktoklogger.ui.components.BrandMark
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.revocx35.tiktoklogger.core.AppState
import io.github.revocx35.tiktoklogger.core.Message
import io.github.revocx35.tiktoklogger.core.MessageEdit
import io.github.revocx35.tiktoklogger.core.TtState
import io.github.revocx35.tiktoklogger.core.TtStatus
import io.github.revocx35.tiktoklogger.core.UserRole
import io.github.revocx35.tiktoklogger.data.AppGraph
import io.github.revocx35.tiktoklogger.data.ServerSender
import io.github.revocx35.tiktoklogger.data.ServerSession
import io.github.revocx35.tiktoklogger.media.MediaOps
import io.github.revocx35.tiktoklogger.media.MediaViewer
import io.github.revocx35.tiktoklogger.ui.components.WaIcon
import io.github.revocx35.tiktoklogger.ui.components.WaStatePill
import io.github.revocx35.tiktoklogger.ui.components.WarnBanner
import io.github.revocx35.tiktoklogger.ui.screens.AdminDeps
import io.github.revocx35.tiktoklogger.ui.screens.AccountSwitcherDialog
import io.github.revocx35.tiktoklogger.ui.screens.AdminScreen
import io.github.revocx35.tiktoklogger.ui.screens.ChatListPane
import io.github.revocx35.tiktoklogger.ui.screens.ChatListViewModel
import io.github.revocx35.tiktoklogger.ui.screens.ChatPane
import io.github.revocx35.tiktoklogger.ui.screens.StoriesScreen
import io.github.revocx35.tiktoklogger.ui.screens.StoriesViewModel
import io.github.revocx35.tiktoklogger.ui.screens.ChatViewModel
import io.github.revocx35.tiktoklogger.ui.screens.DeletedScreen
import io.github.revocx35.tiktoklogger.ui.screens.DeletedViewModel
import io.github.revocx35.tiktoklogger.ui.screens.NoChatSelected
import io.github.revocx35.tiktoklogger.ui.screens.SearchScreen
import io.github.revocx35.tiktoklogger.ui.screens.SearchViewModel
import io.github.revocx35.tiktoklogger.ui.screens.SettingsDeps
import io.github.revocx35.tiktoklogger.ui.screens.SettingsScreen
import io.github.revocx35.tiktoklogger.ui.screens.TikTokScreen
import io.github.revocx35.tiktoklogger.ui.screens.FeedScreen
import io.github.revocx35.tiktoklogger.ui.screens.FeedViewModel
import io.github.revocx35.tiktoklogger.ui.theme.Wa
import io.github.revocx35.tiktoklogger.ui.theme.WaIcons
import io.github.revocx35.tiktoklogger.ui.theme.WaType
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable object ChatsRoute
@Serializable data class ChatRoute(val chatId: String, val focus: String? = null)
@Serializable object DeletedRoute
@Serializable data class SearchRoute(val q: String = "")
@Serializable object TikTokRoute
@Serializable object FeedRoute
@Serializable object StoriesRoute
@Serializable object SettingsRoute
@Serializable object AdminRoute

/**
 * [adminOnly] tabs exist only for the admin (the first account). [tab] is the label in the TikTok app's tab bar on
 * phones (Home · Stories · Inbox · Unsent, then Profile = the sheet with the rest); [iconOn] is the solid twin drawn
 * for the active tab, as TikTok does. The order is the web sidebar's.
 */
enum class Tab(val label: String, val icon: ImageVector, val iconOn: ImageVector, val adminOnly: Boolean = false, val tab: String? = null) {
    Feed("For You", WaIcons.home, WaIcons.homeFill, tab = "Home"),
    Chats("Messages", WaIcons.inbox, WaIcons.inboxFill, tab = "Inbox"),
    Stories("Stories", WaIcons.stories, WaIcons.storiesFill, tab = "Stories"),
    Deleted("Unsent", WaIcons.deleted, WaIcons.deletedFill, tab = "Unsent"),
    TikTok("TikTok", WaIcons.monitor, WaIcons.monitorFill),
    Admin("Users", WaIcons.group, WaIcons.groupFill, adminOnly = true),
    Settings("Settings", WaIcons.settings, WaIcons.settingsFill),
    Search("Search", WaIcons.search, WaIcons.search),
}

/** The phone tab bar's slots, in TikTok's order. */
private val BottomTabs = listOf(Tab.Feed, Tab.Stories, Tab.Chats, Tab.Deleted)

private fun tabRoute(t: Tab): Any = when (t) {
    Tab.Chats -> ChatsRoute
    Tab.Deleted -> DeletedRoute
    Tab.Search -> SearchRoute()
    Tab.Feed -> FeedRoute
    Tab.Stories -> StoriesRoute
    Tab.TikTok -> TikTokRoute
    Tab.Settings -> SettingsRoute
    Tab.Admin -> AdminRoute
}

private fun NavHostController.goTab(t: Tab) = navigate(tabRoute(t)) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** One item of the TikTok sidebar (icons only) or tab bar ([label] under the icon); [dot] = needs attention. */
@Composable
private fun RailItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tabLabel: String? = null,
    tint: Color = Wa.colors.text,
    activeTint: Color = Wa.colors.accentNav,
    dot: Boolean = false,
) {
    val c = Wa.colors
    Column(
        modifier.height(if (tabLabel != null) 50.dp else 52.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Tab, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label; selected = active },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            WaIcon(icon, if (tabLabel != null) 24.dp else 28.dp, if (active) activeTint else tint)
            if (dot) Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(c.rail).padding(1.5.dp).clip(CircleShape).background(c.accent))
        }
        if (tabLabel != null) Text(tabLabel, style = WaType.tabLabel, color = if (active) activeTint else tint, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The Profile sheet (phones) / More sheet (tablets): who is signed in, the pages without a tab, accounts, log out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(tabs: List<Tab>, wa: TtStatus?, username: String?, onTab: (Tab) -> Unit, onLogout: () -> Unit, onAccounts: () -> Unit, onClose: () -> Unit) {
    val c = Wa.colors
    ModalBottomSheet(onDismissRequest = onClose, containerColor = c.panel, dragHandle = { Box(Modifier.padding(top = 8.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.border)) }) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(c.panel2), contentAlignment = Alignment.Center) { WaIcon(WaIcons.userFill, 24.dp, c.muted) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (username != null) "@$username" else "tiktok_logger", style = WaType.base.copy(fontWeight = FontWeight.Bold), color = c.text)
                    WaStatePill(wa)
                }
            }
            val row: @Composable (ImageVector, String, () -> Unit, (@Composable () -> Unit)?) -> Unit = { icon, text, onClick, trailing ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClose(); onClick() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WaIcon(icon, 24.dp, c.text)
                    Text(text, style = WaType.base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = c.text, modifier = Modifier.weight(1f))
                    trailing?.invoke()
                }
            }
            for (t in tabs) row(t.icon, t.label, { onTab(t) }, if (t == Tab.TikTok) ({ WaStatePill(wa, compact = true) }) else null)
            row(WaIcons.user, "Switch account", onAccounts, null)
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(c.border))
            row(WaIcons.logout, if (username != null) "Log out @$username" else "Log out", onLogout, null)
        }
    }
}

/** Media actions for bubbles: viewer, save (system file picker), open with another app, retry, edits. */
@Composable
private fun rememberMediaActions(session: ServerSession, context: Context, onView: (Message) -> Unit): Pair<MessageActions, (Message) -> Unit> {
    val scope = rememberCoroutineScope()
    var pendingSave by remember { mutableStateOf<Message?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val msg = pendingSave
        pendingSave = null
        if (uri != null && msg != null) scope.launch { MediaOps.saveTo(context, session, msg, uri) }
    }
    val save: (Message) -> Unit = { msg ->
        pendingSave = msg
        saveLauncher.launch(MediaOps.defaultName(msg))
    }
    val actions = remember(session) {
        object : MessageActions {
            override fun openMedia(msg: Message) = onView(msg)
            override fun saveMedia(msg: Message) = save(msg)
            override fun saveToGallery(messages: List<Message>) {
                // Session scope: keeps going when the owner leaves the chat.
                session.scope.launch { MediaOps.saveAllToGallery(context.applicationContext, session.api, messages) }
            }
            override fun openDocument(msg: Message) {
                scope.launch { MediaOps.open(context, session, msg) }
            }
            override suspend fun retryMedia(messageId: String) {
                session.api.retryMedia(messageId)
            }
            override suspend fun edits(messageId: String): List<MessageEdit> = session.api.edits(messageId)
        }
    }
    return actions to save
}

/**
 * The logged-in app, laid out like the web UI: a menu rail (left on wide screens, bottom on phones,
 * hidden inside a chat) and a two-pane chat list + chat on wide screens.
 */
@Composable
fun MainShell(graph: AppGraph, session: ServerSession, state: AppState, wa: TtStatus?, onLogout: () -> Unit, onChangeServer: () -> Unit) {
    val c = Wa.colors
    val context = LocalContext.current
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val inChat = dest?.hasRoute<ChatRoute>() == true
    val activeTab = when {
        dest == null -> Tab.Chats
        dest.hasRoute<ChatsRoute>() || inChat -> Tab.Chats
        dest.hasRoute<DeletedRoute>() -> Tab.Deleted
        dest.hasRoute<SearchRoute>() -> Tab.Search
        dest.hasRoute<FeedRoute>() -> Tab.Feed
        dest.hasRoute<StoriesRoute>() -> Tab.Stories
        dest.hasRoute<TikTokRoute>() -> Tab.TikTok
        dest.hasRoute<SettingsRoute>() -> Tab.Settings
        dest.hasRoute<AdminRoute>() -> Tab.Admin
        else -> null
    }
    var viewing by remember { mutableStateOf<Message?>(null) }
    // The account menu: the open account, the others added on this phone, and "add another account".
    val accounts by graph.controller.accounts.collectAsState()
    val activeAccount by graph.controller.active.collectAsState()
    var showAccounts by remember { mutableStateOf(false) }
    val (actions, save) = rememberMediaActions(session, context) { viewing = it }
    val chatList: ChatListViewModel = viewModel(key = "chats:${session.id}") { ChatListViewModel(session) }
    val chatListState = rememberLazyListState()
    val showAdmin = state.role == UserRole.Admin
    val railTabs = Tab.entries.filter { showAdmin || !it.adminOnly }
    var moreOpen by remember { mutableStateOf(false) }
    val needsAttention = wa?.needsLogin == true
    val sender = remember(session) { ServerSender(context.applicationContext, session.api) }
    val sendBlocked = if (wa?.connected == true) null else "TikTok is not connected right now, so messages can’t be sent."

    fun openChat(id: String, focus: String? = null, replace: Boolean = false) {
        nav.navigate(ChatRoute(id, focus)) {
            if (replace) popUpTo<ChatsRoute>()
            launchSingleTop = focus == null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.panel)) {
        val wide = maxWidth >= 900.dp

        val content: @Composable ColumnScope.() -> Unit = {
            if (needsAttention) {
                WarnBanner(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    Text(
                        buildAnnotatedString {
                            append("TikTok is logged out — new messages are not being logged. ")
                            withStyle(SpanStyle(color = c.link, textDecoration = TextDecoration.Underline)) { append("Open the TikTok screen") }
                            append(" to fix it.")
                        },
                        style = WaType.small,
                        color = c.text,
                        modifier = Modifier.clickable { nav.goTab(Tab.TikTok) },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().then(if (needsAttention) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
                CompositionLocalProviderActions(actions, sender) {
                    NavHost(nav, startDestination = ChatsRoute, modifier = Modifier.fillMaxSize()) {
                        composable<ChatsRoute> {
                            if (wide) {
                                Row(Modifier.fillMaxSize()) {
                                    ChatListPane(chatList, null, { openChat(it) }, { nav.navigate(SearchRoute(it)) }, Modifier.widthIn(min = 300.dp, max = 440.dp).fillMaxWidth(0.3f).fillMaxHeight(), chatListState)
                                    Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
                                    NoChatSelected(Modifier.weight(1f).fillMaxHeight())
                                }
                            } else {
                                ChatListPane(chatList, null, { openChat(it) }, { nav.navigate(SearchRoute(it)) }, Modifier.fillMaxSize(), chatListState)
                            }
                        }
                        composable<ChatRoute> { back ->
                            val r = back.toRoute<ChatRoute>()
                            val vm: ChatViewModel = viewModel(key = "chat:${session.id}:${r.chatId}:${r.focus}") { ChatViewModel(session, r.chatId, r.focus) }
                            if (wide) {
                                Row(Modifier.fillMaxSize()) {
                                    ChatListPane(chatList, r.chatId, { openChat(it, replace = true) }, { nav.navigate(SearchRoute(it)) }, Modifier.widthIn(min = 300.dp, max = 440.dp).fillMaxWidth(0.3f).fillMaxHeight(), chatListState)
                                    Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
                                    ChatPane(vm, null, true, actions, Modifier.weight(1f).fillMaxHeight(), sendDisabledReason = sendBlocked)
                                }
                            } else {
                                ChatPane(vm, { nav.popBackStack() }, false, actions, Modifier.fillMaxSize(), sendDisabledReason = sendBlocked)
                            }
                        }
                        composable<DeletedRoute> {
                            val vm: DeletedViewModel = viewModel(key = "deleted:${session.id}") { DeletedViewModel(session) }
                            DeletedScreen(vm) { chatId, messageId -> openChat(chatId, messageId) }
                        }
                        composable<SearchRoute> { back ->
                            val r = back.toRoute<SearchRoute>()
                            val vm: SearchViewModel = viewModel(key = "search:${session.id}:${r.q}") { SearchViewModel(session, r.q) }
                            SearchScreen(vm.query, { vm.query = it }, vm.result, vm.error, vm.busy, vm::run) { chatId, messageId -> openChat(chatId, messageId) }
                        }
                        composable<TikTokRoute> { TikTokScreen(session.api, wa) }
                        composable<FeedRoute> {
                            val vm: FeedViewModel = viewModel(key = "feed:${session.id}") { FeedViewModel(session) }
                            FeedScreen(vm, session, wa?.connected == true, graph.prefs)
                        }
                        composable<StoriesRoute> {
                            val vm: StoriesViewModel = viewModel(key = "stories:${session.id}") { StoriesViewModel(session) }
                            StoriesScreen(vm, session)
                        }
                        composable<AdminRoute> {
                            AdminScreen(AdminDeps(session.api, state.username, state.totpEnabled == true))
                        }
                        composable<SettingsRoute> {
                            SettingsScreen(
                                SettingsDeps(
                                    api = session.api,
                                    graph = graph,
                                    username = state.username,
                                    totpEnabled = state.totpEnabled == true,
                                    wa = wa,
                                    refresh = { graph.controller.refresh().join() },
                                    onChangeServer = onChangeServer,
                                    onOpenAdmin = if (showAdmin) ({ nav.goTab(Tab.Admin) }) else null,
                                    account = activeAccount,
                                    accounts = accounts,
                                    onOpenAccounts = { showAccounts = true },
                                    onAddAccount = { graph.controller.beginAddAccount() },
                                    onRemoveAccount = { graph.controller.removeAccount(it) },
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))) {
                SideRail(activeTab, wa, railTabs, { nav.goTab(it) }) { moreOpen = true }
                Column(Modifier.weight(1f).fillMaxHeight(), content = content)
            }
        } else {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))) {
                content()
                if (!inChat) BottomRail(activeTab, wa, activeTab == Tab.Feed, moreOpen, { nav.goTab(it) }) { moreOpen = true }
            }
        }

        if (moreOpen) {
            // Phones: every page without a tab; tablets: the sidebar shows them all, the sheet keeps accounts + log out.
            MoreSheet(if (wide) emptyList() else railTabs.filter { it !in BottomTabs }, wa, state.username, { nav.goTab(it) }, onLogout, { showAccounts = true }) { moreOpen = false }
        }
    }

    if (showAccounts) {
        AccountSwitcherDialog(
            accounts = accounts,
            active = activeAccount,
            onSwitch = { a -> showAccounts = false; graph.controller.switchTo(a.id) },
            onAdd = { showAccounts = false; graph.controller.beginAddAccount() },
            onRemove = { a -> graph.controller.removeAccount(a) },
            onClose = { showAccounts = false },
        )
    }

    viewing?.let { msg -> MediaViewer(session, msg, onSave = { save(msg) }, onClose = { viewing = null }) }
}

/** TikTok's folded (icons only) sidebar on wide screens: the app's mark, one icon per page, "More" below. */
@Composable
fun SideRail(active: Tab?, wa: TtStatus?, tabs: List<Tab>, onTab: (Tab) -> Unit, onMore: () -> Unit) {
    val c = Wa.colors
    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier.width(72.dp).fillMaxHeight().background(c.rail).statusBarsPadding().navigationBarsPadding().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BrandMark(size = 32.dp, iconSize = 20.dp, corner = 9.dp, modifier = Modifier.padding(bottom = 12.dp))
            for (t in tabs) RailItem(t.label, if (active == t) t.iconOn else t.icon, active == t, { onTab(t) }, dot = t == Tab.TikTok && wa?.connected != true)
            RailItem("More", WaIcons.menuDots, false, onMore)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
    }
}

/** The TikTok app's tab bar on phones (hidden inside a chat; black on For You): four tabs and Profile (the sheet). */
@Composable
fun BottomRail(active: Tab?, wa: TtStatus?, dark: Boolean, profileOpen: Boolean, onTab: (Tab) -> Unit, onProfile: () -> Unit) {
    val c = Wa.colors
    val bg = if (dark) Color.Black else c.tabbar
    val on = if (dark) Color.White else c.text
    val off = if (dark) Color.White.copy(alpha = 0.6f) else c.muted
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (dark) Color.White.copy(alpha = 0.12f) else c.border))
        Row(Modifier.fillMaxWidth().background(bg).navigationBarsPadding().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (t in BottomTabs) RailItem(t.label, if (active == t) t.iconOn else t.icon, active == t, { onTab(t) }, Modifier.weight(1f), tabLabel = t.tab, tint = off, activeTint = on)
            val profileOn = profileOpen || (active != null && active !in BottomTabs)
            RailItem("Profile", if (profileOn) WaIcons.userFill else WaIcons.user, profileOn, onProfile, Modifier.weight(1f), tabLabel = "Profile", tint = off, activeTint = on, dot = wa?.connected != true)
        }
    }
}

@Composable
private fun CompositionLocalProviderActions(actions: MessageActions, sender: MessageSender, content: @Composable () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(LocalMessageActions provides actions, LocalSender provides sender, content = content)
