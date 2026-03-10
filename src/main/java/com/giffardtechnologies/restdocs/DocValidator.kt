package com.giffardtechnologies.restdocs

import tools.jackson.core.JacksonException
import com.giffardtechnologies.restdocs.jackson.createMapper
import com.giffardtechnologies.restdocs.storage.Document
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.collections.HashSet
import com.giffardtechnologies.restdocs.storage.Document as DocumentStorageModel
import io.vavr.collection.HashSet as VavrHashSet
import io.vavr.collection.Set as VavrSet

class DocValidator(val validationOptions: ValidationOptions = ValidationOptions()) {

    constructor(referenceStyleValidation: Boolean) : this(
        if (referenceStyleValidation) {
            ValidationOptions(
                longNameIsOptional = true,
                longNameAllowsSpaces = true,
            )
        } else {
            ValidationOptions()
        }
    )

    @Throws(IOException::class)
    fun validate(sourceFile: File) {
        getValidatedDocument(sourceFile) { message -> println(message) }
    }

    @Throws(IOException::class, JacksonException::class)
    fun getValidatedDocument(sourceFile: File, messageHandler: (String) -> Unit = {}): Document {
        println("Validating '${sourceFile.absolutePath}'...")

        // Jackson Mapper
        val mapper = createMapper(AccumulatingContext(validationOptions))
        val document = mapper.readValue(
            BufferedInputStream(FileInputStream(sourceFile)),
            DocumentStorageModel::class.java
        )

        messageHandler("First pass complete")

        val dataObjectNames = document.dataObjects.map { it.name }
        val enumerationNames = document.enumerations.map { it.name }
        val bitSetNames = document.bitsets.map { it.name }
        val responseTypeNames = document.service?.common?.responseDataObjects?.map { it.name } ?: emptyList()
        val referencableTypes = VavrHashSet.ofAll(dataObjectNames + enumerationNames + responseTypeNames + bitSetNames)

        val contextMapper = createMapper(FullContext(referencableTypes, document, validationOptions))
        contextMapper.readValue(
            BufferedInputStream(FileInputStream(sourceFile)),
            DocumentStorageModel::class.java
        )
        messageHandler("Second pass complete")
        messageHandler("SUCCESS!")

        return document
    }

    abstract class ValidationContext(val validationOptions: ValidationOptions)

    class AccumulatingContext(validationOptions: ValidationOptions) : ValidationContext(validationOptions) {
        val referencableTypes: MutableSet<String> = HashSet()
        val methodClassNames: MutableSet<String> = HashSet()
    }

    class FullContext(
        val referencableTypes: VavrSet<String>,
        val document: DocumentStorageModel,
        validationOptions: ValidationOptions,
    ) : ValidationContext(validationOptions)

    data class ValidationOptions(
        val longNameIsOptional: Boolean = false,
        val longNameAllowsSpaces: Boolean = false,
    )

}

val Any?.documentIfAvailable: Document?
    get() {
        return if (this is DocValidator.FullContext) {
            this.document
        } else {
            null
        }
    }