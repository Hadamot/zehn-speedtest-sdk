import CoreLocation
import Foundation

/// Where the device is, asked once per test. Never blocks the test: without the permission or a fix it
/// answers nil, and a cancelled request answers nil at once.
@MainActor
final class LocationSource: NSObject {

    private let manager = CLLocationManager()
    private var authorizationWaiters: [CheckedContinuation<Void, Never>] = []
    private var locationWaiters: [CheckedContinuation<CLLocation?, Never>] = []

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    /// Asks for the permission when the host app allows it (`RmbtUiConfig.requestLocationPermission`) and
    /// describes the use in its Info.plist, and the user hasn't decided yet. Called on "Start".
    func requestPermissionIfNeeded() {
        guard RmbtUI.config.requestLocationPermission,
              manager.authorizationStatus == .notDetermined,
              Bundle.main.object(forInfoDictionaryKey: "NSLocationWhenInUseUsageDescription") != nil else { return }
        manager.requestWhenInUseAuthorization()
    }

    func current() async -> GeoPoint? {
        await withTaskCancellationHandler {
            if manager.authorizationStatus == .notDetermined {
                await withCheckedContinuation { authorizationWaiters.append($0) }
            }
            guard !Task.isCancelled, isAuthorized else { return nil }
            let location = await withCheckedContinuation { continuation in
                locationWaiters.append(continuation)
                if locationWaiters.count == 1 { manager.requestLocation() }
            }
            return location.map {
                GeoPoint(
                    lat: $0.coordinate.latitude,
                    lon: $0.coordinate.longitude,
                    accuracy: $0.horizontalAccuracy >= 0 ? $0.horizontalAccuracy : nil
                )
            }
        } onCancel: {
            Task { @MainActor in self.resumeAll(location: nil) }
        }
    }

    private var isAuthorized: Bool {
        [.authorizedWhenInUse, .authorizedAlways].contains(manager.authorizationStatus)
    }

    private func resumeAuthorizationWaiters() {
        let waiters = authorizationWaiters
        authorizationWaiters = []
        waiters.forEach { $0.resume() }
    }

    private func resumeAll(location: CLLocation?) {
        resumeAuthorizationWaiters()
        let waiters = locationWaiters
        locationWaiters = []
        waiters.forEach { $0.resume(returning: location) }
    }
}

extension LocationSource: CLLocationManagerDelegate {
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            if status != .notDetermined { self.resumeAuthorizationWaiters() }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let location = locations.last
        Task { @MainActor in self.resumeAll(location: location) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.resumeAll(location: nil) }
    }
}
