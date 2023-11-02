package com.giffardtechnologies.restdocs

import picocli.CommandLine
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.Callable

@CommandLine.Command(
    description = ["Validates the documentation YAML"],
    name = "doc_validator",
    mixinStandardHelpOptions = true,
    version = ["DocGenerator 1.0"]
)
class DocValidatorCommand : Callable<Unit> {

    companion object {
        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val docGenerator = DocValidatorCommand()
            CommandLine(docGenerator).execute(*args)
        }
    }

    @CommandLine.Option(
        names = ["-f", "-p", "--properties"],
        description = ["The properties file describing the generation."]
    )
    private var mPropertiesFile: File? = null

    @Throws(Exception::class)
    override fun call() {
        val propertiesFile: File = (mPropertiesFile ?: File("docbuild.properties")).absoluteFile
        val propsInStream = BufferedInputStream(FileInputStream(propertiesFile))
        val properties = Properties()
        properties.load(propsInStream)
        propsInStream.close()
        val sourceFile = File(propertiesFile.parentFile, properties.getProperty("sourceFile"))

        DocValidator().validate(sourceFile)
    }

}
