package com.giffardtechnologies.restdocs

import com.giffardtechnologies.restdocs.jackson.createMapper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import com.giffardtechnologies.restdocs.storage.Document as DocumentStorageModel

class DocValidator(
    private val sourceFile: File,
) {

    @Throws(IOException::class)
    fun validate() {
        println("Validating '${sourceFile.absolutePath}'...")
        val input = BufferedInputStream(FileInputStream(sourceFile))

        // Jackson Mapper
        val mapper = createMapper()
        val document = mapper.readValue(
            input,
            DocumentStorageModel::class.java
        )
        println("First pass complete")

        val contextMapper = createMapper(document)
        contextMapper.readValue(
            BufferedInputStream(FileInputStream(sourceFile)),
            DocumentStorageModel::class.java
        )
        println("SUCCESS!")
    }

}
