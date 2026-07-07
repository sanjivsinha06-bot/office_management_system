package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees ORDER BY registrationDate DESC")
    fun getAllEmployees(): Flow<List<Employee>>

    @Query("SELECT * FROM employees WHERE employeeId = :employeeId")
    suspend fun getEmployeeById(employeeId: String): Employee?

    @Query("SELECT * FROM employees WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getEmployeeByDeviceId(deviceId: String): Employee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee)

    @Update
    suspend fun updateEmployee(employee: Employee)

    @Query("UPDATE employees SET approvalStatus = :status WHERE employeeId = :employeeId")
    suspend fun updateApprovalStatus(employeeId: String, status: String)

    @Query("UPDATE employees SET activeStatus = :active WHERE employeeId = :employeeId")
    suspend fun updateActiveStatus(employeeId: String, active: Boolean)

    @Query("UPDATE employees SET lastLogin = :timestamp WHERE employeeId = :employeeId")
    suspend fun updateLastLogin(employeeId: String, timestamp: Long)

    @Delete
    suspend fun deleteEmployee(employee: Employee)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance ORDER BY date DESC, checkInTime DESC")
    fun getAllAttendance(): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE employeeId = :employeeId ORDER BY date DESC, checkInTime DESC")
    fun getAttendanceForEmployee(employeeId: String): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE employeeId = :employeeId AND date = :date LIMIT 1")
    suspend fun getTodayAttendanceForEmployee(employeeId: String, date: String): Attendance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: Attendance)

    @Update
    suspend fun updateAttendance(attendance: Attendance)
}

@Dao
interface OfficeSettingsDao {
    @Query("SELECT * FROM office_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<OfficeSettings?>

    @Query("SELECT * FROM office_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): OfficeSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: OfficeSettings)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE employeeId = :employeeId ORDER BY date DESC")
    fun getExpensesForEmployee(employeeId: String): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)
    
    @Update
    suspend fun updateExpense(expense: Expense)
}

@Dao
interface WfhRequestDao {
    @Query("SELECT * FROM wfh_requests ORDER BY timestamp DESC")
    fun getAllWfhRequests(): Flow<List<WfhRequest>>

    @Query("SELECT * FROM wfh_requests WHERE employeeId = :employeeId ORDER BY timestamp DESC")
    fun getWfhRequestsForEmployee(employeeId: String): Flow<List<WfhRequest>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWfhRequest(request: WfhRequest)

    @Update
    suspend fun updateWfhRequest(request: WfhRequest)

    @Query("SELECT * FROM wfh_requests WHERE employeeId = :employeeId AND date = :date LIMIT 1")
    suspend fun getWfhRequestForDate(employeeId: String, date: String): WfhRequest?
}
