package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.KotlinGenerator
import com.giffardtechnologies.restdocs.domain.FieldReference
import com.giffardtechnologies.restdocs.mappers.mapToModel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * End-to-end smoke test that exercises [KotlinGenerator.generate] with a synthetic YAML
 * covering all three async response spec cases:
 * - Case 1: pure async (response is typeref + asyncResponse inline object)
 * - Case 2: mixed async (response has job field + asyncResponse inline object)
 * - Case 3: sync (no asyncResponse block — regression guard)
 */
class AsyncResponseSmokeTest {

    @TempDir
    lateinit var codeDir: File

    @TempDir
    lateinit var iOSCodeDir: File

    private val clientPackage = "com.test.client"

    @BeforeEach
    fun setUp() {
        val sourceFile = File(javaClass.classLoader.getResource("async-response-smoke-test.yaml")!!.toURI())
        val options = KotlinGenerator.Options(
            codeDirectory = codeDir,
            iOSCodeDirectory = iOSCodeDir,
            clientPackage = clientPackage,
            verboseLogging = false,
            forceTopLevel = emptySet<FieldReference>(),
            excludedFields = emptySet<FieldReference>(),
        )
        val document = DocValidator().getValidatedDocument(sourceFile).mapToModel()
        KotlinGenerator().generate(document, options)
    }

    // -- Case 1: Pure async method --

    @Test
    fun `Case 1 - pure async method generates AsyncResponse class`() {
        val requestFile = findGeneratedFile("PureAsyncMethodRequest.kt")
        assertTrue(requestFile.exists(), "PureAsyncMethodRequest.kt should be generated")

        val content = requestFile.readText()
        assertTrue(
            content.contains("data class PureAsyncMethodAsyncResponse"),
            "Should contain PureAsyncMethodAsyncResponse data class.\nContent:\n$content"
        )
        assertTrue(
            content.contains("resultUrl"),
            "Should contain resultUrl field.\nContent:\n$content"
        )
        assertTrue(
            content.contains("completedAt"),
            "Should contain completedAt field.\nContent:\n$content"
        )
    }

    @Test
    fun `Case 1 - pure async method does not generate inline Response class (uses typeref)`() {
        val requestFile = findGeneratedFile("PureAsyncMethodRequest.kt")
        val content = requestFile.readText()
        // Since response is a typeref, there should be no inline Response class generated
        assertFalse(
            content.contains("data class PureAsyncMethodResponse"),
            "Should NOT contain PureAsyncMethodResponse (typeref response).\nContent:\n$content"
        )
    }

    // -- Case 2: Mixed async method --

    @Test
    fun `Case 2 - mixed async method generates both Response and AsyncResponse classes`() {
        val requestFile = findGeneratedFile("MixedAsyncMethodRequest.kt")
        assertTrue(requestFile.exists(), "MixedAsyncMethodRequest.kt should be generated")

        val content = requestFile.readText()
        assertTrue(
            content.contains("data class MixedAsyncMethodResponse"),
            "Should contain MixedAsyncMethodResponse data class.\nContent:\n$content"
        )
        assertTrue(
            content.contains("data class MixedAsyncMethodAsyncResponse"),
            "Should contain MixedAsyncMethodAsyncResponse data class.\nContent:\n$content"
        )
        assertTrue(
            content.contains("analysisResult"),
            "Should contain analysisResult field in async response.\nContent:\n$content"
        )
    }

    // -- Case 3: Sync method (regression) --

    @Test
    fun `Case 3 - sync method does not generate AsyncResponse class`() {
        val requestFile = findGeneratedFile("SyncMethodRequest.kt")
        assertTrue(requestFile.exists(), "SyncMethodRequest.kt should be generated")

        val content = requestFile.readText()
        assertTrue(
            content.contains("data class SyncMethodResponse"),
            "Should contain SyncMethodResponse data class.\nContent:\n$content"
        )
        assertFalse(
            content.contains("AsyncResponse"),
            "Should NOT contain any AsyncResponse class.\nContent:\n$content"
        )
    }

    // -- iOS client --

    @Test
    fun `SwiftAPIServerClient is generated for all three methods`() {
        val clientFile = File(iOSCodeDir, clientPackage.replace('.', '/') + "/SwiftAPIServerClient.kt")
        assertTrue(clientFile.exists(), "SwiftAPIServerClient.kt should be generated in iOSCodeDir")

        val content = clientFile.readText()
        assertTrue(
            content.contains("PureAsyncMethodRequest"),
            "Should contain execute function for pureAsyncMethod.\nContent:\n$content"
        )
        assertTrue(
            content.contains("MixedAsyncMethodRequest"),
            "Should contain execute function for mixedAsyncMethod.\nContent:\n$content"
        )
        assertTrue(
            content.contains("SyncMethodRequest"),
            "Should contain execute function for syncMethod.\nContent:\n$content"
        )
    }

    // -- helper --

    private fun findGeneratedFile(fileName: String): File {
        val requestsDir = File(codeDir, clientPackage.replace('.', '/') + "/requests")
        return File(requestsDir, fileName)
    }
}
