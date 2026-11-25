package app.hissa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoFillTest {
    private val ms = listOf("a", "b", "c", "d")
    private fun st(mode: Mode, vararg picked: String) = SplitState(picked.toSet()).also { it.mode = mode }

    @Test fun twoPeopleFillTheDifference() {
        val s = st(Mode.EXACT, "a", "b")
        s.edit("a", "20", ms, 10000)
        assertEquals("80", s.vals["b"])
        s.edit("b", "40", ms, 10000)
        assertEquals("60", s.vals["a"])
    }

    @Test fun threePeopleFillOnlyTheLastOnceOthersAreEntered() {
        val s = st(Mode.EXACT, "a", "b", "c")
        s.edit("a", "20", ms, 10000)
        assertNull(s.vals["c"]) // b still empty, nothing to fill yet
        s.edit("b", "30", ms, 10000)
        assertEquals("50", s.vals["c"])
    }

    @Test fun percentUsesOneHundred() {
        val s = st(Mode.PERCENT, "a", "b")
        s.edit("a", "33.33", ms, 5000)
        assertEquals("66.67", s.vals["b"])
    }

    @Test fun overshootClampsToZero() {
        val s = st(Mode.EXACT, "a", "b")
        s.edit("a", "150", ms, 10000)
        assertEquals("0", s.vals["b"])
    }

    @Test fun paiseAreKept() {
        val s = st(Mode.EXACT, "a", "b")
        s.edit("a", "33.33", ms, 10000)
        assertEquals("66.67", s.vals["b"])
    }

    @Test fun sharesAndEqualAreUntouched() {
        val s = st(Mode.SHARES, "a", "b")
        s.edit("a", "2", ms, 10000)
        assertNull(s.vals["b"])
        val e = st(Mode.EQUAL, "a", "b")
        e.edit("a", "2", ms, 10000)
        assertNull(e.vals["b"])
    }

    @Test fun singlePersonOrUnknownTotalDoesNothing() {
        val one = st(Mode.EXACT, "a")
        one.edit("a", "20", ms, 10000)
        assertEquals(setOf("a"), one.vals.keys)
        val none = st(Mode.EXACT, "a", "b")
        none.edit("a", "20", ms, null)
        assertNull(none.vals["b"])
    }

    @Test fun clearingAFieldDoesNotFillGarbage() {
        val s = st(Mode.EXACT, "a", "b")
        s.edit("a", "", ms, 10000)
        assertNull(s.vals["b"])
    }
}
