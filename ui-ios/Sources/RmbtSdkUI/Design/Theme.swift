import CoreText
import SwiftUI

/// Colours of the screens, taken from open-rmbt-ios (the start screen's are in HomeComponents.swift, from Android).
enum RmbtColor {
    /// Logo and accent colour (`rmbt_colorAccent`); the network icons on the start button.
    static let brand = Color(red: 0x00 / 255, green: 0x72 / 255, blue: 0xEF / 255)
    /// The running test's background (#282828).
    static let testBackground = Color(red: 40 / 255, green: 40 / 255, blue: 40 / 255)
    /// The start screen's last-test card.
    static let lastTestCard = Color(red: 242 / 255, green: 244 / 255, blue: 247 / 255)
    /// The live speed chart (`graphAccent`).
    static let graphAccent = brand
}

/// Text colours of the screens ported from the Android UI SDK (`rmbt_values.xml`).
enum AndroidColor {
    /// The theme's primary text (87 % black).
    static let textPrimary = Color.black.opacity(0.87)
    /// `rmbt_text_gray`.
    static let textGray = Color(red: 0x54 / 255, green: 0x54 / 255, blue: 0x54 / 255)
    /// `rmbt_text_dark_gray`.
    static let textDarkGray = Color(red: 0x42 / 255, green: 0x42 / 255, blue: 0x42 / 255)
    /// `rmbt_text_gray_transparency_60`.
    static let textGray60 = Color(red: 0x42 / 255, green: 0x42 / 255, blue: 0x42 / 255).opacity(0.6)
    /// `rmbt_horizontal_divider`.
    static let divider = Color(red: 0xEE / 255, green: 0xEE / 255, blue: 0xEE / 255)
}

/// Roboto, as in the app; registered from the SDK bundle on first use.
enum RmbtFont {
    enum Weight: String {
        case regular = "Roboto-Regular"
        case medium = "Roboto-Medium"
        case bold = "Roboto-Bold"
    }

    /// Scales with Dynamic Type relative to `style`.
    static func roboto(_ size: CGFloat, _ weight: Weight = .regular, relativeTo style: Font.TextStyle = .body) -> Font {
        _ = registered
        return .custom(weight.rawValue, size: size, relativeTo: style)
    }

    /// Fixed size: for text drawn on images with fixed geometry (the test gauges).
    static func roboto(fixed size: CGFloat, _ weight: Weight = .regular) -> Font {
        _ = registered
        return .custom(weight.rawValue, fixedSize: size)
    }

    private static let registered: Void = {
        for weight in [Weight.regular, .medium, .bold] {
            guard let url = Bundle.module.url(forResource: weight.rawValue, withExtension: "ttf") else { continue }
            // Fails harmlessly when the host app registered Roboto itself.
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
}

/// Images of the SDK bundle.
enum RmbtImage {
    static func named(_ name: String) -> Image {
        Image(name, bundle: .module)
    }

    static func download(_ rating: Rating?) -> Image { named("down-\(rating?.rawValue ?? 0)") }
    static func upload(_ rating: Rating?) -> Image { named("up-\(rating?.rawValue ?? 0)") }
    static func ping(_ rating: Rating?) -> Image { named("ping-\(rating?.rawValue ?? 0)") }
}
