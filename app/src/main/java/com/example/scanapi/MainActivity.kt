package com.example.scanapi

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var useCameraButton: Button
    private lateinit var uploadFromPhotosButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        useCameraButton = findViewById(R.id.useCameraButton)
        uploadFromPhotosButton = findViewById(R.id.uploadFromPhotosButton)

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
            // Handle successful response
            Log.d("MainActivity", "Inference successful: $inferenceResponse")
            val resultText = inferenceResponse.predictions.joinToString("\n") {
                "Detected: ${it.className} with confidence ${it.confidence}"
            }
            runOnUiThread {
                showBottomSheet(resultText, bitmap)
            }
        }, { errorMessage ->
            // Handle errors
            Log.e("MainActivity", errorMessage)
            runOnUiThread {
                showBottomSheet("Error: $errorMessage", bitmap)
            }
        })
    }

    private fun showBottomSheet(resultText: String, imageBitmap: Bitmap) {
        val bottomSheetFragment = ResultBottomSheetFragment.newInstance(resultText, imageBitmap)
        bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
    }

    companion object {
        private const val REQUEST_IMAGE_PICK = 1001
    }
}
