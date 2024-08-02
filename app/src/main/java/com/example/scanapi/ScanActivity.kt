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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scanapi.MainActivity.nutritionfacts
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
    private lateinit var file : File


    private var ttsInitialized = false

    private var detected = true
    private var upload = false;

    val supabase = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
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

        roboflowService = RoboflowService(BuildConfig.ROBOFLOW_API_KEY)

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
            file = uriToFile(imageUri)
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

    fun goBack(){
        processImage(file)
    }

    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri) ?: throw IOException("Unable to open input stream")
        file = File(externalMediaDirs.first(), "uploaded_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        return file
    }

    private fun compressImage(bitmap: Bitmap, outputFile: File) {
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
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

        val compressedFile = File(resizedFile.parent, "compressed_${resizedFile.name}")
        compressImage(resizedBitmap, compressedFile)

        roboflowService.uploadImage(compressedFile, { inferenceResponse ->
            Log.d("ScanActivity", "Inference successful: $inferenceResponse")
            val detections = inferenceResponse.predictions.map {
                Detection(it.className, it.confidence)
            }

            // Remove duplicates based on className
            val uniqueDetections = detections.distinctBy { it.className }

            runOnUiThread {
                if (uniqueDetections.isNotEmpty()) {
                    showDetectionListBottomSheet(uniqueDetections, compressedFile)
                } else {
                    showBottomSheet(compressedFile, "No Result", "Product Not Found", "Please try again", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0f)
                }
            }
        }, { errorMessage ->
            Log.e("ScanActivity", errorMessage)
            runOnUiThread {
                showBottomSheet(compressedFile, "Error: $errorMessage", "Product Not Found", "Please try again", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0f)
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
                            showBottomSheet(resizedFile, "No Result", "Product Not Found", "Please try again", "", 0.0, 0.0, 0.0, 0.0, 0.0, rotationDegree)
                        }
                    }
                }, { errorMessage ->
                    Log.e("ScanActivity", errorMessage)
                    runOnUiThread {
                        showBottomSheet(resizedFile, "Error: $errorMessage", "Product Not Found", "Please try again", "", 0.0, 0.0, 0.0, 0.0, 0.0, rotationDegree)
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
                        supabase.from("products").select(columns = Columns.list("productid", "prodname", "categoryid", "description", "ingredient")) {
                            filter {
                                com.example.scanapi.MainActivity.products::prodname eq className
                                //or
                                eq("prodname", className)
                            }
                        }
                            .decodeList<com.example.scanapi.MainActivity.products>()
                    } catch (e: Exception) {
                        Log.e("ScanActivity", "Error querying Supabase: ${e.message}", e)
                        emptyList<com.example.scanapi.MainActivity.products>() // Return an empty list in case of error
                    }


                    if (response.isNotEmpty()) {
                        Log.d("ScanActivity", "Product found: ${response[0].prodname} - ${response[0].description}")
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
            val productid = product?.productid ?: "No details available"
            val productName = product?.prodname ?: "No details available"
            val description = product?.description ?: "No details available"
            val ingredients = product?.ingredient ?: "No nutritional facts available"

            val product2 = withContext(Dispatchers.IO) {
                val encodedClassName = URLEncoder.encode(className, "UTF-8")
                Log.d("ScanActivity", "Querying product details for: $encodedClassName")

                try {
                    // Querying Supabase
                    val response = try {
                        supabase.from("nutritionalfacts").select(columns = Columns.list("productid", "servingsize", "amtofserving", "calorie", "carbohydrate", "protein", "fat")) {
                            filter {
                                nutritionfacts::productid eq productid
                                //or
                                eq("productid", productid)
                            }
                        }
                            .decodeList<nutritionfacts>()
                    } catch (e: Exception) {
                        Log.e("ScanActivity", "Error querying Supabase: ${e.message}", e)
                        emptyList<nutritionfacts>() // Return an empty list in case of error
                    }


                    if (response.isNotEmpty()) {
                        Log.d("ScanActivity", "Product found: ${response[0].servingsize} - ${response[0].amtofserving}")
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
            val servingsize = product2?.servingsize ?: ""
            val amtofserving = product2?.amtofserving ?: 0.0
            val calorie = product2?.calorie ?: 0.0
            val carbohydrate = product2?.carbohydrate ?: 0.0
            val protein = product2?.protein ?: 0.0
            val fat = product2?.fat ?: 0.0
//            val product2 = withContext(Dispatchers.IO) {
//                try {
//                    val response2 = try {
//                        supabase.from("nutritionalfacts").select(columns = Columns.list("servingsize", "amtofserving", "calories", "carbohydrate", "protein", "fat")
//                        ) {
//                            filter {
//                                nutritionfacts::productid eq productid
//                                //or
//                                eq("productid", productid)
//                            }
//                        }
//                            .decodeList<nutritionfacts>()
//                    } catch (e: Exception) {
//                        Log.e("ScanActivity", "Error querying Supabase: ${e.message}", e)
//                        emptyList<nutritionfacts>() // Return an empty list in case of error
//                    }
//                } catch (e: Exception) {
//                    Log.e("ScanActivity", "Error querying Supabase: ${e.message}", e)
//                    e.printStackTrace()
//                    null
//                }
//            }
            runOnUiThread {
                showBottomSheet(imageFile, productName, description, ingredients, servingsize, amtofserving, calorie, carbohydrate, protein, fat, rotationDegree)
            }
        }
    }

    private fun showDetectionListBottomSheet(detections: List<Detection>, imageFile: File) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_detection_list, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        val detectionRecyclerView = bottomSheetView.findViewById<RecyclerView>(R.id.detectionRecyclerView)
        detectionRecyclerView.layoutManager = LinearLayoutManager(this)

        val adapter = DetectionAdapter(detections, object : DetectionAdapter.OnItemClickListener {
            override fun onItemClick(detection: Detection) {
                bottomSheetDialog.dismiss()
                displayProductDetails(detection.className, imageFile, 0f)
            }
        })

        detectionRecyclerView.adapter = adapter

        bottomSheetDialog.show()

        val closeButton: Button = bottomSheetView.findViewById(R.id.closeButton)
        closeButton.setOnClickListener {
            Log.d("MainActivity", "Close button clicked")
            bottomSheetDialog.dismiss()
        }
    }



    private fun showBottomSheet(imageFile: File, productName: String, description: String, ingredients: String, servingsize : String, amtofserving : Double, calorie : Double, carbohydrate : Double, protein : Double, fat : Double, rotationDegree: Float) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_result, null)

        val resultTextView = bottomSheetView.findViewById<TextView>(R.id.resultTextView)
        val descriptionTextView = bottomSheetView.findViewById<TextView>(R.id.descriptionTextView)
        val ingredientsTextView = bottomSheetView.findViewById<TextView>(R.id.ingredientsTextView)
        val nutritionalFactsTextView = bottomSheetView.findViewById<TextView>(R.id.nutritionalFactsTextView)
        val resultImageView = bottomSheetView.findViewById<ImageView>(R.id.resultImageView)
        val closeButton = bottomSheetView.findViewById<Button>(R.id.closeButton)
        val backButton: Button = bottomSheetView.findViewById(R.id.backButton)
        val speakerButton = bottomSheetView.findViewById<Button>(R.id.speakerButton)
        val downloadButton = bottomSheetView.findViewById<Button>(R.id.downloadButton)

        val bitmap = BitmapFactory.decodeFile(imageFile.path)
        val rotatedBitmap = rotateImage(bitmap, rotationDegree)
        resultImageView.setImageBitmap(rotatedBitmap)

        resultTextView.text = productName
        descriptionTextView.text = description
        ingredientsTextView.text = ingredients
        val allNutritionFacts = "Serving Size: $servingsize | Serving Amount: $amtofserving | Calorie: $calorie | carbohydrate: $carbohydrate | protein: $protein | fat: $fat"
        nutritionalFactsTextView.text = allNutritionFacts

        if (!detected){
            nutritionalFactsTextView.visibility = View.GONE
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

        backButton.setOnClickListener{
            Log.d("MainActivity", "Back button clicked")
            goBack()
            bottomSheetDialog.dismiss()
        }

        speakerButton.setOnClickListener {
            if (ttsInitialized && detected) {
                val combinedText = "$productName. $description. Ingredients: $ingredients. Serving Size: $servingsize. Serving Amount: $amtofserving. Calorie: $calorie. carbohydrate: $carbohydrate. protein: $protein. fat: $fat.\""
                textToSpeech.speak(combinedText, TextToSpeech.QUEUE_FLUSH, null, null)
            }
            else if (ttsInitialized){
                val combinedText = "$productName. $description. $ingredients."
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
