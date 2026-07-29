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

    // A minimal jobResponse used by tests focused on payloadResponse behavior — its presence is
    // what makes a method "async" (see MethodProcessor.getClassNames' `isAsyncMethod` check).
    private val minimalJobResponse = Response(typeSpec = TypeSpec.BasicSpec(DataType.IntType))

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
    fun `getClassNames returns null asyncResponseClassName when method has no jobResponse or payloadResponse`() {
        val method = createMethod()
        val classNames = methodProcessor.getClassNames(method)

        assertEquals(ClassName(testPackage, "TestMethodRequest"), classNames.requestClassName)
        assertNull(classNames.asyncResponseClassName)
    }

    @Test
    fun `getClassNames returns asyncResponseClassName for ObjectSpec payloadResponse`() {
        val payloadResponse = Response(
            typeSpec = objectSpec {
                add(field("status", "status", TypeSpec.BasicSpec(DataType.StringType)))
            }
        )
        val method = createMethod(jobResponse = minimalJobResponse, payloadResponse = payloadResponse)
        val classNames = methodProcessor.getClassNames(method)

        assertEquals(
            ClassName(testPackage, "TestMethodAsyncResponse"),
            classNames.asyncResponseClassName
        )
    }

    @Test
    fun `getClassNames returns scalar ClassName for BasicSpec payloadResponse`() {
        val payloadResponse = Response(
            typeSpec = TypeSpec.BasicSpec(DataType.LongType)
        )
        val method = createMethod(jobResponse = minimalJobResponse, payloadResponse = payloadResponse)
        val classNames = methodProcessor.getClassNames(method)

        assertEquals(ClassName("kotlin", "Long"), classNames.asyncResponseClassName)
    }

    @Test
    fun `getClassNames returns null asyncResponseClassName for TypeRefSpec payloadResponse`() {
        val payloadResponse = Response(
            typeSpec = TypeSpec.TypeRefSpec("SomeType", object : com.giffardtechnologies.restdocs.domain.Context {
                override fun getTypeByName(name: String) = null
            })
        )
        val method = createMethod(jobResponse = minimalJobResponse, payloadResponse = payloadResponse)
        val classNames = methodProcessor.getClassNames(method)

        assertNull(classNames.asyncResponseClassName)
    }

    // -- processMethod tests --

    @Test
    fun `processMethod generates AsyncResponse class when payloadResponse has ObjectSpec`() {
        val payloadResponse = Response(
            typeSpec = objectSpec {
                add(field("resultUrl", "resultUrl", TypeSpec.BasicSpec(DataType.StringType)))
                add(field("progress", "progress", TypeSpec.BasicSpec(DataType.IntType)))
            }
        )
        val method = createMethod(jobResponse = minimalJobResponse, payloadResponse = payloadResponse)
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
    fun `processMethod does not generate AsyncResponse class when no jobResponse or payloadResponse`() {
        val method = createMethod()
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
    fun `processMethod does not generate AsyncResponse class for TypeRefSpec payloadResponse`() {
        val payloadResponse = Response(
            typeSpec = TypeSpec.TypeRefSpec("ExistingType", object : com.giffardtechnologies.restdocs.domain.Context {
                override fun getTypeByName(name: String) = null
            })
        )
        val method = createMethod(jobResponse = minimalJobResponse, payloadResponse = payloadResponse)
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
    fun `processMethod generates both Response and AsyncResponse when jobResponse and payloadResponse both have ObjectSpec`() {
        val jobResponse = Response(
            typeSpec = objectSpec {
                add(field("job", "job", TypeSpec.BasicSpec(DataType.IntType)))
                add(field("status", "status", TypeSpec.BasicSpec(DataType.StringType)))
            }
        )
        val payloadResponse = Response(
            typeSpec = objectSpec {
                add(field("data", "data", TypeSpec.BasicSpec(DataType.StringType)))
            }
        )
        val method = createMethod(jobResponse = jobResponse, payloadResponse = payloadResponse)
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

    @Test
    fun `processMethod resolves without error and generates no nested class for BasicSpec payloadResponse`() {
        val payloadResponse = Response(
            typeSpec = TypeSpec.BasicSpec(DataType.LongType)
        )
        val method = createMethod(jobResponse = minimalJobResponse, payloadResponse = payloadResponse)
        methodProcessor.processMethod(method)

        val outputFile = File(
            tempDir,
            testPackage.replace('.', '/') + "/TestMethodRequest.kt"
        )
        assertTrue(outputFile.exists(), "Output file should exist")

        val content = outputFile.readText()
        assertTrue(
            !content.contains("data class TestMethodAsyncResponse"),
            "Output should not contain a nested AsyncResponse data class for a scalar type. Content:\n$content"
        )
    }

    // -- helper methods --

    private fun field(name: String, longName: String, type: TypeSpec): Field {
        return Field(name = name, longName = longName, type = type)
    }

    private fun createMethod(
        response: Response? = null,
        jobResponse: Response? = null,
        payloadResponse: Response? = null,
    ): Method {
        return Method(
            method = Method.HTTPMethod.POST,
            id = 100,
            name = "testMethod",
            isAuthenticationRequired = true,
            parameterElementList = FieldElementList(Array.empty()),
            response = response,
            jobResponse = jobResponse,
            payloadResponse = payloadResponse,
        )
    }
}
