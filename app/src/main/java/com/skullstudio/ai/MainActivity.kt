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
import coil.compose.AsyncImage

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
    val points = remember {
        mutableStateListOf(
            RigPoint("Head", Offset(.50f, .25f)),
            RigPoint("Neck", Offset(.50f, .40f)),
            RigPoint("Left shoulder", Offset(.39f, .47f)),
            RigPoint("Left elbow", Offset(.20f, .67f)),
            RigPoint("Left wrist", Offset(.45f, .62f)),
            RigPoint("Right shoulder", Offset(.61f, .47f)),
            RigPoint("Right elbow", Offset(.80f, .67f)),
            RigPoint("Right wrist", Offset(.55f, .62f)),
            RigPoint("Hip", Offset(.50f, .76f))
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
                Modifier.fillMaxWidth()
                    .height(if (ratio == "9:16") 430.dp else 240.dp)
                    .background(Color(0xFF151D2E), RoundedCornerShape(18.dp))
                    .border(1.dp, cyan, RoundedCornerShape(18.dp))
            ) {
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = "Imported character",
                        modifier = Modifier.fillMaxSize()
                    )
                    if (rigMode) {
                        Canvas(
                            Modifier.fillMaxSize().pointerInput(Unit) {
                                var active = -1
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        active = points.indices.minByOrNull { i ->
                                            val p = points[i].start
                                            (Offset(p.x * w, p.y * h) - pos).getDistance()
                                        } ?: -1
                                    },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        if (active >= 0) {
                                            val w = size.width.toFloat().coerceAtLeast(1f)
                                            val h = size.height.toFloat().coerceAtLeast(1f)
                                            val p = points[active]
                                            points[active] = p.copy(
                                                start = Offset(
                                                    (p.start.x + drag.x / w).coerceIn(0f, 1f),
                                                    (p.start.y + drag.y / h).coerceIn(0f, 1f)
                                                )
                                            )
                                        }
                                    },
                                    onDragEnd = { active = -1 },
                                    onDragCancel = { active = -1 }
                                )
                            }
                        ) {
                            val w = size.width
                            val h = size.height
                            val links = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 1 to 5, 5 to 6, 6 to 7, 1 to 8)
                            links.forEach { (a, b) ->
                                drawLine(cyan, Offset(points[a].start.x * w, points[a].start.y * h),
                                    Offset(points[b].start.x * w, points[b].start.y * h), strokeWidth = 4f)
                            }
                            points.forEachIndexed { index, point ->
                                val center = Offset(point.start.x * w, point.start.y * h)
                                drawCircle(Color(0xFFFFD54F), radius = 13f, center = center)
                                drawCircle(Color.Black, radius = 5f, center = center)
                            }
                        }
                    }
                } else {
                    Text("Import your character drawing", color = Color.LightGray, modifier = Modifier.align(Alignment.Center))
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("IMPORT DRAWING")
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("MANUAL RIGGING", color = cyan)
                    Text("Drag yellow joints to match your character.", color = Color.LightGray, fontSize = 11.sp)
                }
                Switch(checked = rigMode, onCheckedChange = { rigMode = it }, enabled = image != null)
            }
            if (rigMode && image != null) {
                Text("Joint guide (approximate starting points)", color = Color.LightGray, fontSize = 11.sp)
                points.forEach { Text("• ${it.label}", color = Color(0xFFFFD54F), fontSize = 11.sp) }
                Text("This marks joints only; it does not cut the PNG into movable body parts yet.", color = Color.LightGray, fontSize = 11.sp)
            }

            Spacer(Modifier.height(14.dp))
            Text("MOTION PREVIEW", color = cyan)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Idle", "Walk", "Run", "Afraid").forEach { preset ->
                    FilterChip(selected = motion == preset, onClick = { motion = preset }, label = { Text(preset) })
                }
            }
            Text("Motion presets are selection-only until the character is separated into rigged layers.", color = Color.LightGray, fontSize = 11.sp)

            Spacer(Modifier.height(14.dp))
            Text("VIDEO CANVAS", color = cyan)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("9:16", "16:9").forEach { canvas ->
                    FilterChip(selected = ratio == canvas, onClick = { ratio = canvas }, label = { Text(canvas) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("NEXT: talking-mouth shapes and voice-synced lip movement after rigging.", color = cyan, fontSize = 12.sp)
        }
    }
}
