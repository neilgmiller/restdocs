package com.giffardtechnologies.restdocs.storage

import com.fasterxml.jackson.annotation.JsonProperty
import com.giffardtechnologies.restdocs.storage.type.BasicType
import com.giffardtechnologies.restdocs.storage.type.DataType
import com.giffardtechnologies.restdocs.storage.type.EnumConstant
import com.giffardtechnologies.restdocs.storage.type.FieldListElement
import com.giffardtechnologies.restdocs.storage.type.FlagType
import com.giffardtechnologies.restdocs.storage.type.KeyType
import com.giffardtechnologies.restdocs.storage.type.TypeSpec
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException

// TODO in the future add an encoding type option and ways to specify non-JSON responses
/**
 * Describes the JSON body returned by the server for a successful API method invocation.
 *
 * Extends [TypeSpec] to carry the full type description of the response payload alongside a
 * human-readable [description].
 *
 * @property description Human-readable description of the response payload.
 * @property longName An optional human-readable name used in documentation, e.g. for a scalar
 * `asyncResponse` rendered as a single field row named "jr".
 * @property noPayload No value is ever written for this response. Set instead of [type]/[typeRef]
 * to positively document a job (or a synchronous method) that produces no payload.
 * @param type The primitive or structural [DataType] of the response body.
 * @param interpretedAs An optional [BasicType] that describes how the raw type should be
 * interpreted (e.g., a STRING interpreted as a date).
 * @param typeRef A reference to a named [com.giffardtechnologies.restdocs.storage.DataObject],
 * [com.giffardtechnologies.restdocs.storage.NamedEnumeration], or
 * [com.giffardtechnologies.restdocs.storage.NamedBitSet] defined elsewhere in the document.
 * @param key The key type for a [DataType.COLLECTION] response.
 * @param flagType The integer storage type for a [DataType.BITSET] response.
 * @param items The element type spec for an [DataType.ARRAY] or [DataType.COLLECTION] response.
 * @param restrictions Optional value restrictions applied to the response.
 * @param fields The inline object fields when the response type is [DataType.OBJECT].
 * @param values The enumeration constants when the response type is [DataType.ENUM] or
 * [DataType.BITSET].
 */
class Response(
    val description: String? = null,
    val longName: String? = null,
    val noPayload: Boolean = false,
    type: DataType? = null,
    parsedAs: BasicType? = null,
    interpretedAs: BasicType? = null,
    @JsonProperty("typeref")
    typeRef: String? = null,
    key: KeyType? = null,
    flagType: FlagType? = null,
    items: TypeSpec? = null,
    restrictions: ArrayList<Restriction>? = null,
    fields: ArrayList<FieldListElement>? = null,
    values: ArrayList<EnumConstant>? = null,
) : TypeSpec(type, parsedAs, interpretedAs, typeRef, key, flagType, items, restrictions, fields, values) {

    override fun validate(validationContext: Any?) {
        if (noPayload) {
            if (type != null || typeRef != null) {
                throw ValidationException(
                    "Response cannot have 'noPayload' combined with 'type' or 'typeref'"
                )
            }
            if (parsedAs != null || interpretedAs != null || key != null || flagType != null ||
                items != null || restrictions != null || fields != null || values != null) {
                throw ValidationException(
                    "Response cannot have 'noPayload' combined with substructure fields " +
                    "(parsedAs/interpretedAs/key/flagType/items/restrictions/fields/values)"
                )
            }
            return
        }
        super.validate(validationContext)
    }
}
