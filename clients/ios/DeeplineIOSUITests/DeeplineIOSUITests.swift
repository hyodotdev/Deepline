import XCTest

final class DeeplineIOSUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testIdentitySetupAndLocalNotesChatAppears() throws {
        let app = XCUIApplication()
        app.launchArguments.append("-resetDeeplineState")
        app.launchEnvironment["DEEPLINE_SERVER_URL"] = "http://localhost:9091"
        app.launch()

        let setupButton = app.buttons["Set Up Local Identity"]
        XCTAssertTrue(setupButton.waitForExistence(timeout: 10))
        setupButton.tap()

        let displayNameField = app.textFields["Display name"]
        XCTAssertTrue(displayNameField.waitForExistence(timeout: 10))
        displayNameField.tap()
        displayNameField.typeText("Codex Tester")

        let deviceField = app.textFields["Device label"]
        XCTAssertTrue(deviceField.waitForExistence(timeout: 10))
        deviceField.tap()
        if let currentValue = deviceField.value as? String, !currentValue.isEmpty {
            let deleteString = String(repeating: XCUIKeyboardKey.delete.rawValue, count: currentValue.count)
            deviceField.typeText(deleteString)
        }
        deviceField.typeText("Simulator Device")

        app.buttons["Create Identity"].tap()

        let composer = app.textFields["Write a private note"]
        if !composer.waitForExistence(timeout: 20) {
            let localNotes = app.staticTexts["Local Notes"]
            XCTAssertTrue(localNotes.waitForExistence(timeout: 20))
            localNotes.tap()
        }
        XCTAssertTrue(composer.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Send"].exists)
        composer.tap()
        composer.typeText("UITest secure note")
        app.buttons["Send"].tap()
        XCTAssertTrue(app.staticTexts["UITest secure note"].waitForExistence(timeout: 10))
    }
}
