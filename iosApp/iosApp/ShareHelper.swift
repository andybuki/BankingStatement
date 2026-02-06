import Foundation
import UIKit

/// Helper class for sharing files and content on iOS
/// Uses UIActivityViewController for native sharing
@objc public class ShareHelper: NSObject {

    @objc public static let shared = ShareHelper()

    private override init() {
        super.init()
    }

    /// Share a file with the given data and filename
    /// - Parameters:
    ///   - data: The file content as Data
    ///   - fileName: The name of the file to share
    ///   - mimeType: Optional MIME type hint
    @objc public func shareFile(data: Data, fileName: String, mimeType: String? = nil) {
        DispatchQueue.main.async {
            // Create a temporary file URL
            let tempDir = FileManager.default.temporaryDirectory
            let fileURL = tempDir.appendingPathComponent(fileName)

            do {
                try data.write(to: fileURL)

                guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                      let rootViewController = windowScene.windows.first?.rootViewController else {
                    print("ShareHelper: Could not find root view controller")
                    return
                }

                // Find the topmost presented view controller
                var topController = rootViewController
                while let presented = topController.presentedViewController {
                    topController = presented
                }

                let activityVC = UIActivityViewController(
                    activityItems: [fileURL],
                    applicationActivities: nil
                )

                // For iPad, configure popover
                if let popover = activityVC.popoverPresentationController {
                    popover.sourceView = topController.view
                    popover.sourceRect = CGRect(
                        x: topController.view.bounds.midX,
                        y: topController.view.bounds.midY,
                        width: 0,
                        height: 0
                    )
                    popover.permittedArrowDirections = []
                }

                // Clean up temp file after sharing is complete
                activityVC.completionWithItemsHandler = { _, _, _, _ in
                    try? FileManager.default.removeItem(at: fileURL)
                }

                topController.present(activityVC, animated: true)

            } catch {
                print("ShareHelper: Error writing temp file: \(error)")
            }
        }
    }

    /// Share text content directly
    /// - Parameter text: The text content to share
    @objc public func shareText(_ text: String) {
        DispatchQueue.main.async {
            guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let rootViewController = windowScene.windows.first?.rootViewController else {
                print("ShareHelper: Could not find root view controller")
                return
            }

            var topController = rootViewController
            while let presented = topController.presentedViewController {
                topController = presented
            }

            let activityVC = UIActivityViewController(
                activityItems: [text],
                applicationActivities: nil
            )

            if let popover = activityVC.popoverPresentationController {
                popover.sourceView = topController.view
                popover.sourceRect = CGRect(
                    x: topController.view.bounds.midX,
                    y: topController.view.bounds.midY,
                    width: 0,
                    height: 0
                )
                popover.permittedArrowDirections = []
            }

            topController.present(activityVC, animated: true)
        }
    }

    /// Save file to the Files app
    /// - Parameters:
    ///   - data: The file content as Data
    ///   - fileName: The name of the file to save
    @objc public func saveToFiles(data: Data, fileName: String) {
        DispatchQueue.main.async {
            // Create a temporary file URL
            let tempDir = FileManager.default.temporaryDirectory
            let fileURL = tempDir.appendingPathComponent(fileName)

            do {
                try data.write(to: fileURL)

                guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                      let rootViewController = windowScene.windows.first?.rootViewController else {
                    print("ShareHelper: Could not find root view controller")
                    return
                }

                var topController = rootViewController
                while let presented = topController.presentedViewController {
                    topController = presented
                }

                let documentPicker = UIDocumentPickerViewController(forExporting: [fileURL])
                documentPicker.modalPresentationStyle = .formSheet

                topController.present(documentPicker, animated: true)

            } catch {
                print("ShareHelper: Error writing temp file: \(error)")
            }
        }
    }
}
