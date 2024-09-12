package com.giffardtechnologies.restdocs.codegen

import com.giffardtechnologies.meter.file
import com.giffardtechnologies.restdocs.domain.NamedBitSet
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.TypeSpec.BitSetSpec
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.File

/**
 *
 * @param bitSetPackage        the package where the bit set should be placed
 */
class BitSetProcessor(
    private val codeDirectory: File,
    private val bitSetPackage: String,
) {

    private val serializers = mutableListOf<TypeSpec>()


    private val serializationPackage = "$bitSetPackage.serialization"

    init {
        val typeVariable = TypeVariableName("E", ClassName("com.allego.api.client.support.bitset", "IntFlag"))
        val intFlagSerializerSpec = TypeSpec.classBuilder(ClassName(serializationPackage, "IntFlagSerializer"))
            .addModifiers(KModifier.ABSTRACT)
            .addTypeVariable(typeVariable)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("descriptor", String::class.asClassName())
                    .build()
            )
            .addSuperinterface(KSerializer::class.asClassName().parameterizedBy(typeVariable))
            .addProperty(
                PropertySpec.builder("descriptor", SerialDescriptor::class.asClassName(), KModifier.OVERRIDE)
                    .initializer(
                        "%M(descriptor, %T.INT)",
                        MemberName("kotlinx.serialization.descriptors", "PrimitiveSerialDescriptor"),
                        PrimitiveKind::class.asClassName()
                    )
                    .build()
            )
            .addFunction(
                FunSpec.builder("serialize")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("encoder", Encoder::class.asClassName())
                    .addParameter("value", typeVariable)
                    .addCode("encoder.encodeInt(value.flag)")
                    .build()
            )
            .addFunction(
                FunSpec.builder("deserialize")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("decoder", Decoder::class.asClassName())
                    .returns(typeVariable)
                    .addStatement("// need to make kotlin poet happy")
                    .addStatement("throw %T(\"Flags can only be serialized\")", ClassName("kotlin", "UnsupportedOperationException"))
//                    .addCode("}")
//                    .addCode(CodeBlock.builder().beginControlFlow("").add("throw UnsupportedOperationException(\"Flags can only be serialized\")",).endControlFlow().build())
                    .build()
            )
            .build()

        serializers.add(intFlagSerializerSpec)
    }

    /**
     * @param namedBitSet   the bit set to process
     * @param useFutureProofBitSet whether the bit set should be generating using future-proof types
     */
    fun processBitSet(
        namedBitSet: NamedBitSet,
        useFutureProofBitSet: Boolean = true
    ) {
        val typeSpec: TypeSpec = processBitSetToTypeSpec(namedBitSet, useFutureProofBitSet)
        val fileSpec = file(bitSetPackage, namedBitSet.typeName) {
            addType(typeSpec)
        }

        fileSpec.writeTo(codeDirectory)
    }

    private fun processBitSetToTypeSpec(
        namedBitSet: NamedBitSet,
        useFutureProofBitSet: Boolean
    ): TypeSpec {
        val bitSetClassName = ClassName(bitSetPackage, namedBitSet.typeName)
        return processBitSetToTypeSpec(bitSetClassName, namedBitSet.type, useFutureProofBitSet, isNamed = true)
    }

    fun processBitSetToTypeSpec(
        bitSetClassName: ClassName,
        bitSetSpec: BitSetSpec<*>,
        useFutureProofBitSet: Boolean,
        isNamed: Boolean = false,
    ): TypeSpec {
        return if (useFutureProofBitSet) {
            processBitSetToSealedTypeSpec(bitSetClassName, bitSetSpec)
        } else {
            processBitSetToSealedTypeSpec(bitSetClassName, bitSetSpec)
        }
    }

    private fun processBitSetToSealedTypeSpec(
        bitSetClassName: ClassName,
        bitSetSpec: BitSetSpec<*>
    ): TypeSpec {

        val (bitSetFlagTypeClass, flagClass) = when (bitSetSpec.flagType) {
            DataType.IntType -> Pair(Int::class, "IntFlag")
            DataType.LongType -> Pair(Long::class, "Flag")
        }

        val serializerClassName =
            ClassName(serializationPackage, "${bitSetClassName.buildNamePrefix()}Serializer")

        val builder = TypeSpec.classBuilder(bitSetClassName).addModifiers(KModifier.SEALED)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("flag", bitSetFlagTypeClass).build())
            .addSuperinterface(
                superinterface = ClassName("com.allego.api.client.support.bitset", flagClass),
                delegate = CodeBlock.of(
                    "%T(flag)",
                    ClassName("com.allego.api.client.support.bitset", "${flagClass}Impl")
                )
            )
            .addAnnotation(
                AnnotationSpec.builder(Serializable::class).addMember("with = %T::class", serializerClassName).build()
            )

        for (bitSetConstant in bitSetSpec.values) {
            val bitSetConstantClassName = fieldNameToClassStyle(bitSetConstant.longName)
            builder.addType(
                TypeSpec.objectBuilder(bitSetConstantClassName).addModifiers(KModifier.DATA).superclass(bitSetClassName)
                    .addSuperclassConstructorParameter(CodeBlock.of("%L", bitSetConstant.value)).build()
            )
        }

        serializers.add(
            TypeSpec.classBuilder(serializerClassName)
                .superclass(
                    ClassName(
                        serializationPackage,
                        bitSetFlagTypeClass.simpleName + "FlagSerializer"
                    ).parameterizedBy(bitSetClassName)
                )
                .addSuperclassConstructorParameter("%S", bitSetClassName.simpleName)
                .build()
        )

        return builder.build()
    }

    fun writeSupportingFiles() {
        file(serializationPackage, "BitSetSerializers") {
            serializers.forEach { typeSpec ->
                addType(typeSpec)
            }
        }.writeTo(codeDirectory)
    }
}
