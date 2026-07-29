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
    fun `async response section headings appear for async method`() {
        assertTrue(html.contains("Response (async — job to poll)"),
            "HTML should contain the async job section heading.\nHTML:\n$html")
        assertTrue(html.contains("Response (sync — direct payload)"),
            "HTML should contain the sync payload section heading.\nHTML:\n$html")
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
        val asyncSection = methodSection.substringAfter("Response (async — job to poll)")
        assertTrue(asyncSection.contains("""href="#AsyncJobResponse">AsyncJobResponse</a>"""),
            "typeRefAsyncMethod async section should contain a link to AsyncJobResponse.\nSection:\n$asyncSection")
    }

    @Test
    fun `scalar async response renders a field row named jr with the configured long name and type`() {
        val methodSection = html.substringAfter("""id="scalarAsyncMethod"""")
        val asyncSection = methodSection.substringAfter("Response (async — job to poll)")
            .substringBefore("<h4 ")
        assertTrue(asyncSection.contains("jr"),
            "scalarAsyncMethod async section should render a field named 'jr'.\nSection:\n$asyncSection")
        assertTrue(asyncSection.contains("analysisScore"),
            "scalarAsyncMethod async section should render the configured long name.\nSection:\n$asyncSection")
        assertTrue(asyncSection.contains("long"),
            "scalarAsyncMethod async section should render the scalar type 'long'.\nSection:\n$asyncSection")
    }

    @Test
    fun `async response section does not appear for sync method`() {
        val syncSection = html
            .substringAfter("""id="syncMethod"""")
            .substringBefore("<h4 ")
        assertFalse(syncSection.contains("Response (async — job to poll)"),
            "syncMethod section should not contain an async response heading.\nSection:\n$syncSection")
    }

    @Test
    fun `sync method shows neither SYNC-ASYNC nor ASYNC badge`() {
        val syncSection = html
            .substringAfter("""id="syncMethod"""")
            .substringBefore("<h4 ")
        assertFalse(syncSection.contains("SYNC/ASYNC"),
            "syncMethod section should not contain a SYNC/ASYNC badge.\nSection:\n$syncSection")
        assertFalse(syncSection.contains(">ASYNC<"),
            "syncMethod section should not contain an ASYNC badge.\nSection:\n$syncSection")
    }

    @Test
    fun `always-mode async method shows a plain ASYNC badge and payload-after-completion wording`() {
        val section = html
            .substringAfter("""id="pureAsyncMethod"""")
            .substringBefore("<h4 ")
        assertTrue(section.contains(">ASYNC<"),
            "pureAsyncMethod section should contain a plain ASYNC badge.\nSection:\n$section")
        assertFalse(section.contains("SYNC/ASYNC"),
            "pureAsyncMethod section should not contain a SYNC/ASYNC badge.\nSection:\n$section")
        assertFalse(section.contains("parameter controls whether"),
            "pureAsyncMethod section should not describe a control parameter.\nSection:\n$section")
        assertTrue(section.contains("Response (payload — after job completes)"),
            "pureAsyncMethod section should use the always-mode payload heading.\nSection:\n$section")
        assertFalse(section.contains("Response (sync — direct payload)"),
            "pureAsyncMethod section should not use the conditional-mode sync heading.\nSection:\n$section")
    }

    @Test
    fun `conditional-mode async method shows the SYNC-ASYNC badge and control parameter wording`() {
        val section = html
            .substringAfter("""id="conditionalAsyncMethod"""")
            .substringBefore("<h4 ")
        assertTrue(section.contains("SYNC/ASYNC"),
            "conditionalAsyncMethod section should contain a SYNC/ASYNC badge.\nSection:\n$section")
        assertTrue(section.contains("the 'async' parameter controls whether"),
            "conditionalAsyncMethod section should describe the control parameter.\nSection:\n$section")
        assertTrue(section.contains("Response (sync — direct payload)"),
            "conditionalAsyncMethod section should use the conditional-mode sync heading.\nSection:\n$section")
    }
}
