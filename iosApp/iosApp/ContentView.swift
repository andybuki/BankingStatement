import UIKit
import SwiftUI
import ComposeApp
import UniformTypeIdentifiers

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @State private var showFilePicker = false
    @State private var showShareSheet = false
    @State private var shareContent: String = ""
    @State private var shareFileName: String = ""

    var body: some View {
        ComposeView()
        .ignoresSafeArea()
        .onReceive(NotificationCenter.default.publisher(for: Notification.Name("ShowFilePicker"))) { _ in
            showFilePicker = true
        }
        .onReceive(NotificationCenter.default.publisher(for: Notification.Name("ShareContent"))) { notification in
            if let userInfo = notification.object as? [String: Any],
               let content = userInfo["content"] as? String,
               let fileName = userInfo["fileName"] as? String {
                shareContent = content
                shareFileName = fileName
                showShareSheet = true
            }
        }
        .sheet(isPresented: $showFilePicker) {
            DocumentPicker(
                onFilePicked: { data, fileName in
                    // Call back to Kotlin via the exposed function
                    IosFilePickerBridgeKt.onFilePickerResult(data: data as NSData, fileName: fileName)
                    showFilePicker = false
                },
                onCancelled: {
                    IosFilePickerBridgeKt.onFilePickerCancelled()
                    showFilePicker = false
                }
            )
        }
        .sheet(isPresented: $showShareSheet) {
            ShareSheet(content: shareContent, fileName: shareFileName)
        }
    }
}

/// Document picker wrapper for SwiftUI
struct DocumentPicker: UIViewControllerRepresentable {
    var onFilePicked: (Data, String) -> Void
    var onCancelled: () -> Void

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let supportedTypes: [UTType] = [
            .pdf,
            .commaSeparatedText,
            UTType(filenameExtension: "csv") ?? .commaSeparatedText,
            UTType(filenameExtension: "xlsx") ?? .data,
            UTType(filenameExtension: "xls") ?? .data,
            .data
        ].compactMap { $0 }

        let picker = UIDocumentPickerViewController(forOpeningContentTypes: supportedTypes)
        picker.delegate = context.coordinator
        picker.allowsMultipleSelection = false
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    class Coordinator: NSObject, UIDocumentPickerDelegate {
        var parent: DocumentPicker

        init(_ parent: DocumentPicker) {
            self.parent = parent
        }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            guard let url = urls.first else {
                parent.onCancelled()
                return
            }

            guard url.startAccessingSecurityScopedResource() else {
                parent.onCancelled()
                return
            }

            defer {
                url.stopAccessingSecurityScopedResource()
            }

            do {
                let data = try Data(contentsOf: url)
                let fileName = url.lastPathComponent
                parent.onFilePicked(data, fileName)
            } catch {
                print("Error reading file: \(error)")
                parent.onCancelled()
            }
        }

        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
            parent.onCancelled()
        }
    }
}

/// Share sheet wrapper for SwiftUI
struct ShareSheet: UIViewControllerRepresentable {
    var content: String
    var fileName: String

    func makeUIViewController(context: Context) -> UIActivityViewController {
        // Create a temporary file
        let tempDir = FileManager.default.temporaryDirectory
        let fileURL = tempDir.appendingPathComponent(fileName)

        do {
            try content.write(to: fileURL, atomically: true, encoding: .utf8)
            return UIActivityViewController(activityItems: [fileURL], applicationActivities: nil)
        } catch {
            // Fall back to sharing text directly
            return UIActivityViewController(activityItems: [content], applicationActivities: nil)
        }
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
