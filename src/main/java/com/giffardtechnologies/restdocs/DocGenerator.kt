package com.giffardtechnologies.restdocs

import com.giffardtechnologies.restdocs.domain.Document
import com.giffardtechnologies.restdocs.gson.GsonFactory
import com.giffardtechnologies.restdocs.htmlgen.LinkTool
import com.giffardtechnologies.restdocs.htmlgen.PlainTextTool
import org.apache.velocity.Template
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.tools.generic.EscapeTool
import org.yaml.snakeyaml.Yaml
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
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
        val doc = parseDocument(sourceFile)

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

        outputFile.parentFile.mkdirs()
        val fileWriter = FileWriter(outputFile)
        t.merge(context, fileWriter)
        fileWriter.close()
    }

    @Throws(IOException::class)
    private fun parseDocument(sourceFile: File): Document {
        val input = BufferedInputStream(FileInputStream(sourceFile))
        val yaml = Yaml()
        //		YamlReader yamlReader = new YamlReader(new InputStreamReader(input));

//		yamlReader.read();
        val map = yaml.load<Any>(input) as Map<*, *>
        //		Document doc = yaml.loadAs(input, Document.class);
        input.close()
        val gsonBuilder = GsonFactory.getGsonBuilder()
        val gsonForPrinting = gsonBuilder.create()
        val json = gsonForPrinting.toJson(map)
        //		System.out.println(json);
        val output = System.out
        //		for (DataObject dataObject : doc.getDataObjects()) {
//			output.println(dataObject.getName());
        val document = gsonForPrinting.fromJson(json, Document::class.java)
        document.buildMappings()
        return document
    }

}
