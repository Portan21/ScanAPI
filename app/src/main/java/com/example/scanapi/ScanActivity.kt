package com.example.scanapi

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import android.Manifest

class ScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var backButton: Button
    private lateinit var uploadButton: Button
    private var imageCapture: ImageCapture? = null

    private lateinit var roboflowService: RoboflowService

    private val requestPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Log.e("ScanActivity", "Permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.captureButton)
        backButton = findViewById(R.id.backButton)
        uploadButton = findViewById(R.id.uploadButton)

        roboflowService = RoboflowService("4LF1NTVpUpZP66V6YLKr")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        backButton.setOnClickListener {
            finish() // Return to MainActivity
        }

        captureButton.setOnClickListener {
            captureImage()
        }

        uploadButton.setOnClickListener {
            pickImageFromGallery()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return

        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + ".jpg"
        val file = File(externalMediaDirs.first(), fileName)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                Log.d("ScanActivity", "Image saved: ${file.absolutePath}")
                val resizedFile = resizeImage(file, 1024, 768)

                roboflowService.uploadImage(resizedFile, { inferenceResponse ->
                    Log.d("ScanActivity", "Inference successful: $inferenceResponse")
                    val resultText = inferenceResponse.predictions.joinToString("\n") {
                        "Detected: ${it.className} with confidence ${it.confidence}"
                    }
                    showResultBottomSheet(resultText)
                }, { errorMessage ->
                    Log.e("ScanActivity", errorMessage)
                    showResultBottomSheet("Error: $errorMessage")
                })
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("ScanActivity", "Image capture failed: ${exception.message}", exception)
            }
        })
    }

    private fun resizeImage(file: File, width: Int, height: Int): File {
        val bitmap = BitmapFactory.decodeFile(file.path)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

        val resizedFile = File(externalMediaDirs.first(), "resized_${file.name}")
        val outputStream = FileOutputStream(resizedFile)
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        outputStream.flush()
        outputStream.close()

        return resizedFile
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
                val file = uriToFile(imageUri)
                uploadImage(file)
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

    private fun uploadImage(file: File) {
        roboflowService.uploadImage(file, { inferenceResponse ->
            Log.d("ScanActivity", "Inference successful: $inferenceResponse")
            val resultText = inferenceResponse.predictions.joinToString("\n") {
                "Detected: ${it.className} with confidence ${it.confidence}"
            }
            showResultBottomSheet(resultText)
        }, { errorMessage ->
            Log.e("ScanActivity", errorMessage)
            showResultBottomSheet("Error: $errorMessage")
        })
    }

    private fun showResultBottomSheet(resultText: String) {
        val bottomSheetFragment = ResultBottomSheetFragment.newInstance(resultText)
        bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
    }

    companion object {
        private const val REQUEST_IMAGE_PICK = 1002
    }
}
