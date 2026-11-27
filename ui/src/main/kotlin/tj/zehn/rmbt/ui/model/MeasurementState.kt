package tj.zehn.rmbt.ui.model

import tj.zehn.rmbt.TestPhase

/** Step of a running test as the copied open-rmbt-android views know it. */
internal enum class MeasurementState {
    IDLE,
    INIT,
    PING,
    DOWNLOAD,
    UPLOAD,
    QOS,
    FINISH,
    ERROR,
    ABORTED,
    JITTER_AND_PACKET_LOSS;

    companion object {
        /** The SDK's pretests belong to the step they prepare; submitting the result ends the upload step. */
        fun of(phase: TestPhase): MeasurementState = when (phase) {
            TestPhase.INIT, TestPhase.DOWN_PRETEST -> INIT
            TestPhase.PING -> PING
            TestPhase.DOWNLOAD -> DOWNLOAD
            TestPhase.UP_PRETEST, TestPhase.UPLOAD, TestPhase.SUBMIT -> UPLOAD
        }
    }
}
