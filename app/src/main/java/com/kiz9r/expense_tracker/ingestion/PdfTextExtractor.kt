package com.kiz9r.expense_tracker.ingestion

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import android.provider.OpenableColumns
import com.kiz9r.expense_tracker.domain.maskAccounts
import javax.inject.Inject

class PdfTextExtractor @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
            if(it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }?.let { maskAccounts(it.take(160)) } ?: "SBI statement.pdf"

    suspend fun read(uri: Uri, password: CharArray): Pair<ByteArray,String> {
        var bytes: ByteArray?=null
        var delivered=false
        try {
            return withContext(Dispatchers.IO) {
                PDFBoxResourceLoader.init(context)
                val scope=coroutineContext
                val data=requireNotNull(context.contentResolver.openInputStream(uri)) { "Unable to open file." }
                    .use { it.readBytesLimited(20*1024*1024) { scope.ensureActive() } }
                bytes=data
                require(data.take(5).toByteArray().toString(Charsets.US_ASCII)=="%PDF-") { "Select a PDF statement." }
                PDDocument.load(data,String(password)).use { document ->
                    require(document.numberOfPages in 1..250) { "PDF must contain 1–250 pages." }
                    scope.ensureActive()
                    // Content order keeps SBI table placeholder text separate from its overlaid opening-balance label.
                    val text=PDFTextStripper().apply { sortByPosition=false }.getText(document)
                    scope.ensureActive()
                    require(text.isNotBlank()) { "This PDF has no readable text. Scanned PDFs are unsupported." }
                    require(text.length<=8_000_000) { "Statement text is too large." }
                    data to text
                }
            }.also { delivered=true }
        } finally {
            password.fill('\u0000')
            if(!delivered) bytes?.fill(0)
        }
    }
}
fun java.io.InputStream.readBytesLimited(limit: Int, checkCancellation: () -> Unit = {}): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        checkCancellation()
        val count = read(buffer)
        if (count < 0) break
        require(output.size().toLong() + count <= limit) { "File exceeds the supported size limit." }
        output.write(buffer,0,count)
    }
    return output.toByteArray()
}
