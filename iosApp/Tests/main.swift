import Foundation

func expect(_ condition: @autoclosure () -> Bool, _ message: String) {
    guard condition() else {
        fputs("FAIL: \(message)\n", stderr)
        exit(1)
    }
}

func testContentRangeValidation() {
    let range = MuzzaResourceLoader.parseContentRange("bytes 5-9/30")
    expect(range?.start == 5 && range?.total == 30, "valid byte range")
    expect(MuzzaResourceLoader.parseContentRange("items 5-9/30") == nil, "invalid range unit")
    expect(MuzzaResourceLoader.parseContentRange("bytes 9-5/30") == nil, "reversed range")
    expect(MuzzaResourceLoader.parseContentRange("bytes 5-30/30") == nil, "range beyond total")
    expect(MuzzaResourceLoader.parseContentRange("bytes 0-4/*") == nil, "unknown total")
    expect(MuzzaResourceLoader.parseContentRange("bytes 0-/30") == nil, "missing range end")
}

func testMediaHosts() {
    expect(MuzzaResourceLoader.isAllowed(URL(string: "https://r1.googlevideo.com/audio")!), "Google video host")
    expect(MuzzaResourceLoader.isAllowed(URL(string: "https://www.youtube.com/audio")!), "YouTube host")
    expect(!MuzzaResourceLoader.isAllowed(URL(string: "http://r1.googlevideo.com/audio")!), "insecure scheme")
    expect(!MuzzaResourceLoader.isAllowed(URL(string: "https://googlevideo.com.example.org/audio")!), "lookalike host")
    expect(!MuzzaResourceLoader.isAllowed(URL(string: "https://user:pass@googlevideo.com/audio")!), "URL credentials")
    expect(!MuzzaResourceLoader.isAllowed(URL(string: "https://googlevideo.com:8443/audio")!), "unexpected port")
}

func testLyricsParser() throws {
    let lyrics = "[00:02.50][00:05.25]Second\n[0:01.2]First\n[00:04:125]Third\n[00:99.00]Invalid"
    let lines = try MuzzaLyricsParser.parse(lyrics)
    expect(lines.map(\.text) == ["First", "Second", "Third", "Second"], "lyric ordering and repeated lines")
    expect(lines.map(\.time) == [1.2, 2.5, 4.125, 5.25], "lyric timestamp precision")
    let plain = try MuzzaLyricsParser.parse("A line without timestamps")
    expect(plain.isEmpty, "plain lyrics")
}

func testCookieSelection() throws {
    func cookie(_ name: String, _ value: String, domain: String, path: String = "/") -> HTTPCookie {
        HTTPCookie(properties: [.name: name, .value: value, .domain: domain, .path: path])!
    }
    let url = URL(string: "https://music.youtube.com/youtubei/v1/")!
    let cookies = [
        cookie("SAPISID", "broad", domain: ".youtube.com"),
        cookie("SAPISID", "specific", domain: "music.youtube.com", path: "/youtubei"),
        cookie("SID", "session", domain: ".youtube.com"),
        cookie("OTHER", "ignored", domain: ".example.org")
    ]
    let header = try MuzzaCookieFilter.header(from: cookies, for: url)
    expect(header.contains("SAPISID=specific"), "most specific YouTube cookie")
    expect(header.contains("SID=session"), "YouTube session cookie")
    expect(!header.contains("broad") && !header.contains("OTHER"), "unrelated and duplicate cookies excluded")
    do {
        _ = try MuzzaCookieFilter.header(from: [cookies[2]], for: url)
        expect(false, "missing authentication cookie")
    } catch MuzzaCookieError.missingSession {
    }
}

func testDownloadFilenamesStayInTheDownloadDirectory() {
    expect(MuzzaDownloadStore.isSafeFilename("12345678-1234-1234-1234-123456789abc.m4a"), "valid download name")
    expect(!MuzzaDownloadStore.isSafeFilename("../12345678-1234-1234-1234-123456789abc.m4a"), "path traversal")
    expect(!MuzzaDownloadStore.isSafeFilename("12345678-1234-1234-1234-123456789abc.mp3"), "unexpected extension")
}

func testQueueRules() {
    expect(MuzzaQueueRules.nextIndex(current: 1, count: 3, repeatMode: .off, automatic: true) == 2, "next song")
    expect(MuzzaQueueRules.nextIndex(current: 2, count: 3, repeatMode: .off, automatic: true) == nil, "queue end")
    expect(MuzzaQueueRules.nextIndex(current: 2, count: 3, repeatMode: .all, automatic: true) == 0, "repeat queue")
    expect(MuzzaQueueRules.nextIndex(current: 1, count: 3, repeatMode: .one, automatic: true) == 1, "repeat song")
    expect(MuzzaQueueRules.nextIndex(current: 1, count: 3, repeatMode: .one, automatic: false) == 2, "manual skip")
    expect(MuzzaQueueRules.previousIndex(current: 0, count: 3, repeatMode: .all) == 2, "previous wraps")
    expect(MuzzaQueueRules.nextIndex(current: 0, count: 0, repeatMode: .all, automatic: true) == nil, "empty queue")
    let order = MuzzaQueueRules.shuffleUpcoming([0, 1, 2, 3], currentIndex: 1)
    expect(order?.prefix(2).elementsEqual([0, 1]) == true, "shuffle keeps played items")
    expect(Set(order ?? []) == Set([0, 1, 2, 3]), "shuffle keeps every queue item")
}

testContentRangeValidation()
testMediaHosts()
testDownloadFilenamesStayInTheDownloadDirectory()
testQueueRules()
do {
    try testLyricsParser()
    try testCookieSelection()
} catch {
    fputs("FAIL: \(error.localizedDescription)\n", stderr)
    exit(1)
}
print("iOS media, lyrics, and cookie checks passed.")
