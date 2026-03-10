package com.giffardtechnologies.restdocs

import tools.jackson.core.JacksonException
import picocli.CommandLine
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@CommandLine.Command(
    description = ["Validates the documentation YAML"],
    name = "doc_validator",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class
)
class DocValidatorCommand : Callable<Int> {

    companion object {
        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val docGenerator = DocValidatorCommand()
            val exitCode = CommandLine(docGenerator).execute(*args)
            exitProcess(exitCode)
        }
    }

    @CommandLine.Option(
        names = ["-f", "-p", "--properties"],
        description = ["The properties file describing the generation."]
    )
    private var mPropertiesFile: File? = null

    @Throws(Exception::class)
    override fun call() : Int {
        val propertiesFile: File = (mPropertiesFile ?: File("docbuild.properties")).absoluteFile
        val propsInStream = BufferedInputStream(FileInputStream(propertiesFile))
        val properties = Properties()
        properties.load(propsInStream)
        propsInStream.close()
        val sourceFile = File(propertiesFile.parentFile, properties.getProperty("sourceFile"))

        try {
            DocValidator().validate(sourceFile)
        } catch (e: JacksonException) {
            System.err.println(e.message)
            return CommandLine.ExitCode.SOFTWARE
        }
        return CommandLine.ExitCode.OK
    }

}
