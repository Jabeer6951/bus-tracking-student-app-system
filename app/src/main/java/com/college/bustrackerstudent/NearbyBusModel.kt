package com.college.bustrackerstudent

class NearbyBusModel() {
    var busId: String? = null
    var busName: String? = null
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var distanceMeters: Double = 0.0

    constructor(
        busId: String?,
        busName: String?,
        latitude: Double,
        longitude: Double,
        distanceMeters: Double
    ) : this() {
        this.busId = busId
        this.busName = busName
        this.latitude = latitude
        this.longitude = longitude
        this.distanceMeters = distanceMeters
    }
}