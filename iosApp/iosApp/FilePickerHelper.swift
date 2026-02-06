import Foundation
import UIKit
import UniformTypeIdentifiers

/// Coordinator class that handles UIDocumentPickerViewController
class FilePickerCoordinator: NSObject, UIDocumentPickerDelegate {

    private var onFilePicked: ((Data, String) -> Void)?
    private var onCancelled: (() -> Void)?

    func pickFile(
        from viewController: UIViewController,
        onFilePicked: @escaping (Data, String) -> Void,
        onCancelled: @escaping () -> Void
    ) {
        self.onFilePicked = onFilePicked
        self.onCancelled = onCancelled

        // Define supported file types
        let supportedTypes: [UTType] = [
            .pdf,
            .commaSeparatedText,
            UTType(filenameExtension: "csv") ?? .commaSeparatedText,
            UTType(filenameExtension: "xlsx") ?? .data,
            UTType(filenameExtension: "xls") ?? .data,
            .spreadsheet,
            .data
        ].compactMap { $0 }

        let picker = UIDocumentPickerViewController(forOpeningContentTypes: supportedTypes)
        picker.delegate = self
        picker.allowsMultipleSelection = false
        picker.modalPresentationStyle = .formSheet

        viewController.present(picker, animated: true)
    }

    // MARK: - UIDocumentPickerDelegate

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else {
            onCancelled?()
            return
        }

        // Start accessing security-scoped resource
        guard url.startAccessingSecurityScopedResource() else {
            print("Failed to access security scoped resource")
            onCancelled?()
            return
        }

        defer {
            url.stopAccessingSecurityScopedResource()
        }

        do {
            let data = try Data(contentsOf: url)
            let fileName = url.lastPathComponent
            onFilePicked?(data, fileName)
        } catch {
            print("Error reading file: \(error)")
            onCancelled?()
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        onCancelled?()
    }
}

/// Singleton helper for file picking that can be accessed from Kotlin
@objc public class FilePickerHelper: NSObject {

    @objc public static let shared = FilePickerHelper()

    private let coordinator = FilePickerCoordinator()
    private var pendingCallback: ((Data?, String?) -> Void)?

    private override init() {
        super.init()
    }

    /// Called from Kotlin to trigger file picker
    @objc public func pickFile(completion: @escaping (Data?, String?) -> Void) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self,
                  let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let rootViewController = windowScene.windows.first?.rootViewController else {
                completion(nil, nil)
                return
            }

            // Find the topmost presented view controller
            var topController = rootViewController
            while let presented = topController.presentedViewController {
                topController = presented
            }

            self.coordinator.pickFile(
                from: topController,
                onFilePicked: { data, fileName in
                    completion(data, fileName)
                },
                onCancelled: {
                    completion(nil, nil)
                }
            )
        }
    }
}
