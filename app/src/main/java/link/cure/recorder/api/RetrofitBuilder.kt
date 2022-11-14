package link.cure.recorder.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitBuilder {
    private const val BASE_URL = "https://curelinktech.in"

    private fun getRetrofit(module: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl("$BASE_URL/$module/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val messagingAPIInterface: MessagingAPIInterface =
        getRetrofit(module = "messaging").create(MessagingAPIInterface::class.java)
}