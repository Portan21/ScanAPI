package com.example.scanapi

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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import setHeightBasedOnChildren
import java.net.URLEncoder

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var useCameraButton: Button
    private lateinit var uploadFromPhotosButton: Button
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var file: File
    private var ttsInitialized = false

    private var detected = false


    val supabase = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Postgrest)
    }


    @Serializable
    data class products(
        val productid: Int,
        val prodname: String,
        val categoryid: Int,
        val description: String,
        val ingredient: String,
    )

    @Serializable
    data class nutritionfacts(
        val productid: Int,
        val servingsize: String,
        val amtofserving: Double,
        val calorie: Double,
        val carbohydrate: Double,
        val protein: Double,
        val fat: Double
    )

    @Serializable
    data class Branch(
        val branchname: String
    )

    @Serializable
    data class branchproduct(
        val branchproductid: Int,
        val branches: Branch,
        val branchid: Int,
        val productid: Int,
        val price: Double,
        val stock: Int,
    )

    data class Store(
        val storeName: Int,
        val branches: String,
        val price: Double,
        val stock: Int
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
                file = uriToFile(imageUri)
                processImage(file)
            }
        }
    }

    fun goBack(){
        processImage(file)
    }

    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri) ?: throw IOException("Unable to open input stream")
        val file = File(externalMediaDirs.first(), "uploaded_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        return file
    }

    private fun resizeImage(imageFile: File, maxWidth: Int, maxHeight: Int): Bitmap {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(imageFile.path, options)
        val (srcWidth, srcHeight) = options.outWidth to options.outHeight

        var inSampleSize = 1
        if (srcHeight > maxHeight || srcWidth > maxWidth) {
            val halfHeight: Int = srcHeight / 2
            val halfWidth: Int = srcWidth / 2
            while (halfHeight / inSampleSize > maxHeight && halfWidth / inSampleSize > maxWidth) {
                inSampleSize *= 2
            }
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        return BitmapFactory.decodeFile(imageFile.path, options)
    }

    private fun compressImage(bitmap: Bitmap, outputFile: File) {
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
    }

    private fun processImage(file: File) {
        val resizedBitmap = resizeImage(file, 640, 480) // Resize to desired dimensions
        val compressedFile = File(file.parent, "compressed_${file.name}")
        compressImage(resizedBitmap, compressedFile)

        val roboflowService = RoboflowService(BuildConfig.ROBOFLOW_API_KEY)

        roboflowService.uploadImage(compressedFile, { inferenceResponse ->
            Log.d("ScanActivity", "Inference successful: $inferenceResponse")
            val detections = inferenceResponse.predictions.map {
                Detection(it.className, it.confidence)
            }

            // Remove duplicates based on className
            val uniqueDetections = detections.distinctBy { it.className }

            runOnUiThread {
                if (uniqueDetections.isNotEmpty()) {
                    detected = true
                    showDetectionListBottomSheet(uniqueDetections, compressedFile)
                } else {
                    detected = false
                    errorBottomSheet(file)
                }
            }
        }, { errorMessage ->
            Log.e("ScanActivity", errorMessage)
            runOnUiThread {
                detected = false
                errorBottomSheet(file)
            }
        })
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
                displayProductDetails(detection.className, imageFile)
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


    // Add a method to query the product details and show the bottom sheet
    private fun displayProductDetails(className: String, imageFile: File) {
        lifecycleScope.launch {
            val product = withContext(Dispatchers.IO) {
                val encodedClassName = URLEncoder.encode(className, "UTF-8")
                Log.d("ScanActivity", "Querying product details for: $encodedClassName")

                try {
                    // Querying Supabase
                    val response = try {
                            supabase.from("products").select(columns = Columns.list("productid", "prodname", "categoryid", "description", "ingredient")) {
                            filter {
                                products::prodname eq className
                                //or
                                eq("prodname", className)
                            }
                        }
                            .decodeList<products>()
                    } catch (e: Exception) {
                        Log.e("ScanActivity", "Error querying Supabase: ${e.message}", e)
                        emptyList<products>() // Return an empty list in case of error
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

            val stores = withContext(Dispatchers.IO) {
                try {
                    val response = try {
                        supabase.from("branchproducts").select(Columns.raw("""
                branchproductid,
                branchid,
                productid,
                price,
                stock,
                branches(branchname)
            """.trimIndent())) {
                            filter {
                                eq("productid", productid)
                            }
                        }.decodeList<branchproduct>()
                    } catch (e: Exception) {
                        Log.e("ScanActivity", "Error querying Supabase: ${e.message}", e)
                        emptyList<branchproduct>()
                    }

                    if (response.isNotEmpty()) {
                        Log.d("ScanActivity", "Products found: ${response.size}")
                        response.map {
                            Store(
                                storeName = it.branchid, // Adjust as necessary
                                price = it.price,
                                stock = it.stock,
                                branches = it.branches.branchname // Use the extracted field
                            )
                        }
                    } else {
                        Log.d("ScanActivity", "No products found in Supabase.")
                        emptyList()
                    }
                } catch (e: Exception) {
                    Log.e("ScanActivity", "Error querying Supabase: ${e.message}", e)
                    emptyList()
                }
            }



            runOnUiThread {
                showBottomSheet(imageFile, productName, description, ingredients, servingsize, amtofserving, calorie, carbohydrate, protein, fat, stores)
            }
        }
    }



    private fun showBottomSheet(imageFile: File, productName: String, description: String, ingredients: String, servingsize : String, amtofserving : Double, calorie : Double, carbohydrate : Double, protein : Double, fat : Double, stores : List<Store>?) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_result, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        val storeRecyclerView: RecyclerView = bottomSheetView.findViewById(R.id.storeRecyclerView)
        storeRecyclerView.layoutManager = LinearLayoutManager(this)
        val storeAdapter = StoreAdapter(stores)
        storeRecyclerView.adapter = storeAdapter
        storeRecyclerView.setHeightBasedOnChildren()


        val resultTextView = bottomSheetView.findViewById<TextView>(R.id.resultTextView)
        val descriptionTextView = bottomSheetView.findViewById<TextView>(R.id.descriptionTextView)
        val ingredientsTextView = bottomSheetView.findViewById<TextView>(R.id.ingredientsTextView)
        val nutritionalFactsDisplayTextView = bottomSheetView.findViewById<TextView>(R.id.nutritionalFactsDisplayTextView)
        val servingSizeTextView = bottomSheetView.findViewById<TextView>(R.id.servingSizeTextView)
        val amtOfServingTextView = bottomSheetView.findViewById<TextView>(R.id.amtOfServingTextView)
        val CalorieTextView = bottomSheetView.findViewById<TextView>(R.id.CalorieTextView)
        val carbohydrateTextView = bottomSheetView.findViewById<TextView>(R.id.carbohydrateTextView)
        val proteinTextView = bottomSheetView.findViewById<TextView>(R.id.proteinTextView)
        val fatTextView = bottomSheetView.findViewById<TextView>(R.id.fatTextView)
        val resultImageView = bottomSheetView.findViewById<ImageView>(R.id.resultImageView)
        val closeButton = bottomSheetView.findViewById<Button>(R.id.closeButton)
        val backButton: Button = bottomSheetView.findViewById(R.id.backButton)
        val speakerButton = bottomSheetView.findViewById<Button>(R.id.speakerButton)
        val downloadButton = bottomSheetView.findViewById<Button>(R.id.downloadButton)

        resultTextView.text = productName
        descriptionTextView.text = description
        ingredientsTextView.text = ingredients
        servingSizeTextView.text = "Serving Size: $servingsize"
        amtOfServingTextView.text = "Serving Amount: $amtofserving"
        CalorieTextView.text = "Calorie: $calorie"
        carbohydrateTextView.text = "Carbohydrate: $carbohydrate"
        proteinTextView.text = "Protein: $protein"
        fatTextView.text = "Fat $fat"

        downloadButton.visibility = View.GONE

        if(!detected){
            nutritionalFactsDisplayTextView.visibility = View.GONE
            servingSizeTextView.visibility = View.GONE
            amtOfServingTextView.visibility = View.GONE
            CalorieTextView.visibility = View.GONE
            carbohydrateTextView.visibility = View.GONE
            proteinTextView.visibility = View.GONE
            fatTextView.visibility = View.GONE
        }

        val bitmap = BitmapFactory.decodeFile(imageFile.path)
        val resizedBitmap = resizeImageForDisplay(bitmap, 800) // Adjust max width as needed
        resultImageView.setImageBitmap(resizedBitmap)

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
                val combinedText = "$productName. $description. Ingredients: $ingredients. Serving Size: $servingsize. Serving Amount: $amtofserving. Calorie: $calorie. carbohydrate: $carbohydrate. protein: $protein. fat: $fat."
                textToSpeech.speak(combinedText, TextToSpeech.QUEUE_FLUSH, null, null)
            }
            else if (ttsInitialized){
                val combinedText = "$productName. $description. $ingredients."
                textToSpeech.speak(combinedText, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        bottomSheetDialog.setOnDismissListener {
            if (this::textToSpeech.isInitialized && textToSpeech.isSpeaking) {
                textToSpeech.stop()
            }
            downloadButton.visibility = View.VISIBLE
        }

        bottomSheetDialog.show()
        val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let { sheet ->
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun errorBottomSheet(imageFile: File){
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_result, null)
        bottomSheetDialog.setContentView(bottomSheetView)


        val resultTextView = bottomSheetView.findViewById<TextView>(R.id.resultTextView)
        val descriptionDisplayTextView = bottomSheetView.findViewById<TextView>(R.id.descriptionDisplayTextView)
        val descriptionTextView = bottomSheetView.findViewById<TextView>(R.id.descriptionTextView)
        val ingredientDisplayTextView = bottomSheetView.findViewById<TextView>(R.id.ingredientsDisplayTextView)
        val storeDisplayTextView = bottomSheetView.findViewById<TextView>(R.id.storeDisplayTextView)
        val ingredientsTextView = bottomSheetView.findViewById<TextView>(R.id.ingredientsTextView)
        val nutritionalFactsDisplayTextView = bottomSheetView.findViewById<TextView>(R.id.nutritionalFactsDisplayTextView)
        val servingSizeTextView = bottomSheetView.findViewById<TextView>(R.id.servingSizeTextView)
        val amtOfServingTextView = bottomSheetView.findViewById<TextView>(R.id.amtOfServingTextView)
        val calorieTextView = bottomSheetView.findViewById<TextView>(R.id.CalorieTextView)
        val carbohydrateTextView = bottomSheetView.findViewById<TextView>(R.id.carbohydrateTextView)
        val proteinTextView = bottomSheetView.findViewById<TextView>(R.id.proteinTextView)
        val fatTextView = bottomSheetView.findViewById<TextView>(R.id.fatTextView)
        val resultImageView = bottomSheetView.findViewById<ImageView>(R.id.resultImageView)
        val closeButton = bottomSheetView.findViewById<Button>(R.id.closeButton)
        val backButton: Button = bottomSheetView.findViewById(R.id.backButton)
        val speakerButton = bottomSheetView.findViewById<Button>(R.id.speakerButton)
        val downloadButton = bottomSheetView.findViewById<Button>(R.id.downloadButton)

        resultTextView.text = "Product Not Found"
        descriptionDisplayTextView.text = "Please try again"


        downloadButton.visibility = View.GONE
        speakerButton.visibility = View.GONE
        descriptionTextView.visibility = View.GONE
        downloadButton.visibility = View.GONE
        storeDisplayTextView.visibility = View.GONE
        ingredientDisplayTextView.visibility = View.GONE
        ingredientsTextView.visibility = View.GONE
        backButton.visibility = View.GONE
        nutritionalFactsDisplayTextView.visibility = View.GONE
        servingSizeTextView.visibility = View.GONE
        amtOfServingTextView.visibility = View.GONE
        calorieTextView.visibility = View.GONE
        carbohydrateTextView.visibility = View.GONE
        proteinTextView.visibility = View.GONE
        fatTextView.visibility = View.GONE

        val bitmap = BitmapFactory.decodeFile(imageFile.path)
        val resizedBitmap = resizeImageForDisplay(bitmap, 800) // Adjust max width as needed
        resultImageView.setImageBitmap(resizedBitmap)

        closeButton.setOnClickListener {
            Log.d("MainActivity", "Close button clicked")
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setOnDismissListener {
            downloadButton.visibility = View.VISIBLE
        }

        bottomSheetDialog.show()
        val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let { sheet ->
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
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
