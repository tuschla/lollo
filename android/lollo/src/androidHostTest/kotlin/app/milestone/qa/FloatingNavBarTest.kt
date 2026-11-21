package app.milestone.qa

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.LocationManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import app.milestone.AppSettings
import app.milestone.EventDispatcher
import app.milestone.NavIconSize
import app.milestone.R
import app.milestone.RunSession
import app.milestone.Space
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The floating navigation bar (chrome §1) over the REAL app and core, on the
 * smallest phone CI's emulator has (320×640dp) unless a test says otherwise: the
 * bar floats over the foot of each destination, so each list's last row must still
 * scroll fully clear of it (and of the run chip above it); the bar stands 16dp
 * above the system navigation bar whatever its height (gesture handle or 3-button
 * bar); the pill and the Log button are one centered group that keeps its place
 * on every destination; and Log logs from every destination.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h640dp-xhdpi")
class FloatingNavBarTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        QaHarness.reset(ctx)
        shadowOf(ctx as Application).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        shadowOf(ctx.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        AppSettings.setShowRouteMaps(ctx, false)
    }

    @After
    fun tearDown() {
        QaHarness.teardown()
        RunSession.reset()
        QaHarness.clearSidecar(ctx)
        QaHarness.freshProcess()
    }

    private fun dp(px: Float) = with(rule.density) { px.toDp().value }

    /** The destination's list scrolled to its end. */
    private fun scrollListToEnd() {
        repeat(8) {
            rule.onAllNodes(hasScrollToIndexAction())[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
            rule.waitForIdle()
        }
    }

    /** The bottom of the list's last row, scrolled to the end. */
    private fun lastRowBottom(): Float {
        scrollListToEnd()
        val list = rule.onAllNodes(hasScrollToIndexAction())[0].fetchSemanticsNode()
        // A lazy list keeps items it no longer shows in its semantics (unplaced).
        return list.children.filter { it.layoutInfo.isPlaced }.map { it.boundsInRoot }.filter { it.height > 0f }.maxOf { it.bottom }
    }

    private fun assertEveryLastRowClearsTheBar(font: Float) {
        RuntimeEnvironment.setFontScale(font)
        QaHarness.seedStrengthHistory(ctx)
        rule.launchApp()
        for (tab in listOf(R.string.main_dest_today, R.string.main_dest_history, R.string.main_dest_profile)) {
            rule.selectTab(str(tab))
            val last = lastRowBottom()
            val barTop = rule.floatingBarTop()
            assertTrue("[${str(tab)} @ x$font] the last row (${dp(last)}dp) clears the floating bar (${dp(barTop)}dp)", last <= barTop + 1f)
        }
    }

    @Test
    fun font100_everyListEndsAboveTheBar() = assertEveryLastRowClearsTheBar(1f)

    @Test
    fun font200_everyListEndsAboveTheBar() = assertEveryLastRowClearsTheBar(2f)

    /** While a run records, its chip stands above the bar, never on it, and Today's
     *  last row clears the chip. */
    @Test
    fun runChip_standsAboveTheBar_andTheListClearsIt() {
        QaHarness.seedLog(ctx, listOf(QaHarness.fiveKRunnerProfile()))
        QaHarness.seedRecordingRun()
        rule.launchOnTracker()
        Espresso.pressBackUnconditionally()
        rule.waitForNode(hasText(str(R.string.main_run_in_progress)))
        val chip = rule.onNode(hasText(str(R.string.main_run_in_progress))).fetchSemanticsNode().let { n ->
            generateSequence(n) { it.parent }.first { it.config.contains(SemanticsActions.OnClick) }.boundsInRoot
        }
        val bar = rule.floatingChromeBounds()
        assertTrue("the run chip $chip stands above the bar $bar", bar.all { chip.bottom <= it.top + 1f })
        val last = lastRowBottom()
        assertTrue("the last row (${dp(last)}dp) clears the run chip (${dp(chip.top)}dp)", last <= chip.top + 1f)
    }

    /** The system navigation bar at the bottom edge, [navDp] tall; a 3-button bar
     *  ([buttons]) is also a tappable element, the gesture handle is not. */
    private fun systemNavBar(navDp: Int, buttons: Boolean) {
        val px = with(rule.density) { navDp.dp.roundToPx() }
        checkNotNull(lastScenario).onActivity { activity ->
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px))
                .setInsets(WindowInsetsCompat.Type.tappableElement(), Insets.of(0, 0, 0, if (buttons) px else 0))
                .build()
            ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, insets)
        }
        rule.waitForIdle()
    }

    private fun pill(): Rect = rule.onNode(hasTestTag("nav-pill")).fetchSemanticsNode().boundsInRoot

    private fun log(): Rect = rule.onNode(logFab).fetchSemanticsNode().boundsInRoot

    /** The three tabs, in order. */
    private fun tabs(): List<Rect> =
        rule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("nav-pill"))).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun window(): Rect = rule.onAllNodes(isRoot()).fetchSemanticsNodes()[0].boundsInRoot

    /** Select [label]'s tab in the pill (History's rows may show the word "Today"). */
    private fun goTo(label: Int) {
        rule.onNode(hasText(str(label)) and hasClickAction() and hasAnyAncestor(hasTestTag("nav-pill"))).performClick()
        rule.waitForIdle()
    }

    /** Gesture handle (24dp) and 3-button bar (48dp): the pill and the Log button
     *  end 16dp above the system bar, and the tabs keep working. */
    @Test
    fun barStandsAboveGestureAndButtonNavigation() {
        QaHarness.seedStrengthHistory(ctx)
        rule.launchApp()
        val window = window()
        for ((navDp, buttons) in listOf(24 to false, 48 to true)) {
            systemNavBar(navDp, buttons)
            val gap = dp(window.bottom - pill().bottom)
            assertEquals("pill above a ${navDp}dp ${if (buttons) "3-button" else "gesture"} bar (dp)", navDp + 16f, gap, 1f)
            val log = log()
            assertEquals("the Log button $log stands beside the pill ${pill()} (dp)", 0f, dp(log.center.y - pill().center.y), 1f)
            goTo(R.string.main_dest_history)
            assertEquals("the Log button stays on History, in its place", log, log())
            goTo(R.string.main_dest_today)
        }
        assertEquals("one tab per destination", 3, tabs().size)
    }

    /**
     * The pill and the Log button are one group centered in the window (as in Google
     * Photos), and neither the pill nor Log moves when another destination is
     * selected: the pill is as wide as its widest selection, and Log shows on every
     * destination. The tabs inside the pill may move: the owner wants Photos' layout
     * (2026-11-21), where each tab is as wide as what it shows, so the selected tab
     * grows by its icon and the others shift. With [photosMargins] (a 1080px phone at
     * 100% font) the group leaves Photos' margins, not the bar's 16dp floor: on
     * Photos' 1080px screenshot 121px a side, 46dp. In RTL the order mirrors: Log
     * stands to the pill's left.
     */
    private fun assertCenteredAndStill(where: String, font: Float = 1f, photosMargins: Boolean = false) {
        RuntimeEnvironment.setFontScale(font)
        QaHarness.seedStrengthHistory(ctx)
        rule.launchApp()
        val window = window()
        val rtl = ctx.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
        var first: List<Rect>? = null
        val selectedWidth = FloatArray(3)
        val unselectedWidth = FloatArray(3) { Float.MAX_VALUE }
        val dests = listOf(R.string.main_dest_today, R.string.main_dest_history, R.string.main_dest_profile, R.string.main_dest_today)
        for ((d, tab) in dests.withIndex()) {
            goTo(tab)
            val pill = pill()
            val log = log()
            val now = listOf(pill, log)
            val start = first ?: now.also { first = it }
            now.zip(start).forEachIndexed { i, (a, b) ->
                val moved = maxOf(abs(a.left - b.left), abs(a.top - b.top), abs(a.right - b.right), abs(a.bottom - b.bottom))
                assertTrue("[$where] ${if (i == 0) "the pill" else "Log"} on ${str(tab)} at $a, on Today at $b", moved <= 0.5f)
            }
            val tabs = tabs()
            assertEquals("[$where] three tabs on ${str(tab)}", 3, tabs.size)
            for ((i, t) in tabs.withIndex()) {
                assertTrue("[$where] tab $i $t inside the pill $pill on ${str(tab)}", t.left >= pill.left && t.right <= pill.right)
                if (i == d % 3) selectedWidth[i] = t.width else unselectedWidth[i] = minOf(unselectedWidth[i], t.width)
            }
            tabs.sortedBy { it.left }.zipWithNext { a, b -> assertTrue("[$where] tabs $a and $b overlap on ${str(tab)}", a.right <= b.left + 0.5f) }
            val left = dp(minOf(pill.left, log.left) - window.left)
            val right = dp(window.right - maxOf(pill.right, log.right))
            assertEquals("[$where] the group is centered: ${left}dp left, ${right}dp right", left, right, 1f)
            if (photosMargins) assertTrue("[$where] the group leaves ${left}dp a side, Photos' 46dp, not the 16dp floor", left >= 32f)
            assertTrue("[$where] the Log button $log does not overlap the pill $pill", log.intersect(pill).let { it.width <= 0f || it.height <= 0f })
            if (abs(log.center.y - pill.center.y) < 1f) {
                assertTrue("[$where] Log after the pill in reading order (rtl=$rtl): $log, $pill", if (rtl) log.right <= pill.left else log.left >= pill.right)
                assertEquals("[$where] the gap between pill and Log (dp)", Space.Md.toFloat(), dp(if (rtl) pill.left - log.right else log.left - pill.right), 1f)
            } else {
                assertEquals("[$where] Log centered above the pill (dp)", 0f, dp(log.center.x - pill.center.x), 1f)
            }
        }
        if (photosMargins) {
            for (i in 0..2) {
                val grows = dp(selectedWidth[i] - unselectedWidth[i])
                assertTrue("[$where] unselected, tab $i keeps no room for the icon: ${grows}dp wider selected", grows >= NavIconSize.value)
            }
        }
    }

    /** 320dp, the smallest phone CI's emulator has. */
    @Test
    fun narrowPhone_groupCenteredAndStill() = assertCenteredAndStill("320dp")

    @Test
    fun narrowPhoneFont200_groupCenteredAndStill() = assertCenteredAndStill("320dp, font 200%", font = 2f)

    /** A 1080px phone (411dp at 420dpi). */
    @Test
    @Config(qualifiers = "w411dp-h914dp-420dpi")
    fun phone1080px_groupCenteredAndStill() = assertCenteredAndStill("1080px", photosMargins = true)

    @Test
    @Config(qualifiers = "w411dp-h914dp-420dpi")
    fun phone1080pxFont200_groupCenteredAndStill() = assertCenteredAndStill("1080px, font 200%", font = 2f)

    @Test
    @Config(qualifiers = "ar-rEG-ldrtl-w411dp-h914dp-420dpi")
    fun rtl_groupCenteredAndStill_orderMirrored() = assertCenteredAndStill("RTL 1080px")

    /** From History: Log opens the chooser, today's planned set logs from it, and
     *  "Done for now" closes the sheet onto History, which lists the set; Today,
     *  left scrolled down, is back at its top on the next visit. */
    @Test
    fun logFromHistory_theSetShowsInHistory_andTodayReturnsToItsTop() {
        QaHarness.seedLog(ctx, listOf(QaHarness.strengthProfile()))
        rule.launchApp()
        val todayTop = hasText(str(R.string.main_todays_session).uppercase(java.util.Locale.US))
        rule.waitForNode(todayTop)
        // The hero's overline lies inside the list's viewport: Today is at its top.
        fun atTop(): Boolean {
            val list = rule.onAllNodes(hasScrollToIndexAction())[0].fetchSemanticsNode().boundsInRoot
            return rule.onAllNodes(todayTop).fetchSemanticsNodes().any { it.boundsInRoot.top >= list.top - 1f && it.boundsInRoot.bottom <= list.bottom }
        }
        assertTrue("Today opens at its top", atTop())
        rule.scrollTodayToEnd()
        assertTrue("precondition: Today scrolled past its first card", !atTop())
        goTo(R.string.main_dest_history)
        val session = EventDispatcher.model.value!!.next_session!!
        val exercise = session.items.first { it.exercise.isNotEmpty() && !it.done }.exercise
        assertEquals("nothing logged yet", 0, rule.countOf(hasText(exercise, substring = true)))
        rule.openLogChooser()
        rule.onNodeWithText(str(R.string.main_todays_plan, session.title)).scrollClearOfChrome().performClick()
        rule.waitForNode(hasTestTag("editor-primary"))
        // A tap at the centre of this weight field (it carries hint lines) leaves the
        // keypad shut here; the field's click action opens it.
        rule.onAllNodes(hasClickAction() and hasContentDescription(str(R.string.log_field_weight), substring = true))[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.typeWeight("60")
        rule.tapEditorPrimary()
        rule.waitForState { EventDispatcher.model.value!!.lifts.any { it.exercise == exercise } }
        rule.onNode(hasText(str(R.string.log_set_done)) and hasClickAction()).scrollClearOfChrome().performClick()
        rule.waitForIdle()
        assertEquals("the sheet closed", 0, rule.countOf(hasTestTag("editor-primary")))
        rule.onNode(hasText(str(R.string.main_dest_history)) and isSelected()).assertExists()
        rule.waitForNode(hasText(exercise, substring = true))
        goTo(R.string.main_dest_today)
        rule.waitUntil(5_000) { atTop() }
    }

    /** From Profile: a run logged by hand through Log saves, Profile stays, and the
     *  confirmation's "View" opens the run in History. */
    @Test
    fun logFromProfile_theRunOpensFromItsConfirmation() {
        QaHarness.seedLog(ctx, listOf(QaHarness.hybridFiveKProfile()))
        rule.launchApp()
        goTo(R.string.main_dest_profile)
        rule.openLogChooser()
        rule.onNode(hasText(str(R.string.main_log_run_title)) and hasClickAction()).scrollClearOfChrome().performClick()
        rule.waitForNode(hasTestTag("numeric-keypad"))
        fun key(k: String) = rule.onNode(hasText(k) and hasAnyAncestor(hasTestTag("numeric-keypad"))).scrollClearOfChrome().performClick()
        key("5")
        rule.onNode(hasContentDescription(str(R.string.log_field_duration))).scrollClearOfChrome().performClick()
        listOf("3", "0", "0", "0").forEach(::key)
        rule.tapEditorPrimary()
        rule.waitForState { EventDispatcher.model.value!!.runs.size == 1 }
        rule.waitForNode(hasText(str(R.string.log_run_saved)))
        rule.onNode(hasText(str(R.string.main_dest_profile)) and isSelected()).assertExists()
        rule.onNode(hasText(str(R.string.import_view)) and hasClickAction()).performClick()
        rule.waitForNode(hasTestTag("run-delete"))
        rule.onNode(hasText(str(R.string.main_dest_history)) and isSelected()).assertExists()
    }
}
