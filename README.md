# zehn-speedtest-sdk

Kotlin Multiplatform SDK for RMBT speed tests against an Open-RMBT backend
(Control Server + `rmbtd` measurement server).

One API on every platform: `RmbtClient(config)` → events (phase, progress) → `RmbtResult`.

| Target | Artifact | Transport |
|---|---|---|
| Android | AAR | TCP / TLS, WebSocket |
| iOS | `RmbtSdk` framework | TCP / TLS, WebSocket |
| JVM (backend, CLI probes) | JAR | TCP / TLS, WebSocket |
| Browser / Node | npm module + `.d.ts` | WebSocket only |

## Flow

1. `POST /settings` registers the client (skipped when `clientUuid` is set).
2. `POST /testRequest` returns token, measurement server, threads, duration, pings.
3. RMBT on n parallel connections: handshake, download pretest, ping, download,
   upload pretest, upload.
4. `POST /result` submits speeds, pings and per-thread speed details.

Speed follows the RMBT spec: bytes of all threads at t* (earliest thread finish,
others interpolated) divided by t*.

## Usage

### Android / JVM (Kotlin)

```kotlin
val client = RmbtClient(
    RmbtConfig(
        controlUrl = "https://speed.example.tj/RMBTControlServer",
        apiKey = "android-app-key",
    )
)

scope.launch {
    client.start().collect { event ->
        when (event) {
            is RmbtEvent.Phase -> showPhase(event.phase)
            is RmbtEvent.Progress -> gauge.set(event.mbps)
            is RmbtEvent.Finished -> showResult(event.result)
            is RmbtEvent.Failed -> showError(event.error)
        }
    }
}

val result = client.run()
client.cancel()
```

Persist `result.clientUuid` and pass it back as `RmbtConfig.clientUuid` to keep
one client identity across tests. Android apps should pass the real
`networkType` (RMBT codes: 99 WLAN, 98 LAN, mobile codes 1-42).

### iOS (Swift)

```swift
import RmbtSdk

let client = RmbtClient(config: RmbtConfig(controlUrl: "https://speed.example.tj/RMBTControlServer", ...))
let result = try await client.run()
```

Without SKIE, Swift sees no Kotlin default arguments (all `RmbtConfig` fields must be
passed) and the `Flow` from `start()` has no `for await`; adding SKIE fixes both.

### Web (TypeScript)

```ts
import { tj } from "zehn-speedtest-sdk-core";

const client = new tj.zehn.rmbt.web.RmbtWebClient(
  new tj.zehn.rmbt.web.RmbtWebOptions("https://speed.example.tj/RMBTControlServer"),
);

const result = await client.start(
  (phase) => console.log(phase),
  (phase, fraction, mbps) => gauge.set(mbps),
);
```

The measurement server must run with `-w` (WebSocket mode).

## Build and test

```bash
./gradlew :core:jvmTest :core:jsNodeTest
./gradlew :core:bundleAndroidMainAar
./gradlew :core:jsBrowserProductionLibraryDistribution
./gradlew :core:linkReleaseFrameworkIosArm64
```

Requires JDK 17+ and an Android SDK (`local.properties` → `sdk.dir`).
iOS frameworks link only on macOS; on Linux iOS sources compile to klibs.

Tests against a live backend (local-speedtest compose) are skipped unless env is set:

```bash
RMBT_IT_CONTROL=http://localhost:18080/RMBTControlServer RMBT_IT_HOST=localhost \
RMBT_IT_TRANSPORT=TCP ./gradlew :core:jvmTest --tests '*EndToEndTest*' --rerun
```

## Known limitations

- WebSocket chunks are capped at 32 KiB: the C `rmbtd` builds WebSocket frames on
  the thread stack and segfaults with large chunks under musl.
- TLS accepts any certificate on JVM/Android (measurement servers use self-signed
  certs); iOS uses system trust.
- QoS tests (DNS, ports, traceroute, VoIP) and signal/location collection are not
  implemented.
