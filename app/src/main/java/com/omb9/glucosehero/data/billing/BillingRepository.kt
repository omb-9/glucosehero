package com.omb9.glucosehero.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Single, app-wide owner of the Play Billing [BillingClient].
 *
 * The client is created once, connected on construction, and kept alive for the
 * lifetime of the process (which is why this repository is a Hilt [Singleton]).
 * Automatic service reconnection is enabled so the Play Billing Library itself
 * re-establishes the service connection before API calls when needed.
 */
@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext context: Context,
) : PurchasesUpdatedListener {

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enableAutoServiceReconnection()
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _subscriptionProducts = MutableStateFlow<List<ProductDetails>>(emptyList())
    val subscriptionProducts: StateFlow<List<ProductDetails>> = _subscriptionProducts.asStateFlow()

    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError: StateFlow<String?> = _purchaseError.asStateFlow()

    init {
        startConnection()
    }

    /** Convenience entry points for the two subscription tiers. */
    fun purchaseMonthly(activity: Activity) = purchase(activity, PRODUCT_ID_MONTHLY)

    fun purchaseYearly(activity: Activity) = purchase(activity, PRODUCT_ID_YEARLY)

    /**
     * Refreshes cached product details and the active-entitlement flag from
     * Google Play. Call this from onResume-style lifecycle events to pick up
     * purchases made on another device or while the app was not running.
     */
    fun refresh() {
        scope.launch { refreshSubscriptionsAndProducts() }
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh()
                } else {
                    _purchaseError.value = billingResult.debugMessage
                }
            }

            override fun onBillingServiceDisconnected() {
                // No-op: enableAutoServiceReconnection() lets the library
                // reconnect automatically before the next API call.
            }
        })
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?,
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty()
                    .filter { isTrackedSubscription(it) }
                    .forEach { purchase -> scope.launch { handlePurchase(purchase) } }
                refresh()
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> Unit

            else -> _purchaseError.value = billingResult.debugMessage
        }
    }

    /**
     * Launches the Google Play purchase flow for [productId].
     *
     * [BillingClient.launchBillingFlow] must be invoked on the main thread,
     * which is guaranteed by [scope].
     */
    private fun purchase(activity: Activity, productId: String) {
        scope.launch {
            val cached = _subscriptionProducts.value.firstOrNull { it.productId == productId }
            val productDetails = cached ?: queryProductDetails(listOf(productId)).firstOrNull()
            if (productDetails == null) {
                _purchaseError.value = "Subscription product '$productId' is not available."
                return@launch
            }

            val offerToken = productDetails.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken
            if (offerToken.isNullOrBlank()) {
                _purchaseError.value = "Subscription product '$productId' has no purchasable offer."
                return@launch
            }

            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()

            val launchResult = billingClient.launchBillingFlow(activity, billingFlowParams)
            if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _purchaseError.value = launchResult.debugMessage
            }
        }
    }

    private suspend fun refreshSubscriptionsAndProducts() {
        _subscriptionProducts.value = queryProductDetails(SUBSCRIPTION_PRODUCT_IDS.toList())

        val purchases = querySubscriptions()
        _isPremium.value = computePremium(purchases)

        purchases.asSequence()
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .filter { !it.isAcknowledged }
            .forEach { acknowledgePurchase(it) }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        if (!purchase.isAcknowledged) {
            acknowledgePurchase(purchase)
        }
        _isPremium.value = true
    }

    private fun computePremium(purchases: List<Purchase>): Boolean =
        purchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                !purchase.isSuspended &&
                purchase.products.any { it in SUBSCRIPTION_PRODUCT_IDS }
        }

    private fun isTrackedSubscription(purchase: Purchase): Boolean =
        purchase.products.any { it in SUBSCRIPTION_PRODUCT_IDS }

    private suspend fun queryProductDetails(productIds: List<String>): List<ProductDetails> =
        suspendCancellableCoroutine { continuation ->
            val productList = productIds.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            billingClient.queryProductDetailsAsync(params) { billingResult, result ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(result.productDetailsList)
                } else {
                    continuation.resume(emptyList())
                }
            }
        }

    private suspend fun querySubscriptions(): List<Purchase> =
        suspendCancellableCoroutine { continuation ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(purchases.orEmpty())
                } else {
                    continuation.resume(emptyList())
                }
            }
        }

    private suspend fun acknowledgePurchase(purchase: Purchase): Boolean =
        suspendCancellableCoroutine { continuation ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(params) { billingResult ->
                continuation.resume(
                    billingResult.responseCode == BillingClient.BillingResponseCode.OK,
                )
            }
        }

    companion object {
        // These product IDs must match the subscription products configured in
        // the Google Play Console for applicationId com.omb9.glucosehero.
        const val PRODUCT_ID_MONTHLY = "glucosehero_pro_monthly"
        const val PRODUCT_ID_YEARLY = "glucosehero_pro_yearly"

        private val SUBSCRIPTION_PRODUCT_IDS = setOf(PRODUCT_ID_MONTHLY, PRODUCT_ID_YEARLY)
    }
}
