package com.giffardtechnologies.meter

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import kotlin.reflect.KClass

fun file(packageName: String, fileName: String, define: File.() -> Unit): FileSpec {
    val file = File(FileSpec.builder(packageName, fileName))
    file.define()
    return file.builder.build()
}

fun file(className: ClassName, define: File.() -> Unit): FileSpec {
    val file = File(FileSpec.builder(className))
    file.define()
    return file.builder.build()
}

class File internal constructor(val builder: FileSpec.Builder) {
    fun function(name: String, define: Function.() -> Unit) {
        val function = Function(FunSpec.builder(name))
        function.define()
        builder.addFunction(function.builder.build())
    }

    fun addClass(name: String, define: ClassDef.() -> Unit) {
        val classBuilder = TypeSpec.classBuilder(name)
        define(ClassDef(classBuilder))
        builder.addType(classBuilder.build())
    }

    fun addType(typeSpec: TypeSpec) {
        builder.addType(typeSpec)
    }

    fun addFunction(funSpec: FunSpec) {
        builder.addFunction(funSpec)
    }

    fun raw(callback: (FileSpec.Builder) -> Unit) {
        callback(builder)
    }

}

class Function internal constructor(val builder: FunSpec.Builder) {

    fun receiver(receiverType: TypeName) {
        builder.receiver(receiverType)
    }

    fun receiver(receiverType: KClass<*>) {
        builder.receiver(receiverType)
    }

    fun returns(returnType: TypeName) {
        builder.returns(returnType)
    }

    fun returns(returnType: KClass<*>) {
        builder.returns(returnType)
    }

}

class ClassDef internal constructor(private val builder: TypeSpec.Builder) {

    fun `companion`(name: String? = null, define: ClassDef.() -> Unit) {
        val companionBuilder = ClassDef(TypeSpec.companionObjectBuilder(name))
        companionBuilder.define()
        builder.addType(companionBuilder.builder.build())
    }

    val `private` = run { MemberBuilder(KModifier.PRIVATE) }

    inner class MemberBuilder(vararg modifiersArg: KModifier) {
        private val modifiers = modifiersArg

        fun `val`(name: String, typeName: TypeName) {
            builder.addProperty(PropertySpec.builder(name, typeName).addModifiers(*modifiers).build())
        }
    }

    fun raw(callback: (TypeSpec.Builder) -> Unit) {
        callback(builder)
    }

}