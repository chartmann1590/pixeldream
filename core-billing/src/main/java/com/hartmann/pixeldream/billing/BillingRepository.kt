package com.hartmann.pixeldream.billing

/**
 * Wraps the Play Billing Library connection lifecycle and subscription purchase
 * flow for the $1.99/month ad-free subscription.
 *
 * TODO(Phase 5): implement using com.android.billingclient.api.BillingClient —
 *  connection lifecycle with backoff, querySubscriptionProducts, launchBillingFlow,
 *  PurchasesUpdatedListener, acknowledgePurchase (required within 3 days), and
 *  queryPurchases() to restore entitlement on app start.
 */
class BillingRepository
