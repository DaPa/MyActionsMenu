package com.dapa1.util

import com.dapa1.model.ActionItemConfig
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class MenuImportExportHelperTest {

    @Test
    fun testShortcutExportIsOptInAndRoundTrips() {
        val items = listOf(
            ActionItemConfig(actionId = "SaveAll"),
            ActionItemConfig(actionId = "Run"),
            ActionItemConfig(actionId = "SaveAll", isSeparator = true),
            ActionItemConfig(actionId = "Other")
        )
        val shortcuts = mapOf(
            "SaveAll" to listOf(KeyboardShortcutExportModel("ctrl pressed S"), KeyboardShortcutExportModel("alt pressed S")),
            "Run" to listOf(KeyboardShortcutExportModel("ctrl pressed K", "ctrl pressed R"))
        )

        val defaultJson = MenuImportExportHelper.exportToJson("Menu", items = items)
        assertFalse(defaultJson.contains("shortcut"))
        assertTrue(MenuImportExportHelper.importFromJson(defaultJson).items.all { it.shortcuts == null })

        val json = MenuImportExportHelper.exportToJson("Menu", items = items, shortcuts = shortcuts)
        val jsonItems = JsonParser.parseString(json).asJsonObject.getAsJsonArray("items")
        assertFalse(jsonItems[0].asJsonObject.getAsJsonArray("shortcuts")[0].asJsonObject.has("secondKeyStroke"))
        assertFalse(jsonItems[2].asJsonObject.has("shortcuts"))
        assertFalse(jsonItems[3].asJsonObject.has("shortcuts"))
        val imported = MenuImportExportHelper.importFromJson(json)
        assertEquals(shortcuts["SaveAll"], imported.items[0].shortcuts)
        assertEquals(shortcuts["Run"], imported.items[1].shortcuts)
        assertNull(imported.items[2].shortcuts)
        assertNull(imported.items[3].shortcuts)
    }

    @Test
    fun testImportShortcutsInLegacyArrayAndObject() {
        val item = """{"actionId":"Run","shortcuts":[{"firstKeyStroke":"ctrl pressed K","secondKeyStroke":"ctrl pressed R"}]}"""
        val expected = listOf(KeyboardShortcutExportModel("ctrl pressed K", "ctrl pressed R"))
        assertEquals(expected, MenuImportExportHelper.importFromJson("[$item]").items.single().shortcuts)
        assertEquals(expected, MenuImportExportHelper.importFromJson("""{"items":[$item]}""").items.single().shortcuts)
        assertNull(MenuImportExportHelper.importFromJson("""[{"actionId":"Run"}]""").items.single().shortcuts)
    }

    @Test
    fun testExportAndImportCycle() {
        val items = listOf(
            ActionItemConfig(actionId = "SaveAll", customText = "Save Everything", customIconPath = "action:SaveAll"),
            ActionItemConfig(isSeparator = true),
            ActionItemConfig(actionId = "Run", customText = null, customIconPath = "C:/icons/custom_run.svg")
        )

        val json = MenuImportExportHelper.exportToJson("My Custom Menu", "action:Execute", items)
        assertNotNull(json)
        assertTrue(json.contains("My Custom Menu"))
        assertTrue(json.contains("action:Execute"))
        assertTrue(json.contains("SaveAll"))
        assertTrue(json.contains("Save Everything"))
        assertTrue(json.contains("custom_run.svg"))

        val imported = MenuImportExportHelper.importFromJson(json)
        assertEquals("My Custom Menu", imported.menuTitle)
        assertEquals("action:Execute", imported.menuIconPath)
        assertEquals(3, imported.items.size)

        val item1 = imported.items[0].toActionItemConfig()
        assertEquals("SaveAll", item1.actionId)
        assertEquals("Save Everything", item1.customText)
        assertEquals("action:SaveAll", item1.customIconPath)
        assertFalse(item1.isSeparator)

        val item2 = imported.items[1].toActionItemConfig()
        assertTrue(item2.isSeparator)

        val item3 = imported.items[2].toActionItemConfig()
        assertEquals("Run", item3.actionId)
        assertNull(item3.customText)
        assertEquals("C:/icons/custom_run.svg", item3.customIconPath)
        assertFalse(item3.isSeparator)
    }

    @Test
    fun testImportArrayOnlyJson() {
        val json = """
            [
                { "actionId": "CompileDirty" },
                { "isSeparator": true },
                { "actionId": "Debug", "customText": "Debug App", "customIconPath": "action:Debug" }
            ]
        """.trimIndent()

        val imported = MenuImportExportHelper.importFromJson(json)
        assertEquals("myActions", imported.menuTitle)
        assertEquals(3, imported.items.size)
        assertEquals("CompileDirty", imported.items[0].actionId)
        assertTrue(imported.items[1].isSeparator)
        assertEquals("Debug", imported.items[2].actionId)
        assertEquals("Debug App", imported.items[2].customText)
        assertEquals("action:Debug", imported.items[2].customIconPath)
    }

    @Test
    fun testImportObjectWithoutTitle() {
        val json = """
            {
                "items": [
                    { "actionId": "SaveAll" }
                ]
            }
        """.trimIndent()

        val imported = MenuImportExportHelper.importFromJson(json)
        assertEquals("myActions", imported.menuTitle)
        assertEquals(1, imported.items.size)
        assertEquals("SaveAll", imported.items[0].actionId)
    }

    @Test
    fun testImportEmptyItems() {
        val json = """
            {
                "menuTitle": "Empty Menu",
                "items": []
            }
        """.trimIndent()

        val imported = MenuImportExportHelper.importFromJson(json)
        assertEquals("Empty Menu", imported.menuTitle)
        assertTrue(imported.items.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testImportEmptyJson() {
        MenuImportExportHelper.importFromJson("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testImportInvalidJson() {
        MenuImportExportHelper.importFromJson("true")
    }
}
