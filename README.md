# zehn-speedtest-sdk

Kotlin Multiplatform SDK for internet speed tests (download, upload, ping) against an
[Open-RMBT](https://github.com/rtr-nettest) backend. One API for Android, iOS, Web and JVM.

```
app ──► RmbtClient ──HTTP──► Control Server   (/settings, /testRequest, /result)
                   └─TCP/TLS/WebSocket──► rmbtd measurement server (RMBT protocol)
```

| Platform | Package | Transport |
|---|---|---|
| Android (minSdk 24) | Maven `tj.zehn.rmbt:rmbt-sdk` | TCP, TLS, WebSocket |
| JVM 17+ (backend, probes, CLI) | Maven `tj.zehn.rmbt:rmbt-sdk` | TCP, TLS, WebSocket |
| iOS 15+ | Swift Package `RmbtSdk` | TCP, TLS, WebSocket |
| Browser, Node | npm `@hadamot/speedtest-sdk` | WebSocket |

Current version: **0.1.0**.

---

## 1. Access to packages

The repository and its packages are private. Every consumer needs a GitHub token.

1. GitHub → Settings → Developer settings → Personal access tokens → **Tokens (classic)**.
2. Create a token with scopes `read:packages` (and `repo` for iOS release downloads).
3. Your GitHub account must have read access to `Hadamot/zehn-speedtest-sdk`.

In GitHub Actions of another Hadamot repository, use `${{ secrets.GITHUB_TOKEN }}` with
`permissions: packages: read`, and grant that repository access under
Package settings → Manage Actions access.

---

## 2. Install

### Android / JVM (Gradle)

`~/.gradle/gradle.properties` (never commit the token):

```properties
gpr.user=<github-username>
gpr.token=<token>
```

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/Hadamot/zehn-speedtest-sdk")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

Module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("tj.zehn.rmbt:rmbt-sdk:0.1.0")
}
```

Gradle picks the right variant (`rmbt-sdk-android`, `rmbt-sdk-jvm`) automatically.
Android apps need `<uses-permission android:name="android.permission.INTERNET" />`, and
`android:usesCleartextTraffic="true"` (or a network security config) when the Control
Server is plain `http://`.

### Web (npm, pnpm, yarn)

`.npmrc` in the project root:

```
@hadamot:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}
```

```bash
export GITHUB_TOKEN=<token>
pnpm add @hadamot/speedtest-sdk
```

TypeScript typings are included. Browsers use the native `WebSocket`; Node uses the
bundled `ws` dependency.

### iOS (Swift Package Manager)

1. Xcode → Settings → Accounts → add your GitHub account (needed for the private
   release download).
2. File → Add Package Dependencies → `https://github.com/Hadamot/zehn-speedtest-sdk`,
   rule "Up to Next Major" from `0.1.0`, product **RmbtSdk**.

Command-line builds (`xcodebuild`, CI) read credentials from `~/.netrc`:

```
machine github.com login <github-username> password <token>
machine api.github.com login <github-username> password <token>
```

If the package download is still refused, download `RmbtSdk.xcframework.zip` from the
GitHub Release, unzip it and drag `RmbtSdk.xcframework` into the project
(Embed & Sign).

### Local development (no publishing)

```bash
./gradlew :rmbt-sdk:publishToMavenLocal                     # then use mavenLocal()
./gradlew :rmbt-sdk:jsBrowserProductionLibraryDistribution  # then:
pnpm add file:../zehn-speedtest-sdk/core/build/dist/js/productionLibrary
```

Or a Gradle composite build: `includeBuild("../zehn-speedtest-sdk")` in `settings.gradle.kts`.

---

## 3. Usage

### Android / JVM (Kotlin)

```kotlin
val client = RmbtClient(
    RmbtConfig(
        controlUrl = "https://speed.example.tj/RMBTControlServer",
        apiKey = "android-app",
        clientUuid = prefs.getString("rmbt_client_uuid", null),
        networkType = 99,
    )
)

lifecycleScope.launch {
    client.start().collect { event ->
        when (event) {
            is RmbtEvent.Phase -> phaseView.text = event.phase.name
            is RmbtEvent.Progress -> gauge.value = event.mbps
            is RmbtEvent.Finished -> {
                prefs.edit().putString("rmbt_client_uuid", event.result.clientUuid).apply()
                showResult(event.result)
            }
            is RmbtEvent.Failed -> showError(event.error)
        }
    }
}

cancelButton.setOnClickListener { client.cancel() }
```

