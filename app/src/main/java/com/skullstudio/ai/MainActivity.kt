package com.skullstudio.ai

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StudioApp() }
    }
}

@Composable
fun StudioApp() {
    var image by remember { mutableStateOf<Uri?>(null) }
    var motion by remember { mutableStateOf("Idle") }
    var ratio by remember { mutableStateOf("9:16") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        image = it
    }
    val bg = Color(0xFF070A14)
    val cyan = Color(0xFF00E5FF)

    MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = Color(0xFF111827), primary = cyan)) {
        Column(
            Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).padding(18.dp)
        ) {
            Text("SKULL STUDIO AI", color = cyan, fontSize = 25.sp)
            Text("DRAW • ANIMATE • CREATE", color = Color.LightGray, fontSize = 11.sp)
            Spacer(Modifier.height(18.dp))

            Box(
                Modifier.fillMaxWidth()
                    .height(if (ratio == "9:16") 390.dp else 220.dp)
                    .background(Color(0xFF151D2E), RoundedCornerShape(18.dp))
                    .border(1.dp, cyan, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = "Your character drawing",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("Import your character drawing", color = Color.LightGray)
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("IMPORT DRAWING")
            }
            Spacer(Modifier.height(14.dp))
            Text("MOTION PREVIEW", color = cyan)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Idle", "Walk", "Run", "Afraid").forEach { preset ->
                    FilterChip(
                        selected = motion == preset,
                        onClick = { motion = preset },
                        label = { Text(preset) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("VIDEO CANVAS", color = cyan)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("9:16", "16:9").forEach { canvas ->
                    FilterChip(
                        selected = ratio == canvas,
                        onClick = { ratio = canvas },
                        label = { Text(canvas) }
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Motion presets currently select an animation mode only. Character rigging and MP4 export are not included yet.",
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }
    }
}
