package com.dgurnick.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dgurnick.android.a2ui.A2uiSurfaceView
import kotlinx.coroutines.flow.collectLatest

private const val DEFAULT_SURFACE_ID = "main"

@Composable
fun A2uiApp(viewModel: A2uiViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var promptText by remember { mutableStateOf("") }

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
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !uiState.isLoading
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.sendPrompt(promptText.trim(), DEFAULT_SURFACE_ID)
                    },
                    enabled = !uiState.isLoading && promptText.isNotBlank()
                ) { Text("Send") }
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
