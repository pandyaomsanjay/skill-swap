package com.example.sgp

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Date

/**
 * Two PDFs, styled exactly like the Reports export in AdminReportsActivity
 * (landscape 842x595, #1B3C53 banner, alternating #EAF1F5 rows, truncated cells),
 * plus a vector coin mark (the 🪙 from Home) and the mandatory demo footer.
 */
object CreditPdfExporter {

    private const val PAGE_W = 842
    private const val PAGE_H = 595
    const val DEMO_FOOTER = "DEMO TRANSACTIONS ONLY – NO REAL MONEY INVOLVED."

    // ---------------- Public API ----------------

    fun exportHistory(ctx: Context, txns: List<AdminCreditTxn>): Result<String> {
        val rows = txns.sortedByDescending { it.timestamp }.map {
            listOf(
                it.id, it.userEmail, it.packageName, CreditFmt.money(it.price),
                it.credits.toString(), it.method, it.status.label, CreditFmt.dateTime(it.timestamp)
            )
        }
        return buildTable(
            ctx = ctx,
            fileName = "SkillSwap_Purchase_History.pdf",
            title = "SkillSwap — Purchase History",
            headers = listOf("Transaction ID", "User Email", "Package", "Amount", "Credits", "Method", "Status", "Date"),
            weights = listOf(0.17f, 0.21f, 0.11f, 0.08f, 0.08f, 0.10f, 0.09f, 0.16f),
            rows = rows,
            rowHeight = 22f,
            tableFraction = 1f,
            emptyMessage = "No transactions found."
        )
    }

    fun exportSummary(ctx: Context, txns: List<AdminCreditTxn>): Result<String> {
        val s = CreditStats.summarize(txns)
        val rows = listOf(
            listOf("Total purchases", s.total.toString()),
            listOf("Successful transactions", s.successful.toString()),
            listOf("Failed transactions", s.failed.toString()),
            listOf("Pending transactions", s.pending.toString()),
            listOf("Total credits purchased (successful)", s.creditsPurchased.toString()),
            listOf("Total simulated purchase amount (successful)", CreditFmt.money(s.amount))
        )
        return buildTable(
            ctx = ctx,
            fileName = "SkillSwap_Payment_Summary.pdf",
            title = "SkillSwap — Payment Summary",
            headers = listOf("Metric", "Value"),
            weights = listOf(0.68f, 0.32f),
            rows = rows,
            rowHeight = 30f,
            tableFraction = 0.7f,
            emptyMessage = null
        )
    }

    // ---------------- Table builder ----------------

