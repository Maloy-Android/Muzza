import Foundation

struct MuzzaLyricLine: Identifiable {
    let id: Int
    let time: TimeInterval
    let text: String
}

enum MuzzaLyricsParser {
    static func parse(_ lyrics: String) throws -> [MuzzaLyricLine] {
        let expression = try NSRegularExpression(pattern: #"\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]"#)
        var parsed: [(time: TimeInterval, text: String, order: Int)] = []
        for line in lyrics.components(separatedBy: .newlines) {
            let source = line as NSString
            let matches = expression.matches(in: line, range: NSRange(location: 0, length: source.length))
            guard let last = matches.last else { continue }
            let text = source.substring(from: NSMaxRange(last.range)).trimmingCharacters(in: .whitespaces)
            for match in matches {
                guard let minutes = Int(source.substring(with: match.range(at: 1))),
                      let seconds = Int(source.substring(with: match.range(at: 2))),
                      seconds < 60 else { continue }
                let fraction: Double
                if match.range(at: 3).location == NSNotFound {
                    fraction = 0
                } else {
                    fraction = Double("0." + source.substring(with: match.range(at: 3))) ?? 0
                }
                parsed.append((Double(minutes * 60 + seconds) + fraction, text, parsed.count))
            }
        }
        parsed.sort { $0.time == $1.time ? $0.order < $1.order : $0.time < $1.time }
        return parsed.enumerated().map { index, line in
            MuzzaLyricLine(id: index, time: line.time, text: line.text)
        }
    }
}
