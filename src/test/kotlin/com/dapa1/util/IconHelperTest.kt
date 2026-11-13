package com.dapa1.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class IconHelperTest {

    @Test
    fun testLoadCustomIconNullOrBlank() {
        assertNull(IconHelper.loadCustomIcon(null))
        assertNull(IconHelper.loadCustomIcon(""))
        assertNull(IconHelper.loadCustomIcon("   "))
    }

    @Test
    fun testLoadCustomIconNonExistentAction() {
        assertNull(IconHelper.loadCustomIcon("action:NonExistentActionId12345"))
    }

    @Test
    fun testLoadCustomIconNonExistentFile() {
        assertNull(IconHelper.loadCustomIcon("non/existent/path/icon.png"))
    }

    @Test
    fun testLoadCustomIconValidPng() {
        val tempFile = File.createTempFile("test_icon_", ".png")
        tempFile.deleteOnExit()

        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color.RED
        g.fillRect(0, 0, 16, 16)
        g.dispose()

        ImageIO.write(img, "png", tempFile)

        val icon = IconHelper.loadCustomIcon(tempFile.absolutePath)
        assertNotNull(icon)
    }

    @Test
    fun testLoadCustomIconSvg() {
        val tempFile = File.createTempFile("test_icon_", ".svg")
        tempFile.deleteOnExit()
        tempFile.writeText("""<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"><rect width="16" height="16" fill="blue"/></svg>""")

        val icon = IconHelper.loadCustomIcon(tempFile.absolutePath)
        assertNotNull(icon)
    }

    @Test
    fun testLoadCustomIconEmptyFile() {
        val tempFile = File.createTempFile("test_empty_", ".svg")
        tempFile.deleteOnExit()
        val icon = IconHelper.loadCustomIcon(tempFile.absolutePath)
        assertNull(icon)
    }

    @Test
    fun testLoadCustomIconCorruptSvg() {
        val tempFile = File.createTempFile("test_corrupt_", ".svg")
        tempFile.deleteOnExit()
        tempFile.writeText("not an xml or svg content")
        val icon = IconHelper.loadCustomIcon(tempFile.absolutePath)
        assertNull(icon)
    }

    @Test
    fun testLoadCustomIconEmptyPng() {
        val tempFile = File.createTempFile("test_empty_", ".png")
        tempFile.deleteOnExit()
        val icon = IconHelper.loadCustomIcon(tempFile.absolutePath)
        assertNull(icon)
    }
}
