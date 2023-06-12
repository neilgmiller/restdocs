package com.giffardtechnologies.restdocs

import com.fasterxml.jackson.databind.JsonMappingException
import com.giffardtechnologies.restdocs.jackson.createMapper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import kotlin.collections.HashSet
import com.giffardtechnologies.restdocs.storage.Document as DocumentStorageModel
import io.vavr.collection.HashSet as VavrHashSet
import io.vavr.collection.Set as VavrSet

class DocValidator(
    private val sourceFile: File,
) {

    @Throws(IOException::class)
    fun validate() {
        println("Validating '${sourceFile.absolutePath}'...")
        val input = BufferedInputStream(FileInputStream(sourceFile))

        try {// Jackson Mapper
            val mapper = createMapper(AccumulatingContext())
            val document = mapper.readValue(
                input,
                DocumentStorageModel::class.java
            )

            println("First pass complete")

            val dataObjectNames = document.dataObjects.map { it.name }
            val enumerationNames = document.enumerations.map { it.name }
            val responseTypeNames = document.service?.common?.responseDataObjects?.map { it.name } ?: emptyList()
            val referencableTypes = VavrHashSet.ofAll(dataObjectNames + enumerationNames + responseTypeNames)

            val contextMapper = createMapper(FullContext(referencableTypes))
            contextMapper.readValue(
                BufferedInputStream(FileInputStream(sourceFile)),
                DocumentStorageModel::class.java
            )
            println("Second pass complete")
            println("SUCCESS!")
        } catch (e: JsonMappingException) {
            System.err.println(e.message)
        }
    }

    class AccumulatingContext {
        val referencableTypes: MutableSet<String> = HashSet()
    }

    data class FullContext(val referencableTypes: VavrSet<String>)

}
