package com.skullstudio.ai

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StudioApp() }
    }
}

private data class RigPoint(val label: String, val start: Offset)

@Composable
fun StudioApp() {
    var image by remember { mutableStateOf<Uri?>(null) }
    var motion by remember { mutableStateOf("Idle") }
    var ratio by remember { mutableStateOf("9:16") }
    var rigMode by remember { mutableStateOf(false) }
    var faceMode by remember { mutableStateOf(false) }
    var mouthPreview by remember { mutableStateOf(false) }
    val bodyPoints = remember {
        mutableStateListOf(
            RigPoint("Head", Offset(.50f, .25f)), RigPoint("Neck", Offset(.50f, .40f)),
            RigPoint("Left shoulder", Offset(.39f, .47f)), RigPoint("Left elbow", Offset(.20f, .67f)),
            RigPoint("Left wrist", Offset(.45f, .62f)), RigPoint("Right shoulder", Offset(.61f, .47f)),
            RigPoint("Right elbow", Offset(.80f, .67f)), RigPoint("Right wrist", Offset(.55f, .62f)),
            RigPoint("Hip", Offset(.50f, .76f))
        )
    }
    val facePoints = remember {
        mutableStateListOf(
            RigPoint("Left eye", Offset(.44f, .30f)),
            RigPoint("Right eye", Offset(.56f, .30f)),
            RigPoint("Mouth centre", Offset(.50f, .37f)),
            RigPoint("Mouth left corner", Offset(.47f, .37f)),
            RigPoint("Mouth right corner", Offset(.53f, .37f))
        )
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { image = it }
    val bg = Color(0xFF070A14)
    val cyan = Color(0xFF00E5FF)

    MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = Color(0xFF111827), primary = cyan)) {
        Column(Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("SKULL STUDIO AI", color = cyan, fontSize = 25.sp)
            Text("DRAW • RIG • ANIMATE", color = Color.LightGray, fontSize = 11.sp)
            Spacer(Modifier.height(16.dp))
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(if (ratio == "9:16") 430.dp else 240.dp)
                    .background(Color(0xFF151D2E), RoundedCornerShape(18.dp))
                    .border(1.dp, cyan, RoundedCornerShape(18.dp))
            ) {
                if (image != null) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { context ->
                            android.widget.ImageView(context).apply {
                                setImageURI(image)
                                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        update = { it.setImageURI(image) },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (rigMode || faceMode) {
                        Canvas(Modifier.fillMaxSize().pointerInput(rigMode, faceMode) {
                            var active = -1
                            val activePoints = if (faceMode) facePoints else bodyPoints
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    active = activePoints.indices.minByOrNull { i ->
                                        val p = activePoints[i].start
                                        (Offset(p.x * w, p.y * h) - pos).getDistance()
                                    } ?: -1
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    if (active >= 0) {
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        val p = activePoints[active]
                                        activePoints[active] = p.copy(start = Offset(
                                            (p.start.x + drag.x / w).coerceIn(0f, 1f),
                                            (p.start.y + drag.y / h).coerceIn(0f, 1f)
                                        ))
                                    }
                                },
                                onDragEnd = { active = -1 },
                                onDragCancel = { active = -1 }
                            )
                        }) {
                            val w = size.width
                            val h = size.height
                            val pts = if (faceMode) facePoints else bodyPoints
                            if (faceMode) {
                                pts.forEach { p ->
                                    val c = Offset(p.start.x * w, p.start.y * h)
                                    drawCircle(Color(0xFFFFD54F), 10f, c)
                                    drawCircle(Color.Black, 4f, c)
                                }
                            } else {
                                val links = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 1 to 5, 5 to 6, 6 to 7, 1 to 8)
                                links.forEach { (a,b) -> drawLine(cyan,
                                    Offset(pts[a].start.x*w, pts[a].start.y*h),
                                    Offset(pts[b].start.x*w, pts[b].start.y*h), 4f) }
                                pts.forEach { p -> drawCircle(Color(0xFFFFD54F), 13f, Offset(p.start.x*w,p.start.y*h)) }
                            }
                        }
                    }
                } else Text("Import your character drawing", color = Color.LightGray, modifier = Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("IMPORT DRAWING") }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BODY RIG GUIDE", color = cyan)
                    Text("Drag yellow joints to match the body.", color = Color.LightGray, fontSize = 11.sp)
                }
                Switch(checked = rigMode, onCheckedChange = { rigMode = it; if (it) faceMode = false }, enabled = image != null)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("FACE RIG GUIDE", color = cyan)
                    Text("Place eye and mouth markers manually.", color = Color.LightGray, fontSize = 11.sp)
                }
                Switch(checked = faceMode, onCheckedChange = { faceMode = it; if (it) rigMode = false }, enabled = image != null)
            }
            if (faceMode && image != null) {
                facePoints.forEach { Text("• ${it.label}", color = Color(0xFFFFD54F), fontSize = 11.sp) }
                Text("These are guide points only. They do not cut or deform the original PNG.", color = Color.LightGray, fontSize = 11.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text("TALKING MOUTH TEST", color = cyan)
            Button(onClick = { mouthPreview = !mouthPreview }, enabled = image != null, modifier = Modifier.fillMaxWidth()) {
                Text(if (mouthPreview) "STOP MOUTH TEST" else "TEST MOUTH OPEN / CLOSE")
            }
            Text(if (mouthPreview) "Mouth test selected. Real lip movement and voice sync are not implemented yet." else "Next: mouth shapes, then Hindi voice-synchronised lip movement.", color = Color.LightGray, fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))
            Text("MOTION PREVIEW", color = cyan)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Idle", "Walk", "Run", "Afraid").forEach { preset ->
                    FilterChip(selected = motion == preset, onClick = { motion = preset }, label = { Text(preset) })
                }
            }
            Text("Motion presets are selection-only until movable character layers are built.", color = Color.LightGray, fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))
            Text("VIDEO CANVAS", color = cyan)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("9:16", "16:9").forEach { canvas ->
                    FilterChip(selected = ratio == canvas, onClick = { ratio = canvas }, label = { Text(canvas) })
                }
            }
        }
    }
}
