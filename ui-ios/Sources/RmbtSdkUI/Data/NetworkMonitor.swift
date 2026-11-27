import CoreTelephony
import Foundation
import Network

/// The network the device is on right now.
struct NetworkState: Equatable {
    var isConnected: Bool
    var kind: NetworkKind?
    /// Mobile generation, e.g. "4G"; nil off mobile or when unknown.
    var technology: String?

    static let offline = NetworkState(isConnected: false, kind: nil, technology: nil)
}

/// Follows the current network (`NWPathMonitor`) and the mobile generation (CoreTelephony, no permission
/// needed). The Wi-Fi name is not shown: iOS gives it only to apps with a special entitlement.
@MainActor
final class NetworkMonitor: ObservableObject {

    static let shared = NetworkMonitor()

    @Published private(set) var state: NetworkState = .offline

    private let monitor = NWPathMonitor()
    private let telephony = CTTelephonyNetworkInfo()
    private var started = false

    /// Idempotent; the monitor then runs for the life of the app (it costs next to nothing).
    func start() {
        guard !started else { return }
        started = true
        monitor.pathUpdateHandler = { [weak self] path in
            let connected = path.status == .satisfied
            let kind = Self.kind(of: path)
            DispatchQueue.main.async {
                guard let self else { return }
                self.state = NetworkState(
                    isConnected: connected,
                    kind: connected ? kind : nil,
                    technology: connected && kind == .cellular ? self.currentTechnology() : nil
                )
            }
        }
        monitor.start(queue: DispatchQueue(label: "tj.zehn.rmbt.ui.network"))
    }

    private nonisolated static func kind(of path: NWPath) -> NetworkKind {
        if path.usesInterfaceType(.wifi) { return .wifi }
        if path.usesInterfaceType(.cellular) { return .cellular }
        if path.usesInterfaceType(.wiredEthernet) { return .ethernet }
        return .other
    }

    private func currentTechnology() -> String? {
        let technologies = telephony.serviceCurrentRadioAccessTechnology ?? [:]
        let service = telephony.dataServiceIdentifier.flatMap { technologies[$0] } ?? technologies.values.first
        return service.flatMap(Self.generation(of:))
    }

    /// "4G" for `CTRadioAccessTechnologyLTE` and so on.
    nonisolated static func generation(of technology: String) -> String? {
        switch technology {
        case CTRadioAccessTechnologyGPRS, CTRadioAccessTechnologyEdge, CTRadioAccessTechnologyCDMA1x:
            return "2G"
        case CTRadioAccessTechnologyWCDMA, CTRadioAccessTechnologyHSDPA, CTRadioAccessTechnologyHSUPA,
             CTRadioAccessTechnologyCDMAEVDORev0, CTRadioAccessTechnologyCDMAEVDORevA,
             CTRadioAccessTechnologyCDMAEVDORevB, CTRadioAccessTechnologyeHRPD:
            return "3G"
        case CTRadioAccessTechnologyLTE:
            return "4G"
        case CTRadioAccessTechnologyNRNSA, CTRadioAccessTechnologyNR:
            return "5G"
        default:
            return nil
        }
    }
}
