package com.dgurnick.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dgurnick.android.a2ui.A2uiSurfaceView
import kotlinx.coroutines.flow.collectLatest

private const val DEFAULT_SURFACE_ID = "main"

private val SUGGESTED_PROMPTS = listOf(
    "Where is the nearest ATM?",
    "What is my account balance?",
    "What offers do you have for me?",
)

@Composable
fun A2uiApp(viewModel: A2uiViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var promptText by remember { mutableStateOf("") }

    fun submit(prompt: String) {
        promptText = prompt
        viewModel.sendPrompt(prompt.trim(), DEFAULT_SURFACE_ID)
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // ── Prompt bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("What would you like to do?") },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp &&
                                event.key == Key.Enter &&
                                !uiState.isLoading &&
                                promptText.isNotBlank()
                            ) {
                                submit(promptText)
                                true
                            } else false
                        },
                    singleLine = true,
                    enabled = !uiState.isLoading
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { submit(promptText) },
                    enabled = !uiState.isLoading && promptText.isNotBlank()
                ) { Text("Send") }
                if (uiState.readySurfaceId != null || uiState.error != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = {
                        promptText = ""
                        viewModel.reset(DEFAULT_SURFACE_ID)
                    }) {
                        Text("✕", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // ── Error banner ──────────────────────────────────────────────────
            uiState.error?.let { error ->
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ── Loading indicator ─────────────────────────────────────────────
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Building your UI…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ── Welcome / suggestion screen (shown when idle with no surface) ─
            if (!uiState.isLoading && uiState.readySurfaceId == null && uiState.error == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "How can I help you?",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap a question below or type your own above.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    SUGGESTED_PROMPTS.forEach { prompt ->
                        OutlinedButton(
                            onClick = { submit(prompt) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(prompt, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // ── A2UI surface rendering ─────────────────────────────────────────
            uiState.readySurfaceId?.let { surfaceId ->
                val surface = viewModel.getSurface(surfaceId)
                if (surface != null) {
                    A2uiSurfaceView(
                        surface = surface,
                        onAction = { action -> viewModel.dispatchAction(action) }
                    )
                }
            }
        }
    }
}
