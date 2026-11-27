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
| iOS 16+ | Swift Package `RmbtSdk` (+ `RmbtSdkUI` screens) | WebSocket (default), TCP |
| Browser, Node | npm `@hadamot/speedtest-sdk` | WebSocket |

Current version: **0.1.2**.

Default backend (Tojiktelecom, IPv4 only):

| | Address |
|---|---|
| Control Server (API, statistics, map) | `https://speedtest.tojiktelecom.tj/RMBTControlServer` |
| Measurement server | `speedtest-m1.tojiktelecom.tj:443` (TLS), assigned by `/testRequest` |

With no configuration the SDK uses this backend; the measurement server never needs to
be configured. Set `controlUrl` only for another backend (e.g. the local stack).

---

## 1. Access to packages

Everything is public and installs without a GitHub account or token.

| Platform | Source |
|---|---|
| Android / JVM | Maven repo on GitHub Pages: `https://hadamot.github.io/zehn-speedtest-sdk/maven` |
| Web / Node | npm tarball attached to each [GitHub Release](https://github.com/Hadamot/zehn-speedtest-sdk/releases) |
| iOS | Swift Package from this repository |

Packages are also mirrored to GitHub Packages (`maven.pkg.github.com`, `npm.pkg.github.com`);
those need a `read:packages` token, so prefer the sources above. Tokenless install starts at **0.1.2**.

---

## 2. Install

### Android / JVM (Gradle)

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://hadamot.github.io/zehn-speedtest-sdk/maven")
    }
}
```

Module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("tj.zehn.rmbt:rmbt-sdk:0.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
```

The coroutines line is needed only for 0.1.2; later versions expose it transitively.

Gradle picks the right variant (`rmbt-sdk-android`, `rmbt-sdk-jvm`) automatically.
Android apps need `<uses-permission android:name="android.permission.INTERNET" />`, and
`android:usesCleartextTraffic="true"` (or a network security config) when the Control
Server is plain `http://`.

### Web (npm, pnpm, yarn)

```bash
pnpm add https://github.com/Hadamot/zehn-speedtest-sdk/releases/download/v0.1.2/speedtest-sdk-0.1.2.tgz
```

The package name stays `@hadamot/speedtest-sdk`; to upgrade, change the version in the URL.
TypeScript typings are included. Browsers use the native `WebSocket`; Node uses the
bundled `ws` dependency.

### iOS (Swift Package Manager)

File → Add Package Dependencies → `https://github.com/Hadamot/zehn-speedtest-sdk`,
rule "Up to Next Major" from `0.1.2`, product **RmbtSdk** (and **RmbtSdkUI** for the ready-made
screens, from 0.2.0). No account or token needed. iOS 16+ (the package required iOS 15 before 0.2.0).

Manual alternative: download `RmbtSdk.xcframework.zip` from the GitHub Release, unzip
and drag `RmbtSdk.xcframework` into the project (Embed & Sign).

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
val result = RmbtClient(RmbtConfig(tag = "probe-dushanbe-1")).run()
println("↓ ${result.downloadMbps} ↑ ${result.uploadMbps} Mbit/s, ping ${result.pingMs} ms")
```

### Android ready-made screens (`rmbt-sdk-ui`)

Start screen, running test, result and history, copied from the NetCheck app
(open-rmbt-android). Views + view binding, minSdk 24; includes `rmbt-sdk`.

```kotlin
dependencies {
    implementation("tj.zehn.rmbt:rmbt-sdk-ui:<version>")
}
```

```kotlin
RmbtUi.configure(RmbtUiConfig(apiKey = "my-app"))   // optional: controlUrl, apiKey, tag, appName
RmbtUi.start(context)                                // start screen + history with bottom navigation
```

Or embed `RmbtHomeFragment` / `RmbtHistoryFragment` in your own activity (any `FragmentActivity`,
also a Compose one with `AndroidFragment`; the fragments bring their own theme and give the status
bar back as they found it). `RmbtUi.startHistory(context)` opens the history as a screen of its own
with a back arrow. A host with a floating bottom bar keeps the start screen's last test card above it:

```kotlin
AndroidFragment<RmbtHomeFragment>(onUpdate = { it.setContentBottomPadding(bottomBarHeightPx) })
```

An app that switches its language itself (not via the system or `AppCompatDelegate`) passes the same
wrapper to the SDK screens: `RmbtUiConfig(wrapContext = { MyLocale.wrap(it) })`.

**Test location on a map.** The result screen shows where the test ran: a Google map with the point
and the coordinates. The SDK declares no location permission and no key; the host app opts in:

```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<application>
    <meta-data android:name="com.google.android.geo.API_KEY" android:value="${mapsApiKey}" />
