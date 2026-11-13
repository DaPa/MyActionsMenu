package com.dapa1.util

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IconLoader
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBImageIcon
import java.awt.Image
import java.awt.Toolkit
import java.io.File
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.Icon

object IconHelper {
    private val LOG = Logger.getInstance(IconHelper::class.java)

    fun loadCustomIcon(iconPath: String?): Icon? {
        if (iconPath.isNullOrBlank()) return null

        if (iconPath.startsWith("action:")) {
            val actionId = iconPath.removePrefix("action:")
            val actionManager = runCatching { ActionManager.getInstance() }.getOrNull()
            val action = actionManager?.getAction(actionId)
            val icon = action?.templatePresentation?.icon
            if (icon != null) {
                return scaleIfNeeded(icon)
            }
            return null
        }

        val actionManager = runCatching { ActionManager.getInstance() }.getOrNull()
        val action = actionManager?.getAction(iconPath)
        val actionIcon = action?.templatePresentation?.icon
        if (actionIcon != null) {
            return scaleIfNeeded(actionIcon)
        }

        val file = File(iconPath)
        if (!file.exists() || !file.isFile || file.length() == 0L) return null

        val lowerName = file.name.lowercase()
        if (lowerName.endsWith(".svg")) {
            val svgIcon = loadSvgIcon(file)
            if (svgIcon != null) return svgIcon
        }

        try {
            val image: Image? = ImageIO.read(file)
            if (image != null && image.getWidth(null) > 0 && image.getHeight(null) > 0) {
                return scaleIfNeeded(JBImageIcon(image))
            }
        } catch (e: Throwable) {
            LOG.debug("Failed to read image with ImageIO: $iconPath", e)
        }

        try {
            val image: Image? = Toolkit.getDefaultToolkit().createImage(file.absolutePath)
            if (image != null) {
                val swingIcon = ImageIcon(image)
                if (swingIcon.iconWidth > 0 && swingIcon.iconHeight > 0) {
                    return scaleIfNeeded(JBImageIcon(image))
                }
            }
        } catch (e: Throwable) {
            LOG.debug("Failed to read image with Toolkit: $iconPath", e)
        }

        if (!lowerName.endsWith(".svg")) {
            val svgIcon = loadSvgIcon(file)
            if (svgIcon != null) return svgIcon
        }

        return null
    }

    private fun loadSvgIcon(file: File): Icon? {
        return try {
            if (file.length() == 0L) return null
            val text = file.readText()
            if (!text.contains("<svg", ignoreCase = true)) return null
            val url = file.toURI().toURL()
            val icon = IconLoader.findIcon(url) ?: return null
            if (icon.iconWidth > 0 && icon.iconHeight > 0) {
                scaleIfNeeded(icon)
            } else {
                null
            }
        } catch (e: Throwable) {
            LOG.debug("Failed to load SVG icon: ${file.absolutePath}", e)
            null
        }
    }

    private fun scaleIfNeeded(icon: Icon): Icon {
        return if (icon.iconWidth > 24 || icon.iconHeight > 24) {
            IconUtil.resizeSquared(icon, 16)
        } else {
            icon
        }
    }
}
