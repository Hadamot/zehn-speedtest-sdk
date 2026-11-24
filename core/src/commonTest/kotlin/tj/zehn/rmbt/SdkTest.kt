package tj.zehn.rmbt

import kotlin.test.Test
import kotlin.test.assertEquals

class SdkTest {
    @Test
    fun exposesVersion() {
        assertEquals("0.1.0", Sdk.VERSION)
    }
}
