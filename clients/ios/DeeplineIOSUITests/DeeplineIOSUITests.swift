import XCTest

final class DeeplineIOSUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testIdentitySetupAndLocalNotesChatAppears() throws {
        let app = XCUIApplication()
        app.launchArguments.append("-resetDeeplineState")
        app.launchEnvironment["DEEPLINE_SERVER_URL"] = "http://localhost:9091"
        addUIInterruptionMonitor(withDescription: "Notification permission") { alert in
            guard alert.buttons.count > 0 else { return false }
            alert.buttons.element(boundBy: 0).tap()
            return true
        }
        app.launch()
        app.tap()

        let setupButton = app.buttons["Get Started"]
        XCTAssertTrue(setupButton.waitForExistence(timeout: 10))
        setupButton.tap()

        let displayNameField = app.textFields["Enter your name"]
        if !displayNameField.waitForExistence(timeout: 3), setupButton.exists {
            setupButton.tap()
        }
        XCTAssertTrue(displayNameField.waitForExistence(timeout: 10))
        displayNameField.tap()
        displayNameField.typeText("Codex Tester")

        let deviceField = app.textFields["e.g. iPhone 15 Pro"]
        XCTAssertTrue(deviceField.waitForExistence(timeout: 10))
        deviceField.tap()
        if let currentValue = deviceField.value as? String, !currentValue.isEmpty {
            let deleteString = String(repeating: XCUIKeyboardKey.delete.rawValue, count: currentValue.count)
            deviceField.typeText(deleteString)
        }
        deviceField.typeText("Simulator Device")

        app.buttons["Create Identity"].tap()

        let composer = app.textFields["Message"]
        if !composer.waitForExistence(timeout: 20) {
            let localNotes = app.staticTexts["Local Notes"]
            XCTAssertTrue(localNotes.waitForExistence(timeout: 20))
            localNotes.tap()
        }
        XCTAssertTrue(composer.waitForExistence(timeout: 10))
        composer.tap()
        composer.typeText("UITest secure note")
        let sendButton = app.buttons["Send"]
        XCTAssertTrue(sendButton.waitForExistence(timeout: 10))
        sendButton.tap()
        XCTAssertTrue(app.staticTexts["UITest secure note"].waitForExistence(timeout: 10))
    }
}
