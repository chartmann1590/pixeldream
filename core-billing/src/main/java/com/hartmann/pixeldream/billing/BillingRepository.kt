package com.hartmann.pixeldream.billing

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.hartmann.pixeldream.data.entitlement.EntitlementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The $1.99/month ad-free subscription product, configured in Play Console. */
const val AD_FREE_SUBSCRIPTION_PRODUCT_ID = "pixeldream_ad_free_monthly"

private val Context.entitlementDataStore by preferencesDataStore(name = "entitlement")
private val IS_AD_FREE_KEY = booleanPreferencesKey("is_ad_free")

/**
 * Wraps Play Billing for the ad-free subscription. Purchase state is cached
 * in DataStore so ad-gating decisions don't block on a live Billing query,
 * with [refreshEntitlement] re-syncing from Play on app start.
 *
 * Today this trusts client-side Billing Library state. A future
 * `RemoteVerifiedEntitlementRepository` can swap in behind the same
 * [EntitlementRepository] interface (e.g. backed by a Cloudflare Worker
 * validating purchase tokens server-side) without call sites changing.
 */
class BillingRepository(private val context: Context) : EntitlementRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach { handlePurchase(it) }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    override val isAdFree: Flow<Boolean> =
        context.entitlementDataStore.data.map { it[IS_AD_FREE_KEY] ?: false }

    override suspend fun refreshEntitlement() {
        connectIfNeeded()
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        val hasActiveSubscription = result.purchasesList.any { purchase ->
            purchase.products.contains(AD_FREE_SUBSCRIPTION_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        setAdFree(hasActiveSubscription)
        result.purchasesList.forEach { handlePurchase(it) }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.products.contains(AD_FREE_SUBSCRIPTION_PRODUCT_ID)) return

        scope.launch { setAdFree(true) }

        if (!purchase.isAcknowledged) {
            scope.launch {
                billingClient.acknowledgePurchase(
                    com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build(),
                )
            }
        }
    }

    suspend fun fetchSubscriptionProductDetails(): ProductDetails? {
        connectIfNeeded()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(AD_FREE_SUBSCRIPTION_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        return billingClient.queryProductDetails(params).productDetailsList?.firstOrNull()
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails, offerToken: String) {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    private suspend fun setAdFree(value: Boolean) {
        context.entitlementDataStore.edit { it[IS_AD_FREE_KEY] = value }
    }

    private suspend fun connectIfNeeded() {
        if (billingClient.isReady) return
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resumeWith(Result.success(Unit))
                }

                override fun onBillingServiceDisconnected() {
                    // BillingClient retries internally; next call to a billing
                    // method will trigger reconnection via connectIfNeeded().
                }
            })
        }
    }
}
