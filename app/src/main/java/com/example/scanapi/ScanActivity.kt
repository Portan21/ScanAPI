package com.example.scanapi

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import android.Manifest
import android.graphics.Matrix
import android.media.ExifInterface
import android.view.View


class ScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var backButton: Button

    private var imageCapture: ImageCapture? = null
    private lateinit var roboflowService: RoboflowService
    private lateinit var resultImageView: ImageView
    private lateinit var resultTextView: TextView
    private lateinit var closeButton: Button

    private val requestPermissionLauncher = registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
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

        roboflowService = RoboflowService("4LF1NTVpUpZP66V6YLKr")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Set up the back button to finish the activity and return to MainActivity
        backButton.setOnClickListener {
            finish() // This will return to the previous activity (MainActivity)
        }

        captureButton.setOnClickListener {
            captureImage()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Create preview use case
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9) // Adjust as needed
                .build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            // Create image capture use case
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY) // Set capture mode to minimize latency
                .setTargetAspectRatio(AspectRatio.RATIO_16_9) // Adjust as needed
                .build()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            try {
                // Unbind all use cases before binding new ones
                cameraProvider.unbindAll()

                // Bind use cases to the camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("ScanActivity", "Failed to bind camera use cases", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        pauseCamera() // Hide the camera preview

        val imageCapture = imageCapture ?: return

        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + ".jpg"
        val file = File(externalMediaDirs.first(), fileName)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                Log.d("ScanActivity", "Image saved: ${file.absolutePath}")
                val resizedFile = resizeImage(file, 1024, 768)  // Adjust the dimensions as needed

                roboflowService.uploadImage(resizedFile, { inferenceResponse ->
                    // Handle successful response
                    Log.d("ScanActivity", "Inference successful: $inferenceResponse")
                    val resultText = inferenceResponse.predictions.joinToString("\n") {
                        "Detected: ${it.className} with confidence ${it.confidence}"
                    }
                    runOnUiThread {
                        showBottomSheet(resizedFile, resultText) // Ensure showBottomSheet is called on the main thread
                    }
                }, { errorMessage ->
                    // Handle errors
                    Log.e("ScanActivity", errorMessage)
                    runOnUiThread {
                        showBottomSheet(resizedFile, "Error: $errorMessage") // Pass error message as resultText
                    }
                })
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("ScanActivity", "Image capture failed: ${exception.message}", exception)
            }
        })
    }

    private fun pauseCamera() {
        previewView.visibility = View.GONE // Hide the preview view
    }

    private fun resumeCamera() {
        previewView.visibility = View.VISIBLE // Show the preview view
        startCamera() // Rebind the camera use cases
    }

    private fun showBottomSheet(file: File, resultText: String) {
        runOnUiThread {
            val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_result, null)
            val bottomSheetDialog = BottomSheetDialog(this)
            bottomSheetDialog.setContentView(bottomSheetView)

            // Get references to views
            val resultImageView: ImageView = bottomSheetView.findViewById(R.id.resultImageView)
            val resultTextView: TextView = bottomSheetView.findViewById(R.id.resultTextView)
            val closeButton: Button = bottomSheetView.findViewById(R.id.closeButton)

            // Load and display the image
            val bitmap = BitmapFactory.decodeFile(file.path)
            val rotatedBitmap = rotateImageIfRequired(bitmap, file.path)
            val resizedBitmap = resizeImageForDisplay(rotatedBitmap, 800) // Adjust width as needed
            resultImageView.setImageBitmap(resizedBitmap)

            // Set the result text
            resultTextView.text = resultText

            // Handle the close button
            closeButton.setOnClickListener {
                bottomSheetDialog.dismiss()
            }

            // Resume camera preview when bottom sheet is dismissed
            bottomSheetDialog.setOnDismissListener {
                resumeCamera()
            }
            Log.d("ScanActivity", "Original image size: ${bitmap.width}x${bitmap.height}")
            Log.d("ScanActivity", "Rotated image size: ${rotatedBitmap.width}x${rotatedBitmap.height}")
            Log.d("ScanActivity", "Resized image size: ${resizedBitmap.width}x${resizedBitmap.height}")

            bottomSheetDialog.show()
        }
    }

    private fun rotateImageIfRequired(bitmap: Bitmap, imagePath: String): Bitmap {
        val exif = ExifInterface(imagePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            // Handle other cases if needed
            ExifInterface.ORIENTATION_NORMAL -> {
                // No rotation needed
                Log.d("ScanActivity", "Orientation is NORMAL, no rotation applied")
                return bitmap
            }
            else -> {
                // Fallback for undefined orientations
                Log.d("ScanActivity", "Unknown orientation: $orientation, no rotation applied")
                return bitmap
            }
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun manuallyRotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
}
