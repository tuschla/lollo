package app.milestone

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.milestone.resources.Res
import app.milestone.resources.app_name
import app.milestone.resources.ic_action_log_plus
import app.milestone.resources.ic_brand_mark
import app.milestone.resources.ic_content_run
import app.milestone.resources.ic_ui_close
import app.milestone.resources.main_close
import app.milestone.resources.main_log
import app.milestone.resources.main_return
import app.milestone.resources.main_run_in_progress
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// Shared shell chrome, specified in planning/design/screens/00-chrome.md.

/** Bar height of [BrandTopBar] (chrome §2). */
private val BrandTopBarHeight = 44.dp

/** Size of the route mark in the brand lockup (chrome §2). */
private val BrandMarkSize = 20.dp

/**
 * Top app bar for the destinations (chrome §2): the brand lockup, route
 * mark + `app_name` ("lollo") wordmark, both `Accent`, IS the title; no secondary
 * title string and no actions.
 */
@Composable
fun BrandTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgTop)
            .height(BrandTopBarHeight)
            .padding(start = Space.Screen.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Md.dp),
    ) {
        Icon(
            painterResource(Res.drawable.ic_brand_mark),
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(BrandMarkSize),
        )
        Text(
            stringResource(Res.string.app_name),
            color = Accent,
            style = Type.Title.copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Height of the floating chrome laid over the bottom of a destination: the
 * [FloatingNavBar] with the system navigation bar under it, and the run chip
 * above it while a run records. 0 beside the side rail, where the destination
 * ends above its chrome. A destination's list adds it to its bottom content
 * padding, so its last row scrolls clear of the bar.
 */
internal val LocalBottomChromeInset = compositionLocalOf { 0.dp }

/** Size of the selected tab's icon in the [FloatingNavBar] (chrome §1). */
private val NavIconSize = 24.dp

/** Size of the "+" in the bar's Log button. */
private val LogIconSize = 20.dp

/** Space between the pill's edge and its tabs: a 48dp tab in a 56dp pill. */
private val NavPillInset = Space.Sm.dp

/** A tab's side padding around its label: the comfortable value, and the least
 *  a narrow window or a large font may squeeze it to. */
private val NavTabPadding = Space.Screen.dp
private val NavTabMinPadding = Space.Sm.dp

/**
 * How the [FloatingNavBar] lays out at the width it gets.
 *
 * [tabPadding]: the side padding of every tab. [showIcon]: the selected tab shows
 * its icon before the label. [logAbove]: the Log button stands above the pill
 * instead of beside it. [logLabel]: there is room for the Log button's "Log" text.
 */
internal data class NavBarFit(val tabPadding: Dp, val showIcon: Boolean, val logAbove: Boolean, val logLabel: Boolean)

/**
 * The [FloatingNavBar] layout for [labels] (each tab's label width in its bold,
 * selected style), a Log label [logLabel] wide, in [room] (the bar's width inside
 * its margins). The first that fits wins: the pill beside the Log button with the
 * selected icon, then without the icon, then the Log button above a pill that takes
 * the whole width (the old FAB's place), with the icon, then without. A tab keeps
 * at least [NavTabMinPadding] each side. The result does not depend on the selected
 * tab or on whether Today's Log button shows, so the tabs keep their place across
 * destinations.
 */
internal fun fitNavBar(labels: List<Dp>, logLabel: Dp, room: Dp): NavBarFit {
    // 1dp of slack: dp → px rounding must never leave a label a pixel short.
    val usable = room - 1.dp
    val text = labels.fold(0.dp) { sum, w -> sum + w }
    fun padding(width: Dp, icon: Boolean): Dp {
        val content = text + if (icon) NavIconSize + Space.Md.dp else 0.dp
        return (width - NavPillInset * 2 - content) / (labels.size * 2)
    }
    fun pillWidth(pad: Dp, icon: Boolean): Dp =
        text + (if (icon) NavIconSize + Space.Md.dp else 0.dp) + pad * (labels.size * 2) + NavPillInset * 2
    val logWide = Space.Screen.dp * 2 + LogIconSize + Space.Md.dp + logLabel
    for (above in listOf(false, true)) {
        val width = if (above) usable else usable - LargeControlHeight - Space.Md.dp
        for (icon in listOf(true, false)) {
            val p = padding(width, icon)
            if (p < NavTabMinPadding) continue
            val pad = minOf(p, NavTabPadding)
            val logRoom = if (above) usable else usable - pillWidth(pad, icon) - Space.Md.dp
            return NavBarFit(pad, icon, above, logLabel = logRoom >= logWide)
        }
    }
    return NavBarFit(maxOf(0.dp, padding(usable, icon = false)), showIcon = false, logAbove = true, logLabel = false)
}

/**
 * Floating navigation bar (chrome §1), after Google Photos' floating bottom bar
 * (2026): the three destinations in a pill that floats [Space.Screen] above the
 * system navigation bar and in from the start edge, the content scrolling on
 * under and around it, and on Today the Log button ([onLog]) floating at the end.
 * Unselected tabs show their label; the selected tab is a solid `Accent` pill with
 * its icon (Today's filled) and label in `OnAccent`. [logExpanded]: the Log button
 * shows "Log" beside its "+" where it fits ([fitNavBar]). Behind the system's
 * 3-button bar (not the gesture handle, not iOS's home indicator) a band of the
 * ground keeps its buttons off the content.
 */
@Composable
fun FloatingNavBar(selected: Dest, onSelect: (Dest) -> Unit, onLog: (() -> Unit)?, logExpanded: Boolean, modifier: Modifier = Modifier) {
    val labels = Dest.entries.map { stringResource(it.labelRes) }
    val logLabel = stringResource(Res.string.main_log)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    fun widthOf(text: String, style: TextStyle) = with(density) { measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp() }
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.tappableElement)
                .background(BgTop.copy(alpha = NavScrimAlpha)),
        )
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = Space.Screen.dp, end = Space.Screen.dp, bottom = Space.Screen.dp),
        ) {
            val fit = fitNavBar(labels.map { widthOf(it, Type.Chip) }, widthOf(logLabel, LogLabelStyle), maxWidth)
            val logShown = logExpanded && fit.logLabel
            val chrome = Modifier.testTag("bottom-nav").traversalGroup()
            if (fit.logAbove && onLog != null) {
                Column(chrome.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.Md.dp)) {
                    LogButton(onLog, logShown, logLabel)
                    Box(Modifier.fillMaxWidth()) { NavPill(selected, onSelect, labels, fit) }
                }
            } else {
                Row(chrome.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NavPill(selected, onSelect, labels, fit)
                    Spacer(Modifier.weight(1f))
                    if (onLog != null) LogButton(onLog, logShown, logLabel)
                }
            }
        }
    }
}

