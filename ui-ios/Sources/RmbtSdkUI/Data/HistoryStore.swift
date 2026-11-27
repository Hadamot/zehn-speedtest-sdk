import Foundation

/// Where the test screen keeps a finished test and the client UUID.
@MainActor
protocol TestRecordSink: AnyObject {
    var clientUuid: String? { get set }
    func add(_ record: TestRecord)
}

/// The tests made on this device, newest first, kept in the host app's defaults (like the Android UI SDK's
/// SharedPreferences). Also keeps the client UUID the Control Server assigned, so the server sees one client
/// across tests.
@MainActor
final class HistoryStore: ObservableObject, TestRecordSink {

    static let shared = HistoryStore(defaults: UserDefaults(suiteName: "tj.zehn.rmbt.ui") ?? .standard)

    static let maxRecords = 500

    @Published private(set) var records: [TestRecord]

    private let defaults: UserDefaults

    init(defaults: UserDefaults) {
        self.defaults = defaults
        self.records = Self.decode(defaults.data(forKey: Keys.history))
    }

    var clientUuid: String? {
        get { defaults.string(forKey: Keys.clientUuid) }
        set { defaults.set(newValue, forKey: Keys.clientUuid) }
    }

    func record(_ testUuid: String) -> TestRecord? {
        records.first { $0.testUuid == testUuid }
    }

    func add(_ record: TestRecord) {
        let updated = Array(([record] + records.filter { $0.testUuid != record.testUuid }).prefix(Self.maxRecords))
        defaults.set(Self.encode(updated), forKey: Keys.history)
        records = updated
    }

    static func encode(_ records: [TestRecord]) -> Data? {
        try? JSONEncoder().encode(records)
    }

    /// An unreadable history is dropped rather than breaking the screens.
    static func decode(_ data: Data?) -> [TestRecord] {
        guard let data else { return [] }
        return (try? JSONDecoder().decode([TestRecord].self, from: data)) ?? []
    }

    private enum Keys {
        static let history = "history"
        static let clientUuid = "client_uuid"
    }
}
