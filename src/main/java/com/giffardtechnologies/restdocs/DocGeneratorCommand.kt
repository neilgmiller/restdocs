package com.giffardtechnologies.restdocs

import picocli.CommandLine
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.Callable

@CommandLine.Command(
    description = ["Generates documents or code based for a given API descriptor"],
    name = "doc_generator",
    mixinStandardHelpOptions = true,
    version = ["DocGenerator 1.1"]
)class DocGeneratorCommand : Callable<Unit> {

    companion object {
        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val docGenerator = DocGeneratorCommand()
            CommandLine(docGenerator).execute(*args)
        }
    }

    @CommandLine.Option(
        names = ["-f", "-p", "--properties"],
        description = ["The properties file describing the generation."]
    )
    private var mPropertiesFile: File? = null

    @CommandLine.Option(names = ["-v", "--verbose"], description = ["Turn on verbose output"])
    private var mVerbose = false

    @Throws(Exception::class)
    override fun call() {
        val executableDir = File(System.getProperty("execdir"))
        val propertiesFile: File = (mPropertiesFile ?: File("docbuild.properties")).absoluteFile
        val propsInStream = BufferedInputStream(FileInputStream(propertiesFile))
        val properties = Properties()
        properties.load(propsInStream)
        propsInStream.close()

        val sourceFile = File(propertiesFile.parentFile, properties.getProperty("sourceFile"))
        val outputFile = File(propertiesFile.parentFile, properties.getProperty("outputFile"))

        val templateFilePath = properties.getProperty("templateFile")
        val templateFile: File = if (templateFilePath == null) {
            File(executableDir, "rest_api_doc.vm")
        } else {
            val templateFile = File(propertiesFile.parentFile, templateFilePath).absoluteFile
            if (!templateFile.exists()) {
                System.err.println("No template file at: " + templateFile.absolutePath)
                return
            }
            templateFile
        }

        DocGenerator().generate(sourceFile, templateFile, outputFile, DocGenerator.Options(mVerbose))
    }

}
