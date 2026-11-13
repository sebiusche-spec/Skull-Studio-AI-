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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val erase: Boolean
)

@Composable
fun DrawingEditor(onClose: () -> Unit) {
    val context = LocalContext.current
    val strokes = remember { mutableStateListOf<InkStroke>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var hue by remember { mutableFloatStateOf(190f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var brightness by remember { mutableFloatStateOf(1f) }
    val selectedColor = Color.hsv(hue, saturation, brightness)
    var brushWidth by remember { mutableFloatStateOf(8f) }
    var eraser by remember { mutableStateOf(false) }
    var canvasWidth by remember { mutableFloatStateOf(1f) }
    var canvasHeight by remember { mutableFloatStateOf(1f) }
    var referenceUri by remember { mutableStateOf<Uri?>(null) }
    var referenceOpacity by remember { mutableFloatStateOf(0.35f) }
    var referenceZoom by remember { mutableFloatStateOf(1f) }
    var exportMessage by remember { mutableStateOf("Use a reference image underneath your drawing, then trace it on a new layer.") }
    val scope = rememberCoroutineScope()
    val referencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        referenceUri = it
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val w = canvasWidth.toInt().coerceAtLeast(1)
                    val h = canvasHeight.toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    strokes.forEach { stroke ->
                        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                            color = stroke.color.value.toLong().toInt()
                            xfermode = if (stroke.erase) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
                            style = AndroidPaint.Style.STROKE
                            strokeWidth = stroke.width
                            strokeCap = AndroidPaint.Cap.ROUND
                            strokeJoin = AndroidPaint.Join.ROUND
                        }
                        canvas.drawPath(stroke.path.asAndroidPath(), paint)
                    }
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    } ?: error("Could not open destination")
                    bitmap.recycle()
                }.onSuccess { exportMessage = "Transparent PNG exported. Reference image is not included." }
                    .onFailure { exportMessage = "Export failed: ${it.message ?: "unknown error"}" }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF070A14)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SKULL DRAWING STUDIO", color = Color(0xFF00E5FF), fontSize = 18.sp)
            TextButton(onClick = onClose) { Text("CLOSE") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !eraser, onClick = { eraser = false }, label = { Text("Brush") })
            FilterChip(selected = eraser, onClick = { eraser = true }, label = { Text("Eraser") })
            OutlinedButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }, enabled = strokes.isNotEmpty()) { Text("Undo") }
            OutlinedButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) { Text("Clear") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { referencePicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("ADD REFERENCE", fontSize = 11.sp) }
            OutlinedButton(onClick = { referenceUri = null }, enabled = referenceUri != null, modifier = Modifier.weight(1f)) { Text("REMOVE IMAGE", fontSize = 11.sp) }
        }
        if (referenceUri != null) {
            Text("Reference opacity: ${(referenceOpacity * 100).toInt()}%  •  Zoom: ${"%.1f".format(referenceZoom)}x", color = Color.LightGray, fontSize = 11.sp)
            Slider(value = referenceOpacity, onValueChange = { referenceOpacity = it }, valueRange = 0.05f..1f)
            Slider(value = referenceZoom, onValueChange = { referenceZoom = it }, valueRange = 0.5f..2.5f)
        }
        Text("FULL COLOUR PICKER • HSV", color = Color(0xFF00E5FF), fontSize = 12.sp)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(selectedColor).border(1.dp, Color.White, RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(8.dp))
            Text("#${selectedColor.value.toLong().toString(16).takeLast(8).uppercase()}", color = Color.White, fontSize = 12.sp)
        }
        Text("Hue", color = Color.LightGray, fontSize = 11.sp)
        Slider(value = hue, onValueChange = { hue = it; eraser = false }, valueRange = 0f..360f)
        Text("Saturation", color = Color.LightGray, fontSize = 11.sp)
        Slider(value = saturation, onValueChange = { saturation = it; eraser = false }, valueRange = 0f..1f)
        Text("Brightness", color = Color.LightGray, fontSize = 11.sp)
        Slider(value = brightness, onValueChange = { brightness = it; eraser = false }, valueRange = 0.05f..1f)
        Text("Brush size: ${brushWidth.toInt()} px", color = Color.LightGray, fontSize = 11.sp)
        Slider(value = brushWidth, onValueChange = { brushWidth = it }, valueRange = 1f..48f)

        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF182235))
                .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
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
                Modifier.fillMaxSize().pointerInput(eraser, selectedColor, brushWidth) {
                    detectDragGestures(
                        onDragStart = { start ->
                            currentPath = Path().apply { moveTo(start.x, start.y) }
                        },
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
                                strokes.add(InkStroke(path, if (eraser) Color.Transparent else selectedColor, if (eraser) brushWidth * 3f else brushWidth, eraser))
                            }
                            currentPath = null
                        },
                        onDragCancel = { currentPath = null }
                    )
                }
            ) {
                strokes.forEach { stroke ->
                    if (stroke.erase) {
                        drawPath(stroke.path, Color(0xFF182235), style = Stroke(stroke.width, cap = StrokeCap.Round))
                    } else {
                        drawPath(stroke.path, stroke.color, style = Stroke(stroke.width, cap = StrokeCap.Round))
                    }
                }
                currentPath?.let {
                    drawPath(it, if (eraser) Color(0xFF182235) else selectedColor, style = Stroke(if (eraser) brushWidth * 3f else brushWidth, cap = StrokeCap.Round))
                }
            }
        }
        Text(exportMessage, color = Color.LightGray, fontSize = 10.sp)
        Button(onClick = { exportLauncher.launch("skull-studio-drawing.png") }, modifier = Modifier.fillMaxWidth()) {
            Text("EXPORT TRANSPARENT PNG")
        }
    }
}
