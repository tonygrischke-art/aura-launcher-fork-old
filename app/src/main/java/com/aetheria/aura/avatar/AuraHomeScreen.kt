package com.aetheria.aura.avatar

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.aetheria.aura.db.Message
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.takeLast

enum class OrbStyle { WIRE, VIDEO }
const val ORB_STYLE = "WIRE"

@Composable
fun AuraHomeScreen(
    mood: AuraMood,
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onMic: () -> Unit,
    onSettings: () -> Unit,
    status: String?,
    messages: List<Message>,
    modifier: Modifier = Modifier,
    micLevel: Float = 0f,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    val clock = remember(now) { SimpleDateFormat("h:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now) }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top row with clock and settings button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(28.dp))
                Text(clock, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Thin)
                Text(date, color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
            }

            // Settings button
            androidx.compose.material3.IconButton(
                onClick = onSettings,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            val debuggable = (LocalContext.current.applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            var debugMood by remember { mutableStateOf(mood) }
            if (ORB_STYLE == "VIDEO") {
                VideoOrb(
                    mood = if (debuggable) debugMood else mood,
                    modifier = Modifier.size(side)
                        .pointerInput(Unit) {
                            if (debuggable) {
                                detectTapGestures(onTap = {
                                    debugMood = when (debugMood) {
                                        AuraMood.IDLE -> AuraMood.LISTENING
                                        AuraMood.LISTENING -> AuraMood.THINKING
                                        AuraMood.THINKING -> AuraMood.ACTING
                                        AuraMood.ACTING -> AuraMood.IDLE
                                    }
                                })
                            }
                        }
                )
            } else {
                WireOrb(
                    mood = if (debuggable) debugMood else mood,
                    level = micLevel,
                    onTap = {
                        if (debuggable) {
                            debugMood = when (debugMood) {
                                AuraMood.IDLE -> AuraMood.LISTENING
                                AuraMood.LISTENING -> AuraMood.THINKING
                                AuraMood.THINKING -> AuraMood.ACTING
                                AuraMood.ACTING -> AuraMood.IDLE
                            }
                        }
                    },
                    modifier = Modifier.size(side)
                )
            }
        }

        // Chat history (scrollable)
        if (messages.isNotEmpty()) {
            val scrollState = rememberLazyListState()
            LaunchedEffect(messages) {
                scrollState.animateScrollToItem(messages.lastIndex)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                state = scrollState,
            ) {
                items(messages) { msg ->
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                    when (msg.role) {
                        Message.ROLE_USER -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 56.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF2A2A2A))
                                        .clip(RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = msg.text,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            maxLines = 10
                                        )
                                        Text(
                                            text = time,
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 10.sp,
                                            modifier = Modifier.align(Alignment.End)
                                        )
                                    }
                                }
                            }
                        }
                        Message.ROLE_ASSISTANT -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 56.dp),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF14181F))
                                        .clip(RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = msg.text,
                                            color = Color.Cyan,
                                            fontSize = 14.sp,
                                            maxLines = 10
                                        )
                                        Text(
                                            text = time,
                                            color = Color.Cyan.copy(alpha = 0.5f),
                                            fontSize = 10.sp,
                                            modifier = Modifier.align(Alignment.Start)
                                        )
                                    }
                                }
                            }
                        }
                        Message.ROLE_SYSTEM -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = msg.text,
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    maxLines = 5
                                )
                            }
                        }
                    }
                }
            }
        }

        if (status != null) {
            Text(status, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF14181F))
                .border(1.dp, AuraColors.Cyan.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f).onFocusChanged { onFocusChange(it.isFocused) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(AuraColors.Cyan),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                decorationBox = { inner ->
                    Box(Modifier.padding(vertical = 10.dp)) {
                        if (input.isEmpty()) {
                            Text("Ask Aura...", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
                        }
                        inner()
                    }
                }
            )
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AuraColors.Cyan.copy(alpha = 0.18f))
                    .clickable { if (input.isBlank()) onMic() else onSubmit() },
                contentAlignment = Alignment.Center
            ) {
                CommandIcon(sendMode = input.isNotBlank())
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AuraColors.Cyan.copy(alpha = 0.10f))
                .clickable { onSettings() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "S",
                color = AuraColors.Cyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CommandIcon(sendMode: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val col = AuraColors.Cyan
        val sw = 2.dp.toPx()
        if (sendMode) {
            drawLine(col, Offset(w * 0.5f, h * 0.85f), Offset(w * 0.5f, h * 0.15f), sw, StrokeCap.Round)
            drawLine(col, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.22f, h * 0.43f), sw, StrokeCap.Round)
            drawLine(col, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.78f, h * 0.43f), sw, StrokeCap.Round)
        } else {
            drawRoundRect(col, topLeft = Offset(w * 0.36f, h * 0.05f), size = Size(w * 0.28f, h * 0.5f),
                cornerRadius = CornerRadius(w * 0.14f, w * 0.14f))
            drawArc(col, 0f, 180f, false, topLeft = Offset(w * 0.2f, h * 0.25f), size = Size(w * 0.6f, h * 0.5f),
                style = Stroke(sw, cap = StrokeCap.Round))
            drawLine(col, Offset(w * 0.5f, h * 0.75f), Offset(w * 0.5f, h * 0.95f), sw, StrokeCap.Round)
        }
    }
}