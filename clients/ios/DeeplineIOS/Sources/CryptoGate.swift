import Foundation

enum CryptoBridgeStatus: String {
    case blocked
    case integrating
    case productionReady
}

struct CryptoGate {
    let signalStatus: CryptoBridgeStatus = .blocked
    let groupStatus: CryptoBridgeStatus = .blocked

    var productionLaunchAllowed: Bool {
        signalStatus == .productionReady && groupStatus == .productionReady
    }
}
