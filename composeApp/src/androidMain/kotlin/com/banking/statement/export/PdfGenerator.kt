package com.banking.statement.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Android-specific PDF generator
 */
class PdfGenerator(private val context: Context) {

    private val pageWidth = 595 // A4 width in points
    private val pageHeight = 842 // A4 height in points
    private val margin = 40f
    private val contentWidth = pageWidth - (margin * 2)

    private val titlePaint = Paint().apply {
        color = Color.parseColor("#1976D2")
        textSize = 24f
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val subtitlePaint = Paint().apply {
        color = Color.parseColor("#424242")
        textSize = 16f
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val datePaint = Paint().apply {
        color = Color.parseColor("#757575")
        textSize = 14f
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#212121")
        textSize = 12f
        isAntiAlias = true
    }

    private val categoryPaint = Paint().apply {
        color = Color.parseColor("#9E9E9E")
        textSize = 10f
        isAntiAlias = true
    }

    private val positiveAmountPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        textSize = 12f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }

    private val negativeAmountPaint = Paint().apply {
        color = Color.parseColor("#E57373")
        textSize = 12f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }

    /**
     * Generate a PDF file from content
     */
    fun generatePdf(content: PdfContent, fileName: String): ExportResult {
        return try {
            val document = PdfDocument()
            var currentPage: PdfDocument.Page? = null
            var canvas: Canvas? = null
            var yPosition = 0f
            var pageNumber = 0

            fun startNewPage() {
                currentPage?.let { document.finishPage(it) }
                pageNumber++
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                currentPage = document.startPage(pageInfo)
                canvas = currentPage?.canvas
                yPosition = margin + 20f
            }

            fun checkNewPage(requiredHeight: Float) {
                if (yPosition + requiredHeight > pageHeight - margin) {
                    startNewPage()
                }
            }

            startNewPage()

            for (section in content.sections) {
                when (section) {
                    is PdfSection.Title -> {
                        checkNewPage(40f)
                        canvas?.drawText(section.text, margin, yPosition, titlePaint)
                        yPosition += 35f
                    }

                    is PdfSection.Subtitle -> {
                        checkNewPage(30f)
                        canvas?.drawText(section.text, margin, yPosition, subtitlePaint)
                        yPosition += 25f
                    }

                    is PdfSection.DateHeader -> {
                        checkNewPage(25f)
                        canvas?.drawText(section.date, margin, yPosition, datePaint)
                        yPosition += 20f
                    }

                    is PdfSection.Transaction -> {
                        checkNewPage(35f)
                        // Description
                        canvas?.drawText(section.description.take(40), margin + 10f, yPosition, textPaint)
                        // Amount (right-aligned)
                        val amountPaint = if (section.isPositive) positiveAmountPaint else negativeAmountPaint
                        canvas?.drawText(section.amount, pageWidth - margin, yPosition, amountPaint)
                        yPosition += 14f
                        // Category
                        canvas?.drawText(section.category, margin + 10f, yPosition, categoryPaint)
                        yPosition += 18f
                    }

                    is PdfSection.SummaryLine -> {
                        checkNewPage(25f)
                        canvas?.drawText(section.label, margin, yPosition, textPaint)
                        val amountPaint = if (section.isPositive) positiveAmountPaint else negativeAmountPaint
                        canvas?.drawText(section.value, pageWidth - margin, yPosition, amountPaint)
                        yPosition += 22f
                    }

                    is PdfSection.CategoryRow -> {
                        checkNewPage(30f)
                        // Name and count
                        canvas?.drawText("${section.name} (${section.count})", margin, yPosition, textPaint)
                        // Percentage
                        canvas?.drawText(section.percentage, pageWidth - margin - 80f, yPosition, categoryPaint)
                        // Amount
                        canvas?.drawText(section.amount, pageWidth - margin, yPosition, negativeAmountPaint)
                        yPosition += 22f
                    }

                    is PdfSection.MonthlyRow -> {
                        checkNewPage(40f)
                        // Month
                        canvas?.drawText(section.month, margin, yPosition, subtitlePaint)
                        yPosition += 18f
                        // Income / Expenses / Net
                        canvas?.drawText("Income: ${section.income}", margin + 10f, yPosition, textPaint)
                        canvas?.drawText("Expenses: ${section.expenses}", margin + 150f, yPosition, textPaint)
                        val netPaint = if (section.isPositive) positiveAmountPaint else negativeAmountPaint
                        netPaint.textAlign = Paint.Align.LEFT
                        canvas?.drawText("Net: ${section.net}", margin + 310f, yPosition, netPaint)
                        netPaint.textAlign = Paint.Align.RIGHT
                        yPosition += 22f
                    }

                    PdfSection.Spacer -> {
                        yPosition += 15f
                    }
                }
            }

            // Finish the last page
            currentPage?.let { document.finishPage(it) }

            // Save to file
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val file = File(exportDir, fileName)
            FileOutputStream(file).use { outputStream ->
                document.writeTo(outputStream)
            }
            document.close()

            ExportResult(
                success = true,
                filePath = file.absolutePath,
                fileName = fileName,
                mimeType = "application/pdf"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ExportResult(
                success = false,
                errorMessage = "Failed to generate PDF: ${e.message}"
            )
        }
    }
}
