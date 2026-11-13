package com.skullstudio.ai

import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private data class InkStroke(
    val path: Path,
    val color: Color,
    val width: Float,
    val erase: Boolean,
    val alpha: Float
)

private class SketchLayer(val title: String) {
    val strokes = mutableStateListOf<InkStroke>()
    var visible by mutableStateOf(true)
    var opacity by mutableFloatStateOf(1f)
}

private enum class DrawTool(val label: String) {
    BRUSH("Brush"), PENCIL("Pencil"), MARKER("Marker"), ERASER("Eraser")
}

@Composable
fun DrawingEditor(onClose: () -> Unit) {
    val context = LocalContext.current
    val layers = remember { mutableStateListOf(SketchLayer("Layer 1")) }
    var selectedLayer by remember { mutableIntStateOf(0) }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var hue by remember { mutableFloatStateOf(190f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var brightness by remember { mutableFloatStateOf(1f) }
    val selectedColor = Color.hsv(hue, saturation, brightness)
    var brushWidth by remember { mutableFloatStateOf(8f) }
    var tool by remember { mutableStateOf(DrawTool.BRUSH) }
    var canvasWidth by remember { mutableFloatStateOf(1f) }
    var canvasHeight by remember { mutableFloatStateOf(1f) }
    var referenceUri by remember { mutableStateOf<Uri?>(null) }
    var referenceOpacity by remember { mutableFloatStateOf(0.35f) }
    var referenceZoom by remember { mutableFloatStateOf(1f) }
    var exportMessage by remember { mutableStateOf("Reference stays separate. Draw on editable layers.") }
    val scope = rememberCoroutineScope()
    val activeLayer = layers.getOrNull(selectedLayer) ?: layers.first()
    val referencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        referenceUri = it
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val w = canvasWidth.toInt().coerceAtLeast(1)
                    val h = canvasHeight.toInt().coerceAtLeast(1)
                    val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val outputCanvas = android.graphics.Canvas(output)
                    layers.filter { it.visible }.forEach { layer ->
                        val layerBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val layerCanvas = android.graphics.Canvas(layerBitmap)
                        layer.strokes.forEach { stroke ->
                            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                color = stroke.color.value.toLong().toInt()
                                alpha = (stroke.alpha * 255f).toInt().coerceIn(0, 255)
                                xfermode = if (stroke.erase) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
                                style = AndroidPaint.Style.STROKE
                                strokeWidth = stroke.width
                                strokeCap = AndroidPaint.Cap.ROUND
                                strokeJoin = AndroidPaint.Join.ROUND
                            }
                            layerCanvas.drawPath(stroke.path.asAndroidPath(), paint)
                        }
                        val layerPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                            alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                        }
                        outputCanvas.drawBitmap(layerBitmap, 0f, 0f, layerPaint)
                        layerBitmap.recycle()
                    }
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        output.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    } ?: error("Could not open destination")
                    output.recycle()
                }.onSuccess { exportMessage = "Layered transparent PNG exported as a flattened image." }
                    .onFailure { exportMessage = "Export failed: ${it.message ?: "unknown error"}" }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF070A14)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SKULL DRAWING STUDIO", color = Color(0xFF00E5FF), fontSize = 17.sp)
            TextButton(onClick = onClose) { Text("CLOSE") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DrawTool.values().forEach { item ->
                FilterChip(selected = tool == item, onClick = { tool = item }, label = { Text(item.label) })
            }
            OutlinedButton(onClick = {
                val layer = activeLayer
                if (layer.strokes.isNotEmpty()) layer.strokes.removeAt(layer.strokes.lastIndex)
            }, enabled = activeLayer.strokes.isNotEmpty()) { Text("Undo") }
            OutlinedButton(onClick = { activeLayer.strokes.clear() }, enabled = activeLayer.strokes.isNotEmpty()) { Text("Clear") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { referencePicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("ADD REFERENCE", fontSize = 10.sp) }
            OutlinedButton(onClick = { referenceUri = null }, enabled = referenceUri != null, modifier = Modifier.weight(1f)) { Text("REMOVE", fontSize = 10.sp) }
        }
        if (referenceUri != null) {
            Text("Reference opacity ${(referenceOpacity * 100).toInt()}% • Zoom ${"%.1f".format(referenceZoom)}x", color = Color.LightGray, fontSize = 10.sp)
            Slider(value = referenceOpacity, onValueChange = { referenceOpacity = it }, valueRange = 0.05f..1f)
            Slider(value = referenceZoom, onValueChange = { referenceZoom = it }, valueRange = 0.5f..2.5f)
        }
        Text("FULL COLOUR PICKER • HSV", color = Color(0xFF00E5FF), fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(25.dp).clip(RoundedCornerShape(6.dp)).background(selectedColor).border(1.dp, Color.White, RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(7.dp))
            Text("#${selectedColor.value.toLong().toString(16).takeLast(8).uppercase()}", color = Color.White, fontSize = 11.sp)
        }
        Text("Hue", color = Color.LightGray, fontSize = 10.sp)
        Slider(value = hue, onValueChange = { hue = it; if (tool == DrawTool.ERASER) tool = DrawTool.BRUSH }, valueRange = 0f..360f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Saturation", color = Color.LightGray, fontSize = 10.sp)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
            }
            Column(Modifier.weight(1f)) {
                Text("Brightness", color = Color.LightGray, fontSize = 10.sp)
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0.05f..1f)
            }
        }
        Text("Brush size: ${brushWidth.toInt()} px", color = Color.LightGray, fontSize = 10.sp)
        Slider(value = brushWidth, onValueChange = { brushWidth = it }, valueRange = 1f..48f)

        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF182235))
                .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(10.dp))
                .onSizeChanged {
                    canvasWidth = it.width.toFloat()
                    canvasHeight = it.height.toFloat()
                }
        ) {
            if (referenceUri != null) {
                AsyncImage(
                    model = referenceUri,
                    contentDescription = "Tracing reference image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = referenceOpacity
                        scaleX = referenceZoom
                        scaleY = referenceZoom
                    }
                )
            }
            Canvas(
                Modifier.fillMaxSize().pointerInput(tool, selectedColor, brushWidth, selectedLayer) {
                    detectDragGestures(
                        onDragStart = { start -> currentPath = Path().apply { moveTo(start.x, start.y) } },
                        onDrag = { change, _ ->
                            val previous = currentPath
                            currentPath = Path().apply {
                                if (previous != null) addPath(previous)
                                lineTo(change.position.x, change.position.y)
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            currentPath?.let { path ->
                                val erasing = tool == DrawTool.ERASER
                                val alpha = when (tool) {
                                    DrawTool.PENCIL -> 0.62f
                                    DrawTool.MARKER -> 0.38f
                                    else -> 1f
                                }
                                val width = when (tool) {
                                    DrawTool.PENCIL -> brushWidth * 0.65f
                                    DrawTool.MARKER -> brushWidth * 2.2f
                                    DrawTool.ERASER -> brushWidth * 3f
                                    else -> brushWidth
                                }
                                activeLayer.strokes.add(InkStroke(path, if (erasing) Color.Transparent else selectedColor, width, erasing, alpha))
                            }
                            currentPath = null
                        },
                        onDragCancel = { currentPath = null }
                    )
                }
            ) {
                layers.forEach { layer ->
                    if (layer.visible) {
                        layer.strokes.forEach { stroke ->
                            if (stroke.erase) {
                                drawPath(stroke.path, Color(0xFF182235), alpha = layer.opacity, style = Stroke(stroke.width, cap = StrokeCap.Round))
                            } else {
                                drawPath(stroke.path, stroke.color, alpha = stroke.alpha * layer.opacity, style = Stroke(stroke.width, cap = StrokeCap.Round))
                            }
                        }
                    }
                }
                currentPath?.let {
                    val erase = tool == DrawTool.ERASER
                    val width = if (erase) brushWidth * 3f else if (tool == DrawTool.MARKER) brushWidth * 2.2f else brushWidth
                    drawPath(it, if (erase) Color(0xFF182235) else selectedColor, alpha = if (tool == DrawTool.PENCIL) 0.62f else if (tool == DrawTool.MARKER) 0.38f else 1f, style = Stroke(width, cap = StrokeCap.Round))
                }
            }
        }
        Text("LAYERS • ${layers.size}", color = Color(0xFF00E5FF), fontSize = 11.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            layers.forEachIndexed { index, layer ->
                FilterChip(selected = selectedLayer == index, onClick = { selectedLayer = index }, label = { Text((if (layer.visible) "◉ " else "○ ") + layer.title, fontSize = 10.sp) })
            }
            OutlinedButton(onClick = {
                layers.add(SketchLayer("Layer ${layers.size + 1}"))
                selectedLayer = layers.lastIndex
            }) { Text("+ Layer") }
            OutlinedButton(onClick = {
                if (layers.size > 1) {
                    layers.removeAt(selectedLayer)
                    selectedLayer = selectedLayer.coerceAtMost(layers.lastIndex)
                }
            }, enabled = layers.size > 1) { Text("Delete") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Selected: ${activeLayer.title}", color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { activeLayer.visible = !activeLayer.visible }) { Text(if (activeLayer.visible) "Hide" else "Show") }
        }
        Text("Layer opacity: ${(activeLayer.opacity * 100).toInt()}%", color = Color.LightGray, fontSize = 10.sp)
        Slider(value = activeLayer.opacity, onValueChange = { activeLayer.opacity = it }, valueRange = 0f..1f)
        Text(exportMessage, color = Color.LightGray, fontSize = 9.sp)
        Button(onClick = { exportLauncher.launch("skull-studio-drawing.png") }, modifier = Modifier.fillMaxWidth()) {
            Text("EXPORT TRANSPARENT PNG")
        }
    }
}
