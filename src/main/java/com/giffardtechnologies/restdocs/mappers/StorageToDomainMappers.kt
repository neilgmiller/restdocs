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
import com.giffardtechnologies.restdocs.domain.Field
import com.giffardtechnologies.restdocs.domain.FieldElementList
import com.giffardtechnologies.restdocs.domain.FieldListElement
import com.giffardtechnologies.restdocs.domain.FieldListIncludeElement
import com.giffardtechnologies.restdocs.domain.RequiredOverride
import com.giffardtechnologies.restdocs.domain.NamedBitSet
import com.giffardtechnologies.restdocs.domain.RequestBody
import com.giffardtechnologies.restdocs.domain.Response
import com.giffardtechnologies.restdocs.domain.dsl.DocumentConfiguration
import com.giffardtechnologies.restdocs.domain.dsl.bitSetSpec
import com.giffardtechnologies.restdocs.domain.dsl.field
import com.giffardtechnologies.restdocs.domain.dsl.namedBitSet
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.HTTPMethod
import com.giffardtechnologies.restdocs.storage.type.BasicType
import com.giffardtechnologies.restdocs.storage.type.EnumConstant
import com.giffardtechnologies.restdocs.storage.type.KeyType
import io.vavr.collection.Array
import com.giffardtechnologies.restdocs.storage.Common as CommonStorageModel
import com.giffardtechnologies.restdocs.storage.DataObject as DataObjectStorageModel
import com.giffardtechnologies.restdocs.storage.Document as DocumentStorageModel
import com.giffardtechnologies.restdocs.storage.Method as MethodStorageModel
import com.giffardtechnologies.restdocs.storage.NamedBitSet as NamedBitSetStorageModel
import com.giffardtechnologies.restdocs.storage.NamedEnumeration as NamedEnumerationStorageModel
import com.giffardtechnologies.restdocs.storage.RequestBody as RequestBodyStorageModel
import com.giffardtechnologies.restdocs.storage.Response as ResponseStorageModel
import com.giffardtechnologies.restdocs.storage.Restriction as RestrictionStorageModel
import com.giffardtechnologies.restdocs.storage.Service as ServiceStorageModel
import com.giffardtechnologies.restdocs.storage.type.DataType as DataTypeStorageModel
import com.giffardtechnologies.restdocs.storage.type.Field as FieldStorageModel
import com.giffardtechnologies.restdocs.storage.type.FieldListIncludeElement as FieldListIncludeElementStorageModel
import com.giffardtechnologies.restdocs.storage.type.FieldListElement as FieldListElementStorageModel
import com.giffardtechnologies.restdocs.storage.type.TypeSpec as TypeSpecStorageModel

// StorageModel
fun DocumentStorageModel.mapToModel(): Document {
    val document = document(title) {
        bitsets.map { it.mapToModel() }.forEach { addNamedBitSet(it) }
        enumerations.map { it.mapToModel() }.forEach { addNamedEnumeration(it) }
        dataObjects.map { it.mapToModel(context) }.forEach { addDataObject(it) }
        service = this@mapToModel.service.mapToModel(context, this)
    }
    return document
}

private fun <U, V, T: List<U>> T?.mapList(mapper: (U) -> V): Array<V> {
    return this?.stream()?.map { mapper(it) }?.collect(Array.collector()) ?: Array.empty()
}

private fun NamedBitSetStorageModel.mapToModel() : NamedBitSet {
    check(values != null) { "An enumeration must have a list of values" }
    return when (this.key) {
        null,
        KeyType.INT -> mapBitSetOfType(this.typeName, keyType = DataType.IntType, description, values)
        KeyType.LONG -> mapBitSetOfType(this.typeName, keyType = DataType.LongType, description, values)
        KeyType.STRING -> throw IllegalArgumentException("${this.typeName} of 'bitset' type cannot have a 'key' of type 'string'")
        KeyType.ENUM -> throw IllegalArgumentException("${this.typeName} of 'bitset' type cannot have a 'key' of type 'enum'")
    }
}

