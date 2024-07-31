package com.example.scanapi

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.scanapi.MainActivity.products
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class ScanActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var backButton: Button
    private lateinit var uploadButton: Button

    private var imageCapture: ImageCapture? = null
    private lateinit var roboflowService: RoboflowService
    private lateinit var textToSpeech: TextToSpeech

    private var ttsInitialized = false

    private var detected = true
    private var upload = false;

    val supabase = createSupabaseClient(
        supabaseUrl = "https://dvsqyskvmhmbbmzkzkwi.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR2c3F5c2t2bWhtYmJtemt6a3dpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MjIyMTg5MTksImV4cCI6MjAzNzc5NDkxOX0.ROEJkirzfB1QGXcC98oJyCzytRoHtjg_jypFUNplrwE"
    ) {
        install(Postgrest)
    }


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
            upload = false;
            captureImage()
            captureButton.isEnabled = false // Disable capture button after click
            uploadButton.isEnabled = false
        }

        uploadButton.setOnClickListener {
            upload = true;
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
            ttsInitialized = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsInitialized) {
                Log.e("ScanActivity", "TTS initialization failed: Language not supported")
            }
        } else {
            Log.e("ScanActivity", "TTS initialization failed")
            ttsInitialized = false
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
                detected = true;
                inferenceResponse.predictions.joinToString("\n") {
                    "Detected: ${it.className} with confidence ${it.confidence}"
                }
            }
            runOnUiThread {
                if (inferenceResponse.predictions.isNotEmpty()) {
                    detected = true;
                    val topPrediction = inferenceResponse.predictions[0].className
                    displayProductDetails(topPrediction, resizedFile, 0f)
                } else {
                    detected = false
                    showBottomSheet(resizedFile, "No Result", "Product not found", "Please try again", "", 0f)
                }
            }
        }, { errorMessage ->
            Log.e("ScanActivity", errorMessage)
            runOnUiThread {
                showBottomSheet(resizedFile, "Error: $errorMessage", "No details available", "No nutritional facts available", "", 0f)
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
                        detected = true;
                        inferenceResponse.predictions.joinToString("\n") {
                            "Detected: ${it.className} with confidence ${it.confidence}"
                        }
                    }
                    runOnUiThread {
                        if (inferenceResponse.predictions.isNotEmpty()) {
                            detected = true;
                            val topPrediction = inferenceResponse.predictions[0].className
                            displayProductDetails(topPrediction, resizedFile, rotationDegree)
                        } else {
                            detected = false;
                            showBottomSheet(resizedFile, "No Result", "Product not found", "Please try again", "", rotationDegree)
                        }
                    }
                }, { errorMessage ->
                    Log.e("ScanActivity", errorMessage)
                    runOnUiThread {
                        showBottomSheet(resizedFile, "Error: $errorMessage", "Product not found", "Please try again", "", rotationDegree)
                    }
                })
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("ScanActivity", "Image capture failed: ${exception.message}", exception)
            }
        })
    }

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
            val nutritionalFacts2 = "$nutritionalFacts | $nutritionalFacts | $nutritionalFacts" //////////////////////////////////////////////////////////////////////
            val nutritionalFacts3 = "$nutritionalFacts. $nutritionalFacts. $nutritionalFacts"
            runOnUiThread {
                showBottomSheet(imageFile, className, description, nutritionalFacts2, nutritionalFacts3, rotationDegree)
            }
        }
    }



    private fun showBottomSheet(imageFile: File, resultText: String, description: String, nutritionalFacts2: String, nutritionalFacts3: String, rotationDegree: Float) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_result, null)

        val resultTextView = bottomSheetView.findViewById<TextView>(R.id.resultTextView)
        val descriptionTextView = bottomSheetView.findViewById<TextView>(R.id.descriptionTextView)
        val nutritionalFactsTextView = bottomSheetView.findViewById<TextView>(R.id.nutritionalFactsTextView)
        val resultImageView = bottomSheetView.findViewById<ImageView>(R.id.resultImageView)
        val closeButton = bottomSheetView.findViewById<Button>(R.id.closeButton)
        val speakerButton = bottomSheetView.findViewById<Button>(R.id.speakerButton)
        val downloadButton = bottomSheetView.findViewById<Button>(R.id.downloadButton)

        val bitmap = BitmapFactory.decodeFile(imageFile.path)
        val rotatedBitmap = rotateImage(bitmap, rotationDegree)
        resultImageView.setImageBitmap(rotatedBitmap)

        resultTextView.text = resultText
        descriptionTextView.text = description
        nutritionalFactsTextView.text = nutritionalFacts2

        if (!detected){
           // nutritionalFactsTextView.visibility = View.GONE // Make this PLEASE TRY AGAIN
           // descriptionTextView.visibility = View.GONE
        }

        if (upload){
            downloadButton.visibility = View.GONE
        }

        downloadButton.setOnClickListener {
            saveImageToGallery(rotatedBitmap)
        }

        closeButton.setOnClickListener {
            Log.d("MainActivity", "Close button clicked")
            bottomSheetDialog.dismiss()
        }

        speakerButton.setOnClickListener {
            if (ttsInitialized) {
                val combinedText = "$resultText. $description. $nutritionalFacts3"
                textToSpeech.speak(combinedText, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        bottomSheetDialog.setContentView(bottomSheetView)
        bottomSheetDialog.setOnDismissListener {
            resumeCamera()
        }
        bottomSheetDialog.show()
    }

    private val requestPermissionLauncher = registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestImagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val file = uriToFile(it)
            processImage(file)
        } ?: run {
            resumeCamera()
        }
    }

    private fun pauseCamera() {
        previewView.visibility = View.INVISIBLE // Hide the camera preview
    }

    private fun resumeCamera() {
        previewView.visibility = View.VISIBLE // Show the camera preview
        captureButton.isEnabled = true // Enable capture button
        uploadButton.isEnabled = true
    }

    private fun resizeImageForDisplay(bitmap: Bitmap, targetWidth: Int): Bitmap {
        val aspectRatio = bitmap.height.toFloat() / bitmap.width
        val targetHeight = (targetWidth * aspectRatio).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false)
    }

    private fun resizeImage(imageFile: File, targetWidth: Int, targetHeight: Int): File {
        val bitmap = BitmapFactory.decodeFile(imageFile.path)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false)
        val resizedFile = File(externalCacheDir, "resized_image.jpg")
        FileOutputStream(resizedFile).use { outputStream ->
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }
        return resizedFile
    }

    private fun rotateImage(bitmap: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
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


    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
    }
}
