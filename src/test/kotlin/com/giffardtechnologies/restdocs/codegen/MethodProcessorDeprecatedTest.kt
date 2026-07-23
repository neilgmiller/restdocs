package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.restdocs.domain.FieldElementList
import com.giffardtechnologies.restdocs.domain.Method
import io.vavr.collection.Array
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MethodProcessorDeprecatedTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var methodProcessor: MethodProcessor

    private val testPackage = "com.test.requests"
    private val dtoPackage = "com.test.dto"

    @BeforeEach
    fun setUp() {
        val fieldAndTypeProcessor = FieldAndTypeProcessor(dtoPackage, dtoPackage)
        val enumProcessor = EnumProcessor(tempDir, dtoPackage)
        val bitSetProcessor = BitSetProcessor(tempDir, dtoPackage)
        methodProcessor = MethodProcessor(
            codeDirectory = tempDir,
            requestsPackage = testPackage,
            typeRefPackage = dtoPackage,
            fieldAndTypeProcessor = fieldAndTypeProcessor,
            enumProcessor = enumProcessor,
            bitSetProcessor = bitSetProcessor,
        )
    }

    @Test
    fun `processMethod annotates request class as Deprecated when method is deprecated`() {
        val method = createMethod(deprecated = true, deprecationNote = "Superseded by newMethod.")
        methodProcessor.processMethod(method)

        val content = outputFile().readText()
        assertTrue(
            content.contains("@Deprecated"),
            "Output should contain a @Deprecated annotation.\nContent:\n$content"
        )
        assertTrue(
            content.contains("Superseded by newMethod."),
            "Output should contain the deprecation message.\nContent:\n$content"
        )
    }

    @Test
    fun `processMethod does not annotate request class when method is not deprecated`() {
        val method = createMethod(deprecated = false)
        methodProcessor.processMethod(method)

        val content = outputFile().readText()
        assertFalse(
            content.contains("@Deprecated"),
            "Output should not contain a @Deprecated annotation.\nContent:\n$content"
        )
    }

    private fun outputFile(): File {
        return File(tempDir, testPackage.replace('.', '/') + "/TestMethodRequest.kt")
    }

    private fun createMethod(
        deprecated: Boolean = false,
        deprecationNote: String? = null,
    ): Method {
        return Method(
            method = Method.HTTPMethod.POST,
            id = 100,
            name = "testMethod",
            isAuthenticationRequired = true,
            parameterElementList = FieldElementList(Array.empty()),
            deprecated = deprecated,
            deprecationNote = deprecationNote,
        )
    }
}
