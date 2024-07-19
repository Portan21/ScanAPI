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
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import java.io.IOException


class ScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var backButton: Button

    private var imageCapture: ImageCapture? = null
    private lateinit var roboflowService: RoboflowService
    private lateinit var resultImageView: ImageView
    private lateinit var resultTextView: TextView
    private lateinit var closeButton: Button
    private lateinit var uploadButton: Button

    private val requestPermissionLauncher = registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Log.e("ScanActivity", "Permission denied")
        }
    }

    private val requestImagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val file = uriToFile(it)
            processImage(file)
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
            finish() // This will return to the previous activity (MainActivity)
        }

        captureButton.setOnClickListener {
            captureImage()
        }

        uploadButton.setOnClickListener {
            requestImagePicker.launch("image/*")
            pauseCamera()
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
        // Load the bitmap and resize it using resizeImageForDisplay
        val bitmap = BitmapFactory.decodeFile(file.path)
        val resizedBitmap = resizeImageForDisplay(bitmap, 800) // Adjust width as needed

        // Save the resized bitmap to a temporary file
        val resizedFile = File(externalMediaDirs.first(), "resized_${file.name}")
        FileOutputStream(resizedFile).use { outputStream ->
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        }

        roboflowService.uploadImage(resizedFile, { inferenceResponse ->
            // Handle successful response
            Log.d("ScanActivity", "Inference successful: $inferenceResponse")
            val resultText = inferenceResponse.predictions.joinToString("\n") {
                "Detected: ${it.className} with confidence ${it.confidence}"
            }
            runOnUiThread {
                showBottomSheet(resizedFile, resultText, 0f) // Pass 0f for rotation if not applicable
            }
        }, { errorMessage ->
            // Handle errors
            Log.e("ScanActivity", errorMessage)
            runOnUiThread {
                showBottomSheet(resizedFile, "Error: $errorMessage", 0f) // Pass 0f for rotation if not applicable
            }
        })
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

        val rotationDegree = 90f

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
                        showBottomSheet(file, resultText, rotationDegree) // Ensure showBottomSheet is called on the main thread
                    }
                }, { errorMessage ->
                    // Handle errors
                    Log.e("ScanActivity", errorMessage)
                    runOnUiThread {
                        showBottomSheet(resizedFile, "Error: $errorMessage", rotationDegree) // Pass error message as resultText
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

    private fun showBottomSheet(file: File, resultText: String, rotationDegree: Float) {
        runOnUiThread {
            val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_result, null)
            val bottomSheetDialog = BottomSheetDialog(this)
            bottomSheetDialog.setContentView(bottomSheetView)

            // Get references to views
            val resultImageView: ImageView = bottomSheetView.findViewById(R.id.resultImageView)
            val resultTextView: TextView = bottomSheetView.findViewById(R.id.resultTextView)
            val closeButton: Button = bottomSheetView.findViewById(R.id.closeButton)

            // Load and manually rotate the image
            val bitmap = BitmapFactory.decodeFile(file.path)
            val rotatedBitmap = rotateImage(bitmap, rotationDegree)
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
            else -> {
                // Apply 90-degree rotation if no valid EXIF orientation is found
                Log.d("ScanActivity", "Applying manual rotation of 90 degrees")
                matrix.postRotate(90f)
            }
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
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