/** The pill of the three tabs: one pick-one group, each tab a Role.Tab named by its
 *  visible label (the icon's description is null, so TalkBack reads the tab once). */
@Composable
private fun NavPill(selected: Dest, onSelect: (Dest) -> Unit, labels: List<String>, fit: NavBarFit) {
    Row(
        Modifier
            .testTag("nav-pill")
            .shadow(FloatingShadow, PillShape)
            .background(BgElevated, PillShape)
            .border(1.dp, OnBgBody.copy(alpha = HairlineAlpha.Control), PillShape)
            .padding(NavPillInset)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dest.entries.forEachIndexed { i, dest ->
            val active = dest == selected
            Row(
                modifier = Modifier
                    .heightIn(min = ActionMinHeight)
                    .clip(PillShape)
                    .background(if (active) Accent else Color.Transparent)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(dest) })
                    .padding(horizontal = fit.tabPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.Md.dp),
            ) {
                if (active && fit.showIcon) {
                    Icon(painterResource(dest.iconSelected), contentDescription = null, tint = OnAccent, modifier = Modifier.size(NavIconSize))
                }
                Text(
                    labels[i],
                    color = if (active) OnAccent else OnBgFaint,
                    style = if (active) Type.Chip else Type.ChipRegular,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Text style of the Log button's "Log". */
private val LogLabelStyle = Type.Body.copy(fontWeight = FontWeight.ExtraBold)

/**
 * The bar's Log button: a 56dp `Accent` disc with the "+", widened to "+ Log"
 * while [expanded]. A button named "Log" either way; the visible text is cleared
 * from the semantics so TalkBack does not read it twice.
 */
@Composable
private fun LogButton(onClick: () -> Unit, expanded: Boolean, label: String) {
    Surface(
        onClick = onClick,
        modifier = Modifier.testTag("log-fab").semantics {
            contentDescription = label
            role = Role.Button
        },
        shape = PillShape,
        color = Accent,
        // Dark OnAccent on every accent fill: white text fails AA on the accent.
        contentColor = OnAccent,
        shadowElevation = FloatingShadow,
    ) {
        Row(
            Modifier
                .clearAndSetSemantics {}
                .animateContentSize()
                .heightIn(min = LargeControlHeight)
                .widthIn(min = LargeControlHeight)
                .padding(horizontal = if (expanded) Space.Screen.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.Md.dp, Alignment.CenterHorizontally),
        ) {
            Icon(painterResource(Res.drawable.ic_action_log_plus), contentDescription = null, modifier = Modifier.size(LogIconSize))
            if (expanded) Text(label, style = LogLabelStyle, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Side navigation rail: the [FloatingNavBar]'s three destinations (each with its
 * icon and label, Material's rail indicator) down the start edge, with Log
 * ([onLog], Today only, as in the bar) at its top. Used instead of the floating
 * bar on a wide or a short window, and whenever the bar would be laid over the
 * pinned safety banner (a portrait phone at a large font under a hold): beside the
 * banner, never over it. With [showBrand] (a short window, where the wordmark bar
 * is left out) the route mark tops the rail.
 */
@Composable
fun MilestoneNavRail(selected: Dest, onSelect: (Dest) -> Unit, onLog: (() -> Unit)?, showBrand: Boolean = false) {
    val logLabel = stringResource(Res.string.main_log)
    // Material's NavigationRail puts its header inside the destinations' pick-one
    // group, so TalkBack read Log as a fourth tab; Log and the mark sit above the
    // group instead.
    Column(
        modifier = Modifier
            .testTag("nav-rail")
            .fillMaxHeight()
            .background(BgTop)
            .windowInsetsPadding(NavigationRailDefaults.windowInsets)
            .widthIn(min = RailWidth)
            .padding(vertical = Space.Sm.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.Sm.dp),
    ) {
        if (showBrand) {
            Icon(
                painterResource(Res.drawable.ic_brand_mark),
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.padding(vertical = Space.Md.dp).size(BrandMarkSize).testTag("rail-brand"),
            )
        }
        onLog?.let { log ->
            FloatingActionButton(
                onClick = log,
                modifier = Modifier.padding(bottom = Space.Md.dp).testTag("log-fab").semantics { contentDescription = logLabel },
                containerColor = Accent,
                contentColor = OnAccent,
                shape = RoundedCornerShape(Space.Card.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_action_log_plus), contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Column(
            modifier = Modifier.selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.Sm.dp),
        ) {
        Dest.entries.forEach { dest ->
            val active = dest == selected
            NavigationRailItem(
                selected = active,
                onClick = { onSelect(dest) },
                icon = {
                    Icon(
                        painterResource(if (active) dest.iconSelected else dest.iconUnselected),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = {
                    Text(
                        stringResource(dest.labelRes),
                        style = if (active) Type.Chip else Type.ChipRegular,
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    indicatorColor = AccentTint,
                    unselectedIconColor = OnBgFaint,
                    unselectedTextColor = OnBgFaint,
                ),
            )
        }
        }
    }
}

/** Width of [MilestoneNavRail] (Material's rail). */
private val RailWidth = 80.dp

/**
 * Run-in-progress chip (chrome §4): pinned just above the nav bar on every
 * destination while a run records.
 */
@Composable
fun RunInProgressChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = Space.Screen.dp)
            .padding(bottom = Space.Md.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.Card.dp))
            .background(BgElevated)
            .border(1.dp, Accent.copy(alpha = 0.4f), RoundedCornerShape(Space.Card.dp))
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = Space.Card.dp, vertical = Space.Md.dp + Space.Xs.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Md.dp),
    ) {
        Icon(
            painterResource(Res.drawable.ic_content_run),
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(Res.string.main_run_in_progress),
            color = OnBgBody,
            style = Type.BodyBold,
            modifier = Modifier.weight(1f),
        )
        // Drawn like a TextAction (Bold Accent + chevron); the whole chip is the target.
        Text(stringResource(Res.string.main_return), color = Accent, style = Type.BodyBold)
        RowChevron(tint = Accent)
    }
}

/**
 * The app's bottom sheet: fully expanded by default, so Back and a swipe close it
 * in one step (a half-height detent made Back collapse it first), on the sheet
 * ground [BgSunken] unless [containerColor] says otherwise, with
 * [SheetNavBarScrim] applied. A short window (a phone in landscape) leaves the
 * drag handle out and gives its 48dp to the content: the set logger's keypad
 * column, Done and Save need it there. The sheet still drags by its body, and
 * the scrim and Back close it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: Color = BgSunken,
    sheetGesturesEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shortHeight = LocalWindowLayout.current.shortHeight
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.traversalGroup(),
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        containerColor = containerColor,
        dragHandle = if (shortHeight) null else ({ BottomSheetDefaults.DragHandle() }),
    ) {
        SheetNavBarScrim()
        if (shortHeight) Spacer(Modifier.height(Space.Md.dp))
        content()
    }
}

/** The pane a two-pane screen (History) offers its detail; null under one column. */
internal val LocalDetailDock = staticCompositionLocalOf<SlotDock?> { null }

/**
 * An entry's detail: an [AppSheet] on one column; on a two-pane screen
 * ([LocalDetailDock]) the same body stands in the detail pane beside the list,
 * on the sheet ground [BgSunken] (every entry kind alike) with a close row on
 * top. Back and the close button both call [onDismiss], as the sheet's swipe and
 * scrim do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dock = LocalDetailDock.current
    if (dock == null) {
        AppSheet(onDismiss = onDismiss, content = content)
        return
    }
    Docked(dock) {
        BackHandler(onBack = onDismiss)
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = Space.Card.dp, topEnd = Space.Card.dp))
                .background(BgSunken)
                .testTag("detail-pane")
                .traversalGroup(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.Sm.dp, vertical = Space.Sm.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("detail-pane-close")) {
                    Icon(
                        painterResource(Res.drawable.ic_ui_close),
                        contentDescription = stringResource(Res.string.main_close),
                        tint = OnBgMuted,
                    )
                }
            }
            content()
        }
    }
}

internal fun Modifier.sheetBody(scroll: ScrollState, gutter: Dp = Space.Sheet.dp): Modifier =
    fillMaxWidth().verticalScroll(scroll).navigationBarsPadding().sheetPadding(gutter)

internal fun Modifier.sheetPadding(gutter: Dp = Space.Sheet.dp): Modifier = padding(horizontal = gutter).padding(bottom = Space.Lg.dp)
