package com.college.bustrackerstudent

data class Bus(
    val busId: String = "",
    val busNumber: String = "",
    val routeName: String = "",
    val startLocation: String = "",
    val endLocation: String = "",
    val driverName: String = "",
    val driverPhone: Long = 0L,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val updatedAt: Long = 0L
)