package com.giffardtechnologies.restdocs.mappers

import com.giffardtechnologies.restdocs.domain.Context
import com.giffardtechnologies.restdocs.domain.DataObject
import com.giffardtechnologies.restdocs.domain.Document
import com.giffardtechnologies.restdocs.domain.Method
import com.giffardtechnologies.restdocs.domain.NamedEnumeration
import com.giffardtechnologies.restdocs.domain.Restriction
import com.giffardtechnologies.restdocs.domain.Service
import com.giffardtechnologies.restdocs.domain.dsl.ObjectSpecConfiguration
import com.giffardtechnologies.restdocs.domain.dsl.dataObject
import com.giffardtechnologies.restdocs.domain.dsl.document
import com.giffardtechnologies.restdocs.domain.dsl.enumSpec
import com.giffardtechnologies.restdocs.domain.dsl.namedEnumeration
import com.giffardtechnologies.restdocs.domain.dsl.objectSpec
import com.giffardtechnologies.restdocs.domain.type.BooleanRepresentation
import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.Field
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.type.BasicType
import com.giffardtechnologies.restdocs.storage.type.EnumConstant
import com.giffardtechnologies.restdocs.storage.type.FieldListElement
import com.giffardtechnologies.restdocs.storage.type.FieldListIncludeElement
import com.giffardtechnologies.restdocs.storage.type.KeyType
import io.vavr.collection.Array
import com.giffardtechnologies.restdocs.storage.Common as CommonStorageModel
import com.giffardtechnologies.restdocs.storage.DataObject as DataObjectStorageModel
import com.giffardtechnologies.restdocs.storage.Document as DocumentStorageModel
import com.giffardtechnologies.restdocs.storage.Method as MethodStorageModel
import com.giffardtechnologies.restdocs.storage.NamedEnumeration as NamedEnumerationStorageModel
import com.giffardtechnologies.restdocs.storage.Restriction as RestrictionStorageModel
import com.giffardtechnologies.restdocs.storage.Service as ServiceStorageModel
import com.giffardtechnologies.restdocs.storage.type.DataType as DataTypeStorageModel
import com.giffardtechnologies.restdocs.storage.type.Field as FieldStorageModel
import com.giffardtechnologies.restdocs.storage.type.TypeSpec as TypeSpecStorageModel

// StorageModel
fun DocumentStorageModel.mapToModel(): Document {
    val document = document(title) {
        enumerations.map { it.mapToModel() }.forEach { addNamedEnumeration(it) }
        dataObjects.map { it.mapToModel(context) }.forEach { addDataObject(it) }
        service = this@mapToModel.service.mapToModel(context)
    }
    return document
}

private fun <U, V, T: List<U>> T?.mapList(mapper: (U) -> V): Array<V> {
    return this?.stream()?.map { mapper(it) }?.collect(Array.collector()) ?: Array.empty()
}

private fun NamedEnumerationStorageModel.mapToModel() : NamedEnumeration {
    check(values != null) { "An enumeration must have a list of values" }
    return when (this.key) {
        null,
        KeyType.INT -> mapEnumOfType(this.typeName, keyType = DataType.IntType, description, values)
        KeyType.LONG -> mapEnumOfType(this.typeName, keyType = DataType.LongType, description, values)
        KeyType.STRING -> mapEnumOfType(this.typeName, keyType = DataType.StringType, description, values)
        KeyType.ENUM -> throw IllegalArgumentException("${this.typeName} of 'enum' type cannot have a 'key' of type 'enum'")
    }
}

private fun <T> mapEnumOfType(
    name: String,
    keyType: DataType.BasicKey<T>,
    description: String?,
    values: ArrayList<EnumConstant>,
) = namedEnumeration(
    name = name,
    keyType = keyType,
) {
    this.description = description
    values.forEach {
        value(
            value = keyType.parse(it.value),
            longName = it.longName,
            description = it.description
        )
    }
}

private fun ArrayList<RestrictionStorageModel>?.mapRestrictions(): Array<Restriction> {
    return this?.let { restrictions ->
        Array.ofAll(restrictions.map { restriction ->
            Restriction(restriction.restriction)
        })
    } ?: Array.empty()
}

private fun DataObjectStorageModel.mapToModel(context: Context): DataObject {
    return dataObject(name) {
        description = this@mapToModel.description
        isHidden = this@mapToModel.isHidden
        configureFields(fields, context, this)
    }
}

private fun configureFields(
    fields: ArrayList<FieldListElement>,
    context: Context,
    dataObjectConfiguration: ObjectSpecConfiguration
) {
    fields.forEach { fieldListElement ->
        when (fieldListElement) {
            is FieldStorageModel -> {
                if (fieldListElement.type != null && fieldListElement.typeRef != null) {
                    throw IllegalArgumentException("${fieldListElement.name} cannot have both 'type' and 'typeref'")
                }
                val typeSpec = fieldListElement.mapToModel(fieldListElement.longName, context)
                dataObjectConfiguration.field(
                    name = fieldListElement.name,
                    longName = fieldListElement.longName,
                    type = typeSpec,
                ) {
                    description = fieldListElement.description
                    isRequired = fieldListElement.isRequired
                    defaultValue = fieldListElement.defaultValue
                    // TODO sample values
                }
            }

            is FieldListIncludeElement -> TODO()
        }
    }
}

