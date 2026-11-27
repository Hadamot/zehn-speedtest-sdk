package tj.zehn.rmbt.protocol

open class RmbtException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ConnectionClosedException : RmbtException("connection closed by server")

class UnexpectedMessageException(expected: String, actual: Any) :
    RmbtException("expected $expected, got $actual")

/** A line from the measurement server that is not RMBT (unknown keyword or a number that isn't one). */
class MalformedMessageException(line: String) : RmbtException("malformed server line: $line")
