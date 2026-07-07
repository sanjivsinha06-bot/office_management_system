package com.example.ui

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = MainRepository(
        database.employeeDao(),
        database.attendanceDao(),
        database.officeSettingsDao(),
        database.auditLogDao(),
        database.expenseDao(),
        database.wfhRequestDao()
    )

    // Flows from database
    val allEmployees: StateFlow<List<Employee>> = repository.allEmployees
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAttendance: StateFlow<List<Attendance>> = repository.allAttendance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExpenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWfhRequests: StateFlow<List<WfhRequest>> = repository.allWfhRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val officeSettings: StateFlow<OfficeSettings> = repository.settingsFlow
        .map { it ?: OfficeSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OfficeSettings())

    val auditLogs: StateFlow<List<AuditLog>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI States
    private val _currentDeviceId = MutableStateFlow("")
    val currentDeviceId: StateFlow<String> = _currentDeviceId.asStateFlow()
    val activeDeviceId: StateFlow<String> = _currentDeviceId.asStateFlow()

    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

    private val _gpsPermissionGranted = MutableStateFlow(false)
    val gpsPermissionGranted: StateFlow<Boolean> = _gpsPermissionGranted.asStateFlow()

    private val _gpsHardwareEnabled = MutableStateFlow(true)
    val gpsHardwareEnabled: StateFlow<Boolean> = _gpsHardwareEnabled.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        // Retrieve real device ID
        try {
            val androidId = Settings.Secure.getString(
                application.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "emulator_device_123"
            _currentDeviceId.value = androidId
        } catch (e: Exception) {
            _currentDeviceId.value = "dev_device_${System.currentTimeMillis().toString().takeLast(4)}"
        }

        viewModelScope.launch {
            repository.initSettings()
            seedDatabaseIfEmpty()
            // Check for updates automatically on start
            AppUpdater.checkForUpdate(false)
        }
    }

    // Current logged-in / active employee on this device
    val activeEmployee: StateFlow<Employee?> = combine(allEmployees, activeDeviceId) { employees, devId ->
        employees.firstOrNull { it.deviceId == devId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Registration Status helper
    val registrationStatus: StateFlow<RegStatus> = combine(activeEmployee, activeDeviceId, allEmployees) { emp, devId, list ->
        val registeredEmpWithDevice = list.firstOrNull { it.deviceId == devId }
        when {
            registeredEmpWithDevice == null -> RegStatus.NOT_REGISTERED
            registeredEmpWithDevice.approvalStatus == "PENDING" -> RegStatus.PENDING
            registeredEmpWithDevice.approvalStatus == "REJECTED" -> RegStatus.REJECTED
            !registeredEmpWithDevice.activeStatus -> RegStatus.DISABLED
            else -> RegStatus.APPROVED
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RegStatus.NOT_REGISTERED)

    // Today's attendance for active employee
    val todayAttendance: StateFlow<Attendance?> = combine(activeEmployee, allAttendance) { emp, attendanceList ->
        if (emp == null) null
        else {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            attendanceList.firstOrNull { it.employeeId == emp.employeeId && it.date == todayStr }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayWfhRequest: StateFlow<WfhRequest?> = combine(activeEmployee, allWfhRequests) { emp, wfhList ->
        if (emp == null) null
        else {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            wfhList.firstOrNull { it.employeeId == emp.employeeId && it.date == todayStr }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Distance calculation
    val distanceToOffice: StateFlow<Double?> = combine(currentLocation, officeSettings) { loc, settings ->
        if (loc == null) null
        else {
            calculateDistance(loc.first, loc.second, settings.officeLatitude, settings.officeLongitude)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isInsideGeoFence: StateFlow<Boolean> = combine(distanceToOffice, officeSettings) { dist, settings ->
        if (!settings.geoFencingEnabled) true
        else if (dist == null) false
        else dist <= settings.allowedRadius
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- Location and GPS Helpers ---
    fun setGpsPermissionGranted(granted: Boolean) {
        _gpsPermissionGranted.value = granted
        if (!granted && !_isSimulatingLocation.value) {
            _currentLocation.value = null
        }
    }

    fun setGpsHardwareEnabled(enabled: Boolean) {
        _gpsHardwareEnabled.value = enabled
        if (!enabled && !_isSimulatingLocation.value) {
            _currentLocation.value = null
        }
    }

    private val _isSimulatingLocation = MutableStateFlow(false)
    val isSimulatingLocation: StateFlow<Boolean> = _isSimulatingLocation.asStateFlow()

    fun toggleLocationSimulation() {
        _isSimulatingLocation.value = !_isSimulatingLocation.value
        if (_isSimulatingLocation.value) {
            val settings = officeSettings.value
            _currentLocation.value = Pair(settings.officeLatitude, settings.officeLongitude)
        } else {
            _currentLocation.value = null
        }
    }

    fun updateLocation(lat: Double, lng: Double) {
        if (!_isSimulatingLocation.value) {
            _currentLocation.value = Pair(lat, lng)
        }
    }

    // --- Employee Core Registration Action ---
    fun registerNewEmployee(
        fullName: String,
        phoneNumber: String,
        selfieBase64: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val devId = activeDeviceId.value
            val fingerprint = "Browser: Chrome Mobile, Android OS, Fingerprint: " +
                    android.util.Base64.encodeToString(fullName.take(3).toByteArray(), android.util.Base64.NO_WRAP).take(8).uppercase(Locale.getDefault())

            repository.registerEmployee(
                fullName = fullName,
                phoneNumber = phoneNumber,
                deviceId = devId,
                selfieBase64 = selfieBase64,
                browserFingerprint = fingerprint
            )
            onComplete()
        }
    }

    // --- Attendance Check-in / Check-out ---
    fun checkInEmployee(selfieBase64: String? = null) {
        val emp = activeEmployee.value ?: return
        val loc = currentLocation.value ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        viewModelScope.launch {
            // Check-in location validation
            val settings = officeSettings.value
            val dist = distanceToOffice.value ?: Double.MAX_VALUE
            val status = if (settings.geoFencingEnabled && dist > settings.allowedRadius) {
                repository.logEvent("Warning", "Check-in attempted outside geofence for ${emp.fullName}", emp.employeeId)
                return@launch
            } else {
                // Let's mark as Late if checked in after 09:00 AM
                val now = Calendar.getInstance()
                val nineAM = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 9)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                if (now.after(nineAM)) "Late" else "Present"
            }

            repository.checkIn(
                employeeId = emp.employeeId,
                employeeName = emp.fullName,
                dateStr = todayStr,
                timeStr = timeStr,
                lat = loc.first,
                lng = loc.second,
                status = status,
                selfieBase64 = selfieBase64
            )
            repository.updateEmployeeLastLogin(emp.employeeId)
        }
    }

    fun checkOutEmployee(selfieBase64: String? = null) {
        val emp = activeEmployee.value ?: return
        val loc = currentLocation.value ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        viewModelScope.launch {
            val todayRecord = repository.getTodayAttendanceForEmployee(emp.employeeId, todayStr) ?: return@launch
            if (todayRecord.checkOutTime != null) return@launch // Already checked out

            // Calculate working hours
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val inTime = sdf.parse(todayRecord.checkInTime)
            val outTime = sdf.parse(timeStr)
            var hours = 0.0
            if (inTime != null && outTime != null) {
                val diffMs = outTime.time - inTime.time
                hours = diffMs.toDouble() / (1000 * 60 * 60)
                if (hours < 0) hours = 0.0
            }

            repository.checkOut(
                attendance = todayRecord,
                timeStr = timeStr,
                lat = loc.first,
                lng = loc.second,
                workingHours = hours,
                selfieBase64 = selfieBase64
            )
        }
    }

    // --- Admin Operations ---
    fun approveEmployee(employeeId: String) {
        viewModelScope.launch {
            repository.updateEmployeeApproval(employeeId, "APPROVED")
        }
    }

    fun rejectEmployee(employeeId: String) {
        viewModelScope.launch {
            repository.updateEmployeeApproval(employeeId, "REJECTED")
        }
    }

    fun enableEmployee(employeeId: String) {
        viewModelScope.launch {
            repository.updateEmployeeActive(employeeId, true)
        }
    }

    fun disableEmployee(employeeId: String) {
        viewModelScope.launch {
            repository.updateEmployeeActive(employeeId, false)
        }
    }

    fun deleteEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.deleteEmployee(employee)
        }
    }

    fun updateSettings(
        officeName: String,
        latitude: Double,
        longitude: Double,
        radius: Double,
        geoFencing: Boolean,
        gasUrl: String,
        adminPasscode: String,
        hideAdminButton: Boolean
    ) {
        viewModelScope.launch {
            val updated = OfficeSettings(
                id = 1,
                officeName = officeName,
                officeLatitude = latitude,
                officeLongitude = longitude,
                allowedRadius = radius,
                geoFencingEnabled = geoFencing,
                googleAppsScriptUrl = gasUrl,
                adminPasscode = adminPasscode,
                hideAdminButton = hideAdminButton
            )
            repository.updateSettings(updated)
        }
    }

    fun syncWithSheets() {
        viewModelScope.launch {
            _syncing.value = true
            _syncMessage.value = "Starting Sheet sync..."
            val result = repository.syncWithGoogleSheets()
            _syncing.value = false
            _syncMessage.value = result.fold(
                onSuccess = { it },
                onFailure = { it.message ?: "Failed to sync." }
            )
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun submitExpense(description: String, amount: Double) {
        viewModelScope.launch {
            val empId = _currentDeviceId.value
            val employee = allEmployees.value.find { it.deviceId == empId }
            if (employee != null) {
                repository.addExpense(employee.employeeId, employee.fullName, description, amount)
            }
        }
    }

    fun updateExpenseStatus(expense: Expense, newStatus: String) {
        viewModelScope.launch {
            repository.updateExpenseStatus(expense, newStatus)
        }
    }

    fun addManualAuditLog(action: String, details: String) {
        viewModelScope.launch {
            repository.logEvent(action, details)
        }
    }

    fun submitWfhRequest() {
        val emp = activeEmployee.value ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        viewModelScope.launch {
            repository.submitWfhRequest(emp.employeeId, emp.fullName, todayStr)
        }
    }

    fun updateWfhStatus(requestId: Int, status: String) {
        viewModelScope.launch {
            repository.updateWfhStatus(requestId, status)
        }
    }

    // --- Haversine Distance Helper ---
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    // --- Seeding Database for Polished Out-Of-The-Box UX ---
    private suspend fun seedDatabaseIfEmpty() {
        withContext(Dispatchers.IO) {
            val demoIds = listOf("EMP-928374", "EMP-104928", "EMP-573921")
            var deletedCount = 0
            for (id in demoIds) {
                val emp = database.employeeDao().getEmployeeById(id)
                if (emp != null) {
                    database.employeeDao().deleteEmployee(emp)
                    deletedCount++
                }
            }
            if (deletedCount > 0) {
                repository.logEvent("System", "Cleaned up $deletedCount default demo employees from database")
            }
        }
    }
}

enum class RegStatus {
    NOT_REGISTERED,
    PENDING,
    APPROVED,
    REJECTED,
    DISABLED
}
