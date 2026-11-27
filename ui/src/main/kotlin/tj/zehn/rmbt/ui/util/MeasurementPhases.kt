package tj.zehn.rmbt.ui.util

import androidx.annotation.StringRes
import tj.zehn.rmbt.ui.R
import tj.zehn.rmbt.ui.model.MeasurementState

/**
 * The steps of a running test as the user sees them ("Step 3 of 4 · Download") with a short
 * explanation of what is being measured, so the test is not a black box.
 */
internal object MeasurementPhases {

    data class Phase(val step: Int, val total: Int, @StringRes val title: Int, @StringRes val hint: Int)

    private const val SPEED_STEPS = 4

    /** `null` for states that are not a step of the test (finished, aborted, error). */
    fun of(state: MeasurementState, qosEnabled: Boolean): Phase? {
        val total = if (qosEnabled) SPEED_STEPS + 1 else SPEED_STEPS
        return when (state) {
            MeasurementState.IDLE, MeasurementState.INIT ->
                Phase(1, total, R.string.rmbt_measurement_phase_init, R.string.rmbt_measurement_phase_init_hint)
            MeasurementState.PING, MeasurementState.JITTER_AND_PACKET_LOSS ->
                Phase(2, total, R.string.rmbt_measurement_ping, R.string.rmbt_measurement_phase_ping_hint)
            MeasurementState.DOWNLOAD ->
                Phase(3, total, R.string.rmbt_measurement_download, R.string.rmbt_measurement_phase_download_hint)
            MeasurementState.UPLOAD ->
                Phase(4, total, R.string.rmbt_measurement_upload, R.string.rmbt_measurement_phase_upload_hint)
            MeasurementState.QOS ->
                Phase(SPEED_STEPS + 1, SPEED_STEPS + 1, R.string.rmbt_measurement_qos, R.string.rmbt_measurement_phase_qos_hint)
            MeasurementState.FINISH, MeasurementState.ERROR, MeasurementState.ABORTED -> null
        }
    }

    /**
     * The value the test has just finished measuring when it moves from [previous] to [current]:
     * the ping is final once the download starts, the download once the upload starts, and so on.
     */
    fun finishedValue(previous: MeasurementState?, current: MeasurementState): MeasurementState? = when {
        previous in setOf(MeasurementState.PING, MeasurementState.JITTER_AND_PACKET_LOSS) &&
            current == MeasurementState.DOWNLOAD -> MeasurementState.PING
        previous == MeasurementState.DOWNLOAD && current == MeasurementState.UPLOAD -> MeasurementState.DOWNLOAD
        previous == MeasurementState.UPLOAD &&
            current in setOf(MeasurementState.QOS, MeasurementState.FINISH) -> MeasurementState.UPLOAD
        else -> null
    }
}
