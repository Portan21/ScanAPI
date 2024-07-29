import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SupabaseService {

    @GET("products")
    suspend fun getProductByName(
        @Header("apikey") apiKey: String,
        @Query("name") name: String
    ): List<Product>

    companion object {
        fun create(): SupabaseService {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://dvsqyskvmhmbbmzkzkwi.supabase.co/rest/v1/") // Replace with your project URL
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(SupabaseService::class.java)
        }
    }

    data class Product(
        val id: Int,
        val name: String,
        val description: String,
        val nutritionalFacts: String,
        val category: String?
    )
}