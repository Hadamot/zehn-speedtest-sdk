package tj.zehn.rmbt.run

import tj.zehn.rmbt.measure.PingSample
import tj.zehn.rmbt.measure.Rate
import tj.zehn.rmbt.measure.Sample

data class Measurement(
    val threads: Int,
    val download: Rate,
    val upload: Rate,
    val downloadSamples: List<List<Sample>>,
    val uploadSamples: List<List<Sample>>,
    val pings: List<PingSample>,
)
