import Foundation

enum MuzzaRepeatMode: String, Codable {
    case off
    case all
    case one
}

enum MuzzaQueueRules {
    static func nextIndex(current: Int, count: Int, repeatMode: MuzzaRepeatMode, automatic: Bool) -> Int? {
        guard current >= 0, current < count else { return nil }
        if automatic && repeatMode == .one { return current }
        if current + 1 < count { return current + 1 }
        return repeatMode == .all ? 0 : nil
    }

    static func previousIndex(current: Int, count: Int, repeatMode: MuzzaRepeatMode) -> Int? {
        guard current >= 0, current < count else { return nil }
        if current > 0 { return current - 1 }
        return repeatMode == .all ? count - 1 : nil
    }

    static func shuffleUpcoming<T>(_ values: [T], currentIndex: Int) -> [T]? {
        guard values.indices.contains(currentIndex) else { return nil }
        let played = Array(values.prefix(currentIndex + 1))
        var upcoming = Array(values.dropFirst(currentIndex + 1))
        upcoming.shuffle()
        return played + upcoming
    }

    static func indexAfterRemoving(current: Int, count: Int, indices: IndexSet) -> Int? {
        guard current >= 0, current < count, indices.allSatisfy({ $0 >= 0 && $0 < count }) else { return nil }
        let next = (current..<count).first { !indices.contains($0) }
        let previous = (0..<current).reversed().first { !indices.contains($0) }
        guard let survivor = next ?? previous else { return nil }
        return survivor - indices.filter { $0 < survivor }.count
    }
}
