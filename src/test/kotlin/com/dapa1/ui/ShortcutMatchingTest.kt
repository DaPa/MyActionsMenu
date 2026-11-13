package com.dapa1.ui

import com.intellij.openapi.actionSystem.KeyboardShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.KeyStroke

class ShortcutMatchingTest {

    @Test
    fun testFunctionKeyMatching() {
        val f11Stroke = KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0)
        val button = JButton()
        val event = KeyEvent(button, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_F11, KeyEvent.CHAR_UNDEFINED)
        val eventStroke = KeyStroke.getKeyStrokeForEvent(event)

        val shortcut = KeyboardShortcut(f11Stroke, null)
        assertTrue(MyActionsPopup.matchKeyStrokes(eventStroke, shortcut.firstKeyStroke))
    }

    @Test
    fun testModifierKeyMatching() {
        val ctrlSStroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)
        val button = JButton()
        val event = KeyEvent(button, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_S, 's')
        val eventStroke = KeyStroke.getKeyStrokeForEvent(event)

        val shortcut = KeyboardShortcut(ctrlSStroke, null)
        assertTrue(MyActionsPopup.matchKeyStrokes(eventStroke, shortcut.firstKeyStroke))
    }

    @Test
    fun testCtrlAltLMatching() {
        val ctrlAltLStroke = KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK)
        val button = JButton()
        val event = KeyEvent(button, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK, KeyEvent.VK_L, 'l')
        val eventStroke = KeyStroke.getKeyStrokeForEvent(event)

        val shortcut = KeyboardShortcut(ctrlAltLStroke, null)
        assertTrue(MyActionsPopup.matchKeyStrokes(eventStroke, shortcut.firstKeyStroke))
    }

    @Test
    fun testShiftF10Matching() {
        val shiftF10Stroke = KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK)
        val button = JButton()
        val event = KeyEvent(button, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_F10, KeyEvent.CHAR_UNDEFINED)
        val eventStroke = KeyStroke.getKeyStrokeForEvent(event)

        val shortcut = KeyboardShortcut(shiftF10Stroke, null)
        assertTrue(MyActionsPopup.matchKeyStrokes(eventStroke, shortcut.firstKeyStroke))
    }

    @Test
    fun testNonMatchingKeyStrokes() {
        val f11Stroke = KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0)
        val button = JButton()
        val eventF10 = KeyEvent(button, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_F10, KeyEvent.CHAR_UNDEFINED)
        val eventStrokeF10 = KeyStroke.getKeyStrokeForEvent(eventF10)

        val shortcut = KeyboardShortcut(f11Stroke, null)
        assertFalse(MyActionsPopup.matchKeyStrokes(eventStrokeF10, shortcut.firstKeyStroke))
    }

    @Test
    fun testTextEditingShortcuts() {
        val button = JButton()
        val ctrlA = KeyEvent(button, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_A, 'a')
        val ctrlC = KeyEvent(button, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_C, 'c')
        val ctrlV = KeyEvent(button, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_V, 'v')
        val plainLetter = KeyEvent(button, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_G, 'g')

        assertTrue(MyActionsPopup.isTextEditingShortcut(ctrlA))
        assertTrue(MyActionsPopup.isTextEditingShortcut(ctrlC))
        assertTrue(MyActionsPopup.isTextEditingShortcut(ctrlV))
        assertFalse(MyActionsPopup.isTextEditingShortcut(plainLetter))
    }
}
