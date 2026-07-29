package com.giffardtechnologies.restdocs.storage

import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.type.DataType
import com.giffardtechnologies.restdocs.storage.type.Field
import com.giffardtechnologies.restdocs.storage.type.FieldListElement
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

    private fun contextWithAsyncDefault(defaultName: String): DocValidator.FullContext {
        return DocValidator.FullContext(
            VavrHashSet.empty(),
            Document(
                title = "Test",
                service = Service(common = Common(asyncControlParameterName = defaultName)),
            ),
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

    private fun asyncMethod(
        asyncMode: AsyncMode? = null,
        asyncControlParameter: String? = null,
        jobResponse: Response? = null,
        payloadResponse: Response? = null,
        parameters: ArrayList<FieldListElement>? = null,
    ): Method {
        return Method(
            id = 1,
            name = "testMethod",
            asyncMode = asyncMode,
            asyncControlParameter = asyncControlParameter,
            jobResponse = jobResponse,
            payloadResponse = payloadResponse,
            parameters = parameters,
        )
    }

    private fun booleanParameter(name: String): ArrayList<FieldListElement> {
        val list = ArrayList<FieldListElement>()
        list.add(Field(name = name, type = DataType.BOOLEAN))
        return list
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

    @Test
    fun `async-shaped method with no async mode throws`() {
        val ex = assertThrows(ValidationException::class.java) {
            asyncMethod(
                jobResponse = Response(type = DataType.STRING),
                payloadResponse = Response(type = DataType.STRING),
            ).validate(context()) { }
        }
        assertTrue(ex.message!!.contains("async mode"))
    }

    @Test
    fun `sync method with async mode set throws`() {
        assertThrows(ValidationException::class.java) {
            asyncMethod(asyncMode = AsyncMode.ALWAYS).validate(context()) { }
        }
    }

    @Test
    fun `async control parameter set with async mode always throws`() {
        assertThrows(ValidationException::class.java) {
            asyncMethod(
                asyncMode = AsyncMode.ALWAYS,
                asyncControlParameter = "async",
                jobResponse = Response(type = DataType.STRING),
                payloadResponse = Response(type = DataType.STRING),
            ).validate(context()) { }
        }
    }

    @Test
    fun `conditional mode with service default but no matching parameter throws`() {
        val ex = assertThrows(ValidationException::class.java) {
            asyncMethod(
                asyncMode = AsyncMode.CONDITIONAL,
                jobResponse = Response(type = DataType.STRING),
                payloadResponse = Response(type = DataType.STRING),
            ).validate(contextWithAsyncDefault("async")) { }
        }
        assertTrue(ex.message!!.contains("is not present in 'parameters'"))
    }

    @Test
    fun `conditional mode with no override and no service default throws`() {
        assertThrows(ValidationException::class.java) {
            asyncMethod(
                asyncMode = AsyncMode.CONDITIONAL,
                jobResponse = Response(type = DataType.STRING),
                payloadResponse = Response(type = DataType.STRING),
            ).validate(context()) { }
        }
    }

    @Test
    fun `conditional mode with matching boolean parameter does not throw`() {
        assertDoesNotThrow {
            asyncMethod(
                asyncMode = AsyncMode.CONDITIONAL,
                asyncControlParameter = "async",
                jobResponse = Response(type = DataType.STRING),
                payloadResponse = Response(type = DataType.STRING),
                parameters = booleanParameter("async"),
            ).validate(context()) { }
        }
    }

    @Test
    fun `always mode with only jobResponse throws`() {
        assertThrows(ValidationException::class.java) {
            asyncMethod(
                asyncMode = AsyncMode.ALWAYS,
                jobResponse = Response(type = DataType.STRING),
            ).validate(context()) { }
        }
    }

    @Test
    fun `always mode with both responses does not throw`() {
        assertDoesNotThrow {
            asyncMethod(
                asyncMode = AsyncMode.ALWAYS,
                jobResponse = Response(type = DataType.STRING),
                payloadResponse = Response(type = DataType.STRING),
            ).validate(context()) { }
        }
    }
}
