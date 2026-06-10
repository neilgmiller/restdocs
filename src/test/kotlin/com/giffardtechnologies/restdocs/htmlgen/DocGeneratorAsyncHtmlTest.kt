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
class DocGeneratorAsyncHtmlTest {

    private lateinit var html: String

    @BeforeAll
    fun renderHtml() {
        val tempDir = Files.createTempDirectory("doc-gen-async-html-test").toFile()
        tempDir.deleteOnExit()
        val yamlFile = File(javaClass.classLoader.getResource("async-response-smoke-test.yaml")!!.toURI())
        val templateFile = File(System.getProperty("user.dir"), "rest_api_doc.vm")  // absolute; parentFile must be non-null for VelocityEngine
        val outputFile = File(tempDir, "out.html")
        DocGenerator(false).generate(yamlFile, templateFile, outputFile, DocGenerator.Options(false))
        html = outputFile.readText()
    }

    @Test
    fun `async response section heading appears for async method`() {
        assertTrue(html.contains("Async Response (via getAsyncJobStatus)"),
            "HTML should contain the async section heading.\nHTML:\n$html")
    }

    @Test
    fun `async response prose note references jr and jobStatus`() {
        assertTrue(html.contains("jr"), "HTML should contain 'jr'.\nHTML:\n$html")
        assertTrue(html.contains("jobStatus"), "HTML should contain 'jobStatus'.\nHTML:\n$html")
    }

    @Test
    fun `pure async method fields are rendered in the async section`() {
        assertTrue(html.contains("resultUrl"), "HTML should contain resultUrl field.\nHTML:\n$html")
        assertTrue(html.contains("completedAt"), "HTML should contain completedAt field.\nHTML:\n$html")
    }

    @Test
    fun `mixed async method field is rendered in the async section`() {
        assertTrue(html.contains("analysisResult"),
            "HTML should contain analysisResult field.\nHTML:\n$html")
    }

    @Test
    fun `typeRef async response renders a link to the referenced type`() {
        val methodSection = html.substringAfter("""id="typeRefAsyncMethod"""")
        val asyncSection = methodSection.substringAfter("Async Response (via getAsyncJobStatus)")
        assertTrue(asyncSection.contains("""href="#AsyncJobResponse">AsyncJobResponse</a>"""),
            "typeRefAsyncMethod async section should contain a link to AsyncJobResponse.\nSection:\n$asyncSection")
    }

    @Test
    fun `async response section does not appear for sync method`() {
        val syncSection = html
            .substringAfter("""id="syncMethod"""")
            .substringBefore("<h4 ")
        assertFalse(syncSection.contains("Async Response (via getAsyncJobStatus)"),
            "syncMethod section should not contain an async response heading.\nSection:\n$syncSection")
    }
}
