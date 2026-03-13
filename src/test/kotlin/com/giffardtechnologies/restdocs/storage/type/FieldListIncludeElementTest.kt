package com.giffardtechnologies.restdocs.storage.type

import com.giffardtechnologies.restdocs.DocValidator
import com.giffardtechnologies.restdocs.jackson.validation.ValidationException
import com.giffardtechnologies.restdocs.storage.DataObject
import com.giffardtechnologies.restdocs.storage.Document
import io.vavr.collection.HashSet as VavrHashSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FieldListIncludeElementTest {

    // ── builder helpers ───────────────────────────────────────────────────────

    private fun field(name: String, type: DataType) =
        Field(name = name, longName = name, type = type)

    private fun objectField(name: String, vararg subFields: Field) =
        Field(
            name = name,
            longName = name,
            type = DataType.OBJECT,
            fields = subFields.mapTo(ArrayList()) { it },
        )

    private fun arrayObjectField(name: String, vararg subFields: Field) =
        Field(
            name = name,
            longName = name,
            type = DataType.ARRAY,
            items = TypeSpec(
                type = DataType.OBJECT,
                fields = subFields.mapTo(ArrayList()) { it },
            ),
        )

    private fun typeRefField(name: String, typeRef: String) =
        Field(name = name, longName = name, typeRef = typeRef)

    private fun dataObject(name: String, vararg fields: Field) =
        DataObject(name = name, fields = fields.mapTo(ArrayList()) { it })

    private fun document(vararg dataObjects: DataObject) =
        Document(title = "Test", service = null, dataObjects = ArrayList(dataObjects.toList()))

    private fun fullContext(document: Document): DocValidator.FullContext {
        val names = VavrHashSet.ofAll(document.dataObjects.map { it.name })
        return DocValidator.FullContext(names, document, DocValidator.ValidationOptions())
    }

    private fun resolveFieldNames(
        document: Document,
        includeOnly: List<String> = emptyList(),
        excluding: List<String> = emptyList(),
        targetObject: String = "Base",
    ): List<String> {
        val element = FieldListIncludeElement(
            include = targetObject,
            includeOnly = ArrayList(includeOnly),
            excluding = ArrayList(excluding),
        )
        return FieldElementList(document, listOf(element)).getFields().map { it.longName }
    }

    // ── unchanged behavior ────────────────────────────────────────────────────

    @Test
    fun `neither includeOnly nor excluding returns all fields`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                field("name", DataType.STRING),
                field("tags", DataType.STRING),
            )
        )

        assertEquals(listOf("id", "name", "tags"), resolveFieldNames(doc))
    }

    @Test
    fun `excluding only omits specified top-level field`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                field("name", DataType.STRING),
                field("tags", DataType.STRING),
            )
        )

        assertEquals(listOf("id", "name"), resolveFieldNames(doc, excluding = listOf("tags")))
    }

    @Test
    fun `excluding sub-field trims nested object`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                objectField("address",
                    field("street", DataType.STRING),
                    field("city", DataType.STRING),
                ),
            )
        )

        val element = FieldListIncludeElement(include = "Base", excluding = arrayListOf("address.city"))
        val fields = FieldElementList(doc, listOf(element)).getFields()

        assertEquals(listOf("id", "address"), fields.map { it.longName })
        assertEquals(
            listOf("street"),
            fields.first { it.longName == "address" }.fields!!.filterIsInstance<Field>().map { it.longName }
        )
    }

    // ── includeOnly only ──────────────────────────────────────────────────────

    @Test
    fun `includeOnly returns only specified top-level fields`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                field("name", DataType.STRING),
                field("secret", DataType.STRING),
            )
        )

        assertEquals(listOf("id", "name"), resolveFieldNames(doc, includeOnly = listOf("id", "name")))
    }

    @Test
    fun `includeOnly with object field includes entire sub-tree`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                objectField("address",
                    field("street", DataType.STRING),
                    field("city", DataType.STRING),
                ),
            )
        )

        val element = FieldListIncludeElement(include = "Base", includeOnly = arrayListOf("address"))
        val fields = FieldElementList(doc, listOf(element)).getFields()

        assertEquals(listOf("address"), fields.map { it.longName })
        assertEquals(
            listOf("street", "city"),
            fields[0].fields!!.filterIsInstance<Field>().map { it.longName }
        )
    }

    @Test
    fun `includeOnly with array-of-object field includes entire sub-tree`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                arrayObjectField("items",
                    field("sku", DataType.STRING),
                    field("qty", DataType.INT),
                ),
            )
        )

        val element = FieldListIncludeElement(include = "Base", includeOnly = arrayListOf("items"))
        val fields = FieldElementList(doc, listOf(element)).getFields()

        assertEquals(listOf("items"), fields.map { it.longName })
        assertEquals(DataType.ARRAY, fields[0].type)
        assertEquals(
            listOf("sku", "qty"),
            fields[0].items!!.fields!!.filterIsInstance<Field>().map { it.longName }
        )
    }

    // ── scenario 4: nested includeOnly path ───────────────────────────────────

    @Test
    fun `includeOnly nested path includes only specified sub-field`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                objectField("address",
                    field("street", DataType.STRING),
                    field("city", DataType.STRING),
                    field("zip", DataType.STRING),
                ),
            )
        )

        val element = FieldListIncludeElement(include = "Base", includeOnly = arrayListOf("address.city"))
        val fields = FieldElementList(doc, listOf(element)).getFields()

        assertEquals(listOf("address"), fields.map { it.longName })
        assertEquals(
            listOf("city"),
            fields[0].fields!!.filterIsInstance<Field>().map { it.longName }
        )
    }

    @Test
    fun `includeOnly nested path on typeRef field resolves referenced object`() {
        val doc = document(
            dataObject("Profile",
                field("firstName", DataType.STRING),
                field("lastName", DataType.STRING),
                field("email", DataType.STRING),
            ),
            dataObject("Base",
                field("id", DataType.INT),
                typeRefField("profile", "Profile"),
            )
        )

        val element = FieldListIncludeElement(include = "Base", includeOnly = arrayListOf("profile.firstName"))
        val fields = FieldElementList(doc, listOf(element)).getFields()

        assertEquals(listOf("profile"), fields.map { it.longName })
        assertEquals(DataType.OBJECT, fields[0].type)
        assertEquals(
            listOf("firstName"),
            fields[0].fields!!.filterIsInstance<Field>().map { it.longName }
        )
    }

    @Test
    fun `includeOnly leaf on typeRef field includes field as-is`() {
        val doc = document(
            dataObject("Profile",
                field("firstName", DataType.STRING),
                field("lastName", DataType.STRING),
            ),
            dataObject("Base",
                field("id", DataType.INT),
                typeRefField("profile", "Profile"),
            )
        )

        val element = FieldListIncludeElement(include = "Base", includeOnly = arrayListOf("profile"))
        val fields = FieldElementList(doc, listOf(element)).getFields()

        assertEquals(listOf("profile"), fields.map { it.longName })
        assertEquals("Profile", fields[0].typeRef)
    }

    @Test
    fun `includeOnly nested path on array-of-typeRef field resolves element type`() {
        val doc = document(
            dataObject("Tag",
                field("id", DataType.INT),
                field("label", DataType.STRING),
            ),
            dataObject("Base",
                field("name", DataType.STRING),
                Field(
                    name = "tags",
                    longName = "tags",
                    type = DataType.ARRAY,
                    items = TypeSpec(typeRef = "Tag"),
                ),
            )
        )

        val element = FieldListIncludeElement(include = "Base", includeOnly = arrayListOf("tags.label"))
        val fields = FieldElementList(doc, listOf(element)).getFields()

        assertEquals(listOf("tags"), fields.map { it.longName })
        assertEquals(DataType.ARRAY, fields[0].type)
        assertEquals(
            listOf("label"),
            fields[0].items!!.fields!!.filterIsInstance<Field>().map { it.longName }
        )
    }

    // ── scenario 2: includeOnly + excluding combined ──────────────────────────

    @Test
    fun `includeOnly and excluding combined narrows then trims`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                field("name", DataType.STRING),
                objectField("address",
                    field("street", DataType.STRING),
                    field("city", DataType.STRING),
                    field("zip", DataType.STRING),
                ),
                field("secret", DataType.STRING),
            )
        )

        val element = FieldListIncludeElement(
            include = "Base",
            includeOnly = arrayListOf("id", "address"),
            excluding = arrayListOf("address.zip"),
        )
        val fields = FieldElementList(doc, listOf(element)).getFields()

        assertEquals(listOf("id", "address"), fields.map { it.longName })
        assertEquals(
            listOf("street", "city"),
            fields.first { it.longName == "address" }.fields!!.filterIsInstance<Field>().map { it.longName }
        )
    }

    // ── getFieldDetails mirrors getFields ─────────────────────────────────────

    @Test
    fun `getFieldDetails with includeOnly pairs fields with include element`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                field("name", DataType.STRING),
                field("secret", DataType.STRING),
            )
        )
        val includeElement = FieldListIncludeElement(
            include = "Base",
            includeOnly = arrayListOf("id", "name"),
        )

        val details = FieldElementList(doc, listOf(includeElement)).getFieldDetails()

        assertEquals(listOf("id", "name"), details.map { it.field.longName })
        details.forEach { assertEquals(includeElement, it.includedBy) }
    }

    @Test
    fun `getFieldDetails with includeOnly and excluding narrows and trims`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                objectField("address",
                    field("street", DataType.STRING),
                    field("city", DataType.STRING),
                ),
                field("secret", DataType.STRING),
            )
        )
        val includeElement = FieldListIncludeElement(
            include = "Base",
            includeOnly = arrayListOf("address"),
            excluding = arrayListOf("address.city"),
        )

        val details = FieldElementList(doc, listOf(includeElement)).getFieldDetails()

        assertEquals(listOf("address"), details.map { it.field.longName })
        assertEquals(
            listOf("street"),
            details[0].field.fields!!.filterIsInstance<Field>().map { it.longName }
        )
    }

    // ── scenario 3: validation ────────────────────────────────────────────────

    @Test
    fun `validate includeOnly with unknown field throws ValidationException`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                field("name", DataType.STRING),
            )
        )
        val element = FieldListIncludeElement(include = "Base", includeOnly = arrayListOf("unknown"))

        assertThrows(ValidationException::class.java) {
            element.validate(fullContext(doc))
        }
    }

    @Test
    fun `validate excluding path not covered by includeOnly throws ValidationException`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                field("name", DataType.STRING),
                objectField("address",
                    field("street", DataType.STRING),
                    field("city", DataType.STRING),
                ),
            )
        )
        // includeOnly narrows to id + name, but excluding references address which is not included
        val element = FieldListIncludeElement(
            include = "Base",
            includeOnly = arrayListOf("id", "name"),
            excluding = arrayListOf("address.city"),
        )

        assertThrows(ValidationException::class.java) {
            element.validate(fullContext(doc))
        }
    }

    @Test
    fun `validate valid includeOnly only does not throw`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                field("name", DataType.STRING),
            )
        )
        val element = FieldListIncludeElement(include = "Base", includeOnly = arrayListOf("id"))

        element.validate(fullContext(doc)) // must not throw
    }

    @Test
    fun `validate valid includeOnly and excluding combination does not throw`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                objectField("address",
                    field("street", DataType.STRING),
                    field("city", DataType.STRING),
                ),
            )
        )
        val element = FieldListIncludeElement(
            include = "Base",
            includeOnly = arrayListOf("address"),
            excluding = arrayListOf("address.city"),
        )

        element.validate(fullContext(doc)) // must not throw
    }

    @Test
    fun `validate excluding unknown field throws ValidationException regardless of includeOnly`() {
        val doc = document(
            dataObject("Base",
                field("id", DataType.INT),
                objectField("address",
                    field("street", DataType.STRING),
                ),
            )
        )
        val element = FieldListIncludeElement(
            include = "Base",
            includeOnly = arrayListOf("address"),
            excluding = arrayListOf("address.unknown"),
        )

        assertThrows(ValidationException::class.java) {
            element.validate(fullContext(doc))
        }
    }

    @Test
    fun `validate missing include type throws ValidationException`() {
        val doc = document(
            dataObject("Base", field("id", DataType.INT))
        )
        val element = FieldListIncludeElement(include = "NonExistent")

        assertThrows(ValidationException::class.java) {
            element.validate(fullContext(doc))
        }
    }
}
