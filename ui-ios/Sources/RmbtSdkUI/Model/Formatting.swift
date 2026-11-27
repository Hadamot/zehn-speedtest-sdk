import Foundation

/// Speed and ping values as the result, the history and the shared text show them (open-rmbt-ios
/// `ResultValueFormat`): the decimal separator of the screens' language, a dash when nothing was measured.
enum ValueFormat {
    static let dash = "–"

    /// One decimal, e.g. "23,9".
    static func speed(mbps: Double, locale: Locale = L10n.locale) -> String {
        guard mbps > 0 else { return dash }
        return number(mbps, fractionDigits: 1, locale: locale)
    }

    /// Whole milliseconds, one decimal below 10 ms when it has a fraction, e.g. "14" or "4,5".
    static func ping(ms: Double, locale: Locale = L10n.locale) -> String {
        guard ms > 0 else { return dash }
        let hasFraction = ms > ms.rounded(.towardZero)
        return number(ms, fractionDigits: hasFraction && ms < 10 ? 1 : 0, locale: locale)
    }

    /// The live speed on the test gauge (open-rmbt-ios `RMBTSpeedMbpsString`): whole Mbit/s above 10,
    /// otherwise two significant digits, e.g. "5,2" or "0,48".
    static func liveSpeed(mbps: Double, locale: Locale = L10n.locale) -> String {
        guard mbps > 0 else { return dash }
        if mbps > 10 { return String(Int(mbps.rounded())) }
        let formatter = NumberFormatter()
        formatter.locale = locale
        formatter.numberStyle = .decimal
        formatter.usesSignificantDigits = true
        formatter.maximumSignificantDigits = 2
        return formatter.string(from: NSNumber(value: mbps)) ?? dash
    }

    static func withUnit(_ number: String, _ unit: String) -> String {
        number == dash ? dash : number + "\u{00A0}" + unit
    }

    private static func number(_ value: Double, fractionDigits: Int, locale: Locale) -> String {
        let formatter = NumberFormatter()
        formatter.locale = locale
        formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = false
        formatter.minimumFractionDigits = fractionDigits
        formatter.maximumFractionDigits = fractionDigits
        formatter.roundingMode = .halfUp
        return formatter.string(from: NSNumber(value: value)) ?? dash
    }
}

/// Time of a history entry as people say it: "Today 12:00", "Yesterday 18:30", otherwise "20.11.26 12:00",
/// in the time zone the test ran in (open-rmbt-ios `HistoryTimeFormat`). The start screen's card puts a
/// comma between the day and the time, as on Android ("Today, 12:00").
enum HistoryTimeFormat {
    static func string(
        for date: Date,
        in timeZone: TimeZone,
        now: Date = Date(),
        today: String = L10n.tr("history.today"),
        yesterday: String = L10n.tr("history.yesterday"),
        separator: String = " "
    ) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let clock = fixed("HH:mm", timeZone).string(from: date)
        let day: String
        if calendar.isDate(date, inSameDayAs: now) {
            day = today
        } else if let dayBefore = calendar.date(byAdding: .day, value: -1, to: now),
                  calendar.isDate(date, inSameDayAs: dayBefore) {
            day = yesterday
        } else {
            day = fixed("dd.MM.yy", timeZone).string(from: date)
        }
        return day + separator + clock
    }

    /// A date format that doesn't follow the device's region, e.g. "dd.MM.yy".
    static func fixed(_ format: String, _ timeZone: TimeZone) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timeZone
        formatter.dateFormat = format
        return formatter
    }
}

/// What "Share result" sends: a short text summary in the screens' language (open-rmbt-ios `ResultShare`).
enum ResultShareText {
    static func text(for record: TestRecord, appName: String = L10n.appName, locale: Locale = L10n.locale) -> String {
        let time = HistoryTimeFormat.fixed("dd.MM.yyyy, HH:mm", record.timeZone).string(from: record.date)
        var lines = ["📊 " + L10n.tr("share.title", appName), "📅 " + time]
        lines.append("🌐 " + L10n.tr("share.network") + ": " + record.networkTitle)
        lines.append("")
        let speedUnit = L10n.tr("unit.speed")
        lines.append("⬇️ " + L10n.tr("metric.download") + ": "
            + ValueFormat.withUnit(ValueFormat.speed(mbps: record.download, locale: locale), speedUnit))
        lines.append("⬆️ " + L10n.tr("metric.upload") + ": "
            + ValueFormat.withUnit(ValueFormat.speed(mbps: record.upload, locale: locale), speedUnit))
        lines.append("⏱️ " + L10n.tr("metric.ping") + ": "
            + ValueFormat.withUnit(ValueFormat.ping(ms: record.ping, locale: locale), L10n.tr("unit.ping")))
        if let verdict = record.overallRating {
            lines.append("")
            lines.append(emoji(verdict) + " " + verdict.verdictTitle)
            lines.append(verdict.verdictDescription)
        }
        return lines.joined(separator: "\n")
    }

    private static func emoji(_ rating: Rating) -> String {
        switch rating {
        case .excellent, .good: return "🟢"
        case .normal: return "🟡"
        case .bad: return "🔴"
        }
    }
}
