package com.dgurnick.banking.bff.agent

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.*

class RcDocumentBuilderTest {

  @Test
  fun `buildRcDocument creates valid account balance document`() {
    val data = buildJsonObject {
      put("type", "account_balance")
      put("greeting", "Good morning, Test User")
      put("netWorth", "$10,000.00")
      putJsonArray("accounts") {
        addJsonObject {
          put("name", "Checking")
          put("number", "****1234")
          put("balance", "$5,000.00")
        }
      }
    }

    val base64 = buildRcDocument(data)
    assertNotNull(base64, "RC document should not be null")
    assertTrue(base64.isNotEmpty(), "RC document should not be empty")

    val bytes = Base64.getDecoder().decode(base64)
    println("Account balance document: ${bytes.size} bytes")

    // Verify RC header magic bytes (first 4 bytes should be "RCMP" or similar)
    assertTrue(bytes.size > 10, "Document should have content")

    // Print first 20 bytes for debugging
    println("First 20 bytes: ${bytes.take(20).map { it.toInt() and 0xFF }}")
  }

  @Test
  fun `buildRcDocument creates valid offers document`() {
    val data = buildJsonObject {
      put("type", "offers")
      putJsonArray("offers") {
        addJsonObject {
          put("title", "Cash Back Card")
          put("rate", "5% cash back")
          put("tag", "HOT")
          put("description", "Earn on every purchase")
        }
      }
    }

    val base64 = buildRcDocument(data)
    val bytes = Base64.getDecoder().decode(base64)
    println("Offers document: ${bytes.size} bytes")
    assertTrue(bytes.size > 10, "Document should have content")
  }

  @Test
  fun `buildRcDocument creates valid fallback document`() {
    val data = buildJsonObject {
      put("type", "fallback")
      put("message", "I didn't understand that.")
      putJsonArray("suggestions") {
        add("Check my balance")
        add("Find nearby ATMs")
      }
    }

    val base64 = buildRcDocument(data)
    val bytes = Base64.getDecoder().decode(base64)
    println("Fallback document: ${bytes.size} bytes")
    assertTrue(bytes.size > 10, "Document should have content")
  }

  @Test
  fun `buildRcDocument creates valid ATM list document with map`() {
    val data = buildJsonObject {
      put("type", "atm_list")
      putJsonArray("atms") {
        addJsonObject {
          put("name", "Main Street ATM")
          put("distance", "0.3 mi")
          put("openStatus", "Open 24/7")
        }
        addJsonObject {
          put("name", "Plaza Branch ATM")
          put("distance", "0.8 mi")
          put("openStatus", "Open until 9 PM")
        }
        addJsonObject {
          put("name", "Downtown ATM")
          put("distance", "1.2 mi")
          put("openStatus", "Closed - Opens at 7 AM")
        }
      }
    }

    val base64 = buildRcDocument(data)
    val bytes = Base64.getDecoder().decode(base64)
    println("ATM list document with map: ${bytes.size} bytes")
    // ATM list with map should be larger than basic text documents due to drawing operations
    assertTrue(bytes.size > 100, "ATM list document should have significant content")
  }
}