    private fun buildTable(
        ctx: Context,
        fileName: String,
        title: String,
        headers: List<String>,
        weights: List<Float>,
        rows: List<List<String>>,
        rowHeight: Float,
        tableFraction: Float,
        emptyMessage: String?
    ): Result<String> {
        val leftMargin = 24f
        val headerRowHeight = 24f
        val bannerHeight = 70f
        val bottomLimit = PAGE_H - 44f

        val fullWidth = PAGE_W - leftMargin * 2
        val tableWidth = fullWidth * tableFraction
        val tableLeft = leftMargin + (fullWidth - tableWidth) / 2f
        val colW = weights.map { it * tableWidth }

        val brand = Color.parseColor("#1B3C53")
        val brandDark = Color.parseColor("#102838")

        val bannerBg = Paint().apply { color = brand; style = Paint.Style.FILL }
        val bannerAccent = Paint().apply { color = brandDark; style = Paint.Style.FILL }
        val titlePaint = Paint().apply {
            textSize = 22f; isFakeBoldText = true; color = Color.parseColor("#F9F3EF"); textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint().apply {
            textSize = 11f; color = Color.parseColor("#CBD8E1"); textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint().apply { textSize = 10f; isFakeBoldText = true; color = Color.parseColor("#F9F3EF") }
        val headerBg = Paint().apply { color = brand; style = Paint.Style.FILL }
        val bodyPaint = Paint().apply { textSize = 9f; color = Color.parseColor("#1B3C53") }
        val borderPaint = Paint().apply { color = Color.parseColor("#CCCCCC"); style = Paint.Style.STROKE; strokeWidth = 0.7f }
        val altRow = Paint().apply { color = Color.parseColor("#EAF1F5"); style = Paint.Style.FILL }
        val footerPaint = Paint().apply { textSize = 8f; color = Color.parseColor("#9CA3AF"); textAlign = Paint.Align.CENTER }
        val demoPaint = Paint().apply {
            textSize = 9f; isFakeBoldText = true; color = Color.parseColor("#B8860B"); textAlign = Paint.Align.CENTER
        }
        val emptyPaint = Paint().apply { textSize = 12f; color = Color.parseColor("#456882"); textAlign = Paint.Align.CENTER }
        val coinFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F4B400"); style = Paint.Style.FILL }
        val coinRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C98F00"); style = Paint.Style.STROKE; strokeWidth = 2f
        }
        val coinGlyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8A6200"); textSize = 18f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        }

        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y: Float

        fun drawBanner() {
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), bannerHeight, bannerBg)
            canvas.drawRect(0f, bannerHeight, PAGE_W.toFloat(), bannerHeight + 4f, bannerAccent)
            // coin logo (left)
            val cx = 52f; val cy = bannerHeight / 2f
            canvas.drawCircle(cx, cy, 20f, coinFill)
            canvas.drawCircle(cx, cy, 20f, coinRing)
            canvas.drawCircle(cx, cy, 14f, coinRing)
            canvas.drawText("S", cx, cy + 6.5f, coinGlyph)
            val centerX = PAGE_W / 2f
            canvas.drawText(title, centerX, bannerHeight / 2f, titlePaint)
            canvas.drawText("Generated: ${CreditFmt.dateTime(Date().time)}", centerX, bannerHeight / 2f + 20f, subtitlePaint)
        }

        fun drawTableHeader(startY: Float): Float {
            var x = tableLeft
            canvas.drawRect(tableLeft, startY, tableLeft + tableWidth, startY + headerRowHeight, headerBg)
            headers.forEachIndexed { i, h ->
                canvas.drawText(h, x + 4f, startY + headerRowHeight - 7f, headerPaint)
                canvas.drawRect(x, startY, x + colW[i], startY + headerRowHeight, borderPaint)
                x += colW[i]
            }
            return startY + headerRowHeight
        }

        fun drawFooter() {
            canvas.drawText(DEMO_FOOTER, PAGE_W / 2f, PAGE_H - 24f, demoPaint)
            canvas.drawText("Page $pageNumber", PAGE_W / 2f, PAGE_H - 10f, footerPaint)
        }

        fun truncate(text: String, maxWidth: Float, paint: Paint): String {
            if (paint.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
            return text.substring(0, end) + "…"
        }

        fun newPage() {
            drawFooter()
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            canvas = page.canvas
            drawBanner()
            y = drawTableHeader(bannerHeight + 24f)
        }

        drawBanner()
        y = drawTableHeader(bannerHeight + 24f)

        if (rows.isEmpty() && emptyMessage != null) {
            canvas.drawText(emptyMessage, PAGE_W / 2f, y + 60f, emptyPaint)
        }

        rows.forEachIndexed { rowIndex, values ->
            if (y + rowHeight > bottomLimit) newPage()
            if (rowIndex % 2 == 1) canvas.drawRect(tableLeft, y, tableLeft + tableWidth, y + rowHeight, altRow)
            var x = tableLeft
            values.forEachIndexed { i, v ->
                val txt = truncate(v, colW[i] - 8f, bodyPaint)
                canvas.drawText(txt, x + 4f, y + rowHeight / 2f + 3.5f, bodyPaint)
                canvas.drawRect(x, y, x + colW[i], y + rowHeight, borderPaint)
                x += colW[i]
            }
            y += rowHeight
        }

        drawFooter()
        pdf.finishPage(page)

        return try {
            val out = openDownloadsOutputStream(ctx, fileName, "application/pdf")
                ?: return Result.failure(Exception("Could not open Downloads"))
            out.use { pdf.writeTo(it) }
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            pdf.close()
        }
    }

    private fun openDownloadsOutputStream(ctx: Context, fileName: String, mime: String): OutputStream? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            ctx.contentResolver.openOutputStream(uri)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            FileOutputStream(File(dir, fileName))
        }
    }
}