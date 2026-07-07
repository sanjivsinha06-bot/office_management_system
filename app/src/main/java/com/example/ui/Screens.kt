package com.example.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.OutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Attendance
import com.example.data.Employee
import com.example.data.OfficeSettings
import java.text.SimpleDateFormat
import java.util.*

enum class AppRole {
    PORTAL_CHOOSER,
    EMPLOYEE,
    ADMIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeAppMain(viewModel: MainViewModel) {
    var activeRole by remember { mutableStateOf(AppRole.PORTAL_CHOOSER) }
    val activeEmployee by viewModel.activeEmployee.collectAsState()
    val regStatus by viewModel.registrationStatus.collectAsState()
    val gpsGranted by viewModel.gpsPermissionGranted.collectAsState()
    val officeSettings by viewModel.officeSettings.collectAsState()

    val context = LocalContext.current

    // Setup GPS Location Poller & Permissions
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setGpsPermissionGranted(granted)
        if (granted) {
            triggerLocationFetch(context, viewModel)
        }
    }

    LaunchedEffect(Unit) {
        // Initial permission and location state check
        val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        viewModel.setGpsPermissionGranted(hasPermission)

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        viewModel.setGpsHardwareEnabled(isGpsOn)

        if (hasPermission) {
            triggerLocationFetch(context, viewModel)
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                viewModel.setGpsPermissionGranted(hasPermission)

                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val isGpsOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                viewModel.setGpsHardwareEnabled(isGpsOn)

                if (hasPermission && isGpsOn) {
                    triggerLocationFetch(context, viewModel)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CorporateFare,
                            contentDescription = "Corporate Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Office Portal",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    if (activeRole != AppRole.PORTAL_CHOOSER) {
                        IconButton(
                            onClick = { activeRole = AppRole.PORTAL_CHOOSER },
                            modifier = Modifier.testTag("home_nav_button")
                        ) {
                            Icon(imageVector = Icons.Filled.Home, contentDescription = "Return to Portal Chooser")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AppUpdateBanner()

            AnimatedContent(
                targetState = activeRole,
                transitionSpec = {
                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                },
                label = "RoleTransition"
            ) { role ->
                when (role) {
                    AppRole.PORTAL_CHOOSER -> PortalChooserScreen(
                        viewModel = viewModel,
                        onSelectEmployee = { activeRole = AppRole.EMPLOYEE },
                        onSelectAdmin = { activeRole = AppRole.ADMIN }
                    )
                    AppRole.EMPLOYEE -> EmployeePortalSection(viewModel)
                    AppRole.ADMIN -> AdminDashboardSection(viewModel)
                }
            }
        }
    }
}

fun triggerLocationFetch(context: Context, viewModel: MainViewModel) {
    val settings = viewModel.officeSettings.value

    // Otherwise, fetch real device GPS location!
    try {
        val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            Toast.makeText(context, "Location permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // Check if GPS/Network is enabled
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        
        viewModel.setGpsHardwareEnabled(isGpsEnabled || isNetworkEnabled)
        
        if (!isGpsEnabled && !isNetworkEnabled) {
            Toast.makeText(context, "Please turn on device location/GPS services", Toast.LENGTH_LONG).show()
            return
        }
        
        // 1. Try last known location first for instantaneous responsiveness
        var bestLocation: android.location.Location? = null
        val providers = locationManager.getProviders(true)
        for (prov in providers) {
            val loc = locationManager.getLastKnownLocation(prov) ?: continue
            if (bestLocation == null || loc.time > bestLocation!!.time) {
                bestLocation = loc
            }
        }
        
        if (bestLocation != null) {
            viewModel.updateLocation(bestLocation.latitude, bestLocation.longitude)
            android.util.Log.d("LocationFetch", "Last known location: ${bestLocation.latitude}, ${bestLocation.longitude}")
        } else {
            // No last known location. Do not set fallback to prevent fake office status on startup.
            android.util.Log.d("LocationFetch", "No last known location. Waiting for live GPS signal.")
        }
        
        // 2. Request a fresh precise location update
        val providerToUse = when {
            isGpsEnabled -> LocationManager.GPS_PROVIDER
            isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        
        if (providerToUse != null) {
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    viewModel.updateLocation(location.latitude, location.longitude)
                    android.util.Log.d("LocationFetch", "Fresh location update: ${location.latitude}, ${location.longitude}")
                    // Stop listening after we get a fresh high-accuracy location
                    locationManager.removeUpdates(this)
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }
            
            // Request on main thread
            locationManager.requestLocationUpdates(
                providerToUse,
                0L,
                0f,
                listener,
                context.mainLooper
            )
        }
        
        Toast.makeText(context, "GPS Location active", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error fetching location: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PortalChooserScreen(
    viewModel: MainViewModel,
    onSelectEmployee: () -> Unit,
    onSelectAdmin: () -> Unit
) {
    val settings by viewModel.officeSettings.collectAsState()
    val context = LocalContext.current

    var secretTapCount by remember { mutableStateOf(0) }
    var showHiddenAdmin by remember { mutableStateOf(false) }
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var enteredPasscode by remember { mutableStateOf("") }
    var passcodeError by remember { mutableStateOf(false) }

    val adminVisible = !settings.hideAdminButton || showHiddenAdmin

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Clickable Header Area (Tap 5 times anywhere here to reveal hidden Admin Dashboard)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    secretTapCount++
                    if (secretTapCount >= 5) {
                        showHiddenAdmin = !showHiddenAdmin
                        secretTapCount = 0
                        val statusText = if (showHiddenAdmin) "revealed" else "hidden"
                        Toast.makeText(context, "Developer shortcut: Admin Portal $statusText!", Toast.LENGTH_SHORT).show()
                    }
                }
        ) {
            // Hero Image/Illustration (using a styled canvas)
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CorporateFare,
                    contentDescription = "Office Portal Illustration",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Corporate Attendance & Management",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Mobile-first geofenced attendance system secured by local hardware authentication.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Employee Access Button
        Button(
            onClick = onSelectEmployee,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag("employee_portal_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Badge,
                    contentDescription = "Employee icon",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text("Employee Portal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Check-In, View Profile, Logs", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        if (adminVisible) {
            Spacer(modifier = Modifier.height(16.dp))

            // Admin Access Button
            OutlinedButton(
                onClick = {
                    if (settings.adminPasscode.isEmpty()) {
                        onSelectAdmin()
                    } else {
                        enteredPasscode = ""
                        passcodeError = false
                        showPasscodeDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("admin_portal_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AdminPanelSettings,
                        contentDescription = "Admin icon",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("Admin Dashboard", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Manage Staff, Approvals, Configs", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Manual Update Check Button
        Button(
            onClick = { AppUpdater.checkForUpdate(true, context) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Check for App Updates", fontWeight = FontWeight.Bold)
        }
    }

    if (showPasscodeDialog) {
        AlertDialog(
            onDismissRequest = { showPasscodeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AdminPanelSettings,
                        contentDescription = "Admin Security",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Admin Verification", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Please enter the Administrator Passcode to unlock the dashboard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = enteredPasscode,
                        onValueChange = { 
                            enteredPasscode = it
                            passcodeError = false
                        },
                        label = { Text("Passcode") },
                        placeholder = { Text("Enter PIN") },
                        isError = passcodeError,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (passcodeError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Incorrect passcode. Please try again.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (enteredPasscode == settings.adminPasscode) {
                            showPasscodeDialog = false
                            onSelectAdmin()
                        } else {
                            passcodeError = true
                        }
                    }
                ) {
                    Text("Verify & Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasscodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmployeePortalSection(viewModel: MainViewModel) {
    val regStatus by viewModel.registrationStatus.collectAsState()

    AnimatedContent(
        targetState = regStatus,
        label = "EmployeeScreenTransition"
    ) { status ->
        when (status) {
            RegStatus.NOT_REGISTERED -> EmployeeRegistrationScreen(viewModel)
            RegStatus.PENDING -> EmployeePendingApprovalScreen(viewModel)
            RegStatus.REJECTED -> EmployeeRejectedScreen(viewModel)
            RegStatus.DISABLED -> EmployeeDisabledScreen(viewModel)
            RegStatus.APPROVED -> EmployeeDashboardScreen(viewModel)
        }
    }
}

@Composable
fun EmployeeRegistrationScreen(viewModel: MainViewModel) {
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var selfieBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Selfie Capture Launcher
    val selfieLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            selfieBitmap = bitmap
            Toast.makeText(context, "Selfie captured successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission Launcher for Camera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            selfieLauncher.launch()
        } else {
            Toast.makeText(context, "Camera permission required to capture selfie", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Device Registration",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Register your device to gain secure access to the Corporate Attendance platform.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Full Name TextField
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name") },
            placeholder = { Text("Enter your official full name") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reg_name_input"),
            leadingIcon = { Icon(imageVector = Icons.Filled.Person, contentDescription = "Person Icon") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Phone Number TextField
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number") },
            placeholder = { Text("+1 (555) 000-0000") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reg_phone_input"),
            leadingIcon = { Icon(imageVector = Icons.Filled.Phone, contentDescription = "Phone Icon") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Selfie Capture Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Selfie Identity Verification",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (selfieBitmap != null) {
                    Image(
                        bitmap = selfieBitmap!!.asImageBitmap(),
                        contentDescription = "Captured Selfie Thumbnail",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(60.dp))
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }) {
                        Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = "Recapture Icon")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retake Selfie")
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                            .clickable {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = "Photo Camera Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Snap a live selfie. This is required for secure face verification.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Submit Button
        Button(
            onClick = {
                if (fullName.isBlank() || phoneNumber.isBlank()) {
                    Toast.makeText(context, "Please complete all fields.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (selfieBitmap == null) {
                    Toast.makeText(context, "Please snap a selfie for identification.", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                loading = true
                val selfieBase64 = ImageUtils.bitmapToBase64(selfieBitmap!!)
                viewModel.registerNewEmployee(fullName, phoneNumber, selfieBase64) {
                    loading = false
                    Toast.makeText(context, "Registration submitted successfully!", Toast.LENGTH_LONG).show()
                }
            },
            enabled = !loading && fullName.isNotBlank() && phoneNumber.isNotBlank() && selfieBitmap != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("submit_registration_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Register Device", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun EmployeePendingApprovalScreen(viewModel: MainViewModel) {
    val activeEmployee by viewModel.activeEmployee.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFFFFB300).copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.HourglassEmpty,
                contentDescription = "Pending Approval Icon",
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Registration Pending",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFFFB300)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your device registration is pending approval from the administrator.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Submitted Employee Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Name: ${activeEmployee?.fullName ?: "N/A"}", fontSize = 13.sp)
                Text("Phone: ${activeEmployee?.phoneNumber ?: "N/A"}", fontSize = 13.sp)
                Text("Employee ID: ${activeEmployee?.employeeId ?: "N/A"}", fontSize = 13.sp)
                Text("Device Key: ${activeEmployee?.deviceId ?: "N/A"}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Once approved, this page will automatically unlock. Contact your administrator if you need urgent approval.",
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun EmployeeRejectedScreen(viewModel: MainViewModel) {
    val activeEmployee by viewModel.activeEmployee.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFFE53935).copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "Rejected Icon",
                tint = Color(0xFFE53935),
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Device Rejected",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFE53935)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your device has not been approved by the administrator.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Device Lock Details:\n${activeEmployee?.fullName ?: "Unknown User"} - ${activeEmployee?.deviceId ?: "N/A"}",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun EmployeeDisabledScreen(viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Block,
            contentDescription = "Disabled Employee Icon",
            tint = Color(0xFFD32F2F),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Account Suspended",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFD32F2F)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This employee account has been temporarily disabled by the administrator. Please contact human resources.",
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun EmployeeDashboardScreen(viewModel: MainViewModel) {
    val employee by viewModel.activeEmployee.collectAsState()
    val todayAttendance by viewModel.todayAttendance.collectAsState()
    val attendanceHistory by viewModel.allAttendance.collectAsState()
    val settings by viewModel.officeSettings.collectAsState()
    val gpsGranted by viewModel.gpsPermissionGranted.collectAsState()
    val gpsHardwareEnabled by viewModel.gpsHardwareEnabled.collectAsState()
    val distToOffice by viewModel.distanceToOffice.collectAsState()
    val insideGeofence by viewModel.isInsideGeoFence.collectAsState()
    val todayWfhRequest by viewModel.todayWfhRequest.collectAsState()

    var showWfhConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, gpsGranted, gpsHardwareEnabled) {
        if (!gpsGranted || !gpsHardwareEnabled) {
            onDispose {}
        } else {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    viewModel.updateLocation(location.latitude, location.longitude)
                    android.util.Log.d("GeofenceLocation", "Dashboard Location updated: ${location.latitude}, ${location.longitude}")
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }
            
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        4000L, // 4 seconds
                        1f,    // 1 meter
                        listener,
                        context.mainLooper
                    )
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        4000L,
                        1f,
                        listener,
                        context.mainLooper
                    )
                }
                
                // Fetch immediately on entry
                triggerLocationFetch(context, viewModel)
            } catch (e: SecurityException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            onDispose {
                try {
                    locationManager.removeUpdates(listener)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val empHistory = remember(attendanceHistory, employee) {
        attendanceHistory.filter { it.employeeId == employee?.employeeId }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setGpsPermissionGranted(granted)
        if (granted) {
            triggerLocationFetch(context, viewModel)
        }
    }

    var attendanceSelfieBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val attendanceSelfieLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            attendanceSelfieBitmap = bitmap
            Toast.makeText(context, "Attendance selfie captured successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    val dashboardCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            attendanceSelfieLauncher.launch()
        } else {
            Toast.makeText(context, "Camera permission is required to take an attendance selfie.", Toast.LENGTH_LONG).show()
        }
    }

    var employeeTab by remember { mutableStateOf("DASHBOARD") }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (employeeTab == "DASHBOARD") 0 else 1) {
            Tab(selected = employeeTab == "DASHBOARD", onClick = { employeeTab = "DASHBOARD" }) {
                Text("Dashboard", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = employeeTab == "EXPENSES", onClick = { employeeTab = "EXPENSES" }) {
                Text("Expenses", modifier = Modifier.padding(16.dp))
            }
        }

        if (employeeTab == "EXPENSES") {
            EmployeeExpensesTab(viewModel)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
        // Welcome Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bitmap = remember(employee?.selfieImageBase64) {
                        employee?.selfieImageBase64?.let { ImageUtils.base64ToBitmap(it) }
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Profile Photo",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Profile Placeholder",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Welcome, ${employee?.fullName ?: "Staff"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = "ID: ${employee?.employeeId ?: "N/A"}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Today's Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Today's Attendance Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Check-In", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(
                                text = todayAttendance?.checkInTime ?: "--:--:--",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Column {
                            Text("Check-Out", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(
                                text = todayAttendance?.checkOutTime ?: "--:--:--",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Column {
                            Text("Working Hours", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(
                                text = if (todayAttendance?.checkOutTime != null) "${String.format("%.2f", todayAttendance?.workingHours)} hrs" else "Active",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    if (todayAttendance != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    if (todayAttendance?.attendanceStatus == "Present") Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (todayAttendance?.attendanceStatus == "Present") Color(0xFF4CAF50) else Color(0xFFFF9800),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = todayAttendance?.attendanceStatus ?: "Logged",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (todayAttendance?.attendanceStatus == "Present") Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                        }
                    }
                }
            }
        }

        // Work From Home (WFH) Action Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Work From Home (WFH)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Request WFH for today",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        if (todayWfhRequest != null) {
                            val wfhStatusText = when (todayWfhRequest?.status) {
                                "PENDING" -> "WFH Pending"
                                "APPROVED" -> "WFH Approved"
                                "REJECTED" -> "WFH Rejected"
                                else -> "Requested"
                            }
                            val wfhColor = when (todayWfhRequest?.status) {
                                "PENDING" -> Color(0xFFFF9800)
                                "APPROVED" -> Color(0xFF4CAF50)
                                "REJECTED" -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Box(
                                modifier = Modifier
                                    .background(wfhColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .border(1.dp, wfhColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = wfhStatusText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = wfhColor
                                )
                            }
                        } else {
                            Button(
                                onClick = { showWfhConfirm = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("wfh_request_button")
                            ) {
                                Icon(imageVector = Icons.Filled.HomeWork, contentDescription = "WFH icon", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Request WFH")
                            }
                        }
                    }
                }
            }
        }

        // GPS Mandated Verification & Geofencing Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mandatory Location Check",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!gpsGranted || !gpsHardwareEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFEBEE), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    imageVector = Icons.Filled.GpsOff,
                                    contentDescription = "GPS disabled",
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (!gpsGranted) {
                                        "Location permission is required to use this application. Please grant access."
                                    } else {
                                        "Device GPS hardware or location services are turned off. Please turn them on."
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD32F2F),
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        if (gpsGranted && !gpsHardwareEnabled) {
                                            try {
                                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open location settings", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                    modifier = Modifier.testTag("enable_gps_button")
                                ) {
                                    Text(
                                        text = if (gpsGranted && !gpsHardwareEnabled) "Turn On GPS Services" else "Enable Location Permission"
                                    )
                                }
                            }
                        }
                    } else {
                        // GPS is active. Check Geo-fence
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Office: ${settings.officeName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Target: (${String.format("%.4f", settings.officeLatitude)}, ${String.format("%.4f", settings.officeLongitude)})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "Allowed Radius: ${settings.allowedRadius} meters",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            if (settings.geoFencingEnabled) {
                                val statusText = when {
                                    distToOffice == null -> "Locating..."
                                    insideGeofence -> "Inside Premises"
                                    else -> "Outside Premises"
                                }
                                val statusColor = when {
                                    distToOffice == null -> Color(0xFFE65100)
                                    insideGeofence -> Color(0xFF2E7D32)
                                    else -> Color(0xFFC62828)
                                }
                                val statusBgColor = when {
                                    distToOffice == null -> Color(0xFFFFF3E0)
                                    insideGeofence -> Color(0xFFE8F5E9)
                                    else -> Color(0xFFFFEBEE)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(statusBgColor, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = statusColor
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Geo-fencing Disabled",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF1565C0)
                                    )
                                }
                            }
                        }

                        if (settings.geoFencingEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Visual Map Geofence radar (fabulous design canvas touch!)
                            Text("Live Radar Tracking", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val center = Offset(size.width / 2, size.height / 2)
                                    val baseRadius = 35.dp.toPx()

                                    // Draw allowed Geofence radius circle
                                    drawCircle(
                                        color = Color(0xFF1E88E5).copy(alpha = 0.15f),
                                        radius = baseRadius,
                                        center = center
                                    )
                                    drawCircle(
                                        color = Color(0xFF1E88E5).copy(alpha = 0.4f),
                                        radius = baseRadius,
                                        center = center,
                                        style = Stroke(width = 1.dp.toPx())
                                    )

                                    // Center Office Pin
                                    drawCircle(
                                        color = Color(0xFF1565C0),
                                        radius = 5.dp.toPx(),
                                        center = center
                                    )

                                    // Current Employee Pin
                                    if (distToOffice != null) {
                                        val userOffset = if (insideGeofence) {
                                            // inside
                                            Offset(center.x + 12.dp.toPx(), center.y - 8.dp.toPx())
                                        } else {
                                            // outside
                                            Offset(center.x + 55.dp.toPx(), center.y + 16.dp.toPx())
                                        }

                                        drawCircle(
                                            color = if (insideGeofence) Color(0xFF4CAF50) else Color(0xFFF44336),
                                            radius = 6.dp.toPx(),
                                            center = userOffset
                                        )
                                        drawCircle(
                                            color = if (insideGeofence) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color(0xFFF44336).copy(alpha = 0.3f),
                                            radius = 12.dp.toPx(),
                                            center = userOffset
                                        )
                                    }
                                }

                                Text(
                                    text = if (distToOffice == null) "Searching..." else "Distance: ${String.format("%.1f", distToOffice)} m",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = Color.White
                                )
                            }
                        } else {
                            // Friendly, high-fidelity UI block for when Geofencing is turned off
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Geofencing disabled",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Attendance geo-restriction is disabled.\nYou can check-in from any location.",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32),
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Attendance Actions: Check-In & Check-Out
        val showSelfieSection = todayAttendance == null || todayAttendance?.checkOutTime == null
        if (showSelfieSection) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Face Verification Selfie",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (attendanceSelfieBitmap != null) {
                            Image(
                                bitmap = attendanceSelfieBitmap!!.asImageBitmap(),
                                contentDescription = "Attendance Selfie Preview",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(onClick = {
                                    val hasCamPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasCamPermission) {
                                        attendanceSelfieLauncher.launch()
                                    } else {
                                        dashboardCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                    }
                                }) {
                                    Text("Retake")
                                }
                                TextButton(onClick = { attendanceSelfieBitmap = null }) {
                                    Text("Clear", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .clickable {
                                        val hasCamPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (hasCamPermission) {
                                            attendanceSelfieLauncher.launch()
                                        } else {
                                            dashboardCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.CameraAlt,
                                        contentDescription = "Camera Icon",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Snap Selfie", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val selfieLabelText = if (todayAttendance == null) "Take a selfie first to enable Check-In" else "Take a selfie first to enable Check-Out"
                            Text(
                                text = selfieLabelText,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Check-In button
                val checkInAllowed = (if (settings.geoFencingEnabled) {
                    gpsGranted && insideGeofence && todayAttendance == null
                } else {
                    todayAttendance == null
                }) && attendanceSelfieBitmap != null
                
                Button(
                    onClick = {
                        val base64 = attendanceSelfieBitmap?.let { ImageUtils.bitmapToBase64(it) }
                        viewModel.checkInEmployee(base64)
                        attendanceSelfieBitmap = null
                    },
                    enabled = checkInAllowed,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("check_in_button")
                ) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = "Check-in Icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check In", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                // Check-Out button
                val checkOutAllowed = todayAttendance != null && todayAttendance?.checkOutTime == null && attendanceSelfieBitmap != null
                Button(
                    onClick = {
                        val base64 = attendanceSelfieBitmap?.let { ImageUtils.bitmapToBase64(it) }
                        viewModel.checkOutEmployee(base64)
                        attendanceSelfieBitmap = null
                    },
                    enabled = checkOutAllowed,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("check_out_button")
                ) {
                    Icon(imageVector = Icons.Filled.ExitToApp, contentDescription = "Check-out Icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check Out", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            if (gpsGranted && gpsHardwareEnabled && todayAttendance == null) {
                if (distToOffice == null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Acquiring high-accuracy GPS location... please wait.",
                        color = Color(0xFFE65100),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (!insideGeofence) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "You are outside office premises. Check-in not allowed.",
                        color = Color(0xFFD32F2F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
        }

        // Historical Log Headers
        item {
            Text(
                text = "Attendance History",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (empHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "No previous attendance logs recorded.",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            items(empHistory) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = log.date,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AccessTime,
                                        contentDescription = "Time",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "In: ${log.checkInTime} | Out: ${log.checkOutTime ?: "--:--:--"}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (log.attendanceStatus == "Present") Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = log.attendanceStatus,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (log.attendanceStatus == "Present") Color(0xFF2E7D32) else Color(0xFFE65100)
                                    )
                                }
                                if (log.checkOutTime != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${String.format("%.2f", log.workingHours)} hrs",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Show personal logged selfies if they exist
                        if (log.checkInSelfieBase64 != null || log.checkOutSelfieBase64 != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (log.checkInSelfieBase64 != null) {
                                    val checkInBitmap = remember(log.checkInSelfieBase64) {
                                        ImageUtils.base64ToBitmap(log.checkInSelfieBase64)
                                    }
                                    if (checkInBitmap != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Check-In Selfie", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Image(
                                                bitmap = checkInBitmap.asImageBitmap(),
                                                contentDescription = "My Check-In Selfie",
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                        }
                                    }
                                }
                                if (log.checkOutSelfieBase64 != null) {
                                    val checkOutBitmap = remember(log.checkOutSelfieBase64) {
                                        ImageUtils.base64ToBitmap(log.checkOutSelfieBase64)
                                    }
                                    if (checkOutBitmap != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Check-Out Selfie", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Image(
                                                bitmap = checkOutBitmap.asImageBitmap(),
                                                contentDescription = "My Check-Out Selfie",
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { AppUpdater.checkForUpdate(true, context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Check for Updates", fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showWfhConfirm) {
        AlertDialog(
            onDismissRequest = { showWfhConfirm = false },
            title = { Text("Confirm WFH Request") },
            text = { Text("Are you sure you want to request Work From Home for today? This will be sent for administrator approval.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.submitWfhRequest()
                    showWfhConfirm = false
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWfhConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    }
    }
}

@Composable
fun EmployeeExpensesTab(viewModel: MainViewModel) {
    val expenses by viewModel.allExpenses.collectAsState()
    val currentDeviceId by viewModel.currentDeviceId.collectAsState()
    val employees by viewModel.allEmployees.collectAsState()
    
    val currentEmployee = remember(employees, currentDeviceId) {
        employees.find { it.deviceId == currentDeviceId }
    }
    
    val myExpenses = remember(expenses, currentEmployee) {
        expenses.filter { it.employeeId == currentEmployee?.employeeId }
    }

    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Submit Expense", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount (₹)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                val amountDouble = amount.toDoubleOrNull()
                if (description.isNotBlank() && amountDouble != null && amountDouble > 0) {
                    viewModel.submitExpense(description, amountDouble)
                    description = ""
                    amount = ""
                    Toast.makeText(context, "Expense submitted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Please enter valid description and amount", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Expense")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Past Expenses", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(myExpenses) { expense ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(expense.description, fontWeight = FontWeight.Bold)
                            Text(
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(expense.date)),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "₹${String.format("%.2f", expense.amount)}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val statusColor = when (expense.status) {
                                "APPROVED" -> Color(0xFF2E7D32)
                                "REJECTED" -> Color(0xFFC62828)
                                else -> Color(0xFFF57F17)
                            }
                            Text(
                                expense.status,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminWfhTab(viewModel: MainViewModel) {
    val requests by viewModel.allWfhRequests.collectAsState()
    val pending = requests.filter { it.status == "PENDING" }
    val history = requests.filter { it.status != "PENDING" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Pending WFH Requests (${pending.size})",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (pending.isEmpty()) {
            item {
                Text("No pending WFH requests.", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(8.dp))
            }
        } else {
            items(pending) { req ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(req.employeeName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Date: ${req.date}", fontSize = 13.sp)
                                Text("ID: ${req.employeeId}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFF9800).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("PENDING", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.updateWfhStatus(req.requestId, "APPROVED") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = "Approve")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve")
                            }
                            
                            Button(
                                onClick = { viewModel.updateWfhStatus(req.requestId, "REJECTED") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Reject")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reject")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Request History",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        if (history.isEmpty()) {
            item {
                Text("No historical records.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(8.dp))
            }
        } else {
            items(history) { req ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(req.employeeName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${req.date} | ${req.employeeId}", fontSize = 11.sp, color = Color.Gray)
                        }
                        
                        val color = if (req.status == "APPROVED") Color(0xFF4CAF50) else Color(0xFFF44336)
                        Text(
                            text = req.status,
                            color = color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDashboardSection(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Employees", "Attendance", "Expenses", "WFH", "Settings")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> AdminEmployeesTab(viewModel)
                1 -> AdminAttendanceTab(viewModel)
                2 -> AdminExpensesTab(viewModel)
                3 -> AdminWfhTab(viewModel)
                4 -> AdminSettingsTab(viewModel)
            }
        }
    }
}

@Composable
fun AdminEmployeesTab(viewModel: MainViewModel) {
    val employees by viewModel.allEmployees.collectAsState()
    var searchField by remember { mutableStateOf("") }

    val filteredList = remember(employees, searchField) {
        if (searchField.isBlank()) employees
        else employees.filter { it.fullName.contains(searchField, ignoreCase = true) || it.employeeId.contains(searchField, ignoreCase = true) }
    }

    val pending = filteredList.filter { it.approvalStatus == "PENDING" }
    val active = filteredList.filter { it.approvalStatus != "PENDING" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search TextField
        item {
            OutlinedTextField(
                value = searchField,
                onValueChange = { searchField = it },
                label = { Text("Search Employees") },
                placeholder = { Text("Search by name or ID...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_search_input"),
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = "Search Icon") },
                singleLine = true
            )
        }

        // Pending Registrations Headers
        if (pending.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFFFB300), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pending Registrations (${pending.size})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color(0xFFFFB300)
                    )
                }
            }

            items(pending) { emp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(emp.fullName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("ID: ${emp.employeeId}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("Phone: ${emp.phoneNumber}", fontSize = 12.sp)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Selfie Preview
                                val bitmap = remember(emp.selfieImageBase64) {
                                    ImageUtils.base64ToBitmap(emp.selfieImageBase64)
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Employee Selfie Preview",
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.deleteEmployee(emp) },
                                    modifier = Modifier.testTag("delete_btn_${emp.employeeId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete Employee",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.approveEmployee(emp.employeeId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("approve_btn_${emp.employeeId}")
                            ) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = "Approve")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approve")
                            }

                            OutlinedButton(
                                onClick = { viewModel.rejectEmployee(emp.employeeId) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                                border = BorderStroke(1.dp, Color(0xFFD32F2F)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reject_btn_${emp.employeeId}")
                            ) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Reject")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reject")
                            }
                        }
                    }
                }
            }
        }

        // Active Approved Employees
        item {
            Text(
                text = "Staff Members (${active.size})",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (active.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No approved employees matched your search.",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            }
        } else {
            items(active) { emp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(emp.fullName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (emp.approvalStatus == "APPROVED") Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = emp.approvalStatus,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (emp.approvalStatus == "APPROVED") Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                    }
                                }
                                Text("ID: ${emp.employeeId} | Phone: ${emp.phoneNumber}", fontSize = 12.sp)
                                Text("Last Login: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(emp.lastLogin))}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Selfie Preview
                                val bitmap = remember(emp.selfieImageBase64) {
                                    ImageUtils.base64ToBitmap(emp.selfieImageBase64)
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Employee Selfie Preview",
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }

                                // Enable/Disable switch
                                Switch(
                                    checked = emp.activeStatus,
                                    onCheckedChange = { active ->
                                        if (active) viewModel.enableEmployee(emp.employeeId)
                                        else viewModel.disableEmployee(emp.employeeId)
                                    },
                                    modifier = Modifier.testTag("toggle_status_${emp.employeeId}")
                                )

                                IconButton(
                                    onClick = { viewModel.deleteEmployee(emp) },
                                    modifier = Modifier.testTag("delete_btn_${emp.employeeId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete Employee",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminAttendanceTab(viewModel: MainViewModel) {
    val attendance by viewModel.allAttendance.collectAsState()
    val employees by viewModel.allEmployees.collectAsState()
    var searchFilter by remember { mutableStateOf("") }

    val filteredList = remember(attendance, searchFilter) {
        if (searchFilter.isBlank()) attendance
        else attendance.filter { it.employeeName.contains(searchFilter, ignoreCase = true) || it.employeeId.contains(searchFilter, ignoreCase = true) }
    }

    // Analytics Metrics Calculations
    val totalEmployees = employees.size
    val presentToday = attendance.filter { it.date == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) && it.attendanceStatus == "Present" }.size
    val lateToday = attendance.filter { it.date == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) && it.attendanceStatus == "Late" }.size
    val absentToday = (totalEmployees - presentToday - lateToday).coerceAtLeast(0)

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Analytics Summary Cards
        item {
            Text(
                text = "Today's Corporate Analytics",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Present Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Present", fontSize = 11.sp, color = Color(0xFF2E7D32))
                        Text("$presentToday", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF2E7D32))
                    }
                }

                // Late Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Late Arrivals", fontSize = 11.sp, color = Color(0xFFE65100))
                        Text("$lateToday", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFFE65100))
                    }
                }

                // Absent Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Absent", fontSize = 11.sp, color = Color(0xFFC62828))
                        Text("$absentToday", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFFC62828))
                    }
                }
            }
        }

        // Action controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    label = { Text("Filter logs") },
                    placeholder = { Text("Staff name...") },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    singleLine = true
                )

                Button(
                    onClick = {
                        exportAttendanceToCsv(context, filteredList)
                    },
                    modifier = Modifier.height(50.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Download, contentDescription = "Export")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export")
                }
            }
        }

        // Attendance List
        if (filteredList.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No attendance logs found.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            }
        } else {
            items(filteredList) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(log.employeeName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Employee ID: ${log.employeeId}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("Date Logged: ${log.date}", fontSize = 12.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .background(
                                        if (log.attendanceStatus == "Present") Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = log.attendanceStatus,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (log.attendanceStatus == "Present") Color(0xFF2E7D32) else Color(0xFFE65100)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Checked-In: ${log.checkInTime}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "Checked-Out: ${log.checkOutTime ?: "--:--:--"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            if (log.checkOutTime != null) {
                                Text(
                                    text = "Hours: ${String.format("%.2f", log.workingHours)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Show selfies if present
                        if (log.checkInSelfieBase64 != null || log.checkOutSelfieBase64 != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (log.checkInSelfieBase64 != null) {
                                    val checkInBitmap = remember(log.checkInSelfieBase64) {
                                        ImageUtils.base64ToBitmap(log.checkInSelfieBase64)
                                    }
                                    if (checkInBitmap != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Check-In Selfie", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Image(
                                                bitmap = checkInBitmap.asImageBitmap(),
                                                contentDescription = "Check-In Selfie",
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                        }
                                    }
                                }
                                if (log.checkOutSelfieBase64 != null) {
                                    val checkOutBitmap = remember(log.checkOutSelfieBase64) {
                                        ImageUtils.base64ToBitmap(log.checkOutSelfieBase64)
                                    }
                                    if (checkOutBitmap != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Check-Out Selfie", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Image(
                                                bitmap = checkOutBitmap.asImageBitmap(),
                                                contentDescription = "Check-Out Selfie",
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminExpensesTab(viewModel: MainViewModel) {
    val expenses by viewModel.allExpenses.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Employee Expenses", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        
        items(expenses) { expense ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(expense.description, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(expense.employeeName, fontSize = 12.sp)
                            Text(
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(expense.date)),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "₹${String.format("%.2f", expense.amount)}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )
                            val statusColor = when (expense.status) {
                                "APPROVED" -> Color(0xFF2E7D32)
                                "REJECTED" -> Color(0xFFC62828)
                                else -> Color(0xFFF57F17)
                            }
                            Text(
                                expense.status,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                    
                    if (expense.status == "PENDING") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.updateExpenseStatus(expense, "APPROVED") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Text("Approve")
                            }
                            Button(
                                onClick = { viewModel.updateExpenseStatus(expense, "REJECTED") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                            ) {
                                Text("Reject")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSettingsTab(viewModel: MainViewModel) {
    val settings by viewModel.officeSettings.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    var officeName by remember { mutableStateOf("") }
    var officeLatitude by remember { mutableStateOf("") }
    var officeLongitude by remember { mutableStateOf("") }
    var allowedRadius by remember { mutableStateOf("") }
    var geoFencingEnabled by remember { mutableStateOf(true) }
    var gasUrl by remember { mutableStateOf("") }
    var adminPasscode by remember { mutableStateOf("") }
    var hideAdminButton by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var isDetectingLocation by remember { mutableStateOf(false) }

    val detectCurrentLocation = {
        isDetectingLocation = true
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!isGpsEnabled && !isNetworkEnabled) {
                Toast.makeText(context, "Please turn on device location/GPS services", Toast.LENGTH_LONG).show()
                isDetectingLocation = false
            } else {
                // 1. Try last known location first for instantaneous responsiveness
                var bestLocation: android.location.Location? = null
                val providers = locationManager.getProviders(true)
                for (prov in providers) {
                    val loc = locationManager.getLastKnownLocation(prov) ?: continue
                    if (bestLocation == null || loc.time > bestLocation!!.time) {
                        bestLocation = loc
                    }
                }

                if (bestLocation != null) {
                    officeLatitude = bestLocation.latitude.toString()
                    officeLongitude = bestLocation.longitude.toString()
                    Toast.makeText(context, "Location coordinates loaded!", Toast.LENGTH_SHORT).show()
                }

                // 2. Request a fresh precise location update
                val providerToUse = when {
                    isGpsEnabled -> LocationManager.GPS_PROVIDER
                    isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }

                if (providerToUse != null) {
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: android.location.Location) {
                            officeLatitude = location.latitude.toString()
                            officeLongitude = location.longitude.toString()
                            Toast.makeText(context, "Coordinates updated from high-accuracy GPS!", Toast.LENGTH_SHORT).show()
                            isDetectingLocation = false
                            locationManager.removeUpdates(this)
                        }
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    }

                    locationManager.requestLocationUpdates(
                        providerToUse,
                        0L,
                        0f,
                        listener,
                        context.mainLooper
                    )
                } else {
                    isDetectingLocation = false
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
            isDetectingLocation = false
        } catch (e: Exception) {
            Toast.makeText(context, "Error fetching location: ${e.message}", Toast.LENGTH_SHORT).show()
            isDetectingLocation = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            detectCurrentLocation()
        } else {
            Toast.makeText(context, "Location permission is required to detect coordinates.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(settings) {
        officeName = settings.officeName
        officeLatitude = settings.officeLatitude.toString()
        officeLongitude = settings.officeLongitude.toString()
        allowedRadius = settings.allowedRadius.toString()
        geoFencingEnabled = settings.geoFencingEnabled
        gasUrl = settings.googleAppsScriptUrl
        adminPasscode = settings.adminPasscode
        hideAdminButton = settings.hideAdminButton
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Geofence Settings",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Office Name
        OutlinedTextField(
            value = officeName,
            onValueChange = { officeName = it },
            label = { Text("Office Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Latitude
            OutlinedTextField(
                value = officeLatitude,
                onValueChange = { officeLatitude = it },
                label = { Text("Office Latitude") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            // Longitude
            OutlinedTextField(
                value = officeLongitude,
                onValueChange = { officeLongitude = it },
                label = { Text("Office Longitude") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }

        // Auto Select Coordinates Button
        OutlinedButton(
            onClick = {
                val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    detectCurrentLocation()
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isDetectingLocation
        ) {
            if (isDetectingLocation) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Acquiring Current GPS Coordinates...")
            } else {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = "My Location"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Auto Select Coordinates from Current Location")
            }
        }

        // Allowed Radius
        OutlinedTextField(
            value = allowedRadius,
            onValueChange = { allowedRadius = it },
            label = { Text("Allowed Radius (meters)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )

        // Geo-fencing Enabled Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Enable Geo-Fencing Checks", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Mandate that staff check-in from within permitted coordinates.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Switch(
                checked = geoFencingEnabled,
                onCheckedChange = { geoFencingEnabled = it }
            )
        }

        Button(
            onClick = {
                val lat = officeLatitude.toDoubleOrNull() ?: 23.6307
                val lng = officeLongitude.toDoubleOrNull() ?: 87.0937
                val rad = allowedRadius.toDoubleOrNull() ?: 10.0
                viewModel.updateSettings(officeName, lat, lng, rad, geoFencingEnabled, gasUrl, adminPasscode, hideAdminButton)
                Toast.makeText(context, "Office Settings Saved locally!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Geofence Settings")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Divider()

        // Admin Security Settings Section
        Text(
            text = "Admin Access Security",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        OutlinedTextField(
            value = adminPasscode,
            onValueChange = { adminPasscode = it },
            label = { Text("Admin Dashboard Passcode") },
            placeholder = { Text("e.g. 1234") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )

        Button(
            onClick = {
                val lat = officeLatitude.toDoubleOrNull() ?: 23.6307
                val lng = officeLongitude.toDoubleOrNull() ?: 87.0937
                val rad = allowedRadius.toDoubleOrNull() ?: 10.0
                viewModel.updateSettings(officeName, lat, lng, rad, geoFencingEnabled, gasUrl, adminPasscode, hideAdminButton)
                Toast.makeText(context, "Admin Access Settings Saved!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Save Admin Access Settings")
        }

        Divider()

        // Google Sheets Sync Section
        Text(
            text = "Google Sheets Database Synchronization",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        OutlinedTextField(
            value = gasUrl,
            onValueChange = { gasUrl = it },
            label = { Text("Google Apps Script Web App URL") },
            placeholder = { Text("https://script.google.com/macros/s/...") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val lat = officeLatitude.toDoubleOrNull() ?: 23.6307
                    val lng = officeLongitude.toDoubleOrNull() ?: 87.0937
                    val rad = allowedRadius.toDoubleOrNull() ?: 10.0
                    // First save then sync
                    viewModel.updateSettings(officeName, lat, lng, rad, geoFencingEnabled, gasUrl, adminPasscode, hideAdminButton)
                    viewModel.syncWithSheets()
                },
                enabled = !syncing,
                modifier = Modifier.weight(1f)
            ) {
                if (syncing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Icon(imageVector = Icons.Filled.Sync, contentDescription = "Sync icon")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync with Sheets")
                }
            }
        }

        if (syncMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = syncMessage!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearSyncMessage() }) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            }
        }

        // Copyable Apps Script Code block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Google Apps Script Code Template", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(APPS_SCRIPT_CODE))
                            Toast.makeText(context, "Apps Script code copied!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy Code")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Copy the code template using the icon above, open script.google.com, paste it in, deploy as web app accessible to 'Anyone', and paste the generated endpoint URL above.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { AppUpdater.checkForUpdate(true, context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Check for App Updates")
        }
    }
}

@Composable
fun AdminAuditLogsTab(viewModel: MainViewModel) {
    val logs by viewModel.auditLogs.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Secure Audit Ledger",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                IconButton(
                    onClick = {
                        viewModel.addManualAuditLog("Admin", "Audit logs cleared")
                    }
                ) {
                    Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = "Clear logs")
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Audit ledger is currently empty.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            }
        } else {
            items(logs) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        when (log.action) {
                                            "System" -> Color(0xFFE3F2FD)
                                            "Warning" -> Color(0xFFFFF3E0)
                                            "Admin" -> Color(0xFFEDE7F6)
                                            else -> Color(0xFFE8F5E9)
                                        },
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = log.action,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = when (log.action) {
                                        "System" -> Color(0xFF1565C0)
                                        "Warning" -> Color(0xFFE65100)
                                        "Admin" -> Color(0xFF5E35B1)
                                        else -> Color(0xFF2E7D32)
                                    }
                                )
                            }

                            Text(
                                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(log.details, fontSize = 12.sp)

                        if (log.employeeId != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Employee Ref: ${log.employeeId}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun exportAttendanceToCsv(context: Context, attendanceList: List<Attendance>) {
    val fileName = "Attendance_Export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
    val csvHeader = "ID,EmployeeID,Name,Date,InTime,OutTime,Hours,Status\n"
    val csvData = StringBuilder(csvHeader)
    
    for (a in attendanceList) {
        csvData.append("${a.attendanceId},")
        csvData.append("${a.employeeId},")
        csvData.append("${a.employeeName},")
        csvData.append("${a.date},")
        csvData.append("${a.checkInTime},")
        csvData.append("${a.checkOutTime ?: ""},")
        csvData.append("${String.format("%.2f", a.workingHours)},")
        csvData.append("${a.attendanceStatus}\n")
    }

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri).use { outputStream ->
                    outputStream?.write(csvData.toString().toByteArray())
                }
                Toast.makeText(context, "Exported to Downloads: $fileName", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to create file", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Legacy for older Android versions
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, fileName)
            file.writeText(csvData.toString())
            Toast.makeText(context, "Exported to Downloads: $fileName", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

const val APPS_SCRIPT_CODE = """// Google Apps Script for Office Management System
// Deploy as a Web App with access: "Anyone, even anonymous"

function doPost(e) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet();
  var employeesSheet = getOrCreateSheet(sheet, "Employees", [
    "Employee ID", "Full Name", "Phone Number", "Device ID", "Selfie Image URL", "Registration Date", "Approval Status", "Last Login", "Active Status"
  ]);
  var attendanceSheet = getOrCreateSheet(sheet, "Attendance", [
    "Attendance ID", "Employee ID", "Employee Name", "Date", "Check-In Time", "Check-In Latitude", "Check-In Longitude", "Check-Out Time", "Check-Out Latitude", "Check-Out Longitude", "Working Hours", "Attendance Status"
  ]);
  var settingsSheet = getOrCreateSheet(sheet, "Settings", [
    "Office Name", "Office Latitude", "Office Longitude", "Allowed Radius", "Geo-Fencing Enabled"
  ]);
  var expensesSheet = getOrCreateSheet(sheet, "Expenses", [
    "Expense ID", "Employee ID", "Employee Name", "Description", "Amount", "Date", "Status"
  ]);
  var wfhSheet = getOrCreateSheet(sheet, "WFH", [
    "Request ID", "Employee ID", "Employee Name", "Date", "Timestamp", "Status"
  ]);

  var data = JSON.parse(e.postData.contents);
  if (data.action === "sync") {
    // 1. Sync Settings
    if (data.settings) {
      settingsSheet.clearContents();
      settingsSheet.appendRow(["Office Name", "Office Latitude", "Office Longitude", "Allowed Radius", "Geo-Fencing Enabled"]);
      settingsSheet.appendRow([
        data.settings.officeName,
        data.settings.officeLatitude,
        data.settings.officeLongitude,
        data.settings.allowedRadius,
        data.settings.geoFencingEnabled
      ]);
    }

    // 2. Sync Employees (Upsert)
    var empRows = employeesSheet.getDataRange().getValues();
    var existingEmps = {};
    for (var i = 1; i < empRows.length; i++) {
      existingEmps[empRows[i][0]] = i + 1; // map employeeId -> row number
    }

    if (data.employees) {
      data.employees.forEach(function(emp) {
        var rowNum = existingEmps[emp.employeeId];
        var rowData = [
          emp.employeeId,
          emp.fullName,
          emp.phoneNumber,
          emp.deviceId,
          emp.selfieImageBase64 ? "Base64 Image Cached" : "",
          new Date(emp.registrationDate).toISOString(),
          emp.approvalStatus,
          new Date(emp.lastLogin).toISOString(),
          emp.activeStatus ? "TRUE" : "FALSE"
        ];
        if (rowNum) {
          employeesSheet.getRange(rowNum, 1, 1, rowData.length).setValues([rowData]);
        } else {
          employeesSheet.appendRow(rowData);
        }
      });
    }

    // 3. Sync Attendance (Overwrite all for parity)
    attendanceSheet.clearContents();
    attendanceSheet.appendRow([
      "Attendance ID", "Employee ID", "Employee Name", "Date", "Check-In Time", "Check-In Latitude", "Check-In Longitude", "Check-Out Time", "Check-Out Latitude", "Check-Out Longitude", "Working Hours", "Attendance Status"
    ]);
    if (data.attendance) {
      data.attendance.forEach(function(att) {
        attendanceSheet.appendRow([
          att.attendanceId,
          att.employeeId,
          att.employeeName,
          att.date,
          att.checkInTime,
          att.checkInLatitude,
          att.checkInLongitude,
          att.checkOutTime || "",
          att.checkOutLatitude || "",
          att.checkOutLongitude || "",
          att.workingHours,
          att.attendanceStatus
        ]);
      });
    }

    // 4. Sync Expenses (Upsert by ID)
    var expRows = expensesSheet.getDataRange().getValues();
    var existingExps = {};
    for (var k = 1; k < expRows.length; k++) {
      existingExps[expRows[k][0]] = k + 1;
    }
    if (data.expenses) {
      data.expenses.forEach(function(exp) {
        var rowNum = existingExps[exp.expenseId];
        var rowData = [
          exp.expenseId,
          exp.employeeId,
          exp.employeeName,
          exp.description,
          exp.amount,
          exp.date,
          exp.status
        ];
        if (rowNum) {
          expensesSheet.getRange(rowNum, 1, 1, rowData.length).setValues([rowData]);
        } else {
          expensesSheet.appendRow(rowData);
        }
      });
    }

    // 5. Sync WFH Requests (Upsert by ID)
    var wfhRows = wfhSheet.getDataRange().getValues();
    var existingWfh = {};
    for (var l = 1; l < wfhRows.length; l++) {
      existingWfh[wfhRows[l][0]] = l + 1;
    }
    if (data.wfhRequests) {
      data.wfhRequests.forEach(function(wfh) {
        var rowNum = existingWfh[wfh.requestId];
        var rowData = [
          wfh.requestId,
          wfh.employeeId,
          wfh.employeeName,
          wfh.date,
          new Date(wfh.timestamp).toISOString(),
          wfh.status
        ];
        if (rowNum) {
          wfhSheet.getRange(rowNum, 1, 1, rowData.length).setValues([rowData]);
        } else {
          wfhSheet.appendRow(rowData);
        }
      });
    }

    // Fetch updated statuses to return to mobile
    var updatedEmployees = [];
    var freshEmpRows = employeesSheet.getDataRange().getValues();
    for (var j = 1; j < freshEmpRows.length; j++) {
      updatedEmployees.push({
        employeeId: freshEmpRows[j][0],
        fullName: freshEmpRows[j][1],
        phoneNumber: freshEmpRows[j][2],
        deviceId: freshEmpRows[j][3],
        approvalStatus: freshEmpRows[j][6],
        activeStatus: freshEmpRows[j][8] === "TRUE" || freshEmpRows[j][8] === true
      });
    }

    var updatedExpenses = [];
    var freshExpRows = expensesSheet.getDataRange().getValues();
    for (var m = 1; m < freshExpRows.length; m++) {
      updatedExpenses.push({
        expenseId: freshExpRows[m][0],
        status: freshExpRows[m][6]
      });
    }

    var updatedWfh = [];
    var freshWfhRows = wfhSheet.getDataRange().getValues();
    for (var n = 1; n < freshWfhRows.length; n++) {
      updatedWfh.push({
        requestId: freshWfhRows[n][0],
        status: freshWfhRows[n][5]
      });
    }

    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      employees: updatedEmployees,
      expenses: updatedExpenses,
      wfhRequests: updatedWfh,
      settings: data.settings
    })).setMimeType(ContentService.MimeType.JSON);
  }

  return ContentService.createTextOutput(JSON.stringify({
    status: "error",
    message: "Invalid action"
  })).setMimeType(ContentService.MimeType.JSON);
}

function doGet(e) {
  return ContentService.createTextOutput("Office Management System Google Apps Script Sync Endpoint is Active!").setMimeType(ContentService.MimeType.TEXT);
}

function getOrCreateSheet(ss, name, headers) {
  var sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
    sheet.appendRow(headers);
  }
  return sheet;
}"""
