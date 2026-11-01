package ir.smartsmscrm.v4.util

import java.io.InputStream
import java.nio.charset.Charset

/** Standard vCard 2.1/3/4 reader. Reads one logical line at a time (no readLines()). */
data class ImportedPerson(val first: String, val last: String, val phone: String, val company: String = "", val address: String = "", val notes: String = "")

object VCardParser {
    private fun unescape(value: String): String = value.replace("\\n", "\n", ignoreCase = true)
        .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    fun decodeQuotedPrintable(raw: String, charset: String = "UTF-8"): String {
        val stream = java.io.ByteArrayOutputStream()
        var pos = 0
        while (pos < raw.length) {
            if (raw[pos] == '=' && pos + 2 < raw.length) {
                val byte = raw.substring(pos + 1, pos + 3).toIntOrNull(16)
                if (byte != null) { stream.write(byte); pos += 3; continue }
            }
            val next = raw.codePointAt(pos)
            stream.write(String(Character.toChars(next)).toByteArray(Charsets.UTF_8))
            pos += Character.charCount(next)
        }
        val cs = runCatching { Charset.forName(charset) }.getOrDefault(Charsets.UTF_8)
        return String(stream.toByteArray(), cs)
    }

    /** Repair the two observed forms: =D9=85... and D9=85=... (lost initial '='). */
    fun repairEncodedName(raw: String): String {
        val text = raw.trim()
        val normalized = when {
            Regex("^(?:=[0-9A-Fa-f]{2}){2,}").containsMatchIn(text) -> text
            Regex("^[0-9A-Fa-f]{2}(?:=[0-9A-Fa-f]{2}){2,}").containsMatchIn(text) -> "=$text"
            else -> return text
        }
        val fixed = runCatching { decodeQuotedPrintable(normalized) }.getOrDefault(text)
        // Do not rewrite ambiguous text unless it became clearly legible Persian/Arabic text.
        return if (Regex("[\\u0600-\\u06FF]").containsMatchIn(fixed) && !fixed.contains('\uFFFD')) fixed else text
    }

    /** Streams rows to callback; temporary memory is proportional to one contact. */
    fun parseEach(input: InputStream, emit: (ImportedPerson) -> Unit) {
        var active = false
        var full = ""; var structured = ""; var company = ""; var address = ""; var notes = ""
        val phones = arrayListOf<String>()
        fun process(line: String) {
            val colon = line.indexOf(':')
            if (colon < 0) return
            val property = line.substring(0, colon)
            val key = property.substringBefore(';').substringAfterLast('.').uppercase()
            val raw = line.substring(colon + 1)
            val charset = Regex("CHARSET=([^;:]+)", RegexOption.IGNORE_CASE).find(property)?.groupValues?.getOrNull(1) ?: "UTF-8"
            val encoded = property.contains("QUOTED-PRINTABLE", ignoreCase = true)
            val value = unescape(if (encoded) decodeQuotedPrintable(raw, charset) else repairEncodedName(raw))
            when (key) {
                "BEGIN" -> if (value.equals("VCARD", true)) {
                    active = true; full=""; structured=""; company=""; address=""; notes=""; phones.clear()
                }
                "END" -> if (active && value.equals("VCARD", true)) {
                    val parts = structured.split(';')
                    val first = parts.getOrNull(1).orEmpty().ifBlank { full.substringBefore(' ', full) }
                    val last = parts.firstOrNull().orEmpty().ifBlank { full.substringAfter(' ', "") }
                    for (phone in phones) emit(ImportedPerson(first, last, phone, company.substringBefore(';'), address.replace(';', ' ').trim(), notes))
                    active = false
                }
                "FN" -> if (active) full = value
                "N" -> if (active) structured = value
                "TEL" -> if (active) phones.add(value.removePrefix("tel:").trim())
                "ORG" -> if (active) company = value
                "ADR" -> if (active) address = value
                "NOTE" -> if (active) notes = value
            }
        }
        // vCard folds lines with leading space and QP soft breaks with trailing '='.
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            var pending: String? = null
            reader.forEachLine { line ->
                val prev = pending
                when {
                    prev == null -> pending = line
                    line.startsWith(' ') || line.startsWith('\t') -> pending = prev + line.drop(1)
                    prev.endsWith('=') && prev.substringBefore(':').contains("QUOTED-PRINTABLE", true) -> pending = prev.dropLast(1) + line
                    else -> { process(prev); pending = line }
                }
            }
            pending?.let(::process)
        }
    }

    fun parse(input: InputStream): List<ImportedPerson> = buildList { parseEach(input) { add(it) } }
}
