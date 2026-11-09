package com.hartmann.pixeldream.billing

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import com.hartmann.pixeldream.ui.components.PixelDreamButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import com.hartmannstudios.direct.DirectBilling

@Composable
fun PaywallScreen(billingRepository: BillingRepository) {
    var productDetails by remember { mutableStateOf<ProductDetails?>(null) }
    val context = LocalContext.current
    // Tab, Nov 9 2026: show the active state once the ad-free license is present (bought or restored),
    // instead of the price and Continue button.
    val isAdFree by billingRepository.isAdFree.collectAsState(initial = false)
    LaunchedEffect(Unit) { productDetails = billingRepository.fetchSubscriptionProductDetails() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), MaterialTheme.colorScheme.background)))
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text("PIXELDREAM+", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(if (isAdFree) "You're a PixelDream+ member" else "Create without interruptions", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(10.dp))
        Text("Support private, on-device creation and remove ads between generations.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Benefit("✓", "No interstitial ads")
                Benefit("✓", "All models stay on device")
                Benefit("✓", "Cancel any time")
                Spacer(Modifier.height(4.dp))
                if (isAdFree) {
                    Text("Active", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Ad-free is on for this device. Thanks for supporting PixelDream!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("$1.99 / month", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.weight(1f))

        val details = productDetails
        val offerToken = details?.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (isAdFree) {
            if (DirectBilling.isDirect) {
                Row(Modifier.align(Alignment.CenterHorizontally)) {
                    TextButton(onClick = { (context as? Activity)?.let { billingRepository.manageSubscription(it) } }) { Text("Manage subscription") }
                }
            }
        } else if (DirectBilling.isDirect) {
            // Direct (GitHub / charleshartmann.com) build: Stripe Checkout, restore by email, Stripe's customer portal.
            Text(
                "Prices in USD. Secure checkout by Stripe. Renews monthly until you cancel (Manage subscription below). Your purchase is tied to the email you enter at checkout.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            PixelDreamButton(
                text = "Continue",
                enabled = true,
                onClick = { (context as? Activity)?.let { billingRepository.purchaseDirect(it) } },
            )
            Row(Modifier.align(Alignment.CenterHorizontally)) {
                TextButton(onClick = { (context as? Activity)?.let { billingRepository.restore(it) } }) { Text("Restore purchases") }
                TextButton(onClick = { (context as? Activity)?.let { billingRepository.manageSubscription(it) } }) { Text("Manage subscription") }
            }
        } else PixelDreamButton(
            text = if (details == null) "Checking availability…" else "Continue",
            enabled = details != null && offerToken != null,
            onClick = {
                val activity = context as? Activity
                if (activity != null && details != null && offerToken != null) {
                    billingRepository.launchPurchaseFlow(activity, details, offerToken)
                }
            },
        )
    }
}

@Composable
private fun Benefit(symbol: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(symbol, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
