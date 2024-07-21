package com.example.scanapi

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProductDao {
    @Query("SELECT * FROM product_table WHERE name = :name LIMIT 1")
    suspend fun getProductByName(name: String): Product?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(vararg products: Product)
}