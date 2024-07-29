package com.example.scanapi

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import java.net.URLEncoder

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var useCameraButton: Button
    private lateinit var uploadFromPhotosButton: Button
    private lateinit var textToSpeech: TextToSpeech
    private var ttsInitialized = false


    val supabase = createSupabaseClient(
        supabaseUrl = "https://dvsqyskvmhmbbmzkzkwi.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR2c3F5c2t2bWhtYmJtemt6a3dpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MjIyMTg5MTksImV4cCI6MjAzNzc5NDkxOX0.ROEJkirzfB1QGXcC98oJyCzytRoHtjg_jypFUNplrwE"
    ) {
        install(Postgrest)
    }

    @Serializable
    data class countries(
        val id: Int,
        val name: String,
    )

    @Serializable
    data class products(
        val id: Int,
        val name: String,
        val description: String,
        val nutritionalFacts: String,
        val category: String,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        useCameraButton = findViewById(R.id.useCameraButton)
        uploadFromPhotosButton = findViewById(R.id.uploadFromPhotosButton)
        textToSpeech = TextToSpeech(this, this)

        useCameraButton.setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java)
            startActivity(intent)
        }

        uploadFromPhotosButton.setOnClickListener {
            pickImageFromGallery()
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK) {
            val imageUri = data?.data
            if (imageUri != null) {
                // Convert URI to File and upload it
                val file = uriToFile(imageUri)
                processImage(file)
            }
        }
    }

    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri) ?: throw IOException("Unable to open input stream")
        val file = File(externalMediaDirs.first(), "uploaded_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        return file
    }

    private fun processImage(file: File) {
        val roboflowService = RoboflowService("4LF1NTVpUpZP66V6YLKr")

        val bitmap = BitmapFactory.decodeFile(file.path)

        roboflowService.uploadImage(file, { inferenceResponse ->
            Log.d("ScanActivity", "Inference successful: $inferenceResponse")
            val resultText = if (inferenceResponse.predictions.isEmpty()) {
                "No Result"
            } else {
                inferenceResponse.predictions.joinToString("\n") {
                    "Detected: ${it.className} with confidence ${it.confidence}"
                }
            }
            runOnUiThread {
                if (inferenceResponse.predictions.isNotEmpty()) {
                    val topPrediction = inferenceResponse.predictions[0].className
                    displayProductDetails(topPrediction, file, 0f)
                } else {
                    showBottomSheet(file, "No Result", "Product Not Found", "Please try again", 0f)
                }
            }
        }, { errorMessage ->
            Log.e("ScanActivity", errorMessage)
            runOnUiThread {
                showBottomSheet(file, "Error: $errorMessage", "Product Not Found", "Please try again", 0f)
            }
        })
    }

    // Add a method to query the product details and show the bottom sheet
    private fun displayProductDetails(className: String, imageFile: File, rotationDegree: Float) {
        lifecycleScope.launch {
            val product = withContext(Dispatchers.IO) {
                val encodedClassName = URLEncoder.encode(className, "UTF-8")
                Log.d("ScanActivity", "Querying product details for: $encodedClassName")

                try {
                    // Querying Supabase
                    val response = try {
                        supabase.from("products").select(columns = Columns.list("id", "name", "description", "nutritionalFacts", "category")) {
                            filter {
                                products::name eq className
                                //or
                                eq("name", className)
                            }
                        }
                            .decodeList<products>()
                    } catch (e: Exception) {
                        Log.e("ScanActivity", "Error querying Supabase: ${e.message}", e)
                        emptyList<products>() // Return an empty list in case of error
                    }

                    if (response.isNotEmpty()) {
                        Log.d("ScanActivity", "Product found: ${response[0].name} - ${response[0].description}")
                        response[0]
                    } else {
                        Log.d("ScanActivity", "Product not found in Supabase.")
                        null
                    }
                } catch (e: Exception) {
                    Log.e("ScanActivity", "Error querying Supabase: ${e.message}", e)
                    e.printStackTrace()
                    null
                }
            }
            val description = product?.description ?: "No details available"
            val nutritionalFacts = product?.nutritionalFacts ?: "No nutritional facts available"
            runOnUiThread {
                showBottomSheet(imageFile, className, description, nutritionalFacts, rotationDegree)
            }
        }
    }



    private fun showBottomSheet(imageFile: File, resultText: String, description: String, nutritionalFacts: String, rotationDegree: Float) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_result, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        val resultTextView = bottomSheetView.findViewById<TextView>(R.id.resultTextView)
        val descriptionTextView = bottomSheetView.findViewById<TextView>(R.id.descriptionTextView)
        val nutritionalFactsTextView = bottomSheetView.findViewById<TextView>(R.id.nutritionalFactsTextView)
        val resultImageView = bottomSheetView.findViewById<ImageView>(R.id.resultImageView)
        val closeButton = bottomSheetView.findViewById<Button>(R.id.closeButton)
        val speakerButton = bottomSheetView.findViewById<Button>(R.id.speakerButton)
        val downloadButton = bottomSheetView.findViewById<Button>(R.id.downloadButton)

        resultTextView.text = resultText
        descriptionTextView.text = description
        nutritionalFactsTextView.text = nutritionalFacts

        downloadButton.visibility = View.GONE

        val bitmap = BitmapFactory.decodeFile(imageFile.path)
        val resizedBitmap = resizeImageForDisplay(bitmap, 800) // Adjust max width as needed
        resultImageView.setImageBitmap(resizedBitmap)

        closeButton.setOnClickListener {
            Log.d("MainActivity", "Close button clicked")
            bottomSheetDialog.dismiss()
        }

        speakerButton.setOnClickListener {
            if (ttsInitialized) {
                val combinedText = "$resultText. $description. $nutritionalFacts."
                textToSpeech.speak(combinedText, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        bottomSheetDialog.setOnDismissListener {
            downloadButton.visibility = View.VISIBLE
        }

        bottomSheetDialog.show()
    }

    private fun resizeImageForDisplay(bitmap: Bitmap, maxWidth: Int): Bitmap {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (aspectRatio > 1) {
            // Landscape
            newWidth = maxWidth
            newHeight = (newWidth / aspectRatio).toInt()
        } else {
            // Portrait
            newHeight = maxWidth
            newWidth = (newHeight * aspectRatio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.US)
            ttsInitialized = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsInitialized) {
                Log.e("MainActivity", "TTS initialization failed: Language not supported")
            }
        } else {
            Log.e("MainActivity", "TTS initialization failed")
            ttsInitialized = false
        }
    }

    override fun onDestroy() {
        // Shutdown TTS to release resources
        if (textToSpeech != null) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_IMAGE_PICK = 1001
    }
}
