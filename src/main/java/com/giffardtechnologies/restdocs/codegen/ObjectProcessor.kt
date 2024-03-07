package com.giffardtechnologies.restdocs.codegen

import com.allego.meter.file
import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.EnumSpec
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.Nameable
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.ObjectSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import io.vavr.collection.Array
import kotlinx.serialization.Serializable
import java.io.File

class ObjectProcessor(
    private val codeDirectory: File,
    private val fieldAndTypeProcessor: FieldAndTypeProcessor,
    private val enumProcessor: EnumProcessor,
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
        return processObjectToTypeSpec(
            className,
            objectSpec.fields,
            useFutureProofEnum,
            subObjectClassNameFactory = { parentClassName, field ->
                getSubObjectClassName(
                    parentClassName,
                    field,
                    forceTopLevel
                )
            },
            subObjectTypeSpecHandler = { classBuilder, subObjectClassName, subObjectTypeSpec ->
                if (forceTopLevel) {
                    // TODO this could be more cleanly separated or parent method named - write vs process
                    writeClassToFile(subObjectClassName, subObjectTypeSpec)
                } else {
                    classBuilder.addType(subObjectTypeSpec)
                }

            }
        )
    }

    fun processObjectToTypeSpec(
        className: ClassName,
        fields: Array<Field>,
        useFutureProofEnum: Boolean,
        completeConstructor: Boolean  = false,
        subObjectClassNameFactory: (ClassName, Field) -> ClassName,
        subObjectTypeSpecHandler: (TypeSpec.Builder, ClassName, TypeSpec) -> Unit,
    ): TypeSpec {
        val classBuilder = TypeSpec.classBuilder(className)

        classBuilder.addAnnotation(Serializable::class)

        val constructorBuilder = FunSpec.constructorBuilder()
        fields.forEach { field ->
            val propertySpec = fieldAndTypeProcessor.createPropertySpec(
                field,
                useFutureProofEnum,
                objectClassName = className,
                subObjectClassNameFactory = subObjectClassNameFactory
            )
            if (!propertySpec.type.isNullable || completeConstructor) {
                constructorBuilder.addParameter(field.longName, propertySpec.type)
                classBuilder.addProperty(propertySpec.toBuilder().initializer(field.longName).build())
            } else {
                constructorBuilder.addParameter(
                    ParameterSpec.builder(field.longName, propertySpec.type).defaultValue("null").build()
                )
                classBuilder.addProperty(propertySpec.toBuilder().initializer(field.longName).build())
            }

            if (field.type is Nameable) {
                val subObjectClassName = subObjectClassNameFactory(className, field)
                val subObjectTypeSpec = when (field.type) {
                    is ObjectSpec -> {
                        processObjectToTypeSpec(subObjectClassName, field.type, true)
                    }

                    is EnumSpec<*> -> {
                        enumProcessor.processEnumToTypeSpec(subObjectClassName, field.type, true)
                    }
                }

                subObjectTypeSpecHandler(classBuilder, subObjectClassName, subObjectTypeSpec)
            }
        }
        classBuilder.primaryConstructor(constructorBuilder.build())

        return classBuilder.build()
    }

    private fun getSubObjectClassName(parentClassName: ClassName, field: Field, forceTopLevel: Boolean): ClassName {
        return if (forceTopLevel) {
            ClassName(parentClassName.packageName, fieldToClassStyle(field, parentClassName.simpleName))
        } else {
            parentClassName.nestedClass(fieldToClassStyle(field, parentClassName.simpleName, asInner = true))
        }
    }

}