private fun <T> mapBitSetOfType(
    name: String,
    keyType: DataType.UsableAsFlag<T>,
    description: String?,
    values: ArrayList<EnumConstant>,
) = namedBitSet(
    name = name,
    keyType = keyType,
) {
    this.description = description
    values.forEach {
        value(
            value = keyType.parse(it.value),
            longName = requireNotNull(it.longName) { "'longName' must not be blank for flags" },
            description = it.description
        )
    }
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
    keyType: DataType.UsableAsKey<T>,
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
            longName = if (keyType is DataType.StringType) it.longName ?: it.value else requireNotNull(it.longName) { "'longName' must not be blank for numeric key enums" },
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
    fields: ArrayList<FieldListElementStorageModel>,
    context: Context,
    dataObjectConfiguration: ObjectSpecConfiguration
) {
    fields.forEach { it.configureField(context, dataObjectConfiguration) }
}

private fun FieldListElementStorageModel.configureField(
    context: Context,
    dataObjectConfiguration: ObjectSpecConfiguration
) {
    return when (this) {
        is FieldStorageModel -> {
            dataObjectConfiguration.add(
                this.mapToModel(context)
            )
        }

        is FieldListIncludeElementStorageModel -> {
            dataObjectConfiguration.add(
                this.mapToModel(context)
            )
        }
    }
}

private fun FieldListElementStorageModel.mapToModel(
    context: Context
): FieldListElement {
    return when (this) {
        is FieldStorageModel -> mapToModel(context)
        is FieldListIncludeElementStorageModel -> mapToModel(context)
    }
}

private fun FieldStorageModel.mapToModel(context: Context): Field {
    val typeSpec = mapToModel(longName, context)
    return field(
        name = name,
        longName = longName,
        type = typeSpec,
    ) {
        description = this@mapToModel.description
        isRequired = this@mapToModel.isRequired
        defaultValue = this@mapToModel.defaultValue
        // TODO sample values
    }
}

private fun FieldListIncludeElementStorageModel.mapToModel(context: Context): FieldListIncludeElement {
    return FieldListIncludeElement(
        include = context.getTypeByName(include) as DataObject,
        includeOnly = Array.ofAll(includeOnly),
        excluding = Array.ofAll(excluding),
        overrideRequired = overrideRequired?.mapToModel(),
    )
}

private fun com.giffardtechnologies.restdocs.storage.type.RequiredOverride.mapToModel(): RequiredOverride {
    return RequiredOverride(
        required = required,
        excluding = Array.ofAll(excluding ?: emptyList()),
    )
}

