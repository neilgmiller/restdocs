package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.meter.file
import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.ArraySpec
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.BitSetSpec
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.EnumSpec
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.MapSpec
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.Nameable
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.ObjectSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import io.vavr.collection.Array
import kotlinx.serialization.Serializable
import java.io.File

class ObjectProcessor(
    private val codeDirectory: File,
    private val fieldAndTypeProcessor: FieldAndTypeProcessor,
    private val enumProcessor: EnumProcessor,
    private val bitSetProcessor: BitSetProcessor,
) {

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
        completeConstructor: Boolean = false,
    ): TypeSpec {
        return processObjectToTypeSpec(
            className,
            objectSpec.fields,
            useFutureProofEnum,
            completeConstructor,
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
        completeConstructor: Boolean = false,
        initializeWithDefault: Boolean = true,
        subObjectClassNameFactory: (ClassName, Field) -> ClassName,
        subObjectTypeSpecHandler: (TypeSpec.Builder, ClassName, TypeSpec) -> Unit,
        initializeCollections: Boolean = true,
    ): TypeSpec {
        val classBuilder = TypeSpec.classBuilder(className).addModifiers(KModifier.DATA)
            .addAnnotation(Serializable::class)

        val constructorBuilder = FunSpec.constructorBuilder()
        fields.forEach { field ->
            val propertySpec = fieldAndTypeProcessor.createPropertySpec(
                field,
                useFutureProofEnum,
                initializeWithDefault = initializeWithDefault,
                initializeCollections = initializeCollections,
                objectClassName = className,
                subObjectClassNameFactory = subObjectClassNameFactory
            )
            val parameterSpecBuilder = ParameterSpec.builder(field.toPropertyName(), propertySpec.type)
            if (!propertySpec.type.isNullable || completeConstructor) {
                if (!completeConstructor) {
                    parameterSpecBuilder.defaultValue(propertySpec.initializer)
                }
                constructorBuilder.addParameter(parameterSpecBuilder.build())
                classBuilder.addProperty(propertySpec.toBuilder().initializer(field.toPropertyName()).build())
            } else {
                constructorBuilder.addParameter(parameterSpecBuilder.defaultValue("null").build())
                classBuilder.addProperty(propertySpec.toBuilder().initializer(field.toPropertyName()).build())
            }

            // create sub-object classes from Nameable types
            val typeOrItemType = getFieldOrItemType(field.type)
            if (typeOrItemType is Nameable) {
                createSubObjectClass(
                    subObjectClassNameFactory,
                    className,
                    field,
                    typeOrItemType,
                    useFutureProofEnum,
                    subObjectTypeSpecHandler,
                    classBuilder
                )
            }
        }
        classBuilder.primaryConstructor(constructorBuilder.build())

        return classBuilder.build()
    }

    private fun createSubObjectClass(
        subObjectClassNameFactory: (ClassName, Field) -> ClassName,
        className: ClassName,
        field: Field,
        typeOrItemType: Nameable,
        useFutureProofEnum: Boolean,
        subObjectTypeSpecHandler: (TypeSpec.Builder, ClassName, TypeSpec) -> Unit,
        classBuilder: TypeSpec.Builder
    ) {
        val subObjectClassName = subObjectClassNameFactory(className, field)
        val subObjectTypeSpec = when (typeOrItemType) {
            is ObjectSpec -> {
                processObjectToTypeSpec(subObjectClassName, typeOrItemType, useFutureProofEnum)
            }

            is EnumSpec<*> -> {
                enumProcessor.processEnumToTypeSpec(subObjectClassName, typeOrItemType, useFutureProofEnum)
            }

            is BitSetSpec<*> -> {
                bitSetProcessor.processBitSetToTypeSpec(subObjectClassName, typeOrItemType, useFutureProofEnum)
            }
        }

        subObjectTypeSpecHandler(classBuilder, subObjectClassName, subObjectTypeSpec)
    }

    private fun getFieldOrItemType(type: com.giffardtechnologies.restdocs.domain.type.TypeSpec): com.giffardtechnologies.restdocs.domain.type.TypeSpec {
        return when (type) {
            is ArraySpec -> getFieldOrItemType(type.items)
            is MapSpec<*> -> getFieldOrItemType(type.items)
            else -> type
        }
    }

    private fun getSubObjectClassName(parentClassName: ClassName, field: Field, forceTopLevel: Boolean): ClassName {
        return if (forceTopLevel) {
            ClassName(parentClassName.packageName, fieldToClassStyle(field, parentClassName.simpleName))
        } else {
            parentClassName.nestedClass(fieldToClassStyle(field, parentClassName.simpleName, asInner = true))
        }
    }

}