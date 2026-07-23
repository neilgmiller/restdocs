package com.giffardtechnologies.restdocs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine
import java.io.File

/**
 * Exercises [KotlinGeneratorCommand]'s `skipDeprecatedBefore.date` property against
 * `deprecated-method-test.yaml`, which has one method deprecated with a 2020-01-01 date, one
 * deprecated with no recorded date, and one non-deprecated method.
 */
class KotlinGeneratorCommandSkipDeprecatedTest {

    @TempDir
    lateinit var tempDir: File

    private val clientPackage = "com.test.client"

    private fun writePropertiesFile(skipDeprecatedBefore: String?): File {
        val sourceFile = File(javaClass.classLoader.getResource("deprecated-method-test.yaml")!!.toURI())
        val propsFile = File(tempDir, "kotlin-gen.properties")
        propsFile.writeText(
            buildString {
                appendLine("sourceFile=${sourceFile.absolutePath}")
                appendLine("codeDir=gen-code")
                appendLine("iOSCodeDir=gen-ios")
                appendLine("clientPackage=$clientPackage")
                if (skipDeprecatedBefore != null) {
                    appendLine("skipDeprecatedBefore.date=$skipDeprecatedBefore")
                }
            }
        )
        return propsFile
    }

    private fun requestFile(name: String): File {
        return File(tempDir, "gen-code/" + clientPackage.replace('.', '/') + "/requests/${name}Request.kt")
    }

    @Test
    fun `methods deprecated before the cutoff, or with unknown deprecatedSince, are skipped`() {
        val propsFile = writePropertiesFile(skipDeprecatedBefore = "2021-01-01")
        val exitCode = CommandLine(KotlinGeneratorCommand()).execute("-f", propsFile.absolutePath)
        assertEquals(0, exitCode, "kotlin_generator run should succeed")

        assertFalse(
            requestFile("DeprecatedWithDateMethod").exists(),
            "Method deprecated since 2020-01-01 should be skipped under a 2021-01-01 cutoff"
        )
        assertFalse(
            requestFile("DeprecatedNoDateMethod").exists(),
            "A deprecated method with no recorded deprecatedSince should always be skip-eligible"
        )
        assertTrue(
            requestFile("ActiveMethod").exists(),
            "A non-deprecated method should always be generated"
        )
    }

    @Test
    fun `no skipDeprecatedBefore property preserves today's behavior of generating every method`() {
        val propsFile = writePropertiesFile(skipDeprecatedBefore = null)
        val exitCode = CommandLine(KotlinGeneratorCommand()).execute("-f", propsFile.absolutePath)
        assertEquals(0, exitCode, "kotlin_generator run should succeed")

        assertTrue(requestFile("DeprecatedWithDateMethod").exists())
        assertTrue(requestFile("DeprecatedNoDateMethod").exists())
        assertTrue(requestFile("ActiveMethod").exists())
    }
}
