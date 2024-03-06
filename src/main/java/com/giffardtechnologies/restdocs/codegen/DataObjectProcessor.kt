package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.restdocs.codegen.ObjectProcessor.ClassDefinition
import com.giffardtechnologies.restdocs.domain.DataObject
import com.squareup.kotlinpoet.ClassName

class DataObjectProcessor(
    private val dtoPackage: String,
    private val objectProcessor: ObjectProcessor,
) {

    fun generateDataObjectClassFile(dataObject: DataObject) {
        val classDefinition = processDataObjectToClassDefinition(dataObject, true)
        objectProcessor.writeClassToFile(classDefinition)
    }

    fun processDataObjectToClassDefinition(
        dataObject: DataObject,
        useFutureProofEnum: Boolean
    ): ClassDefinition {
        val className = ClassName(dtoPackage, dataObject.typeName)
        return ClassDefinition(
            className,
            objectProcessor.processObjectToTypeSpec(
                className,
                dataObject.type,
                useFutureProofEnum
            )
        )
    }

}