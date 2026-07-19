package com.hartmann.pixeldream.data.entitlement

import kotlinx.coroutines.flow.Flow

/**
 * Whether the current user is ad-free. Today's implementation trusts local Play
 * Billing Library purchase state; a future `RemoteVerifiedEntitlementRepository`
 * can swap in behind this same interface once server-side verification (e.g. via
 * a Cloudflare Worker calling the Play Developer API) exists.
 */
interface EntitlementRepository {
    val isAdFree: Flow<Boolean>
    suspend fun refreshEntitlement()
}
