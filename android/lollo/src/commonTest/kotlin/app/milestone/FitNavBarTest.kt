package app.milestone

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The floating bar's sizing (chrome §1). [pillLabelsWidth]: the pill holds its
 * widest selection, so its width is the same on every destination. [fitNavBar]: as
 * the room shrinks, the bar goes from the pill beside Log with the selected icon,
 * to the same without the icon, to Log above the pill, and never back; beside Log
 * a tab keeps at least `Space.Sm` padding, and no step pads a tab more than
 * `Space.Screen`.
 */
class FitNavBarTest {
    private val text = 134.dp

    private fun step(fit: NavBarFit) = when {
        !fit.logAbove && fit.showIcon -> 0
        !fit.logAbove -> 1
        else -> 2
    }

    @Test
    fun pillHoldsItsWidestSelection() {
        // Selecting the first tab adds 2, the second 4, the third 3 to the regular 129.
        assertEquals(133, pillLabelsWidth(bold = listOf(40, 50, 48), regular = listOf(38, 46, 45)))
        assertEquals(129, pillLabelsWidth(bold = listOf(38, 46, 45), regular = listOf(38, 46, 45)))
    }

    @Test
    fun shrinkingRoomStepsDownInOrder() {
        val fits = (600 downTo 100).map { fitNavBar(text, 3, it.dp) }
        assertEquals(listOf(0, 1, 2), fits.map(::step).distinct(), "every step is taken, in order")
        assertEquals(Space.Screen.dp, fits.first().tabPadding, "a wide bar pads its tabs fully")
        for (fit in fits) {
            assertTrue(fit.tabPadding >= 0.dp && fit.tabPadding <= Space.Screen.dp, "padding $fit within 0..Space.Screen")
            if (!fit.logAbove) assertTrue(fit.tabPadding >= Space.Sm.dp, "beside Log a tab keeps Space.Sm: $fit")
            if (fit.logAbove) assertTrue(!fit.showIcon, "no icon with Log above: $fit")
        }
        assertEquals(NavBarFit(0.dp, showIcon = false, logAbove = true), fits.last(), "no room left: Log above, no padding")
    }

    /** Only the selected tab shows the icon and no other tab keeps room for it, so
     *  the icon costs the pill one icon and its gap, not one per tab. */
    @Test
    fun theIconCostsOneIconOfRoom() {
        fun narrowest(step: Int): Dp =
            (0..2400).map { 100.dp + (it / 4f).dp }.first { step(fitNavBar(text, 3, it)) <= step }
        val icon = narrowest(0) - narrowest(1)
        assertEquals((NavIconSize + NavIconGap).value, icon.value, 0.5f, "room the icon needs beside Log")
    }

    @Test
    fun widerLabelsStepDownSooner() {
        val room = 330.dp
        assertTrue(step(fitNavBar(text, 3, room)) <= step(fitNavBar(text + 60.dp, 3, room)))
        assertTrue(step(fitNavBar(text + 60.dp, 3, room)) <= step(fitNavBar(text * 2, 3, room)))
    }
}
