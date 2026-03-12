package com.dgurnick.banking.client

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Expandable offers view with tap-to-expand cards. Remote Compose cannot support expand/collapse
 * interactions - this is native Compose.
 */
@Composable
fun ExpandableOffersView(offersData: OffersData, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    // Title
    Text(
            text = offersData.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
    )

    // Offer cards
    offersData.offers.forEach { offer ->
      ExpandableOfferCard(offer = offer)
      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@Composable
private fun ExpandableOfferCard(offer: Offer) {
  var expanded by remember { mutableStateOf(false) }
  val context = LocalContext.current

  Card(
          modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header row
      Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top
      ) {
        Column(modifier = Modifier.weight(1f)) {
          // Tag badge
          Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = MaterialTheme.shapes.small
          ) {
            Text(
                    text = offer.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Title
          Text(
                  text = offer.title,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
          )

          // Summary
          Text(
                  text = offer.summary,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        // Rate
        Column(horizontalAlignment = Alignment.End) {
          Text(
                  text = offer.rate,
                  style = MaterialTheme.typography.headlineMedium,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold
          )
          Text(
                  text = offer.rateLabel,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Expand indicator
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Icon(
                imageVector =
                        if (expanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Expandable details
      AnimatedVisibility(
              visible = expanded,
              enter = expandVertically(),
              exit = shrinkVertically()
      ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
          HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

          // Details text
          Text(
                  text = offer.details,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurface
          )

          Spacer(modifier = Modifier.height(16.dp))

          // CTA button
          Button(
                  onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(offer.ctaUrl))
                    context.startActivity(intent)
                  },
                  modifier = Modifier.fillMaxWidth()
          ) { Text(offer.ctaText) }
        }
      }
    }
  }
}
