package com.giffardtechnologies.restdocs.domain

import com.giffardtechnologies.restdocs.domain.type.DataType
import com.giffardtechnologies.restdocs.domain.type.TypeSpec
import io.vavr.collection.Array
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FieldElementListIncludeOnlyTest {

    // ── builder helpers ───────────────────────────────────────────────────────

    private fun field(name: String, type: TypeSpec = TypeSpec.BasicSpec(DataType.StringType)) =
        Field(name = name, longName = name, type = type)

    private fun objectField(name: String, vararg subFields: Field): Field {
        val fieldList = FieldElementList(Array.ofAll(subFields.toList()))
        return Field(name = name, longName = name, type = TypeSpec.ObjectSpec(fieldList))
    }

    private fun arrayObjectField(name: String, vararg subFields: Field): Field {
        val fieldList = FieldElementList(Array.ofAll(subFields.toList()))
        return Field(name = name, longName = name, type = TypeSpec.ArraySpec(TypeSpec.ObjectSpec(fieldList)))
    }

    private fun dataObject(name: String, vararg fields: Field): DataObject {
        val fieldList = FieldElementList(Array.ofAll(fields.toList<FieldListElement>()))
        return DataObject(typeName = name, type = TypeSpec.ObjectSpec(fieldList))
    }

    private fun resolveFieldNames(
        base: DataObject,
        includeOnly: List<String> = emptyList(),
        excluding: List<String> = emptyList(),
    ): List<String> {
        val element = FieldListIncludeElement(
            include = base,
            includeOnly = Array.ofAll(includeOnly),
            excluding = Array.ofAll(excluding),
        )
        return FieldElementList(Array.of(element)).fields.toJavaList().map { it.longName }
    }

    private fun TypeSpec.ObjectSpec.fieldNames() = fields.toJavaList().map { it.longName }

    // ── unchanged behavior ────────────────────────────────────────────────────

    @Test
    fun `neither includeOnly nor excluding returns all fields`() {
        val base = dataObject("Base",
            field("id", TypeSpec.BasicSpec(DataType.IntType)),
            field("name"),
            field("tags"),
        )

        assertEquals(listOf("id", "name", "tags"), resolveFieldNames(base))
    }

    @Test
    fun `excluding only omits specified top-level field`() {
        val base = dataObject("Base",
            field("id", TypeSpec.BasicSpec(DataType.IntType)),
            field("name"),
            field("tags"),
        )

        assertEquals(listOf("id", "name"), resolveFieldNames(base, excluding = listOf("tags")))
    }

    @Test
    fun `excluding sub-field trims nested object`() {
        val base = dataObject("Base",
            field("id", TypeSpec.BasicSpec(DataType.IntType)),
            objectField("address", field("street"), field("city")),
        )

        val element = FieldListIncludeElement(include = base, excluding = Array.of("address.city"))
        val fields = FieldElementList(Array.of(element)).fields.toJavaList()

        assertEquals(listOf("id", "address"), fields.map { it.longName })
        assertEquals(
            listOf("street"),
            (fields.find { it.longName == "address" }!!.type as TypeSpec.ObjectSpec).fieldNames()
        )
    }

    // ── includeOnly only ──────────────────────────────────────────────────────

    @Test
    fun `includeOnly returns only specified top-level fields`() {
        val base = dataObject("Base",
            field("id", TypeSpec.BasicSpec(DataType.IntType)),
            field("name"),
            field("secret"),
        )

        assertEquals(listOf("id", "name"), resolveFieldNames(base, includeOnly = listOf("id", "name")))
    }

    @Test
    fun `includeOnly with object field includes entire sub-tree`() {
        val base = dataObject("Base",
            field("id", TypeSpec.BasicSpec(DataType.IntType)),
            objectField("address", field("street"), field("city")),
        )

        val element = FieldListIncludeElement(include = base, includeOnly = Array.of("address"))
        val fields = FieldElementList(Array.of(element)).fields.toJavaList()

        assertEquals(listOf("address"), fields.map { it.longName })
        assertEquals(listOf("street", "city"), (fields[0].type as TypeSpec.ObjectSpec).fieldNames())
    }

    @Test
    fun `includeOnly with array-of-object field includes entire sub-tree`() {
        val base = dataObject("Base",
            field("id", TypeSpec.BasicSpec(DataType.IntType)),
            arrayObjectField("items", field("sku"), field("qty", TypeSpec.BasicSpec(DataType.IntType))),
        )

        val element = FieldListIncludeElement(include = base, includeOnly = Array.of("items"))
        val fields = FieldElementList(Array.of(element)).fields.toJavaList()

        assertEquals(listOf("items"), fields.map { it.longName })
        val itemsSpec = (fields[0].type as TypeSpec.ArraySpec).items as TypeSpec.ObjectSpec
        assertEquals(listOf("sku", "qty"), itemsSpec.fieldNames())
    }

    // ── nested includeOnly path ───────────────────────────────────────────────

    @Test
    fun `includeOnly nested path includes only specified sub-field`() {
        val base = dataObject("Base",
            field("id", TypeSpec.BasicSpec(DataType.IntType)),
            objectField("address", field("street"), field("city"), field("zip")),
        )

        val element = FieldListIncludeElement(include = base, includeOnly = Array.of("address.city"))
        val fields = FieldElementList(Array.of(element)).fields.toJavaList()

        assertEquals(listOf("address"), fields.map { it.longName })
        assertEquals(listOf("city"), (fields[0].type as TypeSpec.ObjectSpec).fieldNames())
    }

    @Test
    fun `includeOnly nested path on typeRef field resolves referenced object`() {
        val profileObject = dataObject("Profile",
            field("firstName"),
            field("lastName"),
            field("email"),
        )
        val context = object : Context {
            override fun getTypeByName(name: String) = if (name == "Profile") profileObject else null
        }
        val base = dataObject("Base",
            field("id", TypeSpec.BasicSpec(DataType.IntType)),
            field("profile", TypeSpec.TypeRefSpec("Profile", context)),
        )

        val element = FieldListIncludeElement(include = base, includeOnly = Array.of("profile.firstName"))
        val fields = FieldElementList(Array.of(element)).fields.toJavaList()

        assertEquals(listOf("profile"), fields.map { it.longName })
        assertEquals(listOf("firstName"), (fields[0].type as TypeSpec.ObjectSpec).fieldNames())
    }

    @Test
    fun `includeOnly leaf on typeRef field preserves original typeRef type`() {
        val profileObject = dataObject("Profile",
            field("firstName"),
            field("lastName"),
        )
        val context = object : Context {
            override fun getTypeByName(name: String) = if (name == "Profile") profileObject else null
        }
        val base = dataObject("Base",
            field("id", TypeSpec.BasicSpec(DataType.IntType)),
            field("profile", TypeSpec.TypeRefSpec("Profile", context)),
        )

        val element = FieldListIncludeElement(include = base, includeOnly = Array.of("profile"))
        val fields = FieldElementList(Array.of(element)).fields.toJavaList()

        assertEquals(listOf("profile"), fields.map { it.longName })
        assertEquals("Profile", (fields[0].type as TypeSpec.TypeRefSpec).referenceName)
    }

    @Test
    fun `includeOnly nested path on array-of-typeRef field resolves element type`() {
        val tagObject = dataObject("Tag",
            field("id", TypeSpec.BasicSpec(DataType.IntType)),
            field("label"),
        )
        val context = object : Context {
            override fun getTypeByName(name: String) = if (name == "Tag") tagObject else null
        }
        val base = dataObject("Base",
            field("name"),
            field("tags", TypeSpec.ArraySpec(TypeSpec.TypeRefSpec("Tag", context))),
        )

        val element = FieldListIncludeElement(include = base, includeOnly = Array.of("tags.label"))
        val fields = FieldElementList(Array.of(element)).fields.toJavaList()

        assertEquals(listOf("tags"), fields.map { it.longName })
        val tagItemsSpec = (fields[0].type as TypeSpec.ArraySpec).items as TypeSpec.ObjectSpec
        assertEquals(listOf("label"), tagItemsSpec.fieldNames())
    }

    // ── includeOnly + excluding combined ─────────────────────────────────────

    @Test
    fun `includeOnly and excluding combined narrows then trims`() {
        val base = dataObject("Base",
            field("id", TypeSpec.BasicSpec(DataType.IntType)),
            field("name"),
            objectField("address", field("street"), field("city"), field("zip")),
            field("secret"),
        )

        val element = FieldListIncludeElement(
            include = base,
            includeOnly = Array.ofAll(listOf("id", "address")),
            excluding = Array.of("address.zip"),
        )
        val fields = FieldElementList(Array.of(element)).fields.toJavaList()

        assertEquals(listOf("id", "address"), fields.map { it.longName })
        assertEquals(
            listOf("street", "city"),
            (fields.find { it.longName == "address" }!!.type as TypeSpec.ObjectSpec).fieldNames()
        )
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    fun `includeOnly unknown field throws IllegalStateException`() {
        val base = dataObject("Base",
            field("id", TypeSpec.BasicSpec(DataType.IntType)),
            field("name"),
        )
        val element = FieldListIncludeElement(include = base, includeOnly = Array.of("unknown"))

        assertThrows(IllegalStateException::class.java) {
            FieldElementList(Array.of(element)).fields
        }
    }

    @Test
    fun `includeOnly nested path on non-object field throws IllegalStateException`() {
        val base = dataObject("Base",
            field("id", TypeSpec.BasicSpec(DataType.IntType)),
            field("name"),
        )
        val element = FieldListIncludeElement(include = base, includeOnly = Array.of("name.sub"))

        assertThrows(IllegalStateException::class.java) {
            FieldElementList(Array.of(element)).fields
        }
    }
}