</application>
```

With the permissions declared, the SDK asks for them when the user taps "Start" (turn off with
`RmbtUiConfig(requestLocationPermission = false)` if the app asks itself); the test runs whatever the
answer. The key needs "Maps SDK for Android" enabled in Google Cloud and should be restricted to the
app's package and signing certificate. Without the permission the result says the location is unknown;
without the key or Google Play services it shows the coordinates only. The point stays on the device,
in the history; it is not sent to the Control Server.

The history is stored on the device (SharedPreferences of the host app). All resources are prefixed
`rmbt_`, so they never replace the host app's resources. Apps on Kotlin 2.2 or newer can use the SDK
(it is built with Kotlin 2.3). Status, limitations and next steps: [docs/UI_SDK_TZ.md](docs/UI_SDK_TZ.md).

### iOS (Swift)

```swift
import RmbtSdk

let client = RmbtClient(
    config: RmbtConfig(controlUrl: RmbtConfig.companion.DEFAULT_CONTROL_URL, apiKey: "ios-app")
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

Before **0.2.0** a failed test (anything but cancellation) terminated the app instead of throwing,
and the default TCP+TLS transport never worked on iOS (ktor has no TLS sockets on Kotlin/Native):
use 0.2.0+, where `run` throws and iOS defaults to a secure WebSocket.

`RmbtClient()`, `RmbtClient(config:)`, `RmbtConfig()`, `RmbtConfig(controlUrl:apiKey:)`
and `run(onEvent:)` require SDK **0.1.1+**; `RmbtClient()` alone targets the default backend. `RmbtConfig(controlUrl:apiKey:)` uses platform defaults. For more options use the full
initializer; Kotlin default arguments are not visible in Swift, so pass every field
(`Platform.shared` holds the defaults).

### iOS ready-made screens (`RmbtSdkUI`)

Start screen, running test (progress and speed gauges), result and history, ported from the NetCheck
app (open-rmbt-ios) to SwiftUI; en/ru/tg. Product **RmbtSdkUI** of the same package, iOS 16+.

```swift
import RmbtSdkUI

RmbtUI.configure(RmbtUiConfig(apiKey: "my-app"))   // optional: controlUrl, apiKey, tag, appName, language

// SwiftUI: start screen + history in a tab bar
RmbtSpeedtestView()
// or one tab each (each brings its own NavigationStack)
RmbtHomeView()
RmbtHistoryView()

// UIKit
RmbtUI.present(from: viewController)                  // full screen, with a close button
let controller = RmbtUI.makeViewController()          // to push or embed yourself
```

An app with its own `NavigationStack` per tab keeps the navigation and puts the screens in its stacks
(as MyTelecom-iOS does):

```swift
NavigationStack(path: $path) {
    RmbtStartView(onOpenResult: { path.append(Route.speedtestResult($0)) })   // keep the navigation bar
        .navigationDestination(for: Route.self) { route in
            if case let .speedtestResult(uuid) = route { RmbtResultView(testUuid: uuid) }
        }
}
// history, pushed e.g. from a settings list
RmbtHistoryListView(onOpenResult: { path.append(Route.speedtestResult($0)) })
```

The screens follow the system language among the languages the host app declares (it needs `ru.lproj`
/ `tg.lproj` or the matching `CFBundleLocalizations`); an app that switches its language itself passes
it: `RmbtUiConfig(language: "tg")`.

**Test location on a map.** With `NSLocationWhenInUseUsageDescription` in the host's Info.plist the SDK
asks for the location when the user taps "Start" (turn off with `requestLocationPermission: false` if
the app asks itself); the test runs whatever the answer. The result then shows an Apple map with the
point (no key needed). The point stays on the device, in the history; it is not sent to the Control Server.

The history is stored on the device (`UserDefaults` suite `tj.zehn.rmbt.ui`, the same JSON as on
Android). The Wi-Fi name and the signal level are not shown: iOS gives them only to apps with special
entitlements. Demo app: `sample-ios/RmbtSample.swiftpm` (open in Xcode).

An app referencing this folder as a local package (Xcode → Add Local…) uses the locally built core when
`core/build/XCFrameworks/release/RmbtSdk.xcframework` exists (`./gradlew :rmbt-sdk:assembleRmbtSdkReleaseXCFramework`),
otherwise the released one; set `RMBT_RELEASED_CORE=1` to force the release.

### Web (TypeScript)

```ts
import sdk from "@hadamot/speedtest-sdk";

const { RmbtWebClient, RmbtWebOptions } = sdk.tj.zehn.rmbt.web;

const client = new RmbtWebClient(
  new RmbtWebOptions(
    undefined,
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
during SSR. `RmbtWebOptions(controlUrl?, apiKey?, clientUuid?, threads?, durationSec?, tag?, measurementHost?)`;
`new RmbtWebOptions()` targets the default backend.

---

## 4. Reference

### `RmbtConfig`

| Field | Default | Meaning |
|---|---|---|
| `controlUrl` | `https://speedtest.tojiktelecom.tj/RMBTControlServer` | Control Server base URL (`RmbtConfig.DEFAULT_CONTROL_URL`) |
| `apiKey` | `null` | Sent as `X-Api-Key` header on Control Server calls; identifies the source app at a gateway |
| `clientUuid` | `null` | Stored client id; `null` registers a new client via `/settings` |
| `transport` | JVM/Android `TCP`, iOS and JS `WEBSOCKET` | `TCP`/`TLS` or `WEBSOCKET`/`SECURE_WEBSOCKET`; encryption follows the server ticket |
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
- Control Server CORS (`CONTROL_ALLOWED_ORIGIN`) must allow the web origin. Production currently allows only `https://speedtest.tojiktelecom.tj`; other sites get a 403 preflight until added there.
- Local stack: `../local-speedtest` (`docker compose up -d`), Control Server on
  `:18080`, `rmbtd` on `:18081` (plain) and `:18082` (TLS).

---

## 6. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Could not resolve tj.zehn.rmbt:rmbt-sdk` | Missing `maven("https://hadamot.github.io/zehn-speedtest-sdk/maven")` or version below 0.1.2 |
| `401` from `*.pkg.github.com` | GitHub Packages mirror needs a `read:packages` token; use the tokenless sources instead |
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

iOS screens (`ui-ios/`, Swift package `RmbtSdkUI`) against the locally built core:

```bash
./gradlew :rmbt-sdk:assembleRmbtSdkReleaseXCFramework
xcodebuild -scheme RmbtSdk-Package \
  -destination 'platform=iOS Simulator,name=iPhone 17' test      # unit tests
# + TEST_RUNNER_RMBT_E2E=1: a real test against the default backend
# + TEST_RUNNER_RMBT_SNAPSHOTS=/tmp/shots: renders the screens to PNG (en/ru/tg, dark, large text)
```

Layout (`core/src`): `commonMain` (protocol, measurement, control client, `RmbtClient`),
`socketMain` (TCP/TLS for JVM and Android; plain TCP only on iOS), `jsMain` (browser/Node WebSocket, JS API),
platform folders for defaults.

---

## 8. Release

```bash
gh workflow run release -R Hadamot/zehn-speedtest-sdk -f version=0.2.0
```

The workflow builds the XCFramework on macOS, writes its URL and checksum into
`Package.swift`, commits `build: release <version>`, tags `v<version>`, creates the GitHub
Release, then publishes Maven artifacts to the `gh-pages` branch (served by GitHub Pages) and
GitHub Packages, attaches `speedtest-sdk-<version>.tgz` to the Release and publishes npm to GitHub Packages.
Versions are immutable: publish a new version for every change.

---

## 9. Limitations

- Not implemented: QoS tests (DNS, ports, traceroute, VoIP), signal and location
  collection, automatic network type detection.
- TLS to measurement servers accepts any certificate on JVM/Android (self-signed
  servers); iOS tests over a secure WebSocket with system trust.
- WebSocket chunks are capped at 32 KiB for `rmbtd` compatibility.

---

## License

[Apache License 2.0](LICENSE).
