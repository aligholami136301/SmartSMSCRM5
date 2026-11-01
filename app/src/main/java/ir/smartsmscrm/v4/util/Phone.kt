package ir.smartsmscrm.v4.util

object Phone {
    fun normalize(raw: String): String? {
        val digits = buildString {
            raw.trim().forEach { c -> append(when(c) {
                in '۰'..'۹' -> ('0'.code + c.code - '۰'.code).toChar()
                in '٠'..'٩' -> ('0'.code + c.code - '٠'.code).toChar()
                else -> c
            }) }
        }.replace(Regex("[\\s()\\-]"), "")
        val national = when {
            digits.matches(Regex("09[0-9]{9}")) -> digits
            digits.matches(Regex("9[0-9]{9}")) -> "0$digits"
            digits.matches(Regex("\\+989[0-9]{9}")) -> "0" + digits.drop(3)
            digits.matches(Regex("989[0-9]{9}")) -> "0" + digits.drop(2)
            digits.matches(Regex("00989[0-9]{9}")) -> "0" + digits.drop(4)
            else -> null
        }
        return national
    }
}

/** Lines: phone OR phone,name OR phone,name,product. Never auto-message on import. */
object PasteParser {
    data class Item(val phone: String, val name: String, val product: String)
    fun parse(raw: String): List<Item> = raw.lines().mapNotNull { line ->
        val cols = line.trim().split(Regex("[\\t,،;]"), limit = 3)
        Phone.normalize(cols.firstOrNull().orEmpty())?.let {
            Item(it, cols.getOrNull(1)?.trim().orEmpty(), cols.getOrNull(2)?.trim().orEmpty())
        }
    }.distinctBy { it.phone }
}