Without progress UI (backend job, probe, CLI):

```kotlin
val result = RmbtClient(RmbtConfig(controlUrl = url, tag = "probe-dushanbe-1")).run()
println("↓ ${result.downloadMbps} ↑ ${result.uploadMbps} Mbit/s, ping ${result.pingMs} ms")
```

### iOS (Swift)

```swift
import RmbtSdk

let client = RmbtClient(
    config: RmbtConfig(controlUrl: "https://speed.example.tj/RMBTControlServer", apiKey: "ios-app")
)

Task {
    do {
        let result = try await client.run { event in
            if let progress = event as? RmbtEventProgress {
                DispatchQueue.main.async { gauge.value = progress.mbps }
            }
        }
        print("↓ \(result.downloadMbps) ↑ \(result.uploadMbps) ping \(result.pingMs)")
    } catch {
        print("failed: \(error)")
    }
}

client.cancel()
```

`RmbtClient(config:)`, `RmbtConfig(controlUrl:apiKey:)` and `run(onEvent:)` require
SDK **0.1.1+**. `RmbtConfig(controlUrl:apiKey:)` uses platform defaults. For more options use the full
initializer; Kotlin default arguments are not visible in Swift, so pass every field
(`Platform.shared` holds the defaults).

### Web (TypeScript)

```ts
import sdk from "@hadamot/speedtest-sdk";

const { RmbtWebClient, RmbtWebOptions } = sdk.tj.zehn.rmbt.web;

const client = new RmbtWebClient(
  new RmbtWebOptions(
    "https://speed.example.tj/RMBTControlServer",
    "web-site",
    localStorage.getItem("rmbtClientUuid"),
  ),
);

const result = await client.start(
  (phase) => (state.phase = phase),
  (phase, fraction, mbps) => {
    state.progress = fraction;
    state.mbps = mbps;
  },
);
localStorage.setItem("rmbtClientUuid", result.clientUuid);

stopButton.onclick = () => client.cancel();
```

Run it in the browser only (in Nuxt: `onMounted` or a `.client` component), never
during SSR. `RmbtWebOptions(controlUrl, apiKey?, clientUuid?, threads?, durationSec?, tag?, measurementHost?)`.

---

## 4. Reference

### `RmbtConfig`

| Field | Default | Meaning |
|---|---|---|
| `controlUrl` | required | Control Server base URL, e.g. `https://host/RMBTControlServer` |
| `apiKey` | `null` | Sent as `X-Api-Key` header on Control Server calls; identifies the source app at a gateway |
| `clientUuid` | `null` | Stored client id; `null` registers a new client via `/settings` |
| `transport` | JVM/Android/iOS `TCP`, JS `WEBSOCKET` | `TCP`/`TLS` or `WEBSOCKET`/`SECURE_WEBSOCKET`; encryption follows the server ticket |
| `threads` | from server | Parallel connections |
| `durationSec` | from server | Download and upload duration each |
| `tag` | `null` | Free label stored with the test (`test.tag`) |
| `networkType` | JVM/JS `98`, Android/iOS `99` | RMBT network type code, see below |
| `clientInfo` | platform | Platform, type (`MOBILE`/`DESKTOP`), language, timezone sent to the server |
| `measurementHost` | `null` | Override the measurement server host from the ticket (NAT, local dev) |

Network types: `98` LAN, `99` WLAN, `106` Ethernet, `105` cellular (unknown), mobile
codes `1`–`42` as in Android `TelephonyManager` (e.g. `13` LTE, `20` NR). `97` (CLI) is
rejected by the Control Server.

### Events

| Event | Fields |
|---|---|
| `RmbtEvent.Phase` | `phase`: `INIT`, `DOWN_PRETEST`, `PING`, `DOWNLOAD`, `UP_PRETEST`, `UPLOAD`, `SUBMIT` |
| `RmbtEvent.Progress` | `phase`, `fraction` 0..1, `mbps` (live, ~10 per second) |
| `RmbtEvent.Finished` | `result` |
| `RmbtEvent.Failed` | `error` |

### `RmbtResult`

