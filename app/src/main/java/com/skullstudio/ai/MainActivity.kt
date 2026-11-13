package com.skullstudio.ai

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StudioApp() }
    }
}

private enum class LayerKind(val title: String) {
    HAIR("Hair"), ARM("Arm / Hand"), CLOTH("Clothes"), LEG("Leg")
}
private data class CharacterLayer(val kind: LayerKind, val uri: Uri)

@Composable
fun StudioApp() {
    val cyan = Color(0xFF00E5FF)
    val bg = Color(0xFF070A14)
    val panel = Color(0xFF111827)
    val context = LocalContext.current
    var characterUri by remember { mutableStateOf<Uri?>(null) }
    val layers = remember { mutableStateListOf<CharacterLayer>() }
    var pendingKind by remember { mutableStateOf(LayerKind.HAIR) }
    var animateLayers by remember { mutableStateOf(false) }
    var windEnabled by remember { mutableStateOf(false) }
    var ratio by remember { mutableStateOf("9:16") }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var audioPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        characterUri = it
    }
    val layerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) { layers.removeAll { layer -> layer.kind == pendingKind }; layers.add(CharacterLayer(pendingKind, it)) }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        audioUri = it
        audioPlaying = false
    }

    DisposableEffect(audioUri) {
        player?.release()
        player = audioUri?.let { uri -> runCatching { MediaPlayer.create(context, uri) }.getOrNull() }
        player?.setVolume(volume, volume)
        onDispose {
            player?.release()
            player = null
            audioPlaying = false
        }
    }
    LaunchedEffect(volume, player) { player?.setVolume(volume, volume) }

    val transition = rememberInfiniteTransition(label = "layer-motion")
    val phase by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Reverse),
        label = "motion-phase"
    )
    val windPhase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "wind-phase"
    )

    MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = panel, primary = cyan)) {
        Column(
            Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("SKULL STUDIO AI", color = cyan, fontSize = 25.sp)
            Text("2D CHARACTER • LAYERS • AUDIO", color = Color.LightGray, fontSize = 11.sp)

            BoxWithConstraints(
                Modifier.fillMaxWidth()
                    .height(if (ratio == "9:16") 430.dp else 245.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF151D2E))
                    .border(1.dp, cyan, RoundedCornerShape(18.dp))
            ) {
                if (characterUri != null) {
                    AsyncImage(
                        model = characterUri, contentDescription = "Original character",
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                    )
                    layers.forEachIndexed { index, layer ->
                        val amount = if (animateLayers) phase else 0f
                        val rotation = when (layer.kind) {
                            LayerKind.HAIR -> amount * 3.5f
                            LayerKind.ARM -> amount * 7f
                            LayerKind.CLOTH -> amount * 1.8f
                            LayerKind.LEG -> amount * 6f
                        }
                        val vertical = when (layer.kind) {
                            LayerKind.HAIR -> amount * 4f
                            LayerKind.ARM -> amount * 1.5f
                            LayerKind.CLOTH -> amount * 2.5f
                            LayerKind.LEG -> amount * 2f
                        }
                        AsyncImage(
                            model = layer.uri, contentDescription = "${layer.kind.title} animation layer",
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                rotationZ = rotation
                                translationY = vertical
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                    if (layer.kind == LayerKind.ARM) 0.25f else 0.5f,
                                    if (layer.kind == LayerKind.HAIR) 0.8f else 0.5f
                                )
                            },
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (windEnabled) {
                        Canvas(Modifier.fillMaxSize()) {
                            repeat(18) { i ->
                                val x = ((i * 67f + windPhase * size.width * 1.4f) % size.width)
                                val y = (i * 41f % size.height)
                                drawLine(
                                    Color(0x8879E8FF),
                                    androidx.compose.ui.geometry.Offset(x, y),
                                    androidx.compose.ui.geometry.Offset(x + 22f, y - 5f),
                                    strokeWidth = 1.5f
                                )
                            }
                        }
                    }
                } else {
                    Text("Import your character PNG", color = Color.LightGray, modifier = Modifier.align(Alignment.Center))
                }
            }

            Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("IMPORT CHARACTER PNG")
            }

            SectionTitle("CHARACTER LAYER SETUP", cyan)
            Text(
                "For independent movement, import transparent PNG cut-outs made on the same canvas size and position as the original image.",
                color = Color.LightGray, fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                LayerKind.values().forEach { kind ->
                    FilterChip(
                        selected = pendingKind == kind,
                        onClick = { pendingKind = kind },
                        label = { Text(kind.title, fontSize = 11.sp) }
                    )
                }
            }
            OutlinedButton(
                onClick = { layerPicker.launch("image/png") },
                enabled = characterUri != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("ADD SELECTED TRANSPARENT LAYER") }

            if (layers.isNotEmpty()) {
                layers.forEachIndexed { index, layer ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${index + 1}. ${layer.kind.title} layer", color = Color.White, modifier = Modifier.weight(1f))
                        TextButton(onClick = { layers.removeAt(index) }) { Text("Remove", color = Color(0xFFFF8A80)) }
                    }
                }
            } else {
                Text("No separate layers yet. A single flattened PNG cannot move its hair, arms or clothes independently.", color = Color(0xFFFFD54F), fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ANIMATE IMPORTED LAYERS", color = cyan)
                    Text("Subtle independent sway / rotation preview", color = Color.LightGray, fontSize = 11.sp)
                }
                Switch(checked = animateLayers, onCheckedChange = { animateLayers = it }, enabled = layers.isNotEmpty())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WIND PARTICLE EFFECT", color = cyan)
                    Text("Animated wind streaks over the scene", color = Color.LightGray, fontSize = 11.sp)
                }
                Switch(checked = windEnabled, onCheckedChange = { windEnabled = it }, enabled = characterUri != null)
            }

            SectionTitle("AUDIO FX STUDIO", cyan)
            Text("Import an audio track and preview its volume. Multi-track mixing and final MP4 export are not in this milestone yet.", color = Color.LightGray, fontSize = 12.sp)
            OutlinedButton(onClick = { audioPicker.launch("audio/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (audioUri == null) "IMPORT AUDIO" else "CHANGE AUDIO")
            }
            if (audioUri != null) {
                Text("Audio loaded", color = Color.White, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        val p = player
                        if (p != null) {
                            if (audioPlaying) p.pause() else {
                                if (!p.isPlaying) {
                                    if (runCatching { p.currentPosition >= p.duration }.getOrDefault(false)) p.seekTo(0)
                                    p.start()
                                }
                            }
                            audioPlaying = !audioPlaying
                        }
                    }) { Text(if (audioPlaying) "PAUSE" else "PLAY PREVIEW") }
                    TextButton(onClick = { player?.pause(); player?.seekTo(0); audioPlaying = false }) { Text("STOP") }
                }
                Text("Volume: ${(volume * 100).toInt()}%", color = Color.LightGray, fontSize = 11.sp)
                Slider(value = volume, onValueChange = { volume = it })
            }

            SectionTitle("VIDEO CANVAS", cyan)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("9:16", "16:9").forEach { value ->
                    FilterChip(selected = ratio == value, onClick = { ratio = value }, label = { Text(value) })
                }
            }
            Text("Next stages: body-part segmentation, joint-based rigging, original-mouth lip-sync, script pauses, multi-track audio mixer and MP4 export.", color = Color(0xFFFFD54F), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionTitle(value: String, color: Color) {
    Text(value, color = color, fontSize = 17.sp)
}
