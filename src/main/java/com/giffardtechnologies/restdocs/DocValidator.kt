package com.giffardtechnologies.restdocs

import com.fasterxml.jackson.databind.JsonMappingException
import com.giffardtechnologies.restdocs.jackson.createMapper
import com.giffardtechnologies.restdocs.storage.Document
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import kotlin.collections.HashSet
import com.giffardtechnologies.restdocs.storage.Document as DocumentStorageModel
import io.vavr.collection.HashSet as VavrHashSet
import io.vavr.collection.Set as VavrSet

class DocValidator {

    @Throws(IOException::class)
    fun validate(sourceFile: File) {
        try {
            getValidatedDocument(sourceFile) { message -> println(message) }
        } catch (e: JsonMappingException) {
            System.err.println(e.message)
        }
    }

    @Throws(IOException::class, JsonMappingException::class)
    fun getValidatedDocument(sourceFile: File, messageHandler: (String) -> Unit = {}) : Document {
        println("Validating '${sourceFile.absolutePath}'...")
        val input = BufferedInputStream(FileInputStream(sourceFile))

        // Jackson Mapper
        val mapper = createMapper(AccumulatingContext())
        val document = mapper.readValue(
            input,
            DocumentStorageModel::class.java
        )

        messageHandler("First pass complete")

        val dataObjectNames = document.dataObjects.map { it.name }
        val enumerationNames = document.enumerations.map { it.name }
        val responseTypeNames = document.service?.common?.responseDataObjects?.map { it.name } ?: emptyList()
        val referencableTypes = VavrHashSet.ofAll(dataObjectNames + enumerationNames + responseTypeNames)

        val contextMapper = createMapper(FullContext(referencableTypes, document))
        contextMapper.readValue(
            BufferedInputStream(FileInputStream(sourceFile)),
            DocumentStorageModel::class.java
        )
        messageHandler("Second pass complete")
        messageHandler("SUCCESS!")

        return document
    }

    public interface ValidationContext

    class AccumulatingContext : ValidationContext {
        val referencableTypes: MutableSet<String> = HashSet()
    }

    data class FullContext(val referencableTypes: VavrSet<String>, val document: DocumentStorageModel) :
        ValidationContext

}

val Any?.documentIfAvailable: Document?
    get() {
        return if (this is DocValidator.FullContext) {
            this.document
        } else {
            null
        }
    }