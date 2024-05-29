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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import kotlinx.serialization.Serializable
import java.io.File

/**
 *
 * @param bitSetPackage        the package where the bit set should be placed
 */
class BitSetProcessor(
    private val codeDirectory: File,
    private val bitSetPackage: String,
) {

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
        val builder = TypeSpec.classBuilder(bitSetClassName).addModifiers(KModifier.SEALED)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("flag", bitSetFlagTypeClass).build())
            .addSuperinterface(
                superinterface = ClassName("com.allego.api.client.support.bitset", flagClass),
                delegate = CodeBlock.of(
                    "%T(flag)",
                    ClassName("com.allego.api.client.support.bitset", "${flagClass}Impl")
                )
            )

        for (bitSetConstant in bitSetSpec.values) {
            val bitSetConstantClassName = fieldNameToClassStyle(bitSetConstant.longName)
            builder.addType(
                TypeSpec.objectBuilder(bitSetConstantClassName).addModifiers(KModifier.DATA).superclass(bitSetClassName)
                    .addSuperclassConstructorParameter(CodeBlock.of("%L", bitSetConstant.value)).build()
            )
        }

        return builder.build()
    }

}
