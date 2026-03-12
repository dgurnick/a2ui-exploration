package com.dgurnick.banking

import android.content.Context
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test that validates RC documents can be parsed by RemoteComposePlayer without
 * triggering "Too many operations executed".
 */
@RunWith(AndroidJUnit4::class)
class RcDocumentRenderTest {

  private lateinit var context: Context

  @Before
  fun setup() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
  }

  @Test
  fun testMinimalRcDocument() {
    // Minimal hand-crafted RC document with just a header and one text draw
    // This tests the most basic RC rendering path
    val player = RemoteComposePlayer(context)

    // Build minimal document using RemoteComposeWriter
    val writer =
            androidx.compose.remote.creation.RemoteComposeWriter(
                    1080,
                    1920,
                    "test",
                    androidx.compose.remote.core.RcPlatformServices.None
            )

    // Just add a simple draw - no layout containers
    writer.getRcPaint().setAntiAlias(true).setColor(0xFF000000.toInt()).setTextSize(20f).commit()
    writer.drawTextAnchored("Hello", 100f, 100f, -1f, Float.NaN, 0)

    val bytes = writer.buffer()
    println("Minimal document: ${bytes.size} bytes")

    // This should NOT throw "Too many operations"
    try {
      player.setDocument(bytes)
      println("SUCCESS: Minimal document parsed without error")
    } catch (e: RuntimeException) {
      fail("Failed to parse minimal RC document: ${e.message}")
    }
  }

  @Test
  fun testMinimalDocumentWithRoot() {
    val player = RemoteComposePlayer(context)

    val writer =
            androidx.compose.remote.creation.RemoteComposeWriter(
                    1080,
                    1920,
                    "test-root",
                    androidx.compose.remote.core.RcPlatformServices.None
            )

    writer.root {
      writer.getRcPaint().setAntiAlias(true).setColor(0xFF000000.toInt()).setTextSize(20f).commit()
      writer.drawTextAnchored("Hello with root", 100f, 100f, -1f, Float.NaN, 0)
    }

    val bytes = writer.buffer()
    println("Document with root: ${bytes.size} bytes")

    try {
      player.setDocument(bytes)
      println("SUCCESS: Document with root parsed without error")
    } catch (e: RuntimeException) {
      fail("Failed to parse document with root: ${e.message}")
    }
  }

  @Test
  fun testMultipleTextLines() {
    val player = RemoteComposePlayer(context)

    val writer =
            androidx.compose.remote.creation.RemoteComposeWriter(
                    1080,
                    1920,
                    "multi-text",
                    androidx.compose.remote.core.RcPlatformServices.None
            )

    writer.root {
      var y = 100f
      for (i in 1..10) {
        writer.getRcPaint()
                .setAntiAlias(true)
                .setColor(0xFF000000.toInt())
                .setTextSize(18f)
                .commit()
        writer.drawTextAnchored("Line $i of text content", 50f, y, -1f, Float.NaN, 0)
        y += 40f
      }
    }

    val bytes = writer.buffer()
    println("Multi-text document: ${bytes.size} bytes")

    try {
      player.setDocument(bytes)
      println("SUCCESS: Multi-text document parsed without error")
    } catch (e: RuntimeException) {
      fail("Failed to parse multi-text document: ${e.message}")
    }
  }
}