private fun TypeSpecStorageModel.mapToModel(typeSpecIdentifier: String, context: Context): TypeSpec {
    return if (type != null) {
        if (interpretedAs != null) {
            when (interpretedAs) {
                BasicType.INT -> TODO()
                BasicType.LONG -> TODO()
                BasicType.FLOAT -> TODO()
                BasicType.DOUBLE -> TODO()
                BasicType.STRING -> TODO()
                BasicType.BOOLEAN -> if (type == DataTypeStorageModel.INT) {
                    TypeSpec.BooleanSpec(BooleanRepresentation.AsInteger)
                } else {
                    throw IllegalArgumentException()
                }
            }
        } else {
            when (type) {
                DataTypeStorageModel.INT -> TypeSpec.DataSpec(
                    DataType.IntType,
                    restrictions = restrictions.mapRestrictions(),
                )

                DataTypeStorageModel.LONG -> TypeSpec.DataSpec(
                    DataType.LongType,
                    restrictions = restrictions.mapRestrictions(),
                )

                DataTypeStorageModel.FLOAT -> TypeSpec.DataSpec(
                    DataType.FloatType,
                    restrictions = restrictions.mapRestrictions(),
                )

                DataTypeStorageModel.DOUBLE -> TypeSpec.DataSpec(
                    DataType.DoubleType,
                    restrictions = restrictions.mapRestrictions(),
                )

                DataTypeStorageModel.STRING -> TypeSpec.DataSpec(
                    DataType.StringType,
                    restrictions = restrictions.mapRestrictions(),
                )

                DataTypeStorageModel.BOOLEAN -> TypeSpec.BooleanSpec()
                DataTypeStorageModel.DATE -> TypeSpec.DataSpec(
                    DataType.DateType,
                    restrictions = restrictions.mapRestrictions(),
                )

                DataTypeStorageModel.OBJECT -> {
                    objectSpec {
                        val fieldListElements =
                            checkNotNull(fields) { "$typeSpecIdentifier is of object type but declares no fields" }
                        configureFields(fieldListElements, context, this)
                    }
                }

                DataTypeStorageModel.ARRAY -> {
                    if (items == null) {
                        throw IllegalArgumentException("$typeSpecIdentifier of 'array' type must define 'items'")
                    }
                    TypeSpec.ArraySpec(items.mapToModel("array items of '$typeSpecIdentifier'", context))
                }

                DataTypeStorageModel.COLLECTION -> {
                    if (key == null) {
                        throw IllegalArgumentException("$typeSpecIdentifier of 'collection' type must define 'key'")
                    }
                    if (items == null) {
                        throw IllegalArgumentException("$typeSpecIdentifier of 'collection' type must define 'items'")
                    }
                    TypeSpec.MapSpec(
                        key = when (key) {
                            KeyType.INT -> DataType.IntType
                            KeyType.LONG -> DataType.LongType
                            KeyType.STRING -> DataType.StringType
                            KeyType.ENUM -> TODO()
                        },
                        items = items.mapToModel("collection entries of '$typeSpecIdentifier'", context),
                    )
                }
                DataTypeStorageModel.ENUM -> {
                    if (values == null) {
                        throw ValidationException("$typeSpecIdentifier of 'enum' type must define 'values'")
                    }

                    when (this.key) {
                        null,
                        KeyType.INT -> mapEnumOfType(keyType = DataType.IntType, values)
                        KeyType.LONG -> mapEnumOfType(keyType = DataType.LongType, values)
                        KeyType.STRING -> mapEnumOfType(keyType = DataType.StringType, values)
                        KeyType.ENUM -> throw IllegalArgumentException("$typeSpecIdentifier of 'enum' type cannot have a 'key' of type 'enum'")
                    }
                }
                DataTypeStorageModel.BITSET -> TODO()
            }
        }
    } else if (typeRef != null) {
        TypeSpec.TypeRefSpec(typeRef, context)
    } else {
        throw IllegalArgumentException("$typeSpecIdentifier must have one of [type, typeref]")
    }
}

private fun <T> mapEnumOfType(
    keyType: DataType.BasicKey<T>,
    values: ArrayList<EnumConstant>,
) = enumSpec(
    keyType = keyType,
) {
    values.forEach {
        value(
            value = keyType.parse(it.value),
            longName = it.longName,
            description = it.description
        )
    }
}

private fun ServiceStorageModel?.mapToModel(context: Context): Service? {
    return if (this != null) {
        Service(
            description,
            basePath,
            common.mapToModel(context),
            methods = methods.mapList { it.mapToModel() }
        )
    } else {
        null
    }
}

private fun CommonStorageModel?.mapToModel(context: Context): Service.Common? {
    return if (this != null) {
        Service.Common(
            headers.mapList { it.mapToModel() },
            parameters.mapList { it.mapToModel() },
            responseDataObjects.mapList { it.mapToModel(context) }
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
