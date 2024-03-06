package com.giffardtechnologies.restdocs.codegen

import com.allego.meter.file
import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.ObjectSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import java.io.File

class ObjectProcessor(
    private val codeDirectory: File,
    private val fieldAndTypeProcessor: FieldAndTypeProcessor
) {

    data class ClassDefinition(val className: ClassName, val typeSpec: TypeSpec)

    fun writeClassToFile(classDefinition: ClassDefinition) {
        writeClassToFile(classDefinition.className, classDefinition.typeSpec)
    }

    private fun writeClassToFile(
        className: ClassName,
        typeSpec: TypeSpec,
    ) {
        val fileSpec = file(className) {
            addType(typeSpec)
        }
        fileSpec.writeTo(codeDirectory)
    }

    fun processObjectToTypeSpec(
        className: ClassName,
        objectSpec: ObjectSpec,
        useFutureProofEnum: Boolean,
        forceTopLevel: Boolean = false,
    ): TypeSpec {
        val classBuilder = TypeSpec.classBuilder(className)

        val constructorBuilder = FunSpec.constructorBuilder()
        objectSpec.fields.forEach { field ->
            val propertySpec = fieldAndTypeProcessor.createPropertySpec(field, useFutureProofEnum, className)
            if (!propertySpec.type.isNullable) {
                constructorBuilder.addParameter(field.longName, propertySpec.type)
                classBuilder.addProperty(propertySpec.toBuilder().initializer(field.longName).build())
            } else {
                classBuilder.addProperty(propertySpec)
            }

            if (field.type is ObjectSpec) {
                val subObjectClassName = getSubObjectClassName(className, field, forceTopLevel)
                val subObjectTypeSpec =
                    processObjectToTypeSpec(subObjectClassName, field.type, true)
                if (forceTopLevel) {
                    // TODO this could be more cleanly separated or parent method named - write vs process
                    writeClassToFile(subObjectClassName, subObjectTypeSpec)
                } else {
                    classBuilder.addType(subObjectTypeSpec)
                }
            }
        }
        classBuilder.primaryConstructor(constructorBuilder.build())

        return classBuilder.build()
    }

    fun getSubObjectClassName(parentClassName: ClassName, field: Field, forceTopLevel: Boolean): ClassName {
        return if (forceTopLevel) {
            ClassName(parentClassName.packageName, fieldToClassStyle(field, parentClassName.simpleName))
        } else {
            ClassName(
                parentClassName.packageName,
                parentClassName.simpleName,
                fieldToClassStyle(field, parentClassName.simpleName, asInner = true)
            )
        }
    }

}