package com.dgurnick.banking.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dgurnick.banking.client.ChatMessage
import com.dgurnick.banking.client.ExpandableOffersView
import com.dgurnick.banking.client.InteractiveMapView
import com.dgurnick.banking.client.RcDocumentView

private const val DEFAULT_SURFACE_ID = "main"

@SuppressLint("MissingPermission")
private fun fetchLastLocation(context: Context, onResult: (Double, Double) -> Unit) {
  val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
  (lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                  ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                          ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER))
          ?.let { onResult(it.latitude, it.longitude) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankingApp(viewModel: BankingViewModel) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  var promptText by remember { mutableStateOf("") }
  var userLat by remember { mutableStateOf<Double?>(null) }
  var userLon by remember { mutableStateOf<Double?>(null) }
  val listState = rememberLazyListState()
  var showErrorDialog by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }

  // Show error dialog when error occurs
  LaunchedEffect(uiState.error) {
    uiState.error?.let {
      errorMessage = it
      showErrorDialog = true
    }
  }

  val locationLauncher =
          rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted
            ->
            if (granted)
                    fetchLastLocation(context) { lat, lon ->
                      userLat = lat
                      userLon = lon
                    }
          }

  LaunchedEffect(Unit) { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

  // Auto-scroll to bottom when new messages arrive
  LaunchedEffect(uiState.messages.size) {
    if (uiState.messages.isNotEmpty()) {
      listState.animateScrollToItem(uiState.messages.size - 1)
    }
  }

  fun submit(prompt: String) {
    val loc =
            if (userLat != null && userLon != null) "\n[user_lat:$userLat,user_lon:$userLon]"
            else ""
    viewModel.sendPrompt("${prompt.trim()}$loc", DEFAULT_SURFACE_ID)
    promptText = ""
  }

  // Error Dialog
  if (showErrorDialog) {
    AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error") },
            text = {
              Column {
                Text(text = errorMessage, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text =
                                "Check logcat for more details:\nadb logcat -s Banking.ViewModel Banking.GraphQlClient",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            },
            confirmButton = { TextButton(onClick = { showErrorDialog = false }) { Text("OK") } }
    )
  }

  Scaffold(
          topBar = {
            TopAppBar(
                    title = { Text("Banking Assistant") },
                    actions = {
                      IconButton(onClick = { viewModel.reset() }) {
                        Text("🔄", style = MaterialTheme.typography.titleLarge)
                      }
                    }
            )
          }
  ) { paddingValues ->
    Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
      // ── Chat Messages Area (scrollable) ─────────────────────────────────
      LazyColumn(
              state = listState,
              modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              contentPadding = PaddingValues(vertical = 12.dp)
      ) {
        items(uiState.messages, key = { it.id }) { message ->
          ChatMessageItem(
                  message = message,
                  onButtonClick = { buttonText, messageId ->
                    // "Start over" clears chat entirely - no server call needed
                    if (buttonText.trim().equals("Start over", ignoreCase = true)) {
                      viewModel.reset(DEFAULT_SURFACE_ID)
                      return@ChatMessageItem
                    }
                    val loc =
                            if (userLat != null && userLon != null)
                                    "\n[user_lat:$userLat,user_lon:$userLon]"
                            else ""
                    viewModel.sendPrompt("${buttonText.trim()}$loc", DEFAULT_SURFACE_ID, messageId)
                  },
                  onOfferAction = { messageId, offerId, actionMessage ->
                    // Mark the offer as selected (hides other offers, disables button)
                    viewModel.markOfferSelected(messageId, offerId)
                    viewModel.sendPrompt(actionMessage, DEFAULT_SURFACE_ID)
                  },
                  isLoading = uiState.isLoading
          )
        }
      }

      // ── Input Bar (fixed at bottom) ─────────────────────────────────────
      Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
                  value = promptText,
                  onValueChange = { promptText = it },
                  placeholder = { Text("Type a message...") },
                  modifier =
                          Modifier.weight(1f).onKeyEvent { event ->
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
                  enabled = !uiState.isLoading,
                  shape = RoundedCornerShape(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Button(
                  onClick = { submit(promptText) },
                  enabled = !uiState.isLoading && promptText.isNotBlank(),
                  shape = RoundedCornerShape(24.dp)
          ) { Text("Send") }
        }
      }
    }
  }
}

@Composable
private fun ChatMessageItem(
        message: ChatMessage,
        onButtonClick: (String, String) -> Unit, // (buttonText, messageId)
        onOfferAction: (String, String, String) -> Unit, // (messageId, offerId, action)
        isLoading: Boolean
) {
  when (message) {
    is ChatMessage.BotMessage -> BotMessageBubble(message, onButtonClick, isLoading)
    is ChatMessage.UserMessage -> UserMessageBubble(message)
    is ChatMessage.ContentMessage -> ContentMessageItem(message, onOfferAction)
    is ChatMessage.LoadingMessage -> LoadingMessageBubble()
  }
}

@Composable
private fun BotMessageBubble(
        message: ChatMessage.BotMessage,
        onButtonClick: (String, String) -> Unit, // (buttonText, messageId)
        isLoading: Boolean
) {
  Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
    // Message bubble
    Box(
            modifier =
                    Modifier.clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(12.dp)
                            .widthIn(max = 300.dp)
    ) {
      Text(
              text = message.text,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              style = MaterialTheme.typography.bodyMedium
      )
    }

    // Option buttons (if any, and not already used)
    if (message.buttons.isNotEmpty() && !message.buttonsUsed) {
      Spacer(modifier = Modifier.height(8.dp))
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        message.buttons.forEach { buttonText ->
          OutlinedButton(
                  onClick = { onButtonClick(buttonText, message.id) },
                  enabled = !isLoading,
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(12.dp)
          ) { Text(buttonText, style = MaterialTheme.typography.bodyMedium) }
        }
      }
    }
  }
}

@Composable
private fun UserMessageBubble(message: ChatMessage.UserMessage) {
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
    Box(
            modifier =
                    Modifier.clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(12.dp)
                            .widthIn(max = 300.dp)
    ) {
      Text(
              text = message.text.substringBefore("\n[user_lat:"), // Hide location tag
              color = MaterialTheme.colorScheme.onPrimary,
              style = MaterialTheme.typography.bodyMedium
      )
    }
  }
}

@Composable
private fun ContentMessageItem(
        message: ChatMessage.ContentMessage,
        onOfferAction: (String, String, String) -> Unit // (messageId, offerId, action)
) {
  Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
    // Map content
    message.mapData?.let { mapData ->
      InteractiveMapView(
              mapData = mapData,
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
      )
    }

    // Offers content
    message.offersData?.let { offersData ->
      ExpandableOffersView(
              offersData = offersData,
              messageId = message.id,
              selectedOfferId = message.selectedOfferId,
              modifier = Modifier.fillMaxWidth(),
              onAction = onOfferAction
      )
    }

    // RC document content
    message.rcDocument?.let { bytes ->
      Box(
              modifier =
                      Modifier.clip(RoundedCornerShape(12.dp))
                              .background(MaterialTheme.colorScheme.surfaceVariant)
      ) { RcDocumentView(bytes = bytes, modifier = Modifier.fillMaxWidth()) }
    }
  }
}

@Composable
private fun LoadingMessageBubble() {
  Box(
          modifier =
                  Modifier.clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                          .background(MaterialTheme.colorScheme.secondaryContainer)
                          .padding(12.dp)
  ) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
      Text(
              text = "Thinking...",
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              style = MaterialTheme.typography.bodyMedium
      )
    }
  }
}
