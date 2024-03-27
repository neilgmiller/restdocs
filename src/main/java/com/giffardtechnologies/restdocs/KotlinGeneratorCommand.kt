package com.giffardtechnologies.restdocs

import com.giffardtechnologies.restdocs.KotlinGenerator.Options
import com.giffardtechnologies.restdocs.domain.FieldReference
import picocli.CommandLine
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.Callable
import java.util.stream.Collectors

@CommandLine.Command(
    description = ["Constructs a set of classes for a Kotlin Multi-Platform library for accessing the API"],
    name = "kotlin_generator",
    mixinStandardHelpOptions = true,
    version = ["KotlinGenerator 1.0"]
)
class KotlinGeneratorCommand : Callable<Unit> {

    companion object {
        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val kotlinGenerator = KotlinGeneratorCommand()
            CommandLine(kotlinGenerator).execute(*args)
        }
    }

    @CommandLine.Option(
        names = ["-f", "-p", "--properties"],
        description = ["The properties file describing the generation."]
    )
    private var mPropertiesFile: File? = null

    @Throws(Exception::class)
    override fun call() {

        val propertiesFile: File = (mPropertiesFile ?: File("kotlin-gen.properties")).absoluteFile
        val properties = Properties()
        loadPropertiesFromFile(propertiesFile, properties)

        val localPropertiesFile = getLocalPropertiesFile(propertiesFile)
        println("Looking for $localPropertiesFile")
        if (localPropertiesFile.exists()) {
            // load the overriding values
            loadPropertiesFromFile(localPropertiesFile, properties)
        }

        val initialSourceFile = File(properties.getProperty("sourceFile"))
        val sourceFile = if (initialSourceFile.isAbsolute) {
            initialSourceFile
        } else {
            File(propertiesFile.parentFile, properties.getProperty("sourceFile"))
        }

//        mUsePathAnnotation = Boolean.parseBoolean(mProperties.getProperty("usePathAnnotation"))
//        mMethodIDStyleRequestClass = mProperties.getProperty("altMethodIDStyleRequestClass", "AllegoRequest")
//        mAuthenticatedMethodIDStyleRequestClass = mProperties.getProperty(
//            "altAuthenticatedMethodIDStyleRequestClass",
//            "AuthenticatedAllegoRequest"
//        )
//        mPathAndBodyStyleRequestClass = mProperties.getProperty("altPathAndBodyStyleRequestClass")
        val codeDir = File(propertiesFile.parentFile, properties.getProperty("codeDir"))
        val iOSCodeDir = File(propertiesFile.parentFile, properties.getProperty("iOSCodeDir"))


        val forceTopLevel: Set<FieldReference> = properties.getSetProperty("javagen.forceTopLevel") { reference ->
            FieldReference.fromString(reference)
        }

        val excludedFields: Set<FieldReference> = properties.getSetProperty("javagen.excludeFields") { reference ->
            FieldReference.fromString(reference)
        }

        val clientPackage: String = properties.getProperty("clientPackage")

        KotlinGenerator().generate(
            sourceFile,
            Options(codeDir, iOSCodeDir, clientPackage, false, forceTopLevel, excludedFields)
        )
    }

    private fun getLocalPropertiesFile(propertiesFile: File): File {
        val basename = propertiesFile.name.replace(".properties$".toRegex(), "")
        return File(
            propertiesFile.parentFile,
            "$basename-local.properties"
        )
    }

    private fun loadPropertiesFromFile(propertiesFile: File, properties: Properties) {
        val propsInStream = BufferedInputStream(FileInputStream(propertiesFile))
        properties.load(propsInStream)
        propsInStream.close()
    }
    private fun <T> Properties.getSetProperty(key: String, parser: (String) -> T): Set<T> {
        return if (containsKey(key)) {
            val property: String = getProperty(key)
            if (property.isBlank()) {
                emptySet()
            } else {
                Arrays.stream(property.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()).map(parser)
                    .collect(Collectors.toSet())
            }
        } else {
            emptySet()
        }
    }

}