| Field | Meaning |
|---|---|
| `testUuid` | Test id on the Control Server |
| `openTestUuid` | Public id (`O…`) for open-data links |
| `clientUuid` | Client id to persist and reuse |
| `downloadMbps`, `uploadMbps` | Mbit/s |
| `pingMs` | Median of server-measured round trips |
| `serverName` | Measurement server name |

### How a test runs

1. `POST /settings` registers the client (skipped when `clientUuid` is set).
2. `POST /testRequest` returns a token, measurement server, threads, duration and pings.
3. RMBT over n connections: handshake, ~2 s download pretest (picks chunk size), pings,
   download, upload pretest, upload.
4. `POST /result` stores speeds, pings and per-thread speed samples.

Speed per the RMBT spec: bytes of all threads at t* (earliest thread finish, slower
threads interpolated) divided by t*.

---

## 5. Backend requirements

- Open-RMBT Control Server and `rmbtd` reachable from the client (public IP or VPN).
  The address in `test_server.web_address` must be reachable by clients.
- Browsers: `rmbtd -w` (WebSocket). A page served over HTTPS needs the encrypted
  measurement port (`wss://`), otherwise the browser blocks mixed content.
- Control Server CORS (`CONTROL_ALLOWED_ORIGIN`) must allow the web origin.
- Local stack: `../local-speedtest` (`docker compose up -d`), Control Server on
  `:18080`, `rmbtd` on `:18081` (plain) and `:18082` (TLS).

---

## 6. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `401` / `Could not resolve tj.zehn.rmbt:rmbt-sdk` / `npm 401` | Token missing, expired or without `read:packages` |
| `Connection refused` to measurement server | Wrong `test_server.web_address` (e.g. stale `HOST_IP`); fix the backend or set `measurementHost` |
| `control server: SQL Error requesting from database.` on submit | Backend DB misses `interpolate_radio_signal_location_v2`; fix the database |
| `No enum constant ... NetworkGroupType.CLI` | `networkType = 97` is not supported; use 98/99/mobile codes |
| `Upload invalid` / `Download insane` | Measured 0 or impossible speed; check connectivity to `rmbtd` |
| `rmbtd` container exits with 139 | Old `rmbtd` crashes on large WebSocket chunks; the SDK caps them at 32 KiB, update `rmbtd` |

---

## 7. Development

Requirements: JDK 17+, Android SDK (`local.properties` → `sdk.dir=...`), macOS + Xcode
for iOS frameworks (Linux compiles iOS sources to klibs only).

```bash
./gradlew :rmbt-sdk:jvmTest :rmbt-sdk:jsNodeTest             # unit tests
./gradlew :rmbt-sdk:bundleAndroidMainAar                       # AAR
./gradlew :rmbt-sdk:jsBrowserProductionLibraryDistribution     # npm package
./gradlew :rmbt-sdk:assembleRmbtSdkReleaseXCFramework          # iOS (macOS)
```

Tests against a live backend are skipped unless the environment is set:

```bash
RMBT_IT_CONTROL=http://localhost:18080/RMBTControlServer RMBT_IT_HOST=localhost \
RMBT_IT_TRANSPORT=TCP ./gradlew :rmbt-sdk:jvmTest --tests '*EndToEndTest*' --rerun
```

Layout (`core/src`): `commonMain` (protocol, measurement, control client, `RmbtClient`),
`socketMain` (TCP/TLS for JVM, Android, iOS), `jsMain` (browser/Node WebSocket, JS API),
platform folders for defaults.

---

## 8. Release

```bash
gh workflow run release -R Hadamot/zehn-speedtest-sdk -f version=0.2.0
```

The workflow builds the XCFramework on macOS, writes its URL and checksum into
`Package.swift`, commits `build: release <version>`, tags `v<version>`, creates the GitHub
Release, then publishes Maven artifacts and the npm package to GitHub Packages.
Versions are immutable: publish a new version for every change.

---

## 9. Limitations

- Not implemented: QoS tests (DNS, ports, traceroute, VoIP), signal and location
  collection, automatic network type detection.
- TLS to measurement servers accepts any certificate on JVM/Android (self-signed
  servers); iOS uses system trust.
- WebSocket chunks are capped at 32 KiB for `rmbtd` compatibility.
