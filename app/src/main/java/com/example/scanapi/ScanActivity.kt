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
    import android.content.ContentValues
    import android.graphics.Matrix
    import android.media.ExifInterface
    import android.view.View
    import android.content.Intent
    import android.net.Uri
    import android.provider.MediaStore
    import android.speech.tts.TextToSpeech
    import android.widget.Toast
    import androidx.activity.result.contract.ActivityResultContracts
    import java.io.IOException
    import androidx.lifecycle.lifecycleScope
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext


    class ScanActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

        private lateinit var previewView: PreviewView
        private lateinit var captureButton: Button
        private lateinit var backButton: Button
        private lateinit var uploadButton: Button

        private var imageCapture: ImageCapture? = null
        private lateinit var roboflowService: RoboflowService
        private lateinit var textToSpeech: TextToSpeech

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_scan)

            previewView = findViewById(R.id.previewView)
            captureButton = findViewById(R.id.captureButton)
            backButton = findViewById(R.id.backButton)
            uploadButton = findViewById(R.id.uploadButton)

            roboflowService = RoboflowService("4LF1NTVpUpZP66V6YLKr")

            textToSpeech = TextToSpeech(this, this)

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
                captureButton.isEnabled = false // Disable capture button after click
            }

            uploadButton.setOnClickListener {
                requestImagePicker.launch("image/*")
                pauseCamera()
            }

            // Handle image URI passed from MainActivity
            val imageUriString = intent.getStringExtra("imageUri")
            if (imageUriString != null) {
                val imageUri = Uri.parse(imageUriString)
                val file = uriToFile(imageUri)
                processImage(file)
            }
        }

        override fun onInit(status: Int) {
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("ScanActivity", "Language not supported")
                }
            } else {
                Log.e("ScanActivity", "Initialization failed")
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

        private fun processImage(imageFile: File) {
            pauseCamera()

            // Resize the image if needed and prepare for upload
            val bitmap = BitmapFactory.decodeFile(imageFile.path)
            val resizedBitmap = resizeImageForDisplay(bitmap, 640)
            val resizedFile = File(externalCacheDir, "resized_image.jpg")
            FileOutputStream(resizedFile).use { outputStream ->
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }

            roboflowService.uploadImage(resizedFile, { inferenceResponse ->
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
                        displayProductDetails(topPrediction, resizedFile, 0f)
                    } else {
                        showBottomSheet(resizedFile, "No Result", "No details available", "No nutritional facts available", 0f)
                    }
                }
            }, { errorMessage ->
                Log.e("ScanActivity", errorMessage)
                runOnUiThread {
                    showBottomSheet(resizedFile, "Error: $errorMessage", "No details available", "No nutritional facts available", 0f)
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
                                displayProductDetails(topPrediction, resizedFile, rotationDegree)
                            } else {
                                showBottomSheet(resizedFile, "No Result", "No details available", "No nutritional facts available", rotationDegree)
                            }
                        }
                    }, { errorMessage ->
                        Log.e("ScanActivity", errorMessage)
                        runOnUiThread {
                            showBottomSheet(resizedFile, "Error: $errorMessage", "No details available", "No nutritional facts available", rotationDegree)
                        }
                    })
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("ScanActivity", "Image capture failed: ${exception.message}", exception)
                }
            })
        }

        // Add a method to query the product details and show the bottom sheet
        private fun displayProductDetails(className: String, imageFile: File, rotationDegree: Float) {
            lifecycleScope.launch {
                val product = withContext(Dispatchers.IO) {
                    val db = ProductDatabase.getDatabase(applicationContext)
                    db.productDao().getProductByName(className)
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

            val bitmap = BitmapFactory.decodeFile(imageFile.path)
            val rotatedBitmap = rotateImage(bitmap, rotationDegree)
            val resizedBitmap = resizeImageForDisplay(rotatedBitmap, 800) // Adjust max width as needed
            resultImageView.setImageBitmap(resizedBitmap)

            speakerButton.setOnClickListener {
                textToSpeech.speak(resultText, TextToSpeech.QUEUE_FLUSH, null, null)
            }

            downloadButton.setOnClickListener {
                saveImageToGallery(resizedBitmap)
            }

            closeButton.setOnClickListener {
                bottomSheetDialog.dismiss()
            }

            bottomSheetDialog.setOnDismissListener {
                resumeCamera() // Enable capture button when bottom sheet is dismissed
            }

            bottomSheetDialog.show()
        }

        private fun resizeImage(imageFile: File, maxWidth: Int, maxHeight: Int): File {
            val bitmap = BitmapFactory.decodeFile(imageFile.path)
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, maxHeight, true)

            val resizedFile = File(imageFile.parent, "resized_${imageFile.name}")
            FileOutputStream(resizedFile).use { outputStream ->
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            }
            return resizedFile
        }

        private fun resizeImageForDisplay(bitmap: Bitmap, maxWidth: Int): Bitmap {
            val aspectRatio = bitmap.width.toDouble() / bitmap.height.toDouble()
            val targetWidth = maxWidth
            val targetHeight = (targetWidth / aspectRatio).toInt()
            return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        }

        private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
            val matrix = Matrix().apply { postRotate(degrees) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        private fun saveImageToGallery(bitmap: Bitmap) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyApp")
            }

            val contentResolver = applicationContext.contentResolver
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                // Show toast message after saving the image
                runOnUiThread {
                    Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                }
            } ?: Log.e("ScanActivity", "Failed to create new MediaStore entry")
        }

        private fun pauseCamera() {
            captureButton.isEnabled = false
            uploadButton.isEnabled = false

            previewView.visibility = View.GONE
        }

        private fun resumeCamera() {
            captureButton.isEnabled = true
            uploadButton.isEnabled = true

            previewView.visibility = View.VISIBLE
        }

        private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                // Handle permission denial
            }
        }

        private val requestImagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val file = uriToFile(uri)
                processImage(file)
            } else {
                resumeCamera()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            textToSpeech.shutdown()
        }
    }
