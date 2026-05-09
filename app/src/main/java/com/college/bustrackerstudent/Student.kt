package com.college.bustrackerstudent

data class Student(
    var uid: String = "",
    var name: String = "",
    var email: String = "",
    var rollNumber: String = "",
    var selectedBusId: String = "",
    var createdAt: Long = 0L
)