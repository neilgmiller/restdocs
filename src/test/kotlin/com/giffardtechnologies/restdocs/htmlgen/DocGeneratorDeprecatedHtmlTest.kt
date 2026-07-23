package com.giffardtechnologies.restdocs.htmlgen

import com.giffardtechnologies.restdocs.DocGenerator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.nio.file.Files

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocGeneratorDeprecatedHtmlTest {

    private lateinit var html: String

    @BeforeAll
    fun renderHtml() {
        val tempDir = Files.createTempDirectory("doc-gen-deprecated-html-test").toFile()
        tempDir.deleteOnExit()
        val yamlFile = File(javaClass.classLoader.getResource("deprecated-method-test.yaml")!!.toURI())
        val templateFile = File(System.getProperty("user.dir"), "rest_api_doc.vm")  // absolute; parentFile must be non-null for VelocityEngine
        val outputFile = File(tempDir, "out.html")
        DocGenerator(false).generate(yamlFile, templateFile, outputFile, DocGenerator.Options(false))
        html = outputFile.readText()
    }

    private fun sectionFor(methodId: String): String {
        return html.substringAfter("""id="$methodId"""").substringBefore("<h4 ")
    }

    @Test
    fun `deprecated method with a date renders the badge and the note with date`() {
        val section = sectionFor("deprecatedWithDateMethod")
        assertTrue(section.contains("DEPRECATED"), "Should render the DEPRECATED badge.\nSection:\n$section")
        assertTrue(section.contains("Superseded by newMethod."), "Should render the deprecation note.\nSection:\n$section")
        assertTrue(section.contains("2020-01-01"), "Should render the deprecatedSince date.\nSection:\n$section")
    }

    @Test
    fun `deprecated method with no date renders the badge and note without a date`() {
        val section = sectionFor("deprecatedNoDateMethod")
        assertTrue(section.contains("DEPRECATED"), "Should render the DEPRECATED badge.\nSection:\n$section")
        assertTrue(section.contains("Retired no-op handler."), "Should render the deprecation note.\nSection:\n$section")
    }

    @Test
    fun `non-deprecated method renders no badge and no deprecation note`() {
        val section = sectionFor("activeMethod")
        assertFalse(section.contains("DEPRECATED"), "Should not render a DEPRECATED badge.\nSection:\n$section")
        assertFalse(section.contains("Deprecated</strong>"), "Should not render a deprecation note.\nSection:\n$section")
    }
}
