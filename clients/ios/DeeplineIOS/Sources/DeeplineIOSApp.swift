import SwiftUI
import UserNotifications

@main
struct DeeplineIOSApp: App {
    @StateObject private var model = DeeplineAppModel()
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .onAppear {
                    appDelegate.model = model
                    requestNotificationPermissions()
                }
        }
    }

    private func requestNotificationPermissions() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
            if granted {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    weak var model: DeeplineAppModel?

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Task { @MainActor in
            await model?.registerApnsToken(deviceToken)
        }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("Failed to register for remote notifications: \(error.localizedDescription)")
    }

    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable: Any], fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        // Handle incoming push notification
        if let conversationId = userInfo["conversationId"] as? String {
            // Could update badge count or trigger refresh
            Task { @MainActor in
                await model?.loadMessages(conversationId: conversationId)
            }
        }
        completionHandler(.newData)
    }
}
