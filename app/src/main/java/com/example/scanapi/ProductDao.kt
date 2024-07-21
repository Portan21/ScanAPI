package com.example.scanapi

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ProductDao {
    @Query("SELECT * FROM product_table WHERE name = :name LIMIT 1")
    suspend fun getProductByName(name: String): Product?
}