package ir.smartsmscrm.v4.util

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class PhoneTest {
    @Test fun normalizeIranianMobile() {
        for(value in listOf("09120000001","9120000001","+989120000001","989120000001","00989120000001","۰۹۱۲۰۰۰۰۰۰۱"))
            assertEquals("09120000001",Phone.normalize(value))
        assertNull(Phone.normalize("02123456789"))
        assertNull(Phone.normalize("0912000000"))
    }
    @Test fun parseDeduplicatedPaste() {
        val items=PasteParser.parse("09120000001,علی,قطعات\n+989120000001,تکراری\n09120000002,مریم")
        assertEquals(2,items.size)
        assertEquals("علی",items[0].name)
        assertEquals("قطعات",items[0].product)
    }
    @Test fun persianVcfAndEncodedName() {
        val raw="""BEGIN:VCARD
VERSION:2.1
FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:=D8=B9=D9=84=DB=8C
TEL:+989120000001
END:VCARD
""".trimIndent()
        val people=VCardParser.parse(ByteArrayInputStream(raw.toByteArray()))
        assertEquals(1,people.size)
        assertEquals("علی",people[0].first)
        assertEquals("09120000001",Phone.normalize(people[0].phone))
    }
}
