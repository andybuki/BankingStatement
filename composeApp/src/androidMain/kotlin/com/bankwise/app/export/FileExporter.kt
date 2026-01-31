package com.bankwise.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Android file exporter for saving and sharing files
 */
class FileExporter(private val context: Context) {

    private val authority = "${context.packageName}.fileprovider"

    /**
     * Save CSV content to a file
     */
    fun saveCsv(content: String, fileName: String): ExportResult {
        return try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val file = File(exportDir, fileName)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            }

            ExportResult(
                success = true,
                filePath = file.absolutePath,
                fileName = fileName,
                mimeType = "text/csv"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ExportResult(
                success = false,
                errorMessage = "Failed to save CSV: ${e.message}"
            )
        }
    }

    /**
     * Create a share intent for an exported file
     */
    fun createShareIntent(result: ExportResult): Intent? {
        if (!result.success || result.filePath == null) return null

        return try {
            val file = File(result.filePath)
            val uri = FileProvider.getUriForFile(context, authority, file)

            Intent(Intent.ACTION_SEND).apply {
                type = result.mimeType ?: "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, result.fileName ?: "Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get content URI for a file
     */
    fun getFileUri(filePath: String): Uri? {
        return try {
            val file = File(filePath)
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Clean up old export files (older than 24 hours)
     */
    fun cleanupOldExports() {
        try {
            val exportDir = File(context.cacheDir, "exports")
            if (exportDir.exists()) {
                val cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                exportDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoffTime) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
