package com.omb9.glucosehero.data.remote.off

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {

    @GET("api/v2/product/{barcode}.json")
    suspend fun product(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = FIELDS,
    ): Response<OffProductResponse>

    @GET("api/v2/search")
    suspend fun search(
        @Query("search_terms") query: String,
        @Query("fields") fields: String = SEARCH_FIELDS,
        @Query("page_size") pageSize: Int = 20,
        @Query("json") json: Int = 1,
    ): Response<OffSearchResponse>

    companion object {
        /** Only what we consume. A full product document is hundreds of kilobytes. */
        const val FIELDS = "code,product_name,brands,quantity,serving_size,nutriments"

        /** Same shape as [FIELDS], since search results are product documents too. */
        const val SEARCH_FIELDS = "code,product_name,brands,quantity,serving_size,nutriments"
    }
}
