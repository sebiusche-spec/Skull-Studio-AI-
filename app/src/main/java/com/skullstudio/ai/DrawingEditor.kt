package com.skullstudio.ai

import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Path as AndroidPath
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.layout.onSizeChanged
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
    var selectedColor by remember { mutableStateOf(Color.White) }
    var brushWidth by remember { mutableFloatStateOf(8f) }
    var eraser by remember { mutableStateOf(false) }
    var canvasWidth by remember { mutableFloatStateOf(1f) }
    var canvasHeight by remember { mutableFloatStateOf(1f) }
    var exportMessage by remember { mutableStateOf("Draw with your finger. Export saves a transparent PNG.") }
    val scope = rememberCoroutineScope()
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
                }.onSuccess { exportMessage = "PNG exported successfully." }
                    .onFailure { exportMessage = "Export failed: ${it.message ?: "unknown error"}" }
            }
        }
    }
    val colors = listOf(Color.White, Color.Black, Color(0xFF00E5FF), Color(0xFFFF2D95), Color(0xFFFFD54F), Color(0xFF8A2BE2))

    Column(Modifier.fillMaxSize().background(Color(0xFF070A14)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("DRAWING EDITOR", color = Color(0xFF00E5FF), fontSize = 19.sp)
            TextButton(onClick = onClose) { Text("CLOSE") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !eraser, onClick = { eraser = false }, label = { Text("Brush") })
            FilterChip(selected = eraser, onClick = { eraser = true }, label = { Text("Eraser") })
            OutlinedButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }, enabled = strokes.isNotEmpty()) { Text("Undo") }
            OutlinedButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) { Text("Clear") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            colors.forEach { color ->
                Surface(onClick = { selectedColor = color; eraser = false }, shape = RoundedCornerShape(50), color = color, modifier = Modifier.size(30.dp).border(if (selectedColor == color && !eraser) 3.dp else 1.dp, Color.White, RoundedCornerShape(50))) {}
            }
        }
        Text("Brush size: ${brushWidth.toInt()} px", color = Color.LightGray, fontSize = 12.sp)
        Slider(value = brushWidth, onValueChange = { brushWidth = it }, valueRange = 2f..36f)
        Canvas(
            Modifier.fillMaxWidth().weight(1f).onSizeChanged {
                canvasWidth = it.width.toFloat()
                canvasHeight = it.height.toFloat()
            }.background(Color(0xFF182235), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                .pointerInput(eraser, selectedColor, brushWidth) {
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
                if (stroke.color == Color.Transparent) {
                    drawPath(stroke.path, Color(0xFF182235), style = Stroke(stroke.width * 3f, cap = StrokeCap.Round))
                } else {
                    drawPath(stroke.path, stroke.color, style = Stroke(stroke.width, cap = StrokeCap.Round))
                }
            }
            currentPath?.let { drawPath(it, if (eraser) Color(0xFF182235) else selectedColor, style = Stroke(if (eraser) brushWidth * 3f else brushWidth, cap = StrokeCap.Round)) }
        }
        Text(exportMessage, color = Color.LightGray, fontSize = 11.sp)
        Button(onClick = { exportLauncher.launch("skull-studio-drawing.png") }, modifier = Modifier.fillMaxWidth()) {
            Text("EXPORT TRANSPARENT PNG")
        }
    }
}
