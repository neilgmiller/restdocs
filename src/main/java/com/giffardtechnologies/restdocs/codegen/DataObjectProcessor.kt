package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.restdocs.domain.DataObject
import com.squareup.kotlinpoet.ClassName

class DataObjectProcessor(
    private val dtoPackage: String,
    private val requestsDtoPackage: String,
    private val objectProcessor: ObjectProcessor,
    private val classifier: DataObjectUsageClassifier,
) {

    fun generateDataObjectClassFile(dataObject: DataObject) {
        when (classifier.classify(dataObject.typeName)) {
            DataObjectClassification.ResponseOnly -> {
                objectProcessor.writeClassToFile(processDataObjectToClassDefinition(dataObject, true))
            }
            DataObjectClassification.ParameterOnly -> {
                objectProcessor.writeClassToFile(processInputClassDefinition(dataObject, true))
            }
            DataObjectClassification.Mixed -> {
                objectProcessor.writeClassToFile(processDataObjectToClassDefinition(dataObject, true))
                objectProcessor.writeClassToFile(processInputClassDefinition(dataObject, true))
            }
        }
    }

    fun processDataObjectToClassDefinition(
        dataObject: DataObject,
        useFutureProofEnum: Boolean,
        initializeWithDefault: Boolean = true,
    ): ClassDefinition {
        val className = ClassName(dtoPackage, dataObject.typeName)
        return ClassDefinition(
            className,
            objectProcessor.processObjectToTypeSpec(
                className,
                dataObject.type,
                useFutureProofEnum,
                initializeWithDefault = initializeWithDefault,
            )
        )
    }

    private fun processInputClassDefinition(
        dataObject: DataObject,
        useFutureProofEnum: Boolean,
    ): ClassDefinition {
        val className = ClassName(requestsDtoPackage, dataObject.typeName + "Input")
        return ClassDefinition(
            className,
            objectProcessor.processObjectToTypeSpec(
                className,
                dataObject.type,
                useFutureProofEnum,
                initializeWithDefault = false,
            )
        )
    }

}
