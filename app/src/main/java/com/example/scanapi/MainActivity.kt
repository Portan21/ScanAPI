package com.example.scanapi

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
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
                showBottomSheet(file, resultText)
            }
        }, { errorMessage ->
            // Handle errors
            Log.e("MainActivity", errorMessage)
            runOnUiThread {
                showBottomSheet(file, "Error: $errorMessage")
            }
        })
    }

    private fun showBottomSheet(imageFile: File, resultText: String) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_result, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        val resultTextView = bottomSheetView.findViewById<TextView>(R.id.resultTextView)
        val resultImageView = bottomSheetView.findViewById<ImageView>(R.id.resultImageView)
        val closeButton = bottomSheetView.findViewById<Button>(R.id.closeButton)

        resultTextView.text = resultText

        val bitmap = BitmapFactory.decodeFile(imageFile.path)
        val resizedBitmap = resizeImageForDisplay(bitmap, 800) // Adjust max width as needed
        resultImageView.setImageBitmap(resizedBitmap)

        closeButton.setOnClickListener {
            Log.d("MainActivity", "Close button clicked")
            bottomSheetDialog.dismiss()
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

    companion object {
        private const val REQUEST_IMAGE_PICK = 1001
    }
}
