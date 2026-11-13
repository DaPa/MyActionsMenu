package com.dapa1.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.Presentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MyActionsToolbarWidgetTest {

    @Test
    fun testWidgetTracking() {
        val widgetAction = MyActionsToolbarWidget()
        val presentation = Presentation()

        val component = widgetAction.createCustomComponent(presentation, ActionPlaces.MAIN_TOOLBAR)
        assertNotNull(component)

        // When component is not showing on screen, findVisibleToolbarWidget returns null
        val visible = findVisibleToolbarWidget()
        // In headless test environment with no visible frame, visible is null
        assertNull(visible)
    }

    @Test
    fun testDefaultIconIsIntentionBulb() {
        val widgetAction = MyActionsToolbarWidget()
        assertEquals(AllIcons.Actions.IntentionBulb, widgetAction.templatePresentation.icon)
    }
}
