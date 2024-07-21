package com.example.scanapi

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "product_table")
data class Product(
    @PrimaryKey val name: String,
    val description: String,
    val nutritionalFacts: String
)
