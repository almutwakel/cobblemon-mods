package com.cobblemonmarket.data

data class Transaction(
    val type: String, // "buy" or "sell"
    val timestamp: Long
)

data class ItemState(
    var priceFactor: Double = 1.0,
    val transactions: MutableList<Transaction> = mutableListOf()
)
