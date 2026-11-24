package tj.zehn.rmbt.measure

import kotlin.test.Test
import kotlin.test.assertEquals

class PingSampleTest {
    private fun pings(vararg server: Long) = server.map { PingSample(0, it, 0) }

    @Test
    fun medianOfOddCountIsMiddleValue() {
        assertEquals(20, pings(30, 10, 20).medianServerNanos())
    }

    @Test
    fun medianOfEvenCountAveragesMiddleValues() {
        assertEquals(25, pings(40, 10, 20, 30).medianServerNanos())
    }
}
