package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.database.DetectionHistory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtil {

    private fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = getFileUri(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share $title"))
    }

    fun generateCSV(context: Context, historyList: List<DetectionHistory>): File {
        val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "detection_report_${System.currentTimeMillis()}.csv"
        val file = File(cacheDir, fileName)

        FileOutputStream(file).use { out ->
            // Write CSV Header
            out.write("ID,Session Name,Date,Source Type,Total Count,Label Breakdown,Processing Time (ms)\n".toByteArray())
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            for (h in historyList) {
                val dateStr = dateFormat.format(Date(h.date))
                // Clean CSV values to avoid quote collisions
                val cleanSession = h.sessionName.replace("\"", "\"\"")
                val cleanBreakdown = h.labeledCounts.replace("\"", "\"\"")
                
                out.write("\"${h.id}\",\"$cleanSession\",\"$dateStr\",\"${h.sourceType}\",\"${h.totalObjectsCount}\",\"$cleanBreakdown\",\"${h.processingTimeMs}\"\n".toByteArray())
            }
        }
        return file
    }

    fun generatePDF(context: Context, history: DetectionHistory): File {
        val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "detection_report_${history.id}.pdf"
        val file = File(cacheDir, fileName)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size: 595 x 842 points
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(22, 163, 74) // Emerald green
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(75, 85, 99)
            textSize = 12f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            isAntiAlias = true
        }

        val boldTextPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(209, 213, 219)
            strokeWidth = 1f
        }

        // Drawer
        var y = 50f
        canvas.drawText("OBJECT COUNTER AI - REPORT", 50f, y, titlePaint)
        
        y += 20f
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy HH:mm:ss", Locale.getDefault())
        canvas.drawText("Generated on: ${dateFormat.format(Date())}", 50f, y, subtitlePaint)
        
        y += 30f
        canvas.drawLine(50f, y, 545f, y, linePaint)
        
        y += 40f
        canvas.drawText("Session Name:", 50f, y, boldTextPaint)
        canvas.drawText(history.sessionName, 180f, y, textPaint)
        
        y += 25f
        canvas.drawText("Scan Date:", 50f, y, boldTextPaint)
        canvas.drawText(dateFormat.format(Date(history.date)), 180f, y, textPaint)
        
        y += 25f
        canvas.drawText("Source Category:", 50f, y, boldTextPaint)
        canvas.drawText(history.sourceType.uppercase(Locale.getDefault()), 180f, y, textPaint)
        
        y += 25f
        canvas.drawText("Processing Speed:", 50f, y, boldTextPaint)
        canvas.drawText("${history.processingTimeMs} ms", 180f, y, textPaint)
        
        y += 40f
        canvas.drawLine(50f, y, 545f, y, linePaint)
        
        y += 35f
        canvas.drawText("DETECTION RESULTS BREAKDOWN", 50f, y, titlePaint.apply { textSize = 16f })
        
        y += 30f
        canvas.drawText("Total Items Counted:", 50f, y, boldTextPaint)
        canvas.drawText("${history.totalObjectsCount}", 220f, y, boldTextPaint.apply { color = Color.rgb(22, 163, 74) })
        boldTextPaint.color = Color.BLACK // Revert
        
        y += 40f
        // Headers of dynamic table
        canvas.drawText("Object / Class", 70f, y, boldTextPaint)
        canvas.drawText("Quantity Detected", 350f, y, boldTextPaint)
        
        y += 10f
        canvas.drawLine(50f, y, 545f, y, linePaint)
        
        // Split breakdown "person: 2, car: 1"
        val rows = history.labeledCounts.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        y += 20f
        for (row in rows) {
            val parts = row.split(":")
            val objectName = parts.getOrNull(0)?.trim()?.replaceFirstChar { it.titlecase() } ?: "Unknown"
            val qty = parts.getOrNull(1)?.trim() ?: "0"
            
            canvas.drawText(objectName, 70f, y, textPaint)
            canvas.drawText(qty, 350f, y, textPaint)
            
            y += 22f
            if (y > 780f) break // Avoid page overflow
        }
        
        y += 20f
        canvas.drawLine(50f, y, 545f, y, linePaint)
        
        y = 800f
        canvas.drawText("Report powered by Object Counter AI (Offline)", 50f, y, subtitlePaint)

        document.finishPage(page)
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }
}
