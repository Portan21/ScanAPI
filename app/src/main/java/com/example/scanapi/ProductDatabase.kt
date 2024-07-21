package com.example.scanapi

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Product::class], version = 1, exportSchema = false)
abstract class ProductDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao

    companion object {
        @Volatile
        private var INSTANCE: ProductDatabase? = null

        fun getDatabase(context: Context): ProductDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ProductDatabase::class.java,
                    "product_database"
                )
                    //.addMigrations(MIGRATION_1_2)
                    .addCallback(ProductDatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

//        private val MIGRATION_1_2 = object : Migration(1, 2) {
//            override fun migrate(db: SupportSQLiteDatabase) {
//                db.execSQL("ALTER TABLE product_table ADD COLUMN category TEXT")
//            }
//        }
    }

    private class ProductDatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    populateDatabase(database.productDao())
                }
            }
        }

        suspend fun populateDatabase(productDao: ProductDao) {
            // Add initial products to the database
            val initialProducts = listOf(
                Product(
                    name = "Apple",
                    description = "A sweet, edible fruit produced by an apple tree.",
                    nutritionalFacts = "Calories: 95, Carbs: 25g, Fiber: 4g",
                ),
                Product(
                    name = "Banana",
                    description = "An elongated, edible fruit produced by several kinds of large herbaceous flowering plants.",
                    nutritionalFacts = "Calories: 105, Carbs: 27g, Fiber: 3g",
                ),
                Product(
                    name = "Datu Puti Soy Sauce 200mL",
                    description = "Soy sauce produced by Datu Puti. Toyo ni justine",
                    nutritionalFacts = "Calories: 10, Carbs: 1g, Protein: 2g",
                )
                // Add more products as needed
            )
            productDao.insertAll(*initialProducts.toTypedArray())
        }
    }
}