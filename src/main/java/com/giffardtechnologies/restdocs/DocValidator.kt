package com.giffardtechnologies.restdocs

import com.fasterxml.jackson.databind.JsonMappingException
import com.giffardtechnologies.restdocs.jackson.createMapper
import com.giffardtechnologies.restdocs.storage.Document
import org.yaml.snakeyaml.Yaml
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.IOException
import java.util.*
import kotlin.collections.HashSet
import com.giffardtechnologies.restdocs.storage.Document as DocumentStorageModel
import io.vavr.collection.HashSet as VavrHashSet
import io.vavr.collection.Set as VavrSet

class DocValidator(val options: Options = Options(false)) {
    data class Options(val useNameForLongName: Boolean)

    @Throws(IOException::class)
    fun validate(sourceFile: File) {
        getValidatedDocument(sourceFile) { message -> println(message) }
    }

    @Throws(IOException::class, JsonMappingException::class)
    fun getValidatedDocument(sourceFile: File, messageHandler: (String) -> Unit = {}): Document {
        val trueSourceFile = if (options.useNameForLongName) {
            addLongNamesToDocument(sourceFile)
        } else {
            sourceFile
        }
        val ignoreUnknown = !options.useNameForLongName
        val relaxedLongNames = options.useNameForLongName

        println("Validating '${sourceFile.absolutePath}'...")
        val input = BufferedInputStream(FileInputStream(trueSourceFile))

        // Jackson Mapper
        val mapper = createMapper(AccumulatingContext(relaxedLongNames), ignoreUnknown)
        val document = mapper.readValue(
            input,
            DocumentStorageModel::class.java
        )

        messageHandler("First pass complete")

        val dataObjectNames = document.dataObjects.map { it.name }
        val enumerationNames = document.enumerations.map { it.name }
        val responseTypeNames = document.service?.common?.responseDataObjects?.map { it.name } ?: emptyList()
        val referencableTypes = VavrHashSet.ofAll(dataObjectNames + enumerationNames + responseTypeNames)

        val contextMapper =
            createMapper(FullContext(referencableTypes, document, relaxedLongNames), ignoreUnknown)
        contextMapper.readValue(
            BufferedInputStream(FileInputStream(trueSourceFile)),
            DocumentStorageModel::class.java
        )
        messageHandler("Second pass complete")
        messageHandler("SUCCESS!")

        return document
    }

    interface ValidationContext {
        val relaxedLongNames: Boolean
    }

    class AccumulatingContext(override val relaxedLongNames: Boolean) : ValidationContext {
        val referencableTypes: MutableSet<String> = HashSet()
        val methodClassNames: MutableSet<String> = HashSet()
    }

    data class FullContext(
        val referencableTypes: VavrSet<String>,
        val document: DocumentStorageModel,
        override val relaxedLongNames: Boolean
    ) : ValidationContext

    @Throws(IOException::class)
    private fun addLongNamesToDocument(sourceFile: File): File {
        val input = BufferedInputStream(FileInputStream(sourceFile))
        val yaml = Yaml()
        @Suppress("UNCHECKED_CAST")
        val map = yaml.load<Any>(input) as MutableMap<String, Any>
        input.close()
        addLongNames(map)
        val tmpFile = File.createTempFile(sourceFile.name.dropLast(5), ".yaml")
        yaml.dump(map, FileWriter(tmpFile))
        return tmpFile
    }

    private fun addLongNames(map: MutableMap<String, Any>) {
        map.map { it.value }
            .filterIsInstance<MutableMap<String, Any>>()
            .forEach {
                addLongNames(it)
            }
        map.map { it.value }
            .filterIsInstance<MutableList<Any>>()
            .forEach {
                addLongNames(it)
            }
        val name = map["name"]
        if (name != null) {
            map["longName"] = name
        }
    }

    private fun addLongNames(list: MutableList<Any>) {
        list.filterIsInstance<MutableMap<String, Any>>()
            .forEach {
                addLongNames(it)
            }
        list.filterIsInstance<MutableList<Any>>()
            .forEach {
                addLongNames(it)
            }
    }

}

val Any?.documentIfAvailable: Document?
    get() {
        return if (this is DocValidator.FullContext) {
            this.document
        } else {
            null
        }
    }