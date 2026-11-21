import Foundation
import XCTest

final class MuzzaResourceLoaderTests: XCTestCase {
    func testContentRangeValidation() {
        let range = MuzzaResourceLoader.parseContentRange("bytes 5-9/30")
        XCTAssertEqual(range?.start, 5)
        XCTAssertEqual(range?.total, 30)
        XCTAssertNil(MuzzaResourceLoader.parseContentRange("items 5-9/30"))
        XCTAssertNil(MuzzaResourceLoader.parseContentRange("bytes 9-5/30"))
        XCTAssertNil(MuzzaResourceLoader.parseContentRange("bytes 5-30/30"))
        XCTAssertNil(MuzzaResourceLoader.parseContentRange("bytes 0-4/*"))
        XCTAssertNil(MuzzaResourceLoader.parseContentRange("bytes 0-/30"))
    }

    func testMediaHostsRequireTrustedHTTPSOrigins() {
        XCTAssertTrue(MuzzaResourceLoader.isAllowed(URL(string: "https://r1.googlevideo.com/audio")!))
        XCTAssertTrue(MuzzaResourceLoader.isAllowed(URL(string: "https://www.youtube.com/audio")!))
        XCTAssertFalse(MuzzaResourceLoader.isAllowed(URL(string: "http://r1.googlevideo.com/audio")!))
        XCTAssertFalse(MuzzaResourceLoader.isAllowed(URL(string: "https://googlevideo.com.example.org/audio")!))
        XCTAssertFalse(MuzzaResourceLoader.isAllowed(URL(string: "https://user:pass@googlevideo.com/audio")!))
        XCTAssertFalse(MuzzaResourceLoader.isAllowed(URL(string: "https://googlevideo.com:8443/audio")!))
    }
}

final class MuzzaLyricsParserTests: XCTestCase {
    func testMultipleTimestampsAndFractions() throws {
        let lyrics = "[00:02.50][00:05.25]Second\n[0:01.2]First\n[00:04:125]Third\n[00:99.00]Invalid"
        let lines = try MuzzaLyricsParser.parse(lyrics)
        XCTAssertEqual(lines.map(\.text), ["First", "Second", "Third", "Second"])
        XCTAssertEqual(lines.map(\.time), [1.2, 2.5, 4.125, 5.25])
    }

    func testUnsyncedTextHasNoTimedLines() throws {
        XCTAssertTrue(try MuzzaLyricsParser.parse("A line without timestamps").isEmpty)
    }
}

XCTMain([
    testCase([
        ("testContentRangeValidation", MuzzaResourceLoaderTests.testContentRangeValidation),
        ("testMediaHostsRequireTrustedHTTPSOrigins", MuzzaResourceLoaderTests.testMediaHostsRequireTrustedHTTPSOrigins)
    ]),
    testCase([
        ("testMultipleTimestampsAndFractions", MuzzaLyricsParserTests.testMultipleTimestampsAndFractions),
        ("testUnsyncedTextHasNoTimedLines", MuzzaLyricsParserTests.testUnsyncedTextHasNoTimedLines)
    ])
])
