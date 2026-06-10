package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.FieldElementList
import com.giffardtechnologies.restdocs.domain.Method
import com.giffardtechnologies.restdocs.domain.Response
import com.giffardtechnologies.restdocs.domain.dsl.objectSpec
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import com.squareup.kotlinpoet.ClassName
import io.vavr.collection.Array
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MethodProcessorAsyncResponseTest {

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

    // -- getClassNames tests --

    @Test
    fun `getClassNames returns null asyncResponseClassName when method has no asyncResponse`() {
        val method = createMethod(asyncResponse = null)
        val classNames = methodProcessor.getClassNames(method)

        assertEquals(ClassName(testPackage, "TestMethodRequest"), classNames.requestClassName)
        assertNull(classNames.asyncResponseClassName)
    }

    @Test
    fun `getClassNames returns asyncResponseClassName for ObjectSpec asyncResponse`() {
        val asyncResponse = Response(
            typeSpec = objectSpec {
                add(field("status", "status", TypeSpec.BasicSpec(DataType.StringType)))
            }
        )
        val method = createMethod(asyncResponse = asyncResponse)
        val classNames = methodProcessor.getClassNames(method)

        assertEquals(
            ClassName(testPackage, "TestMethodAsyncResponse"),
            classNames.asyncResponseClassName
        )
    }

    @Test
    fun `getClassNames returns null asyncResponseClassName for TypeRefSpec asyncResponse`() {
        val asyncResponse = Response(
            typeSpec = TypeSpec.TypeRefSpec("SomeType", object : com.giffardtechnologies.restdocs.domain.Context {
                override fun getTypeByName(name: String) = null
            })
        )
        val method = createMethod(asyncResponse = asyncResponse)
        val classNames = methodProcessor.getClassNames(method)

        assertNull(classNames.asyncResponseClassName)
    }

    // -- processMethod tests --

    @Test
    fun `processMethod generates AsyncResponse class when asyncResponse has ObjectSpec`() {
        val asyncResponse = Response(
            typeSpec = objectSpec {
                add(field("resultUrl", "resultUrl", TypeSpec.BasicSpec(DataType.StringType)))
                add(field("progress", "progress", TypeSpec.BasicSpec(DataType.IntType)))
            }
        )
        val method = createMethod(asyncResponse = asyncResponse)
        methodProcessor.processMethod(method)

        // The output file should exist in the requests package directory
        val outputFile = File(
            tempDir,
            testPackage.replace('.', '/') + "/TestMethodRequest.kt"
        )
        assertTrue(outputFile.exists(), "Output file should exist: ${outputFile.absolutePath}")

        val content = outputFile.readText()
        // Should contain the async response data class
        assertTrue(
            content.contains("data class TestMethodAsyncResponse"),
            "Output should contain TestMethodAsyncResponse data class. Content:\n$content"
        )
        // Should contain the fields
        assertTrue(
            content.contains("resultUrl"),
            "Output should contain resultUrl field. Content:\n$content"
        )
        assertTrue(
            content.contains("progress"),
            "Output should contain progress field. Content:\n$content"
        )
    }

    @Test
    fun `processMethod does not generate AsyncResponse class when no asyncResponse`() {
        val method = createMethod(asyncResponse = null)
        methodProcessor.processMethod(method)

        val outputFile = File(
            tempDir,
            testPackage.replace('.', '/') + "/TestMethodRequest.kt"
        )
        assertTrue(outputFile.exists(), "Output file should exist")

        val content = outputFile.readText()
        assertTrue(
            !content.contains("AsyncResponse"),
            "Output should not contain AsyncResponse. Content:\n$content"
        )
    }

    @Test
    fun `processMethod does not generate AsyncResponse class for TypeRefSpec asyncResponse`() {
        val asyncResponse = Response(
            typeSpec = TypeSpec.TypeRefSpec("ExistingType", object : com.giffardtechnologies.restdocs.domain.Context {
                override fun getTypeByName(name: String) = null
            })
        )
        val method = createMethod(asyncResponse = asyncResponse)
        methodProcessor.processMethod(method)

        val outputFile = File(
            tempDir,
            testPackage.replace('.', '/') + "/TestMethodRequest.kt"
        )
        assertTrue(outputFile.exists(), "Output file should exist")

        val content = outputFile.readText()
        assertTrue(
            !content.contains("AsyncResponse"),
            "Output should not contain AsyncResponse for TypeRefSpec. Content:\n$content"
        )
    }

    @Test
    fun `processMethod generates both Response and AsyncResponse when both have ObjectSpec`() {
        val response = Response(
            typeSpec = objectSpec {
                add(field("job", "job", TypeSpec.BasicSpec(DataType.IntType)))
                add(field("status", "status", TypeSpec.BasicSpec(DataType.StringType)))
            }
        )
        val asyncResponse = Response(
            typeSpec = objectSpec {
                add(field("data", "data", TypeSpec.BasicSpec(DataType.StringType)))
            }
        )
        val method = createMethod(response = response, asyncResponse = asyncResponse)
        methodProcessor.processMethod(method)

        val outputFile = File(
            tempDir,
            testPackage.replace('.', '/') + "/TestMethodRequest.kt"
        )
        val content = outputFile.readText()

        assertTrue(
            content.contains("data class TestMethodResponse"),
            "Output should contain TestMethodResponse. Content:\n$content"
        )
        assertTrue(
            content.contains("data class TestMethodAsyncResponse"),
            "Output should contain TestMethodAsyncResponse. Content:\n$content"
        )
    }

    // -- helper methods --

    private fun field(name: String, longName: String, type: TypeSpec): Field {
        return Field(name = name, longName = longName, type = type)
    }

    private fun createMethod(
        response: Response? = null,
        asyncResponse: Response? = null,
    ): Method {
        return Method(
            method = Method.HTTPMethod.POST,
            id = 100,
            name = "testMethod",
            isAuthenticationRequired = true,
            parameterElementList = FieldElementList(Array.empty()),
            response = response,
            asyncResponse = asyncResponse,
        )
    }
}
