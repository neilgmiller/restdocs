package com.giffardtechnologies.restdocs

import com.giffardtechnologies.restdocs.codegen.DataObjectProcessor
import com.giffardtechnologies.restdocs.codegen.EnumProcessor
import com.giffardtechnologies.restdocs.codegen.FieldAndTypeProcessor
import com.giffardtechnologies.restdocs.codegen.MethodProcessor
import com.giffardtechnologies.restdocs.codegen.ObjectProcessor
import com.giffardtechnologies.restdocs.domain.FieldReference
import com.giffardtechnologies.restdocs.mappers.mapToModel
import java.io.File

class KotlinGenerator {

    data class Options(
        val codeDirectory: File,
        val clientPackage: String,
        val verboseLogging: Boolean = false,
        val forceTopLevel: Set<FieldReference>,
        val excludedFields: Set<FieldReference>
    )

    fun generate(sourceFile: File, options: Options) {
        val document = DocValidator().getValidatedDocument(sourceFile).mapToModel()

//        val typeVariable = TypeVariableName.invoke("T")
//        val typeSpec = TypeSpec.interfaceBuilder(
//            ClassName(options.dtoPackage, "EnumID")
//        )
//            .addTypeVariable(typeVariable)
//            .addProperty("id", typeVariable)
//            .build()
//
//        file(options.dtoPackage, "EnumID") {
//            addType(
//                TypeSpec.interfaceBuilder("EnumID")
//                    .addTypeVariable(typeVariable)
//                    .addProperty("id", typeVariable)
//                    .build()
//            )
//            addType(
//                TypeSpec.classBuilder("ImmutableEnumID")
//                    .addTypeVariable(typeVariable)
//                    .addProperty("id", typeVariable)
//                    .build()
//            )
//
//        }

        val dtoPackage = options.clientPackage + ".dto"
        val enumProcessor = EnumProcessor(options.codeDirectory, dtoPackage)

        for (namedEnumeration in document.enumerations) {
            enumProcessor.processEnum(namedEnumeration)
        }

        val fieldAndTypeProcessor = FieldAndTypeProcessor(dtoPackage, dtoPackage)
        val objectProcessor = ObjectProcessor(options.codeDirectory, fieldAndTypeProcessor, enumProcessor)
        val dataObjectProcessor = DataObjectProcessor(dtoPackage, objectProcessor)

        document.dataObjects.filter { !it.isHidden }.forEach { dataObject ->
            dataObjectProcessor.generateDataObjectClassFile(dataObject)
        }

        val methodProcessor = MethodProcessor(
            options.codeDirectory,
            options.clientPackage + ".requests",
            dtoPackage,
            fieldAndTypeProcessor,
            enumProcessor,
            supportPackage = options.clientPackage + ".requests.support"
        )

        document.service?.methods?.forEach {
            methodProcessor.processMethod(it)
        }

        enumProcessor.writeSupportingFiles()
    }

}