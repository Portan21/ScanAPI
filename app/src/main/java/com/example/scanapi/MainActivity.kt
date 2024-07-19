package com.example.scanapi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private var imageCapture: ImageCapture? = null

    private lateinit var roboflowService: RoboflowService

    private val requestPermissionLauncher = registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Log.e("MainActivity", "Permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.captureButton)

        roboflowService = RoboflowService("4LF1NTVpUpZP66V6YLKr")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        captureButton.setOnClickListener {
            captureImage()
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
                Log.d("MainActivity", "Image saved: ${file.absolutePath}")
                roboflowService.uploadImage(file, object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("MainActivity", "Upload failed: ${e.message}", e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()
                            // Process the response (e.g., parse JSON and update UI)
                            Log.d("MainActivity", "Upload successful: $responseBody")
                        } else {
                            Log.e("MainActivity", "Upload error: ${response.message}")
                        }
                    }
                })
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("MainActivity", "Image capture failed: ${exception.message}", exception)
            }
        })
    }
}
