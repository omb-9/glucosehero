package com.omb9.glucosehero.data.remote.off

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OffProductResponse(
    val status: Int = 0,
    @SerialName("status_verbose") val statusVerbose: String? = null,
    val code: String? = null,
    val product: OffProduct? = null,
) {
    /** True when Open Food Facts confirms the product was found in its database. */
    val isFound: Boolean get() = status == 1 && product != null

    /**
     * True when Open Food Facts indicates the product is not in its database.
     * OFF returns HTTP 200 with status == 0, rather than an HTTP 404 error.
     */
    val isNotFound: Boolean get() = status == 0 || product == null
}

@Serializable
data class OffSearchResponse(
    val count: Int = 0,
    val products: List<OffProduct> = emptyList(),
)

@Serializable
data class OffProduct(
    @SerialName("code") val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    val quantity: String? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    val nutriments: OffNutriments? = null,
)

@Serializable
data class OffNutriments(
    @SerialName("carbohydrates_100g") val carbs100g: Double? = null,
    @SerialName("proteins_100g") val protein100g: Double? = null,
    @SerialName("fat_100g") val fat100g: Double? = null,
    @SerialName("energy-kcal_100g") val kcal100g: Double? = null,
    @SerialName("carbohydrates_serving") val carbsServing: Double? = null,
    @SerialName("proteins_serving") val proteinServing: Double? = null,
    @SerialName("fat_serving") val fatServing: Double? = null,
    @SerialName("energy-kcal_serving") val kcalServing: Double? = null,
)
