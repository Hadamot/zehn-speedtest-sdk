package tj.zehn.rmbt.ui.model

/** A finished test with plausible values; override what the test is about. */
internal fun testRecord(
    testUuid: String = "test-1",
    downloadMbps: Double = 45.2,
    uploadMbps: Double = 12.1,
    pingMs: Double = 18.0,
    timeMillis: Long = 1_764_050_400_000L,
    network: NetworkKind = NetworkKind.WIFI,
    networkName: String? = "Home",
    signalLevel: Int? = 3,
    openTestUuid: String? = "open-1",
    location: GeoPoint? = null,
) = TestRecord(
    testUuid = testUuid,
    openTestUuid = openTestUuid,
    timeMillis = timeMillis,
    timezone = "Asia/Dushanbe",
    downloadMbps = downloadMbps,
    uploadMbps = uploadMbps,
    pingMs = pingMs,
    serverName = "Dushanbe",
    network = network,
    networkName = networkName,
    signalLevel = signalLevel,
    location = location,
)
