package com.example.scanapi

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

class RoboflowService(private val apiKey: String) {

    private val client = OkHttpClient()

    fun uploadImage(file: File, callback: Callback) {
        val mediaType = "image/jpeg".toMediaTypeOrNull()
        val requestFile = file.asRequestBody(mediaType)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, requestFile)
            .build()

        val request = Request.Builder()
            .url("https://detect.roboflow.com/shopping-assistant/3?api_key=4LF1NTVpUpZP66V6YLKr") // Ensure this is the correct URL
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RoboflowService", "Upload failed: ${e.message}", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("RoboflowService", "Upload successful: $responseBody")
                } else {
                    val errorBody = response.body?.string()
                    Log.e("RoboflowService", "Upload error: ${response.message} - $errorBody")
                }
            }
        })
    }
}
