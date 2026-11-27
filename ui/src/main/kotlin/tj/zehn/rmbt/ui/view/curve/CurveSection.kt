package tj.zehn.rmbt.ui.view.curve

import tj.zehn.rmbt.ui.model.MeasurementState

/**
 * Helper class describes the section of measurement curve. It contains data about count of squares, label, start & end angles and length of section curve
 */
internal class CurveSection(
    val count: Int,
    val text: String,
    val isUpside: Boolean,
    val state: MeasurementState = MeasurementState.IDLE,
    var startAngle: Float = 0f,
    var endAngle: Float = 0f,
    var length: Float = 0f
)