package com.banking.statement.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enhanced PDF generator with professional layout
 */
class PdfGenerator(private val context: Context) {

    private val pageWidth = 595 // A4 width in points
    private val pageHeight = 842 // A4 height in points
    private val margin = 50f
    private val contentWidth = pageWidth - (margin * 2)

    // Color palette
    private val primaryColor = Color.parseColor("#1976D2")
    private val accentColor = Color.parseColor("#2196F3")
    private val successColor = Color.parseColor("#4CAF50")
    private val errorColor = Color.parseColor("#F44336")
    private val lightGray = Color.parseColor("#F5F5F5")
    private val mediumGray = Color.parseColor("#9E9E9E")
    private val darkGray = Color.parseColor("#424242")

    // Paint styles
    private val headerPaint = Paint().apply {
        color = primaryColor
        textSize = 28f
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val titlePaint = Paint().apply {
        color = darkGray
        textSize = 18f
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val subtitlePaint = Paint().apply {
        color = darkGray
        textSize = 14f
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 11f
        isAntiAlias = true
    }

    private val smallTextPaint = Paint().apply {
        color = mediumGray
        textSize = 9f
        isAntiAlias = true
    }

    private val boldTextPaint = Paint().apply {
        color = Color.BLACK
        textSize = 11f
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val positiveAmountPaint = Paint().apply {
        color = successColor
        textSize = 12f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }

    private val negativeAmountPaint = Paint().apply {
        color = errorColor
        textSize = 12f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }

    private val linePaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val thickLinePaint = Paint().apply {
        color = primaryColor
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val boxPaint = Paint().apply {
        color = lightGray
        style = Paint.Style.FILL
    }

    private val chartBarPaint = Paint().apply {
        color = accentColor
        style = Paint.Style.FILL
    }

    /**
     * Generate a professional PDF file
     */
    fun generatePdf(content: PdfContent, fileName: String): ExportResult {
        return try {
            val document = PdfDocument()
            var currentPage: PdfDocument.Page? = null
            var canvas: Canvas? = null
            var yPosition = 0f
            var pageNumber = 0
            val totalPages = estimateTotalPages(content)

            fun startNewPage() {
                currentPage?.let {
                    drawFooter(it.canvas, pageNumber, totalPages)
                    document.finishPage(it)
                }
                pageNumber++
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                currentPage = document.startPage(pageInfo)
                canvas = currentPage?.canvas
                yPosition = margin

                // Draw header on each page
                if (pageNumber == 1) {
                    drawHeader(canvas!!)
                    yPosition += 120f
                } else {
                    yPosition += 30f
                }
            }

            fun checkNewPage(requiredHeight: Float) {
                if (yPosition + requiredHeight > pageHeight - margin - 30f) {
                    startNewPage()
                }
            }

            startNewPage()

            for (section in content.sections) {
                when (section) {
                    is PdfSection.Title -> {
                        checkNewPage(50f)
                        yPosition += 10f
                        canvas?.drawLine(margin, yPosition, pageWidth - margin, yPosition, thickLinePaint)
                        yPosition += 15f
                        canvas?.drawText(section.text, margin, yPosition, titlePaint)
                        yPosition += 20f
                        canvas?.drawLine(margin, yPosition, pageWidth - margin, yPosition, linePaint)
                        yPosition += 20f
                    }

                    is PdfSection.Subtitle -> {
                        checkNewPage(40f)
                        yPosition += 8f
                        canvas?.drawText(section.text, margin, yPosition, subtitlePaint)
                        yPosition += 25f
                    }

                    is PdfSection.DateHeader -> {
                        checkNewPage(35f)
                        // Draw date in a box
                        val boxHeight = 25f
                        canvas?.drawRoundRect(
                            RectF(margin, yPosition - 18f, pageWidth - margin, yPosition + 7f),
                            5f, 5f, boxPaint
                        )
                        canvas?.drawText(section.date, margin + 10f, yPosition, boldTextPaint)
                        yPosition += 30f
                    }

                    is PdfSection.Transaction -> {
                        checkNewPage(40f)
                        // Transaction row with alternating background
                        if ((section.description.hashCode() % 2) == 0) {
                            canvas?.drawRect(
                                margin - 5f, yPosition - 15f,
                                pageWidth - margin + 5f, yPosition + 18f,
                                boxPaint
                            )
                        }

                        // Description
                        canvas?.drawText(
                            truncateText(section.description, 50),
                            margin + 5f, yPosition, textPaint
                        )
                        // Amount
                        val amountPaint = if (section.isPositive) positiveAmountPaint else negativeAmountPaint
                        canvas?.drawText(section.amount, pageWidth - margin - 5f, yPosition, amountPaint)
                        yPosition += 15f

                        // Category/Counterparty
                        canvas?.drawText(section.category, margin + 5f, yPosition, smallTextPaint)
                        yPosition += 20f
                    }

                    is PdfSection.SummaryLine -> {
                        checkNewPage(30f)
                        // Draw in a highlighted box
                        canvas?.drawRoundRect(
                            RectF(margin, yPosition - 18f, pageWidth - margin, yPosition + 10f),
                            8f, 8f, boxPaint
                        )
                        canvas?.drawRoundRect(
                            RectF(margin, yPosition - 18f, pageWidth - margin, yPosition + 10f),
                            8f, 8f, linePaint
                        )

                        canvas?.drawText(section.label, margin + 10f, yPosition, boldTextPaint)
                        val amountPaint = if (section.isPositive) positiveAmountPaint else negativeAmountPaint
                        amountPaint.textSize = 14f
                        canvas?.drawText(section.value, pageWidth - margin - 10f, yPosition, amountPaint)
                        amountPaint.textSize = 12f
                        yPosition += 35f
                    }

                    is PdfSection.CategoryRow -> {
                        checkNewPage(35f)
                        // Draw category bar chart
                        val barWidth = (section.percentage.replace("%", "").toFloatOrNull() ?: 0f) / 100f * (contentWidth * 0.4f)
                        canvas?.drawRoundRect(
                            RectF(margin + 200f, yPosition - 12f, margin + 200f + barWidth, yPosition + 3f),
                            3f, 3f, chartBarPaint
                        )

                        // Category name
                        val emoji = getCategoryEmoji(section.name)
                        canvas?.drawText("$emoji ${section.name} (${section.count})", margin, yPosition, textPaint)

                        // Percentage
                        canvas?.drawText(section.percentage, margin + 160f, yPosition, smallTextPaint)

                        // Amount
                        canvas?.drawText(section.amount, pageWidth - margin, yPosition, negativeAmountPaint)
                        yPosition += 25f
                    }

                    is PdfSection.MonthlyRow -> {
                        checkNewPage(60f)
                        // Month header
                        canvas?.drawRoundRect(
                            RectF(margin, yPosition - 18f, pageWidth - margin, yPosition + 10f),
                            5f, 5f, boxPaint
                        )
                        canvas?.drawText(section.month, margin + 10f, yPosition, subtitlePaint)
                        yPosition += 25f

                        // Income/Expenses/Net in columns
                        val colWidth = contentWidth / 3f
                        canvas?.drawText("Income:", margin + 10f, yPosition, smallTextPaint)
                        canvas?.drawText(section.income, margin + colWidth - 50f, yPosition, positiveAmountPaint)

                        canvas?.drawText("Expenses:", margin + colWidth + 10f, yPosition, smallTextPaint)
                        negativeAmountPaint.textAlign = Paint.Align.LEFT
                        canvas?.drawText(section.expenses, margin + colWidth * 2 - 50f, yPosition, negativeAmountPaint)
                        negativeAmountPaint.textAlign = Paint.Align.RIGHT

                        canvas?.drawText("Net:", margin + colWidth * 2 + 10f, yPosition, smallTextPaint)
                        val netPaint = if (section.isPositive) positiveAmountPaint else negativeAmountPaint
                        canvas?.drawText(section.net, pageWidth - margin - 10f, yPosition, netPaint)
                        yPosition += 30f
                    }

                    PdfSection.Spacer -> {
                        yPosition += 20f
                    }
                }
            }

            // Finish last page
            currentPage?.let {
                drawFooter(it.canvas, pageNumber, totalPages)
                document.finishPage(it)
            }

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

    private fun drawHeader(canvas: Canvas) {
        // Title
        canvas.drawText("Banking Statement Analysis", margin, margin + 30f, headerPaint)

        // Subtitle line
        canvas.drawLine(margin, margin + 45f, pageWidth - margin, margin + 45f, thickLinePaint)

        // Generation date
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        canvas.drawText(
            "Generated: ${dateFormat.format(Date())}",
            margin, margin + 65f, smallTextPaint
        )

        // Decorative line
        canvas.drawLine(margin, margin + 75f, pageWidth - margin, margin + 75f, linePaint)
    }

    private fun drawFooter(canvas: Canvas, currentPage: Int, totalPages: Int) {
        val y = pageHeight - margin + 10f

        // Page number
        val footerText = "Page $currentPage of $totalPages"
        val footerWidth = smallTextPaint.measureText(footerText)
        canvas.drawText(
            footerText,
            (pageWidth - footerWidth) / 2,
            y,
            smallTextPaint
        )

        // Copyright/App name
        canvas.drawText(
            "Banking Statement App",
            margin,
            y,
            smallTextPaint
        )
    }

    private fun estimateTotalPages(content: PdfContent): Int {
        var estimatedHeight = 120f // Header
        for (section in content.sections) {
            estimatedHeight += when (section) {
                is PdfSection.Title -> 50f
                is PdfSection.Subtitle -> 40f
                is PdfSection.DateHeader -> 35f
                is PdfSection.Transaction -> 40f
                is PdfSection.SummaryLine -> 30f
                is PdfSection.CategoryRow -> 35f
                is PdfSection.MonthlyRow -> 60f
                PdfSection.Spacer -> 20f
            }
        }
        val usablePageHeight = pageHeight - (margin * 2) - 30f // 30f for footer
        return ((estimatedHeight / usablePageHeight) + 1).toInt()
    }

    private fun truncateText(text: String, maxLength: Int): String {
        return if (text.length > maxLength) {
            text.take(maxLength - 3) + "..."
        } else {
            text
        }
    }

    private fun getCategoryEmoji(categoryName: String): String {
        return when {
            categoryName.contains("Groceries", ignoreCase = true) -> "🛒"
            categoryName.contains("Restaurant", ignoreCase = true) -> "🍽️"
            categoryName.contains("Transport", ignoreCase = true) -> "🚗"
            categoryName.contains("Shopping", ignoreCase = true) -> "🛍️"
            categoryName.contains("Health", ignoreCase = true) -> "🏥"
            categoryName.contains("Entertainment", ignoreCase = true) -> "🎬"
            categoryName.contains("Utilities", ignoreCase = true) -> "💡"
            categoryName.contains("Salary", ignoreCase = true) -> "💰"
            categoryName.contains("Rent", ignoreCase = true) -> "🏠"
            else -> "📌"
        }
    }
}
