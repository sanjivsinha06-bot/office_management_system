package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainRepository(
    private val employeeDao: EmployeeDao,
    private val attendanceDao: AttendanceDao,
    private val settingsDao: OfficeSettingsDao,
    private val auditLogDao: AuditLogDao,
    private val expenseDao: ExpenseDao,
    private val wfhRequestDao: WfhRequestDao
) {
    val allEmployees: Flow<List<Employee>> = employeeDao.getAllEmployees()
    val allAttendance: Flow<List<Attendance>> = attendanceDao.getAllAttendance()
    val settingsFlow: Flow<OfficeSettings?> = settingsDao.getSettingsFlow()
    val auditLogs: Flow<List<AuditLog>> = auditLogDao.getLogs()
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()
    val allWfhRequests: Flow<List<WfhRequest>> = wfhRequestDao.getAllWfhRequests()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true) // Important for Google Apps Script Web Apps
        .followSslRedirects(true)
        .build()

    // Ensure settings are initialized
    suspend fun initSettings() {
        withContext(Dispatchers.IO) {
            val current = settingsDao.getSettingsDirect()
            if (current == null) {
                settingsDao.insertOrUpdateSettings(OfficeSettings())
                logEvent("System", "Initialized default Office settings")
            } else {
                var migrated = current
                var updated = false

                if (!migrated.hideAdminButton) {
                    migrated = migrated.copy(hideAdminButton = true)
                    updated = true
                    logEvent("System", "Automatically hid Admin Dashboard by default")
                }

                if (migrated.officeLatitude == 37.4220 && migrated.officeLongitude == -122.0841) {
                    migrated = migrated.copy(officeLatitude = 23.6307, officeLongitude = 87.0937)
                    updated = true
                    logEvent("System", "Migrated default coordinates to 23.6307, 87.0937")
                }

                if (updated) {
                    settingsDao.insertOrUpdateSettings(migrated)
                }
            }
        }
    }

    suspend fun getSettingsDirect(): OfficeSettings {
        return withContext(Dispatchers.IO) {
            settingsDao.getSettingsDirect() ?: OfficeSettings()
        }
    }

    suspend fun updateSettings(settings: OfficeSettings) {
        withContext(Dispatchers.IO) {
            settingsDao.insertOrUpdateSettings(settings)
            logEvent("Admin", "Updated office settings: ${settings.officeName}")
        }
    }

    // --- Employee Actions ---
    suspend fun registerEmployee(
        fullName: String,
        phoneNumber: String,
        deviceId: String,
        selfieBase64: String,
        browserFingerprint: String
    ): Employee {
        return withContext(Dispatchers.IO) {
            val empId = "EMP-${UUID.randomUUID().toString().take(6).uppercase()}"
            val employee = Employee(
                employeeId = empId,
                fullName = fullName,
                phoneNumber = phoneNumber,
                deviceId = deviceId,
                selfieImageBase64 = selfieBase64,
                registrationDate = System.currentTimeMillis(),
                approvalStatus = "PENDING",
                lastLogin = System.currentTimeMillis(),
                activeStatus = true,
                browserFingerprint = browserFingerprint
            )
            employeeDao.insertEmployee(employee)
            logEvent("System", "Employee registered successfully: $fullName ($empId)", empId)
            employee
        }
    }

    suspend fun getEmployeeByDeviceId(deviceId: String): Employee? {
        return withContext(Dispatchers.IO) {
            employeeDao.getEmployeeByDeviceId(deviceId)
        }
    }

    suspend fun getEmployeeById(employeeId: String): Employee? {
        return withContext(Dispatchers.IO) {
            employeeDao.getEmployeeById(employeeId)
        }
    }

    suspend fun updateEmployeeApproval(employeeId: String, status: String) {
        withContext(Dispatchers.IO) {
            employeeDao.updateApprovalStatus(employeeId, status)
            logEvent("Admin", "Changed employee approval status of $employeeId to $status", employeeId)
        }
    }

    suspend fun updateEmployeeActive(employeeId: String, active: Boolean) {
        withContext(Dispatchers.IO) {
            employeeDao.updateActiveStatus(employeeId, active)
            logEvent("Admin", "Set employee active status of $employeeId to $active", employeeId)
        }
    }

    suspend fun deleteEmployee(employee: Employee) {
        withContext(Dispatchers.IO) {
            employeeDao.deleteEmployee(employee)
            logEvent("Admin", "Deleted employee: ${employee.fullName} (${employee.employeeId})", employee.employeeId)
        }
    }

    suspend fun updateEmployeeLastLogin(employeeId: String) {
        withContext(Dispatchers.IO) {
            employeeDao.updateLastLogin(employeeId, System.currentTimeMillis())
        }
    }

    // --- Attendance Actions ---
    fun getAttendanceForEmployee(employeeId: String): Flow<List<Attendance>> {
        return attendanceDao.getAttendanceForEmployee(employeeId)
    }

    suspend fun getTodayAttendanceForEmployee(employeeId: String, dateStr: String): Attendance? {
        return withContext(Dispatchers.IO) {
            attendanceDao.getTodayAttendanceForEmployee(employeeId, dateStr)
        }
    }

    suspend fun checkIn(
        employeeId: String,
        employeeName: String,
        dateStr: String,
        timeStr: String,
        lat: Double,
        lng: Double,
        status: String,
        selfieBase64: String? = null
    ): Attendance {
        return withContext(Dispatchers.IO) {
            val attendance = Attendance(
                employeeId = employeeId,
                employeeName = employeeName,
                date = dateStr,
                checkInTime = timeStr,
                checkInLatitude = lat,
                checkInLongitude = lng,
                attendanceStatus = status,
                checkInSelfieBase64 = selfieBase64
            )
            attendanceDao.insertAttendance(attendance)
            logEvent("Employee", "$employeeName checked in at $timeStr with selfie verification. Status: $status", employeeId)
            attendance
        }
    }

    suspend fun checkOut(
        attendance: Attendance,
        timeStr: String,
        lat: Double,
        lng: Double,
        workingHours: Double,
        selfieBase64: String? = null
    ) {
        withContext(Dispatchers.IO) {
            val updated = attendance.copy(
                checkOutTime = timeStr,
                checkOutLatitude = lat,
                checkOutLongitude = lng,
                workingHours = workingHours,
                checkOutSelfieBase64 = selfieBase64
            )
            attendanceDao.insertAttendance(updated)
            logEvent("Employee", "${attendance.employeeName} checked out at $timeStr with selfie verification. Hours: ${String.format("%.2f", workingHours)}", attendance.employeeId)
        }
    }

    // --- Logger ---
    suspend fun logEvent(action: String, details: String, employeeId: String? = null) {
        withContext(Dispatchers.IO) {
            auditLogDao.insertLog(AuditLog(action = action, details = details, employeeId = employeeId))
            Log.d("AuditLog", "[$action] $details")
        }
    }

    suspend fun addExpense(employeeId: String, employeeName: String, description: String, amount: Double) {
        withContext(Dispatchers.IO) {
            val expense = Expense(
                employeeId = employeeId,
                employeeName = employeeName,
                description = description,
                amount = amount
            )
            expenseDao.insertExpense(expense)
            logEvent("Expense Added", "Added expense '$description' (₹$amount)", employeeId)
        }
    }

    suspend fun updateExpenseStatus(expense: Expense, newStatus: String) {
        withContext(Dispatchers.IO) {
            val updated = expense.copy(status = newStatus)
            expenseDao.updateExpense(updated)
            logEvent("Expense Updated", "Status changed to $newStatus for expense '${expense.description}'", expense.employeeId)
        }
    }

    fun getExpensesForEmployee(employeeId: String): Flow<List<Expense>> {
        return expenseDao.getExpensesForEmployee(employeeId)
    }

    // --- WFH Actions ---
    suspend fun submitWfhRequest(employeeId: String, employeeName: String, date: String) {
        withContext(Dispatchers.IO) {
            val existing = wfhRequestDao.getWfhRequestForDate(employeeId, date)
            if (existing == null) {
                val request = WfhRequest(
                    employeeId = employeeId,
                    employeeName = employeeName,
                    date = date,
                    status = "PENDING"
                )
                wfhRequestDao.insertWfhRequest(request)
                logEvent("WFH Request", "Requested Work From Home for $date", employeeId)
            }
        }
    }

    suspend fun updateWfhStatus(requestId: Int, status: String) {
        withContext(Dispatchers.IO) {
            val all = wfhRequestDao.getAllWfhRequests().firstOrNull() ?: emptyList()
            val request = all.find { it.requestId == requestId }
            if (request != null) {
                val updated = request.copy(status = status)
                wfhRequestDao.updateWfhRequest(updated)
                logEvent("WFH Update", "WFH status changed to $status for ${request.employeeName} on ${request.date}", request.employeeId)
            }
        }
    }

    fun getWfhRequestsForEmployee(employeeId: String): Flow<List<WfhRequest>> {
        return wfhRequestDao.getWfhRequestsForEmployee(employeeId)
    }

    // --- Google Sheets Integration via Apps Script ---
    suspend fun syncWithGoogleSheets(): Result<String> {
        return withContext(Dispatchers.IO) {
            val settings = settingsDao.getSettingsDirect() ?: OfficeSettings()
            val scriptUrl = settings.googleAppsScriptUrl

            if (scriptUrl.isBlank()) {
                return@withContext Result.failure(Exception("Google Apps Script URL is empty. Configure it in Settings."))
            }

            try {
                // Fetch all local records to upload
                val employees = employeeDao.getAllEmployees().firstOrNull() ?: emptyList()
                val attendance = attendanceDao.getAllAttendance().firstOrNull() ?: emptyList()
                val expenses = expenseDao.getAllExpenses().firstOrNull() ?: emptyList()
                val wfhRequests = wfhRequestDao.getAllWfhRequests().firstOrNull() ?: emptyList()

                // Construct JSON payload
                val payload = JSONObject()
                payload.put("action", "sync")

                // Local settings info to synchronize
                val settingsJson = JSONObject()
                settingsJson.put("officeName", settings.officeName)
                settingsJson.put("officeLatitude", settings.officeLatitude)
                settingsJson.put("officeLongitude", settings.officeLongitude)
                settingsJson.put("allowedRadius", settings.allowedRadius)
                settingsJson.put("geoFencingEnabled", settings.geoFencingEnabled)
                payload.put("settings", settingsJson)

                // Employees list
                val employeesArray = JSONArray()
                for (emp in employees) {
                    val empObj = JSONObject()
                    empObj.put("employeeId", emp.employeeId)
                    empObj.put("fullName", emp.fullName)
                    empObj.put("phoneNumber", emp.phoneNumber)
                    empObj.put("deviceId", emp.deviceId)
                    empObj.put("selfieImageBase64", emp.selfieImageBase64)
                    empObj.put("registrationDate", emp.registrationDate)
                    empObj.put("approvalStatus", emp.approvalStatus)
                    empObj.put("lastLogin", emp.lastLogin)
                    empObj.put("activeStatus", emp.activeStatus)
                    empObj.put("browserFingerprint", emp.browserFingerprint)
                    employeesArray.put(empObj)
                }
                payload.put("employees", employeesArray)

                // Attendance list
                val attendanceArray = JSONArray()
                for (att in attendance) {
                    val attObj = JSONObject()
                    attObj.put("attendanceId", att.attendanceId)
                    attObj.put("employeeId", att.employeeId)
                    attObj.put("employeeName", att.employeeName)
                    attObj.put("date", att.date)
                    attObj.put("checkInTime", att.checkInTime)
                    attObj.put("checkInLatitude", att.checkInLatitude)
                    attObj.put("checkInLongitude", att.checkInLongitude)
                    attObj.put("checkOutTime", att.checkOutTime ?: "")
                    attObj.put("checkOutLatitude", att.checkOutLatitude ?: 0.0)
                    attObj.put("checkOutLongitude", att.checkOutLongitude ?: 0.0)
                    attObj.put("workingHours", att.workingHours)
                    attObj.put("attendanceStatus", att.attendanceStatus)
                    attendanceArray.put(attObj)
                }
                payload.put("attendance", attendanceArray)

                // Expenses list
                val expensesArray = JSONArray()
                for (exp in expenses) {
                    val expObj = JSONObject()
                    expObj.put("expenseId", exp.expenseId)
                    expObj.put("employeeId", exp.employeeId)
                    expObj.put("employeeName", exp.employeeName)
                    expObj.put("description", exp.description)
                    expObj.put("amount", exp.amount)
                    expObj.put("date", exp.date)
                    expObj.put("status", exp.status)
                    expensesArray.put(expObj)
                }
                payload.put("expenses", expensesArray)

                // WFH requests list
                val wfhArray = JSONArray()
                for (wfh in wfhRequests) {
                    val wfhObj = JSONObject()
                    wfhObj.put("requestId", wfh.requestId)
                    wfhObj.put("employeeId", wfh.employeeId)
                    wfhObj.put("employeeName", wfh.employeeName)
                    wfhObj.put("date", wfh.date)
                    wfhObj.put("timestamp", wfh.timestamp)
                    wfhObj.put("status", wfh.status)
                    wfhArray.put(wfhObj)
                }
                payload.put("wfhRequests", wfhArray)

                // Make the request
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = payload.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(scriptUrl)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Server returned error code: ${response.code}"))
                }

                val responseString = response.body?.string() ?: ""
                val responseJson = JSONObject(responseString)

                if (responseJson.optString("status") == "success") {
                    // Update local database with server settings or modifications if applicable
                    val updatedEmployees = responseJson.optJSONArray("employees")
                    if (updatedEmployees != null && updatedEmployees.length() > 0) {
                        for (i in 0 until updatedEmployees.length()) {
                            val empObj = updatedEmployees.getJSONObject(i)
                            val empId = empObj.getString("employeeId")
                            val localEmp = employeeDao.getEmployeeById(empId)
                            if (localEmp != null) {
                                // Sync approval status & active status from Sheet to local DB
                                val updatedEmp = localEmp.copy(
                                    approvalStatus = empObj.getString("approvalStatus"),
                                    activeStatus = empObj.getBoolean("activeStatus")
                                )
                                employeeDao.insertEmployee(updatedEmp)
                            } else {
                                // Insert newly registered from Sheet
                                val newEmp = Employee(
                                    employeeId = empId,
                                    fullName = empObj.getString("fullName"),
                                    phoneNumber = empObj.getString("phoneNumber"),
                                    deviceId = empObj.getString("deviceId"),
                                    selfieImageBase64 = empObj.optString("selfieImageBase64", ""),
                                    registrationDate = empObj.getLong("registrationDate"),
                                    approvalStatus = empObj.getString("approvalStatus"),
                                    lastLogin = empObj.optLong("lastLogin", System.currentTimeMillis()),
                                    activeStatus = empObj.getBoolean("activeStatus"),
                                    browserFingerprint = empObj.optString("browserFingerprint", "")
                                )
                                employeeDao.insertEmployee(newEmp)
                            }
                        }
                    }

                    // Update local expenses from server
                    val updatedExpenses = responseJson.optJSONArray("expenses")
                    if (updatedExpenses != null && updatedExpenses.length() > 0) {
                        for (i in 0 until updatedExpenses.length()) {
                            val expObj = updatedExpenses.getJSONObject(i)
                            val expId = expObj.optInt("expenseId")
                            if (expId > 0) {
                                val newStatus = expObj.optString("status")
                                val localExp = expenses.find { it.expenseId == expId }
                                if (localExp != null && newStatus.isNotBlank() && localExp.status != newStatus) {
                                    val updatedExp = localExp.copy(status = newStatus)
                                    expenseDao.updateExpense(updatedExp)
                                }
                            }
                        }
                    }

                    // Update local WFH requests from server
                    val updatedWfh = responseJson.optJSONArray("wfhRequests")
                    if (updatedWfh != null && updatedWfh.length() > 0) {
                        for (i in 0 until updatedWfh.length()) {
                            val wfhObj = updatedWfh.getJSONObject(i)
                            val requestId = wfhObj.optInt("requestId")
                            if (requestId > 0) {
                                val newStatus = wfhObj.optString("status")
                                val localWfh = wfhRequests.find { it.requestId == requestId }
                                if (localWfh != null && newStatus.isNotBlank() && localWfh.status != newStatus) {
                                    val updatedWfhReq = localWfh.copy(status = newStatus)
                                    wfhRequestDao.updateWfhRequest(updatedWfhReq)
                                }
                            }
                        }
                    }

                    val updatedSettings = responseJson.optJSONObject("settings")
                    if (updatedSettings != null) {
                        val newSettings = OfficeSettings(
                            officeName = updatedSettings.optString("officeName", settings.officeName),
                            officeLatitude = updatedSettings.optDouble("officeLatitude", settings.officeLatitude),
                            officeLongitude = updatedSettings.optDouble("officeLongitude", settings.officeLongitude),
                            allowedRadius = updatedSettings.optDouble("allowedRadius", settings.allowedRadius),
                            geoFencingEnabled = updatedSettings.optBoolean("geoFencingEnabled", settings.geoFencingEnabled),
                            googleAppsScriptUrl = scriptUrl,
                            adminPasscode = settings.adminPasscode,
                            hideAdminButton = settings.hideAdminButton
                        )
                        settingsDao.insertOrUpdateSettings(newSettings)
                    }

                    logEvent("Sync", "Successfully synced all data with Google Sheets")
                    Result.success("Sync complete! Updated data fetched from Google Sheets.")
                } else {
                    val errMsg = responseJson.optString("message", "Unknown error from server")
                    Result.failure(Exception("Sync failed: $errMsg"))
                }
            } catch (e: Exception) {
                Log.e("SyncError", "Exception during sync", e)
                Result.failure(e)
            }
        }
    }
}
