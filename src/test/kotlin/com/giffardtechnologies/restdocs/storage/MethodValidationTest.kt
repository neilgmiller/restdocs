package com.giffardtechnologies.restdocs.storage

import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import io.vavr.collection.HashSet as VavrHashSet
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MethodValidationTest {

    private fun context(): DocValidator.FullContext {
        return DocValidator.FullContext(
            VavrHashSet.empty(),
            Document(title = "Test", service = null),
            DocValidator.ValidationOptions(),
        )
    }

    private fun method(
        deprecated: Boolean = false,
        deprecationNote: String? = null,
        deprecatedSince: String? = null,
    ): Method {
        return Method(
            id = 1,
            name = "testMethod",
            deprecated = deprecated,
            deprecationNote = deprecationNote,
            deprecatedSince = deprecatedSince,
        )
    }

    @Test
    fun `deprecated true with no note throws`() {
        val ex = assertThrows(ValidationException::class.java) {
            method(deprecated = true).validate(context())
        }
        assertTrue(ex.message!!.contains("deprecationNote"))
    }

    @Test
    fun `deprecated true with blank note throws`() {
        assertThrows(ValidationException::class.java) {
            method(deprecated = true, deprecationNote = "   ").validate(context())
        }
    }

    @Test
    fun `non-deprecated method with deprecationNote set throws`() {
        assertThrows(ValidationException::class.java) {
            method(deprecated = false, deprecationNote = "some note").validate(context())
        }
    }

    @Test
    fun `non-deprecated method with deprecatedSince set throws`() {
        assertThrows(ValidationException::class.java) {
            method(deprecated = false, deprecatedSince = "2020-01-01").validate(context())
        }
    }

    @Test
    fun `malformed deprecatedSince throws`() {
        val ex = assertThrows(ValidationException::class.java) {
            method(deprecated = true, deprecationNote = "note", deprecatedSince = "not-a-date").validate(context())
        }
        assertTrue(ex.message!!.contains("ISO-8601"))
    }

    @Test
    fun `future deprecatedSince throws`() {
        assertThrows(ValidationException::class.java) {
            method(deprecated = true, deprecationNote = "note", deprecatedSince = "2099-01-01").validate(context())
        }
    }

    @Test
    fun `deprecated true with valid past date does not throw`() {
        assertDoesNotThrow {
            method(deprecated = true, deprecationNote = "note", deprecatedSince = "2020-01-01").validate(context())
        }
    }

    @Test
    fun `deprecated true with no deprecatedSince passes but emits a warning`() {
        val warnings = mutableListOf<String>()
        method(deprecated = true, deprecationNote = "note").validate(context()) { warnings.add(it) }
        assertTrue(warnings.any { it.contains("deprecatedSince") })
    }
}
