package app.milestone.qa

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.LocationManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import app.milestone.AppSettings
import app.milestone.R
import app.milestone.RunSession
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
 * The floating navigation bar (chrome §1) over the REAL app and core on the
 * smallest phone CI's emulator has (320×640dp): the bar floats over the foot of
 * each destination, so each list's last row must still scroll fully clear of it
 * (and of the run chip above it); and the bar stands 16dp above the system
 * navigation bar whatever its height (gesture handle or 3-button bar).
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

    /** Gesture handle (24dp) and 3-button bar (48dp): the pill and the Log button
     *  end 16dp above the system bar, and the tabs keep working. */
    @Test
    fun barStandsAboveGestureAndButtonNavigation() {
        QaHarness.seedStrengthHistory(ctx)
        rule.launchApp()
        val window = rule.onAllNodes(isRoot()).fetchSemanticsNodes()[0].boundsInRoot
        for ((navDp, buttons) in listOf(24 to false, 48 to true)) {
            systemNavBar(navDp, buttons)
            val gap = dp(window.bottom - pill().bottom)
            assertEquals("pill above a ${navDp}dp ${if (buttons) "3-button" else "gesture"} bar (dp)", navDp + 16f, gap, 1f)
            val log = rule.onNode(logFab).fetchSemanticsNode().boundsInRoot
            assertEquals("the Log button $log stands beside the pill ${pill()} (dp)", 0f, dp(log.center.y - pill().center.y), 1f)
            rule.selectTab(str(R.string.main_dest_history))
            assertEquals("no Log button on History", 0, rule.countOf(logFab))
            rule.selectTab(str(R.string.main_dest_today))
        }
        assertEquals("one tab per destination", 3, rule.countOf(hasClickAction() and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("nav-pill"))))
    }
}
