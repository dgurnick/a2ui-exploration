package com.dgurnick.banking.client

import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun RcDocumentView(bytes: ByteArray, modifier: Modifier = Modifier) {
  // key(bytes) recreates the player only when the document changes,
  // preventing setDocument from being called on every recomposition.
  androidx.compose.runtime.key(bytes) {
    AndroidView(
            factory = { ctx -> RemoteComposePlayer(ctx).apply { setDocument(bytes) } },
            modifier = modifier
    )
  }
}
