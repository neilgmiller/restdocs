package com.giffardtechnologies.restdocs

import com.giffardtechnologies.restdocs.htmlgen.LinkTool
import com.giffardtechnologies.restdocs.htmlgen.ObjectInspectionHelper
import com.giffardtechnologies.restdocs.htmlgen.PlainTextTool
import org.apache.velocity.Template
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.tools.generic.EscapeTool
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*

class DocGenerator {

    data class Options(val verboseLogging: Boolean)

    @Throws(Exception::class)
    fun generate(sourceFile: File, templateFile: File, outputFile: File, options: Options) {
        if (options.verboseLogging) {
            println("Template file is: $templateFile")
        }

        generateHTML(sourceFile, templateFile, outputFile)
    }

    @Throws(IOException::class)
    private fun generateHTML(sourceFile: File, templateFile: File, outputFile: File) {
        val doc = DocValidator().getValidatedDocument(sourceFile)

        /*
		 *  create a new instance of the engine
		 */
        val ve = VelocityEngine()

        /*
		 *  initialize the engine
		 */
        val p = Properties()
        p.setProperty("file.resource.loader.path", templateFile.parentFile!!.absolutePath)
        ve.init(p)
        val t: Template = ve.getTemplate(templateFile.name)
        val context = VelocityContext()
        context.put("document", doc)
        context.put("esc", EscapeTool())
        context.put("link", LinkTool(doc))
        context.put("text", PlainTextTool(doc))
        context.put("helper", ObjectInspectionHelper(doc))

        outputFile.parentFile.mkdirs()
        val fileWriter = FileWriter(outputFile)
        t.merge(context, fileWriter)
        fileWriter.close()
    }

}
