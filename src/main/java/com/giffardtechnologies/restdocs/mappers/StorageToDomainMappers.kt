package com.giffardtechnologies.restdocs.mappers

import com.giffardtechnologies.restdocs.domain.DataObject
import com.giffardtechnologies.restdocs.domain.Document
import com.giffardtechnologies.restdocs.domain.Method
import com.giffardtechnologies.restdocs.domain.NamedEnumeration
import com.giffardtechnologies.restdocs.domain.Service
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.Field
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array
import com.giffardtechnologies.restdocs.storage.Common as CommonStorageModel
import com.giffardtechnologies.restdocs.storage.DataObject as DataObjectStorageModel
import com.giffardtechnologies.restdocs.storage.Document as DocumentStorageModel
import com.giffardtechnologies.restdocs.storage.Method as MethodStorageModel
import com.giffardtechnologies.restdocs.storage.NamedEnumeration as NamedEnumerationStorageModel
import com.giffardtechnologies.restdocs.storage.Service as ServiceStorageModel
import com.giffardtechnologies.restdocs.storage.type.Field as FieldStorageModel

// StorageModel
fun DocumentStorageModel.mapToModel(): Document {
    return Document(
        title,
        service.mapToModel(),
        enumerations.stream().map { it.mapToModel() }.collect(Array.collector()),
        dataObjects.stream().map { it.mapToModel() }.collect(Array.collector()),
    )
}

private fun <U, V, T: List<U>> T?.mapList(mapper: (U) -> V): Array<V> {
    return this?.stream()?.map { mapper(it) }?.collect(Array.collector()) ?: Array.empty()
}

private fun ServiceStorageModel?.mapToModel(): Service? {
    return if (this != null) {
        Service(
            description,
            basePath,
            common.mapToModel(),
            methods = methods.mapList { it.mapToModel() }
        )
    } else {
        null
    }
}

private fun CommonStorageModel?.mapToModel(): Service.Common? {
    return if (this != null) {
        Service.Common(
            headers.mapList { it.mapToModel() },
            parameters.mapList { it.mapToModel() },
            responseDataObjects.mapList { it.mapToModel() }
        )
    } else {
        null
    }
}

private fun FieldStorageModel.mapToModel(): Field {
    return Field(
        name, longName, TypeSpec.DataSpec(DataType.IntType), description, defaultValue, isRequired
    )
}

private fun MethodStorageModel.mapToModel(): Method {
    TODO("Not yet implemented")
}

private fun NamedEnumerationStorageModel.mapToModel() : NamedEnumeration {
    TODO("Not yet implemented")
}

private fun DataObjectStorageModel.mapToModel() : DataObject {
    TODO("Not yet implemented")
}

private fun Int.mapToModel(): Document {
    TODO()
}
