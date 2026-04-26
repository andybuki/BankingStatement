package com.banking.statement.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

actual class PdfPageRenderer {
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    actual fun open(filePath: String): Boolean {
        close()
        return try {
            val file = File(filePath)
            if (!file.exists()) return false
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor = pfd
            renderer = PdfRenderer(pfd)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            close()
            false
        }
    }

    actual fun pageCount(): Int = renderer?.pageCount ?: 0

    actual fun renderPage(pageIndex: Int, targetWidth: Int): ImageBitmap? {
        val r = renderer ?: return null
        if (pageIndex < 0 || pageIndex >= r.pageCount) return null
        return try {
            r.openPage(pageIndex).use { page ->
                val safeWidth = targetWidth.coerceAtLeast(1)
                val scale = safeWidth.toFloat() / page.width.toFloat()
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(safeWidth, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.asImageBitmap()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    actual fun close() {
        try {
            renderer?.close()
        } catch (_: Exception) { /* ignore */ }
        try {
            descriptor?.close()
        } catch (_: Exception) { /* ignore */ }
        renderer = null
        descriptor = null
    }
}