private fun TypeSpecStorageModel.mapToModel(typeSpecIdentifier: String, context: Context): TypeSpec {
    return if (type != null) {
        if (interpretedAs != null) {
            if (parsedAs == null) {
                convertToTypeSpec(type.toBasicType(), interpretedAs).let {
                    if (it is TypeSpec.BasicSpec) {
                        it.copy(restrictions = restrictions.mapRestrictions())
                    } else {
                        it
                    }
                }
            } else {
                val typeSpec = convertToTypeSpec(parsedAs, interpretedAs)
                if (typeSpec is TypeSpec.BasicSpec) {
                    TypeSpec.StringSpec(typeSpec.type, typeSpec.representedAs)
                } else if (typeSpec is TypeSpec.BooleanSpec && typeSpec.representedAs == BooleanRepresentation.AsInteger) {
                    TypeSpec.StringSpec(parsedAs = DataType.IntType, representedAs = DataType.BooleanType)
                } else {
                    throw IllegalArgumentException("cannot interpret as '$interpretedAs' after parsing from String")
                }
            }
        } else {
            when (type) {
                DataTypeStorageModel.INT -> {
                    if (restrictions?.size == 1 && restrictions[0].restriction == "boolean") {
                        TypeSpec.BooleanSpec(BooleanRepresentation.AsInteger)
                    } else {
                        TypeSpec.BasicSpec(
                            DataType.IntType,
                            restrictions = restrictions.mapRestrictions(),
                        )
                    }
                }

                DataTypeStorageModel.LONG -> TypeSpec.BasicSpec(
                    DataType.LongType,
                    restrictions = restrictions.mapRestrictions(),
                )

                DataTypeStorageModel.FLOAT -> TypeSpec.BasicSpec(
                    DataType.FloatType,
                    restrictions = restrictions.mapRestrictions(),
                )

                DataTypeStorageModel.DOUBLE -> TypeSpec.BasicSpec(
                    DataType.DoubleType,
                    restrictions = restrictions.mapRestrictions(),
                )

                DataTypeStorageModel.STRING -> {
                    when (parsedAs) {
                        BasicType.BOOLEAN -> {
                            // TODO valid this versus approach in else-clause
                            TypeSpec.BooleanSpec(BooleanRepresentation.AsString)
                        }
                        null -> {
                            TypeSpec.BasicSpec(
                                DataType.StringType,
                                restrictions = restrictions.mapRestrictions(),
                            )
                        }
                        else -> {
                            TypeSpec.StringSpec(parsedAs.mapToModel())
                        }
                    }
                }

                DataTypeStorageModel.BOOLEAN -> TypeSpec.BooleanSpec()
                DataTypeStorageModel.DATE -> TypeSpec.DateSpec(
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
                DataTypeStorageModel.BITSET -> {
                    if (values == null) {
                        throw ValidationException("$typeSpecIdentifier of 'enum' type must define 'values'")
                    }

                    when (this.key) {
                        null,
                        KeyType.INT -> mapBitSetOfType(keyType = DataType.IntType, values)
                        KeyType.LONG -> mapBitSetOfType(keyType = DataType.LongType, values)
                        KeyType.STRING,
                        KeyType.ENUM -> throw IllegalArgumentException("$typeSpecIdentifier of 'bitset' type cannot have a 'key' of type '${this.key}'")
                    }
                }
            }
        }
    } else if (typeRef != null) {
        TypeSpec.TypeRefSpec(typeRef, context)
    } else {
        throw IllegalArgumentException("$typeSpecIdentifier must have one of [type, typeref]")
    }
}

/**
 * Converts a [BasicType] to a [TypeSpec] with a different underlying representation.
 *
 * This function is used when a field is defined as one type but needs to be interpreted as another. For example, a
 * field might be a string that should be interpreted as an integer.
 *
 * @param type The original type of the field.
 * @param interpretedAs The type that the field should be interpreted as.
 * @return A [TypeSpec] that represents the interpreted type.
 * @throws ValidationException if the `interpretedAs` type is not a valid interpretation of the `type`.
 */
private fun convertToTypeSpec(
    type: BasicType,
    interpretedAs: BasicType
): TypeSpec = when (interpretedAs) {
    BasicType.INT -> {
        when (type.mapToModel()) {
            DataType.StringType -> TypeSpec.BasicSpec(DataType.LongType, DataType.StringType)
            DataType.IntType,
            DataType.LongType,
            DataType.DateType,
            DataType.DoubleType,
            DataType.FloatType,
            DataType.BooleanType -> throw ValidationException("'$interpretedAs' not a valid as interpretation of '$type'")
        }
    }

    BasicType.LONG -> {
        when (type.mapToModel()) {
            DataType.IntType -> TypeSpec.BasicSpec(DataType.LongType, DataType.IntType)
            DataType.StringType -> TypeSpec.BasicSpec(DataType.LongType, DataType.StringType)
            DataType.LongType,
            DataType.DateType,
            DataType.DoubleType,
            DataType.FloatType,
            DataType.BooleanType -> throw ValidationException("'$interpretedAs' not a valid as interpretation of '$type'")
        }
    }

    BasicType.FLOAT -> TODO()
    BasicType.DOUBLE -> TODO()
    BasicType.STRING -> TODO()
    BasicType.BOOLEAN -> if (type == BasicType.INT) {
        TypeSpec.BooleanSpec(BooleanRepresentation.AsInteger)
    } else {
        throw IllegalArgumentException("'$interpretedAs' is not a valid interpretation target from '$type'")
    }

}

private fun BasicType.mapToModel(): DataType.BasicType<*> {
    return when (this) {
        BasicType.INT -> DataType.IntType
        BasicType.LONG -> DataType.LongType
        BasicType.FLOAT -> DataType.FloatType
        BasicType.DOUBLE -> DataType.DoubleType
        BasicType.STRING -> DataType.StringType
        BasicType.BOOLEAN -> DataType.BooleanType
    }
}

@Suppress("unused")
private fun DataTypeStorageModel.mapToModel() : DataType<*> {
    return when(this) {
        DataTypeStorageModel.INT -> DataType.IntType
        DataTypeStorageModel.LONG -> DataType.LongType
        DataTypeStorageModel.FLOAT -> DataType.FloatType
        DataTypeStorageModel.DOUBLE -> DataType.DoubleType
        DataTypeStorageModel.STRING -> DataType.StringType
        DataTypeStorageModel.DATE -> DataType.DateType
        DataTypeStorageModel.BOOLEAN ,
        DataTypeStorageModel.ARRAY,
        DataTypeStorageModel.OBJECT,
        DataTypeStorageModel.COLLECTION,
        DataTypeStorageModel.ENUM,
        DataTypeStorageModel.BITSET -> throw RuntimeException("'$this' cannot be converted to a DataType")
    }
}

private fun <T> mapEnumOfType(
    keyType: DataType.UsableAsKey<T>,
    values: ArrayList<EnumConstant>,
) = enumSpec(
    keyType = keyType,
) {
    values.forEach {
        value(
            value = keyType.parse(it.value),
            longName = if (keyType is DataType.StringType) it.longName ?: it.value else requireNotNull(it.longName) { "'longName' must not be blank for numeric key enums" },
            description = it.description
        )
    }
}

private fun <T> mapBitSetOfType(
    keyType: DataType.UsableAsFlag<T>,
    values: ArrayList<EnumConstant>,
) = bitSetSpec(
    keyType = keyType,
) {
    values.forEach {
        value(
            value = keyType.parse(it.value),
            longName = checkNotNull(it.longName) { "Flag constants must have a long name."},
            description = it.description
        )
    }
}

private fun ServiceStorageModel?.mapToModel(context: Context, documentConfiguration: DocumentConfiguration): Service? {
    return if (this != null) {
        Service(
            description,
            basePath,
            common.mapToModel(context, documentConfiguration),
            methods = methods.mapList { it.mapToModel(context) },
        )
    } else {
        null
    }
}

private fun CommonStorageModel?.mapToModel(context: Context, documentConfiguration: DocumentConfiguration): Service.Common? {
    return if (this != null) {
        Service.Common(
            headers.mapList { it.mapToModelInHeaderContext() },
            parameters.mapList { it.mapToModel(context) },
            responseDataObjects.mapList {
                val dataObject = it.mapToModel(context)
                documentConfiguration.addDataObject(dataObject)
                dataObject
            }
        )
    } else {
        null
    }
}

private fun FieldStorageModel.mapToModelInHeaderContext(): Field {
    return Field(
        name, longName, TypeSpec.BasicSpec(DataType.IntType), description, defaultValue, isRequired
    )
}

private fun HTTPMethod.toModel(): Method.HTTPMethod {
    return Method.HTTPMethod.valueOf(this.name)
}

private fun MethodStorageModel.mapToModel(context: Context): Method {
    return Method(
        method = this.method?.toModel() ?: Method.HTTPMethod.POST,
        path = path,
        protocolsAllowed = Array.ofAll(protocolsAllowed),
        id = id,
        name = name,
        isAuthenticationRequired = isAuthenticationRequired,
        parameterElementList = FieldElementList(parameters.mapList { it.mapToModel(context) }),
        failureCodes = Array.ofAll(failureCodes),
        successCodes = Array.ofAll(successCodes),
        response = response?.mapToModel(context),
        requestBody = requestBody.mapToModel(),
        headers = headers.mapList { it.mapToModelInHeaderContext() },
        description = description,

        )
}

private fun RequestBodyStorageModel?.mapToModel(): RequestBody? {
    return this?.let {
        RequestBody(
            description = description,
            contentTypes = Array.ofAll(contentTypes)
        )
    }
}

private fun ResponseStorageModel.mapToModel(context: Context) : Response {
    return Response(
        typeSpec = this.mapToModel("response", context), // TODO better ID
        description = description,
    )
}
