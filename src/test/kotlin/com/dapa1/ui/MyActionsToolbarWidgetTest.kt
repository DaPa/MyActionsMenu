package com.dapa1.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.Presentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import javax.swing.JPanel

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

    @Test
    fun testVisibleWidgetIsSelectedFromInvokingWindowOnly() {
        val firstWindow = JPanel()
        val secondWindow = JPanel()
        val firstWidget = object : JPanel() { override fun isShowing() = true }
        val secondWidget = object : JPanel() { override fun isShowing() = true }
        val hiddenWidget = JPanel()
        firstWindow.add(firstWidget)
        secondWindow.add(secondWidget)
        secondWindow.add(hiddenWidget)

        val widgets = listOf(firstWidget, hiddenWidget, secondWidget)
        assertSame(secondWidget, findVisibleToolbarWidget(widgets, secondWindow))
        assertSame(firstWidget, findVisibleToolbarWidget(widgets, firstWindow))
        assertNull(findVisibleToolbarWidget(widgets, JPanel()))
        assertNull(findVisibleToolbarWidget(widgets, null))
    }
}
