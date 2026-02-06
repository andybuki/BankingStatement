import Foundation
import PDFKit

/// Helper class for PDF text extraction using iOS PDFKit
/// Can be called from Kotlin via interop
@objc public class PdfProcessorHelper: NSObject {

    @objc public static let shared = PdfProcessorHelper()

    private override init() {
        super.init()
    }

    /// Extract text content from PDF data
    /// - Parameter pdfData: The PDF file as Data
    /// - Returns: Extracted text or nil if extraction failed
    @objc public func extractText(from pdfData: Data) -> String? {
        guard let pdfDocument = PDFDocument(data: pdfData) else {
            print("PdfProcessorHelper: Failed to create PDF document from data")
            return nil
        }

        var extractedText = ""
        let pageCount = pdfDocument.pageCount

        for pageIndex in 0..<pageCount {
            guard let page = pdfDocument.page(at: pageIndex) else {
                continue
            }

            if let pageText = page.string {
                extractedText += pageText
                // Add page separator for multi-page documents
                if pageIndex < pageCount - 1 {
                    extractedText += "\n\n"
                }
            }
        }

        // Return nil if no text was extracted
        if extractedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            print("PdfProcessorHelper: No text content found in PDF")
            return nil
        }

        return extractedText
    }

    /// Check if the data represents a valid PDF file
    /// - Parameter data: The file data to check
    /// - Returns: true if the data starts with PDF magic bytes
    @objc public func isPdfData(_ data: Data) -> Bool {
        guard data.count >= 5 else {
            return false
        }

        // Check for PDF magic bytes: %PDF-
        let pdfMagic: [UInt8] = [0x25, 0x50, 0x44, 0x46, 0x2D] // %PDF-
        let headerBytes = [UInt8](data.prefix(5))

        return headerBytes == pdfMagic
    }

    /// Get page count of a PDF document
    /// - Parameter pdfData: The PDF file as Data
    /// - Returns: Number of pages or 0 if invalid
    @objc public func getPageCount(from pdfData: Data) -> Int {
        guard let pdfDocument = PDFDocument(data: pdfData) else {
            return 0
        }
        return pdfDocument.pageCount
    }

    /// Extract text from a specific page
    /// - Parameters:
    ///   - pdfData: The PDF file as Data
    ///   - pageIndex: Zero-based page index
    /// - Returns: Text from the specified page or nil
    @objc public func extractTextFromPage(pdfData: Data, pageIndex: Int) -> String? {
        guard let pdfDocument = PDFDocument(data: pdfData),
              pageIndex >= 0 && pageIndex < pdfDocument.pageCount,
              let page = pdfDocument.page(at: pageIndex) else {
            return nil
        }

        return page.string
    }
}
