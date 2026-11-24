package tj.zehn.rmbt.transport

import io.ktor.network.tls.TLSConfigBuilder

internal actual fun TLSConfigBuilder.acceptAnyCertificate() = Unit
