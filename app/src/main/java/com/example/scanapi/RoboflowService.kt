package com.example.scanapi

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.IOException
import com.google.gson.annotations.SerializedName

class RoboflowService(private val apiKey: String) {

    private val client = OkHttpClient()
    private val gson = Gson()

    fun uploadImage(file: File, callback: (InferenceResponse) -> Unit, errorCallback: (String) -> Unit) {
        val mediaType = "image/jpeg".toMediaTypeOrNull()
        val requestFile = file.asRequestBody(mediaType)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, requestFile)
            .build()

        val request = Request.Builder()
            .url("https://detect.roboflow.com/shopping-assistant/3?api_key=4LF1NTVpUpZP66V6YLKr") // Ensure this is correct
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorCallback("Upload failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    responseBody?.let {
                        try {
                            val inferenceResponse = gson.fromJson(it, InferenceResponse::class.java)
                            callback(inferenceResponse)
                        } catch (e: Exception) {
                            errorCallback("Parsing error: ${e.message}")
                        }
                    } ?: errorCallback("Empty response body")
                } else {
                    val errorBody = response.body?.string()
                    errorCallback("Upload error: ${response.message} - $errorBody")
                }
            }
        })
    }
}

data class Prediction(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val confidence: Float,
    @SerializedName("class") val className: String,  // Map the JSON key `class` to `className`
    val class_id: Int,
    val detection_id: String
)

data class InferenceResponse(
    val inference_id: String,
    val time: Float,
    val image: ImageMetadata,
    val predictions: List<Prediction>
)

data class ImageMetadata(
    val width: Int,
    val height: Int
)
