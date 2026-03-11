package com.dgurnick.banking.client

import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun RcDocumentView(bytes: ByteArray, modifier: Modifier = Modifier) {
  AndroidView(
          factory = { ctx -> RemoteComposePlayer(ctx) },
          update = { player -> player.setDocument(bytes) },
          modifier = modifier
  )
}
