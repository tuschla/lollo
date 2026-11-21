package app.milestone

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
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
internal val NavIconSize = 24.dp

/** Space between the selected tab's icon and its label. */
internal val NavIconGap = Space.Md.dp

/** Size of the "+" in the bar's Log button. */
private val LogIconSize = 20.dp

/** Space between the pill and the Log button, beside or above it: Photos leaves
 *  0.14 of the bar's height between its pill and its round button, 8dp of 56dp. */
private val NavGroupGap = Space.Md.dp

/** Space between the pill's edge and its tabs: a 48dp tab in a 56dp pill. */
private val NavPillInset = Space.Sm.dp

/** A tab's side padding around its content: the comfortable value, and the least
 *  a narrow window or a large font may squeeze it to. */
private val NavTabPadding = Space.Screen.dp
private val NavTabMinPadding = Space.Sm.dp

/**
 * How the [FloatingNavBar] lays out at the width it gets.
 *
 * [tabPadding]: the side padding of every tab. [showIcon]: the selected tab shows
 * its icon before the label. [logAbove]: the Log button stands above the pill
 * instead of beside it.
 */
internal data class NavBarFit(val tabPadding: Dp, val showIcon: Boolean, val logAbove: Boolean)

/**
 * The width the pill's labels take together, the most over the possible
 * selections: the selected tab's label in [bold], the others in [regular] (each
 * tab's label width in that weight). Sizing the pill to it keeps the pill's width
 * the same whichever tab is selected.
 */
internal fun pillLabelsWidth(bold: List<Int>, regular: List<Int>): Int {
    val all = regular.sum()
    return bold.indices.maxOf { all - regular[it] + bold[it] }
}

/**
 * The [FloatingNavBar] layout for [tabs] tabs whose labels take [text] (see
 * [pillLabelsWidth]) in [room] (the bar's width inside its margins). The first
 * that fits wins: the pill beside the Log button with the selected icon, then
 * without the icon, each tab keeping at least [NavTabMinPadding] a side; else the
 * Log button above a pill that may take the whole width, without the icon, its
 * tabs' padding down to none. Each step gives up what the one before it showed, so
 * a narrower window or a larger font never shows more. The result does not depend
 * on the selected tab, so the pill and Log keep their size and place on every
 * destination.
 */
internal fun fitNavBar(text: Dp, tabs: Int, room: Dp): NavBarFit {
    // 1dp of slack: dp → px rounding must never leave a label a pixel short.
    val usable = room - 1.dp
    fun padding(width: Dp, icon: Boolean): Dp {
        val content = text + if (icon) NavIconSize + NavIconGap else 0.dp
        return (width - NavPillInset * 2 - content) / (tabs * 2)
    }
    val beside = usable - LargeControlHeight - NavGroupGap
    for (icon in listOf(true, false)) {
        val p = padding(beside, icon)
        if (p >= NavTabMinPadding) return NavBarFit(minOf(p, NavTabPadding), icon, logAbove = false)
    }
    return NavBarFit(padding(usable, icon = false).coerceIn(0.dp, NavTabPadding), showIcon = false, logAbove = true)
}

/**
 * Floating navigation bar (chrome §1), after Google Photos' floating bottom bar
 * (2026): the three destinations in a pill and the round Log button ([onLog])
 * beside it, centered as one group that floats [Space.Screen] above the system
 * navigation bar, the content scrolling on under and around it. Unselected tabs
 * show their label; the selected tab is a solid `Accent` pill with its icon
 * (Today's filled) and label in `OnAccent`. Behind the system's 3-button bar (not
 * the gesture handle, not iOS's home indicator) a band of the ground keeps its
 * buttons off the content.
 */
@Composable
fun FloatingNavBar(selected: Dest, onSelect: (Dest) -> Unit, onLog: () -> Unit, modifier: Modifier = Modifier) {
    val labels = Dest.entries.map { stringResource(it.labelRes) }
    val logLabel = stringResource(Res.string.main_log)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    fun widthPx(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
    val textPx = pillLabelsWidth(labels.map { widthPx(it, Type.Chip) }, labels.map { widthPx(it, Type.ChipRegular) })
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
            val fit = fitNavBar(with(density) { textPx.toDp() }, labels.size, maxWidth)
            val chrome = Modifier.testTag("bottom-nav").traversalGroup().fillMaxWidth()
            if (fit.logAbove) {
                Column(chrome, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NavGroupGap)) {
                    LogButton(onLog, logLabel)
                    NavPill(selected, onSelect, labels, textPx, fit)
                }
            } else {
                Row(
                    chrome,
                    horizontalArrangement = Arrangement.spacedBy(NavGroupGap, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavPill(selected, onSelect, labels, textPx, fit)
                    LogButton(onLog, logLabel)
                }
            }
        }
    }
}

