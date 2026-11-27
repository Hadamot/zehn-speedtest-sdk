package tj.zehn.rmbt.ui.model

/**
 * Rating of a measured value, as in open-rmbt-android. The app gets it from the server; the SDK
 * rates locally with the thresholds the app uses for the live icons (DataBindingAdapters.kt).
 */
internal enum class Classification(val intValue: Int) {
    NONE(0),
    BAD(1),
    NORMAL(2),
    GOOD(3),
    EXCELLENT(4);

    companion object {
        /** Download: below 5 Mbit/s bad, 10 normal, 100 good, otherwise excellent. */
        fun ofDownload(mbps: Double): Classification = ofSpeed(mbps, 5.0, 10.0, 100.0)

        /** Upload: below 2.5 Mbit/s bad, 5 normal, 50 good, otherwise excellent. */
        fun ofUpload(mbps: Double): Classification = ofSpeed(mbps, 2.5, 5.0, 50.0)

        /** Ping: 75 ms and more bad, from 25 normal, from 10 good, below 10 excellent. */
        fun ofPing(ms: Double): Classification = when {
            ms <= 0.0 -> NONE
            ms >= 75.0 -> BAD
            ms >= 25.0 -> NORMAL
            ms >= 10.0 -> GOOD
            else -> EXCELLENT
        }

        private fun ofSpeed(mbps: Double, normal: Double, good: Double, excellent: Double) = when {
            mbps <= 0.0 -> NONE
            mbps < normal -> BAD
            mbps < good -> NORMAL
            mbps < excellent -> GOOD
            else -> EXCELLENT
        }
    }
}
