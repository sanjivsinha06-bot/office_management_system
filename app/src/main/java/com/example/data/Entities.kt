package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey val employeeId: String,
    val fullName: String,
    val phoneNumber: String,
    val deviceId: String,
    val selfieImageBase64: String, // Stored as Base64 string for offline capability
    val registrationDate: Long,
    val approvalStatus: String, // "PENDING", "APPROVED", "REJECTED"
    val lastLogin: Long,
    val activeStatus: Boolean,
    val browserFingerprint: String
)

@Entity(tableName = "attendance")
data class Attendance(
    @PrimaryKey(autoGenerate = true) val attendanceId: Int = 0,
    val employeeId: String,
    val employeeName: String,
    val date: String, // "yyyy-MM-dd"
    val checkInTime: String, // "HH:mm:ss"
    val checkInLatitude: Double,
    val checkInLongitude: Double,
    val checkOutTime: String? = null, // "HH:mm:ss"
    val checkOutLatitude: Double? = null,
    val checkOutLongitude: Double? = null,
    val workingHours: Double = 0.0,
    val attendanceStatus: String, // "Present", "Late", "Absent"
    val checkInSelfieBase64: String? = null,
    val checkOutSelfieBase64: String? = null
)

@Entity(tableName = "office_settings")
data class OfficeSettings(
    @PrimaryKey val id: Int = 1,
    val officeName: String = "Corporate HQ",
    val officeLatitude: Double = 23.6307,
    val officeLongitude: Double = 87.0937,
    val allowedRadius: Double = 10.0, // in meters
    val geoFencingEnabled: Boolean = true,
    val googleAppsScriptUrl: String = "", // For syncing to Google Sheets
    val adminPasscode: String = "1234",
    val hideAdminButton: Boolean = true
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val action: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis(),
    val employeeId: String? = null
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val expenseId: Int = 0,
    val employeeId: String,
    val employeeName: String,
    val description: String,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val status: String = "PENDING"
)

@Entity(tableName = "wfh_requests")
data class WfhRequest(
    @PrimaryKey(autoGenerate = true) val requestId: Int = 0,
    val employeeId: String,
    val employeeName: String,
    val date: String, // "yyyy-MM-dd"
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING" // "PENDING", "APPROVED", "REJECTED"
)
