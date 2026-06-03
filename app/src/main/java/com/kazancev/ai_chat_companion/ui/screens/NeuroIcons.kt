package com.kazancev.ai_chat_companion.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object NeuroIcons {
    val Back = lineIcon("NeuroBack") {
        moveTo(19f, 12f)
        lineTo(5.5f, 12f)
        moveTo(11f, 6.5f)
        lineTo(5.5f, 12f)
        lineTo(11f, 17.5f)
    }

    val Add = lineIcon("NeuroAdd") {
        moveTo(12f, 4.5f)
        lineTo(12f, 19.5f)
        moveTo(4.5f, 12f)
        lineTo(19.5f, 12f)
    }

    val Apps = lineIcon("NeuroApps", stroke = 2.6f) {
        dot(6.5f, 6.5f); dot(12f, 6.5f); dot(17.5f, 6.5f)
        dot(6.5f, 12f); dot(12f, 12f); dot(17.5f, 12f)
        dot(6.5f, 17.5f); dot(12f, 17.5f); dot(17.5f, 17.5f)
    }

    val ArrowUp = lineIcon("NeuroArrowUp", stroke = 2.5f) {
        moveTo(12f, 19f)
        lineTo(12f, 5.2f)
        moveTo(6.7f, 10.4f)
        lineTo(12f, 5.1f)
        lineTo(17.3f, 10.4f)
    }

    val Agent = lineIcon("NeuroAgent") {
        roundedRect(4.5f, 5f, 16.5f, 18.5f)
        moveTo(8f, 9f)
        lineTo(13f, 9f)
        moveTo(8f, 13f)
        lineTo(11.5f, 13f)
        moveTo(15.5f, 14f)
        lineTo(21f, 18.8f)
        lineTo(17.1f, 19.7f)
        lineTo(15.5f, 23f)
        close()
    }

    val Branch = lineIcon("NeuroBranch") {
        moveTo(7f, 6f)
        lineTo(7f, 15f)
        curveTo(7f, 17.2f, 8.8f, 19f, 11f, 19f)
        lineTo(17f, 19f)
        moveTo(17f, 19f)
        lineTo(14.2f, 16.2f)
        moveTo(17f, 19f)
        lineTo(14.2f, 21.8f)
        moveTo(7f, 10.5f)
        lineTo(17f, 10.5f)
        moveTo(17f, 10.5f)
        lineTo(14.2f, 7.7f)
        moveTo(17f, 10.5f)
        lineTo(14.2f, 13.3f)
    }

    val Camera = lineIcon("NeuroCamera") {
        roundedRect(4.3f, 7.2f, 19.7f, 18.8f)
        moveTo(8.2f, 7.2f)
        lineTo(9.8f, 4.9f)
        lineTo(14.2f, 4.9f)
        lineTo(15.8f, 7.2f)
        moveTo(12f, 10.4f)
        curveTo(10.2f, 10.4f, 8.8f, 11.8f, 8.8f, 13.6f)
        curveTo(8.8f, 15.4f, 10.2f, 16.8f, 12f, 16.8f)
        curveTo(13.8f, 16.8f, 15.2f, 15.4f, 15.2f, 13.6f)
        curveTo(15.2f, 11.8f, 13.8f, 10.4f, 12f, 10.4f)
    }

    val Close = lineIcon("NeuroClose", stroke = 2.6f) {
        moveTo(7f, 7f)
        lineTo(17f, 17f)
        moveTo(17f, 7f)
        lineTo(7f, 17f)
    }

    val Check = lineIcon("NeuroCheck", stroke = 2.4f) {
        moveTo(5.2f, 12.4f)
        lineTo(9.5f, 16.7f)
        lineTo(18.8f, 7.3f)
    }

    val ChevronDown = lineIcon("NeuroChevronDown", stroke = 2.4f) {
        moveTo(6.7f, 9.2f)
        lineTo(12f, 14.5f)
        lineTo(17.3f, 9.2f)
    }

    val ChevronUp = lineIcon("NeuroChevronUp", stroke = 2.4f) {
        moveTo(6.7f, 14.8f)
        lineTo(12f, 9.5f)
        lineTo(17.3f, 14.8f)
    }

    val AgentCore = lineIcon("NeuroAgentCore", stroke = 2.1f) {
        moveTo(12f, 3.8f)
        curveTo(14.2f, 3.8f, 15.1f, 5.2f, 15.3f, 6.7f)
        curveTo(16.8f, 6.1f, 18.6f, 6.4f, 19.7f, 7.9f)
        curveTo(20.9f, 9.6f, 20.2f, 11.2f, 19.1f, 12f)
        curveTo(20.2f, 12.8f, 20.9f, 14.4f, 19.7f, 16.1f)
        curveTo(18.6f, 17.6f, 16.8f, 17.9f, 15.3f, 17.3f)
        curveTo(15.1f, 18.8f, 14.2f, 20.2f, 12f, 20.2f)
        curveTo(9.8f, 20.2f, 8.9f, 18.8f, 8.7f, 17.3f)
        curveTo(7.2f, 17.9f, 5.4f, 17.6f, 4.3f, 16.1f)
        curveTo(3.1f, 14.4f, 3.8f, 12.8f, 4.9f, 12f)
        curveTo(3.8f, 11.2f, 3.1f, 9.6f, 4.3f, 7.9f)
        curveTo(5.4f, 6.4f, 7.2f, 6.1f, 8.7f, 6.7f)
        curveTo(8.9f, 5.2f, 9.8f, 3.8f, 12f, 3.8f)
        moveTo(9f, 12f)
        lineTo(15f, 12f)
    }

    val Copy = lineIcon("NeuroCopy") {
        roundedRect(8f, 6f, 17.5f, 18.5f)
        moveTo(5.8f, 15.2f)
        lineTo(5.8f, 4.8f)
        lineTo(14.8f, 4.8f)
    }

    val Download = lineIcon("NeuroDownload", stroke = 2.35f) {
        moveTo(12f, 3.8f)
        lineTo(12f, 15.2f)
        moveTo(7.3f, 10.7f)
        lineTo(12f, 15.4f)
        lineTo(16.7f, 10.7f)
        moveTo(5.2f, 14.4f)
        lineTo(5.2f, 18.8f)
        curveTo(5.2f, 20f, 6f, 20.8f, 7.2f, 20.8f)
        lineTo(16.8f, 20.8f)
        curveTo(18f, 20.8f, 18.8f, 20f, 18.8f, 18.8f)
        lineTo(18.8f, 14.4f)
    }

    val Edit = lineIcon("NeuroEdit", stroke = 2.15f) {
        roundedRect(4.5f, 4.5f, 19.2f, 19.2f)
        moveTo(8.7f, 15.1f)
        lineTo(9.4f, 11.7f)
        lineTo(16f, 5.1f)
        curveTo(16.7f, 4.4f, 17.8f, 4.4f, 18.5f, 5.1f)
        curveTo(19.2f, 5.8f, 19.2f, 6.9f, 18.5f, 7.6f)
        lineTo(11.9f, 14.2f)
        lineTo(8.7f, 15.1f)
    }

    val Globe = lineIcon("NeuroGlobe") {
        moveTo(12f, 3.8f)
        curveTo(7.5f, 3.8f, 3.8f, 7.5f, 3.8f, 12f)
        curveTo(3.8f, 16.5f, 7.5f, 20.2f, 12f, 20.2f)
        curveTo(16.5f, 20.2f, 20.2f, 16.5f, 20.2f, 12f)
        curveTo(20.2f, 7.5f, 16.5f, 3.8f, 12f, 3.8f)
        moveTo(4.5f, 12f)
        lineTo(19.5f, 12f)
        moveTo(12f, 3.8f)
        curveTo(14.2f, 6f, 15.2f, 8.8f, 15.2f, 12f)
        curveTo(15.2f, 15.2f, 14.2f, 18f, 12f, 20.2f)
        moveTo(12f, 3.8f)
        curveTo(9.8f, 6f, 8.8f, 8.8f, 8.8f, 12f)
        curveTo(8.8f, 15.2f, 9.8f, 18f, 12f, 20.2f)
    }

    val Help = lineIcon("NeuroHelp", stroke = 2.25f) {
        moveTo(9f, 9f)
        curveTo(9f, 7.2f, 10.3f, 6f, 12.1f, 6f)
        curveTo(13.9f, 6f, 15.2f, 7.1f, 15.2f, 8.8f)
        curveTo(15.2f, 10f, 14.5f, 10.8f, 13.4f, 11.5f)
        curveTo(12.5f, 12.1f, 12f, 12.9f, 12f, 14f)
        moveTo(12f, 17.6f)
        lineTo(12.01f, 17.6f)
    }

    val Image = lineIcon("NeuroImage") {
        roundedRect(6.4f, 6.8f, 20f, 18.8f)
        moveTo(4f, 16.5f)
        lineTo(4f, 5.2f)
        lineTo(17.6f, 5.2f)
        moveTo(8.5f, 15.6f)
        lineTo(11f, 12.7f)
        lineTo(13.2f, 14.5f)
        lineTo(15.1f, 12.4f)
        lineTo(18.2f, 15.6f)
        moveTo(16.5f, 9.4f)
        lineTo(16.6f, 9.4f)
    }

    val ImageCreate = lineIcon("NeuroImageCreate") {
        moveTo(5f, 15.5f)
        curveTo(6.8f, 18.2f, 10.4f, 19f, 13.1f, 17.2f)
        curveTo(15.8f, 15.4f, 16.6f, 11.8f, 14.8f, 9.1f)
        moveTo(14.8f, 9.1f)
        lineTo(18.8f, 5.1f)
        moveTo(16.4f, 4.6f)
        lineTo(19.3f, 7.5f)
        moveTo(6.6f, 14.7f)
        lineTo(9.3f, 12f)
        moveTo(4.2f, 19.8f)
        lineTo(7f, 17f)
    }

    val Library = lineIcon("NeuroLibrary") {
        roundedRect(4.2f, 5f, 8.4f, 19f)
        roundedRect(10f, 5f, 14.2f, 19f)
        roundedRect(15.8f, 5f, 20f, 19f)
    }

    val Memory = lineIcon("NeuroMemory") {
        roundedRect(6f, 6f, 18f, 18f, radius = 2.5f)
        moveTo(9.2f, 9.2f)
        lineTo(14.8f, 9.2f)
        lineTo(14.8f, 14.8f)
        lineTo(9.2f, 14.8f)
        close()
        moveTo(8f, 3.8f); lineTo(8f, 6f)
        moveTo(12f, 3.8f); lineTo(12f, 6f)
        moveTo(16f, 3.8f); lineTo(16f, 6f)
        moveTo(8f, 18f); lineTo(8f, 20.2f)
        moveTo(12f, 18f); lineTo(12f, 20.2f)
        moveTo(16f, 18f); lineTo(16f, 20.2f)
    }

    val Menu = lineIcon("NeuroMenu", stroke = 2.3f) {
        moveTo(4.8f, 7f)
        lineTo(19.2f, 7f)
        moveTo(4.8f, 12f)
        lineTo(19.2f, 12f)
        moveTo(4.8f, 17f)
        lineTo(19.2f, 17f)
    }

    val Mic = lineIcon("NeuroMic") {
        roundedRect(9f, 3.8f, 15f, 14.8f)
        moveTo(5.8f, 11.2f)
        curveTo(5.8f, 15f, 8.5f, 17.5f, 12f, 17.5f)
        curveTo(15.5f, 17.5f, 18.2f, 15f, 18.2f, 11.2f)
        moveTo(12f, 17.5f)
        lineTo(12f, 21f)
        moveTo(8.6f, 21f)
        lineTo(15.4f, 21f)
    }

    val Music = lineIcon("NeuroMusic") {
        moveTo(9f, 17.5f)
        curveTo(9f, 19.2f, 7.6f, 20.5f, 5.9f, 20.5f)
        curveTo(4.2f, 20.5f, 3f, 19.4f, 3f, 17.9f)
        curveTo(3f, 16.3f, 4.3f, 15.1f, 6f, 15.1f)
        curveTo(7.2f, 15.1f, 8.2f, 15.5f, 9f, 16.2f)
        lineTo(9f, 6.2f)
        lineTo(19f, 4f)
        lineTo(19f, 15.3f)
        moveTo(19f, 15.3f)
        curveTo(18.2f, 14.6f, 17.2f, 14.2f, 16f, 14.2f)
        curveTo(14.3f, 14.2f, 13f, 15.4f, 13f, 17f)
        curveTo(13f, 18.5f, 14.2f, 19.6f, 15.9f, 19.6f)
        curveTo(17.6f, 19.6f, 19f, 18.3f, 19f, 16.6f)
        moveTo(9f, 9f)
        lineTo(19f, 6.8f)
    }

    val More = lineIcon("NeuroMore", stroke = 3f) {
        dot(6.5f, 12f)
        dot(12f, 12f)
        dot(17.5f, 12f)
    }

    val Paperclip = lineIcon("NeuroPaperclip") {
        moveTo(7.2f, 13.2f)
        lineTo(13.8f, 6.6f)
        curveTo(15.3f, 5.1f, 17.7f, 5.1f, 19.2f, 6.6f)
        curveTo(20.7f, 8.1f, 20.7f, 10.5f, 19.2f, 12f)
        lineTo(10.6f, 20.6f)
        curveTo(8.4f, 22.8f, 4.8f, 22.8f, 2.6f, 20.6f)
        curveTo(0.4f, 18.4f, 0.4f, 14.8f, 2.6f, 12.6f)
        lineTo(11.2f, 4f)
        curveTo(13.9f, 1.3f, 18.3f, 1.3f, 21f, 4f)
        moveTo(9.2f, 15.2f)
        lineTo(15.8f, 8.6f)
    }

    val Refresh = lineIcon("NeuroRefresh") {
        moveTo(18.8f, 9f)
        curveTo(17.6f, 6.4f, 15f, 4.7f, 12f, 4.7f)
        curveTo(7.9f, 4.7f, 4.7f, 7.9f, 4.7f, 12f)
        curveTo(4.7f, 16.1f, 7.9f, 19.3f, 12f, 19.3f)
        curveTo(15.3f, 19.3f, 18.1f, 17.2f, 19f, 14.3f)
        moveTo(19.2f, 4.8f)
        lineTo(19.2f, 9.2f)
        lineTo(14.8f, 9.2f)
    }

    val Search = lineIcon("NeuroSearch", stroke = 2.4f) {
        moveTo(10.5f, 4.2f)
        curveTo(7f, 4.2f, 4.2f, 7f, 4.2f, 10.5f)
        curveTo(4.2f, 14f, 7f, 16.8f, 10.5f, 16.8f)
        curveTo(14f, 16.8f, 16.8f, 14f, 16.8f, 10.5f)
        curveTo(16.8f, 7f, 14f, 4.2f, 10.5f, 4.2f)
        moveTo(15.2f, 15.2f)
        lineTo(20f, 20f)
    }

    val Share = lineIcon("NeuroShare") {
        moveTo(12f, 4f)
        lineTo(12f, 15f)
        moveTo(7.2f, 8.8f)
        lineTo(12f, 4f)
        lineTo(16.8f, 8.8f)
        moveTo(5.5f, 13f)
        lineTo(5.5f, 19.5f)
        lineTo(18.5f, 19.5f)
        lineTo(18.5f, 13f)
    }

    val Sliders = lineIcon("NeuroSliders") {
        moveTo(5f, 7f)
        lineTo(19f, 7f)
        moveTo(5f, 17f)
        lineTo(19f, 17f)
        moveTo(9f, 4.7f)
        lineTo(9f, 9.3f)
        moveTo(15f, 14.7f)
        lineTo(15f, 19.3f)
    }

    val Speaker = lineIcon("NeuroSpeaker") {
        moveTo(4.2f, 10f)
        lineTo(8f, 10f)
        lineTo(12.5f, 6.3f)
        lineTo(12.5f, 17.7f)
        lineTo(8f, 14f)
        lineTo(4.2f, 14f)
        close()
        moveTo(16.2f, 9.2f)
        curveTo(17.1f, 10.1f, 17.5f, 11f, 17.5f, 12f)
        curveTo(17.5f, 13f, 17.1f, 13.9f, 16.2f, 14.8f)
        moveTo(18.8f, 6.8f)
        curveTo(20.1f, 8.3f, 20.8f, 10f, 20.8f, 12f)
        curveTo(20.8f, 14f, 20.1f, 15.7f, 18.8f, 17.2f)
    }

    val Stop = lineIcon("NeuroStopBars", stroke = 2.8f) {
        moveTo(6.5f, 14.8f)
        lineTo(6.5f, 9.2f)
        moveTo(10.2f, 18f)
        lineTo(10.2f, 6f)
        moveTo(13.8f, 18f)
        lineTo(13.8f, 6f)
        moveTo(17.5f, 14.8f)
        lineTo(17.5f, 9.2f)
    }

    val Telescope = lineIcon("NeuroTelescope") {
        moveTo(5f, 13.4f)
        lineTo(16.8f, 8.7f)
        lineTo(18.5f, 12.8f)
        lineTo(6.7f, 17.5f)
        close()
        moveTo(4.2f, 16.4f)
        lineTo(6.3f, 21f)
        moveTo(11f, 15.8f)
        lineTo(13.1f, 20.4f)
    }

    val ThumbDown = lineIcon("NeuroThumbDown") {
        moveTo(8f, 4.8f)
        lineTo(8f, 14.5f)
        lineTo(11.2f, 20f)
        curveTo(11.8f, 20.9f, 13.4f, 20.5f, 13.4f, 19.4f)
        lineTo(13.4f, 15.8f)
        lineTo(18.2f, 15.8f)
        curveTo(19.7f, 15.8f, 20.6f, 14.4f, 20.1f, 13f)
        lineTo(17.9f, 6.4f)
        curveTo(17.6f, 5.4f, 16.7f, 4.8f, 15.7f, 4.8f)
        close()
        moveTo(4f, 5.2f)
        lineTo(4f, 14.7f)
    }

    val ThumbUp = lineIcon("NeuroThumbUp") {
        moveTo(8f, 19.2f)
        lineTo(8f, 9.5f)
        lineTo(11.2f, 4f)
        curveTo(11.8f, 3.1f, 13.4f, 3.5f, 13.4f, 4.6f)
        lineTo(13.4f, 8.2f)
        lineTo(18.2f, 8.2f)
        curveTo(19.7f, 8.2f, 20.6f, 9.6f, 20.1f, 11f)
        lineTo(17.9f, 17.6f)
        curveTo(17.6f, 18.6f, 16.7f, 19.2f, 15.7f, 19.2f)
        close()
        moveTo(4f, 9.3f)
        lineTo(4f, 18.8f)
    }
}

private fun lineIcon(
    name: String,
    stroke: Float = 2.2f,
    block: PathBuilder.() -> Unit
): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Transparent),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = stroke,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block
    ).build()
}

private fun PathBuilder.dot(x: Float, y: Float) {
    moveTo(x, y)
    lineTo(x + 0.01f, y)
}

private fun PathBuilder.roundedRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float = 1.7f
) {
    moveTo(left + radius, top)
    lineTo(right - radius, top)
    curveTo(right - 0.8f, top, right, top + 0.8f, right, top + radius)
    lineTo(right, bottom - radius)
    curveTo(right, bottom - 0.8f, right - 0.8f, bottom, right - radius, bottom)
    lineTo(left + radius, bottom)
    curveTo(left + 0.8f, bottom, left, bottom - 0.8f, left, bottom - radius)
    lineTo(left, top + radius)
    curveTo(left, top + 0.8f, left + 0.8f, top, left + radius, top)
    close()
}
