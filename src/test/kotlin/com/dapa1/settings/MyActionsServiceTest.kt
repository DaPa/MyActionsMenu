package com.dapa1.settings

import com.dapa1.model.ActionItemConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyActionsServiceTest {

    @Test
    fun testDefaultState() {
        val state = MyActionsState()
        assertEquals("myActions", state.menuTitle)
        assertNull(state.menuIconPath)
        assertFalse(state.items.isEmpty())

        val firstItem = state.items.first()
        assertEquals("SaveAll", firstItem.actionId)
        assertFalse(firstItem.isSeparator)
    }

    @Test
    fun testActionItemConfigCopy() {
        val original = ActionItemConfig(
            actionId = "ReformatCode",
            isSeparator = false,
            customText = "Format All Code",
            customIconPath = "/icons/custom.svg"
        )
        val copy = original.copyItem()

        assertNotEquals(original.id, copy.id)
        assertEquals(original.actionId, copy.actionId)
        assertEquals(original.isSeparator, copy.isSeparator)
        assertEquals(original.customText, copy.customText)
        assertEquals(original.customIconPath, copy.customIconPath)
    }

    @Test
    fun testStateItemManagement() {
        val service = MyActionsService()
        val originalItems = service.getItems()
        assertNotNull(originalItems)
        assertTrue(originalItems.isNotEmpty())

        val newItems = listOf(
            ActionItemConfig(actionId = "Cut"),
            ActionItemConfig(isSeparator = true),
            ActionItemConfig(actionId = "Paste", customText = "Custom Paste")
        )

        // Note: service.setItems triggers messageBus if application is initialized;
        // let's test state directly
        val state = service.state
        state.items.clear()
        state.items.addAll(newItems)
        state.menuTitle = "My Custom Toolbar"

        assertEquals(3, service.getItems().size)
        assertEquals("Cut", service.getItems()[0].actionId)
        assertTrue(service.getItems()[1].isSeparator)
        assertEquals("Paste", service.getItems()[2].actionId)
        assertEquals("Custom Paste", service.getItems()[2].customText)
        assertEquals("My Custom Toolbar", service.getMenuTitle())
    }

    @Test
    fun testMenuIconPath() {
        val service = MyActionsService()
        assertNull(service.getMenuIconPath())

        val state = service.state
        state.menuIconPath = "action:Execute"
        assertEquals("action:Execute", service.getMenuIconPath())
    }

    @Test
    fun testPopupDimensions() {
        val service = MyActionsService()
        assertEquals(320, service.getPopupWidth())
        assertEquals(360, service.getPopupHeight())

        service.setPopupWidth(400)
        service.setPopupHeight(500)

        assertEquals(400, service.getPopupWidth())
        assertEquals(500, service.getPopupHeight())
    }
}
