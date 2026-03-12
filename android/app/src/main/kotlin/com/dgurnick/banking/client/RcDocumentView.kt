package com.dgurnick.banking.client

import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun RcDocumentView(bytes: ByteArray, modifier: Modifier = Modifier) {
  // Use contentHashCode as key so ByteArray changes are detected by value
  val key = remember(bytes.contentHashCode()) { bytes.contentHashCode() }
  androidx.compose.runtime.key(key) {
    AndroidView(
            factory = { ctx -> RemoteComposePlayer(ctx).apply { setDocument(bytes) } },
            modifier = modifier
    )
  }
}
