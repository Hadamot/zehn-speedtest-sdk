package tj.zehn.rmbt

sealed interface RmbtEvent {
    data class Phase(val phase: TestPhase) : RmbtEvent
    data class Progress(val phase: TestPhase, val fraction: Double, val mbps: Double) : RmbtEvent
    data class Finished(val result: RmbtResult) : RmbtEvent
    data class Failed(val error: Throwable) : RmbtEvent
}
