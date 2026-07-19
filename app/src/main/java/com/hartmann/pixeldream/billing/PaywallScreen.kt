package com.hartmann.pixeldream.billing

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import com.hartmann.pixeldream.ui.components.PixelDreamButton

@Composable
fun PaywallScreen(billingRepository: BillingRepository) {
    var productDetails by remember { mutableStateOf<ProductDetails?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        productDetails = billingRepository.fetchSubscriptionProductDetails()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Go ad-free", style = MaterialTheme.typography.headlineMedium)
        Text(
            "$1.99/month removes ads between generations. Cancel any time.",
            style = MaterialTheme.typography.bodyLarge,
        )

        val details = productDetails
        val offerToken = details?.subscriptionOfferDetails?.firstOrNull()?.offerToken

        PixelDreamButton(
            text = if (details == null) "Loading..." else "Subscribe",
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
