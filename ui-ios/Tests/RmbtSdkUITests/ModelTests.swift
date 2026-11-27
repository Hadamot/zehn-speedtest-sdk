import Foundation
import Testing
@testable import RmbtSdkUI

struct RatingTests {
    @Test func downloadThresholds() {
        #expect(Rating.download(mbps: 0) == nil)
        #expect(Rating.download(mbps: 4.9) == .bad)
        #expect(Rating.download(mbps: 5) == .normal)
        #expect(Rating.download(mbps: 10) == .good)
        #expect(Rating.download(mbps: 100) == .excellent)
    }

    @Test func uploadThresholds() {
        #expect(Rating.upload(mbps: 2.4) == .bad)
        #expect(Rating.upload(mbps: 2.5) == .normal)
        #expect(Rating.upload(mbps: 5) == .good)
        #expect(Rating.upload(mbps: 50) == .excellent)
    }

    @Test func pingThresholds() {
        #expect(Rating.ping(ms: 0) == nil)
        #expect(Rating.ping(ms: 75) == .bad)
        #expect(Rating.ping(ms: 25) == .normal)
        #expect(Rating.ping(ms: 10) == .good)
        #expect(Rating.ping(ms: 9.9) == .excellent)
    }

    @Test func overallIsTheWeakestRatedValue() {
        #expect(Rating.overall(.excellent, nil, .normal) == .normal)
        #expect(Rating.overall(nil, nil, nil) == nil)
    }
}

struct ValueFormatTests {
    private let ru = Locale(identifier: "ru")
    private let en = Locale(identifier: "en")

    @Test func speedHasOneDecimalInTheLanguagesSeparator() {
        #expect(ValueFormat.speed(mbps: 23.96, locale: ru) == "24,0")
        #expect(ValueFormat.speed(mbps: 23.94, locale: en) == "23.9")
        #expect(ValueFormat.speed(mbps: 0, locale: en) == ValueFormat.dash)
    }

    @Test func pingIsWholeAboveTenMilliseconds() {
        #expect(ValueFormat.ping(ms: 14.4, locale: en) == "14")
        #expect(ValueFormat.ping(ms: 4.5, locale: ru) == "4,5")
        #expect(ValueFormat.ping(ms: 4, locale: en) == "4")
    }

    @Test func liveSpeedRoundsLikeTheApp() {
        #expect(ValueFormat.liveSpeed(mbps: 1297.6, locale: en) == "1298")
        #expect(ValueFormat.liveSpeed(mbps: 5.23, locale: ru) == "5,2")
    }

    @Test func dashTakesNoUnit() {
        #expect(ValueFormat.withUnit(ValueFormat.dash, "ms") == ValueFormat.dash)
        #expect(ValueFormat.withUnit("14", "ms") == "14\u{00A0}ms")
    }
}

struct HistoryTimeFormatTests {
    private let zone = TimeZone(identifier: "Asia/Dushanbe")!

    private func date(_ text: String) -> Date {
        let formatter = HistoryTimeFormat.fixed("yyyy-MM-dd HH:mm", zone)
        return formatter.date(from: text)!
    }

    @Test func todayYesterdayOtherwiseTheDate() {
        let now = date("2026-11-26 10:00")
        #expect(HistoryTimeFormat.string(for: date("2026-11-26 09:10"), in: zone, now: now, today: "T", yesterday: "Y") == "T 09:10")
        #expect(HistoryTimeFormat.string(for: date("2026-11-25 23:59"), in: zone, now: now, today: "T", yesterday: "Y") == "Y 23:59")
        #expect(HistoryTimeFormat.string(for: date("2026-11-20 12:00"), in: zone, now: now, today: "T", yesterday: "Y") == "20.11.26 12:00")
    }
}

struct TestStepTests {
    @Test func pretestsBelongToTheStepTheyPrepare() {
        #expect(TestStep(.initializing) == .connecting)
        #expect(TestStep(.downPretest) == .connecting)
        #expect(TestStep(.ping) == .ping)
        #expect(TestStep(.download) == .download)
        #expect(TestStep(.upPretest) == .upload)
        #expect(TestStep(.submit) == .upload)
    }

    @Test func everyCorePhaseIsKnown() {
        #expect(SpeedTestPhase.allCases.map(\.rawValue) == ["INIT", "DOWN_PRETEST", "PING", "DOWNLOAD", "UP_PRETEST", "UPLOAD", "SUBMIT"])
    }

    @Test func speedGaugeIsLogarithmic() {
        #expect(SpeedGauge.value(mbps: 0.05) == 0)
        #expect(abs(SpeedGauge.value(mbps: 10) - 0.4) < 1e-9)
        #expect(SpeedGauge.value(mbps: 20_000) == 1)
    }

    @Test func percentAndGaugeFollowTheStep() {
        var state = MeasurementUiState(step: .download, stepFraction: 0.5)
        #expect(state.totalPercent == 55)
        #expect(abs(state.progressGauge - 0.36) < 1e-9)
        state.step = .upload
        state.stepFraction = 1
        #expect(state.totalPercent == 100)
    }
}

@MainActor
struct HistoryStoreTests {
    private func defaults() -> UserDefaults {
        let name = "test-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        return defaults
    }

    @Test func readsTheAndroidHistoryFormat() throws {
        // As the Android UI SDK writes it: no "technology", an unknown network kind, no location.
        let json = """
        [{"testUuid":"a","openTestUuid":null,"time":1764150000000,"timezone":"Asia/Dushanbe","download":45.2,
          "upload":12.1,"ping":18.0,"server":"Dushanbe","network":"SATELLITE","networkName":null,"signalLevel":3}]
        """
        let records = HistoryStore.decode(Data(json.utf8))
        let record = try #require(records.first)
        #expect(record.network == .other)
        #expect(record.technology == nil)
        #expect(record.location == nil)
        #expect(record.signalLevel == 3)
    }

    @Test func newestFirstWithoutDuplicates() {
        let store = HistoryStore(defaults: defaults())
        store.add(.sample("a"))
        store.add(.sample("b"))
        store.add(.sample("a", download: 99))
        #expect(store.records.map(\.testUuid) == ["a", "b"])
        #expect(store.records.first?.download == 99)
    }

    @Test func keepsAtMostFiveHundredTests() {
        let store = HistoryStore(defaults: defaults())
        for index in 0...HistoryStore.maxRecords { store.add(.sample("\(index)")) }
        #expect(store.records.count == HistoryStore.maxRecords)
        #expect(store.records.first?.testUuid == "\(HistoryStore.maxRecords)")
    }

    @Test func survivesARestart() {
        let defaults = defaults()
        let store = HistoryStore(defaults: defaults)
        store.add(.sample("a"))
        store.clientUuid = "client"
        let reopened = HistoryStore(defaults: defaults)
        #expect(reopened.records.map(\.testUuid) == ["a"])
        #expect(reopened.clientUuid == "client")
    }
}

extension TestRecord {
    static func sample(_ uuid: String, download: Double = 45) -> TestRecord {
        TestRecord(
            testUuid: uuid, openTestUuid: nil, time: 1_764_150_000_000, timezone: "Asia/Dushanbe",
            download: download, upload: 12, ping: 18, server: "Dushanbe", network: .cellular, networkName: nil,
            signalLevel: nil, location: nil, technology: "4G"
        )
    }
}
