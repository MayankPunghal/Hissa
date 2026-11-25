package app.hissa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidateTest {
    @Test fun emails() {
        listOf("a@b.co", "first.last+tag@mail.example.com").forEach { assertNull(it, Check.email(it)) }
        listOf("plain", "a@b", "a@@b.com", "a b@c.com", "@x.com", "a@x.c").forEach { assertNotNull(it, Check.email(it)) }
        assertNull(Check.email(""))
    }

    @Test fun passwords() {
        assertNull(Check.password("abcdefg1"))
        assertNotNull(Check.password("short1"))
        assertNotNull(Check.password("allletters"))
        assertNotNull(Check.password("12345678"))
    }

    @Test fun names() {
        assertNull(Check.personName("Aarav Shah"))
        assertNotNull(Check.personName("A"))
        assertNotNull(Check.personName("1234"))
        assertEquals(null, Check.groupName("Goa"))
        assertNotNull(Check.groupName("G"))
    }
}
