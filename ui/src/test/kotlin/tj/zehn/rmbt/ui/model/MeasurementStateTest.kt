package tj.zehn.rmbt.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test
import tj.zehn.rmbt.TestPhase

class MeasurementStateTest {

    @Test
    fun `pretests belong to the step they prepare and submit ends the upload`() {
        val expected = mapOf(
            TestPhase.INIT to MeasurementState.INIT,
            TestPhase.DOWN_PRETEST to MeasurementState.INIT,
            TestPhase.PING to MeasurementState.PING,
            TestPhase.DOWNLOAD to MeasurementState.DOWNLOAD,
            TestPhase.UP_PRETEST to MeasurementState.UPLOAD,
            TestPhase.UPLOAD to MeasurementState.UPLOAD,
            TestPhase.SUBMIT to MeasurementState.UPLOAD,
        )
        assertEquals("every SDK phase is mapped", TestPhase.entries.toSet(), expected.keys)
        expected.forEach { (phase, state) -> assertEquals(phase.name, state, MeasurementState.of(phase)) }
    }
}