/** The pill of the three tabs: one pick-one group, each tab a Role.Tab named by its
 *  visible label (the icon's description is null, so TalkBack reads the tab once).
 *  A tab is as wide as what it shows (unselected its label, selected its icon and
 *  label), so the tabs shift inside the pill when the selection moves, as Photos'
 *  do, over [MotionMedium]. The pill is as wide as its widest selection ([textPx],
 *  see [pillLabelsWidth]) on every destination, its tabs centered in it, so neither
 *  the pill nor the Log button beside it moves. */
@Composable
private fun NavPill(selected: Dest, onSelect: (Dest) -> Unit, labels: List<String>, textPx: Int, fit: NavBarFit) {
    val density = LocalDensity.current
    // In whole pixels, as the tabs' padding, icon and gap are laid out, so the
    // labels get exactly their measured width.
    val widthPx = with(density) {
        textPx + NavPillInset.roundToPx() * 2 + fit.tabPadding.roundToPx() * 2 * labels.size +
            if (fit.showIcon) NavIconSize.roundToPx() + NavIconGap.roundToPx() else 0
    }
    Row(
        Modifier
            .testTag("nav-pill")
            .width(with(density) { widthPx.toDp() })
            .shadow(FloatingShadow, PillShape)
            .background(BgElevated, PillShape)
            .border(1.dp, OnBgBody.copy(alpha = HairlineAlpha.Control), PillShape)
            .padding(NavPillInset)
            .selectableGroup(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dest.entries.forEachIndexed { i, dest ->
            val active = dest == selected
            val fill by animateColorAsState(if (active) Accent else Accent.copy(alpha = 0f), tween(MotionMedium))
            val ink by animateColorAsState(if (active) OnAccent else OnBgFaint, tween(MotionMedium))
            Row(
                modifier = Modifier
                    .heightIn(min = ActionMinHeight)
                    .clip(PillShape)
                    .background(fill)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(dest) })
                    .animateContentSize(tween(MotionMedium), alignment = Alignment.Center)
                    .padding(horizontal = fit.tabPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NavIconGap),
            ) {
                if (active && fit.showIcon) {
                    Icon(painterResource(dest.iconSelected), contentDescription = null, tint = OnAccent, modifier = Modifier.size(NavIconSize))
                }
                Text(
                    labels[i],
                    color = ink,
                    style = if (active) Type.Chip else Type.ChipRegular,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * The bar's Log button: a 56dp `Accent` disc with the "+", as Photos' round
 * button carries only its icon. A button named "Log" ([label]).
 */
@Composable
private fun LogButton(onClick: () -> Unit, label: String) {
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
        Box(Modifier.size(LargeControlHeight), contentAlignment = Alignment.Center) {
            Icon(painterResource(Res.drawable.ic_action_log_plus), contentDescription = null, modifier = Modifier.size(LogIconSize))
        }
    }
}

/**
 * Side navigation rail: the [FloatingNavBar]'s three destinations (each with its
 * icon and label, Material's rail indicator) down the start edge, with Log
 * ([onLog], on every destination, as in the bar) at its top. Used instead of the
 * floating bar on a wide or a short window, and whenever the bar would be laid over
 * the pinned safety banner (a portrait phone at a large font under a hold): beside
 * the banner, never over it. With [showBrand] (a short window, where the wordmark
 * bar is left out) the route mark tops the rail.
 */
@Composable
fun MilestoneNavRail(selected: Dest, onSelect: (Dest) -> Unit, onLog: () -> Unit, showBrand: Boolean = false) {
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
        FloatingActionButton(
            onClick = onLog,
            modifier = Modifier.padding(bottom = Space.Md.dp).testTag("log-fab").semantics { contentDescription = logLabel },
            containerColor = Accent,
            contentColor = OnAccent,
            shape = RoundedCornerShape(Space.Card.dp),
        ) {
            Icon(painterResource(Res.drawable.ic_action_log_plus), contentDescription = null, modifier = Modifier.size(LogIconSize))
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
