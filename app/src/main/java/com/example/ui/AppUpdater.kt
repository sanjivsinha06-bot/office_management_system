package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val serverBuildTime: Long, val url: String) : UpdateState()
    object NoUpdate : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    object Installing : UpdateState()
    data class Error(val message: String) : UpdateState()
}

object AppUpdater {
    private const val TAG = "AppUpdater"
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val quickClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private var resolvedAppUrl: String? = null

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    fun checkForUpdate(manualCheck: Boolean = false, context: Context? = null) {
        val currentState = _updateState.value
        if (currentState is UpdateState.Checking || 
            currentState is UpdateState.Downloading || 
            currentState is UpdateState.Installing || 
            (!manualCheck && currentState is UpdateState.UpdateAvailable)) {
            return
        }
        
        _updateState.value = UpdateState.Checking

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    var finalUrl = ""
                    var body = ""
                    var success = false
                    var errorMessage: String? = null

                    // 1. Try local emulator loopback IP checks across multiple common ports
                    val ports = listOf(3000, 8080, 8000, 5000, 80)
                    for (port in ports) {
                        try {
                            val localUrl = "http://10.0.2.2:$port/version.json"
                            val request = Request.Builder().url(localUrl).build()
                            quickClient.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val respBody = response.body?.string() ?: ""
                                    if (respBody.trim().startsWith("{")) {
                                        body = respBody
                                        finalUrl = "http://10.0.2.2:$port"
                                        success = true
                                        break
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Local loopback check failed on port $port: ${e.message}")
                        }
                    }

                    // 2. Fallback to Shared or Dev App URL
                    if (!success) {
                        val urlsToTry = mutableListOf<String>()
                        if (BuildConfig.APP_URL.isNotEmpty()) {
                            // Try the direct APP_URL first (could be dev URL serving version)
                            urlsToTry.add(BuildConfig.APP_URL)
                            val replacedUrl = BuildConfig.APP_URL.replace("ais-dev-", "ais-pre-")
                            if (replacedUrl != BuildConfig.APP_URL) {
                                urlsToTry.add(replacedUrl)
                            }
                        }

                        if (urlsToTry.isEmpty()) {
                            return@withContext Triple("EMPTY_URL", null, null)
                        }

                        for (appUrl in urlsToTry) {
                            val remoteUrl = if (appUrl.endsWith("/")) "${appUrl}version.json" else "$appUrl/version.json"
                            try {
                                val request = Request.Builder().url(remoteUrl).build()
                                client.newCall(request).execute().use { response ->
                                    if (response.isSuccessful) {
                                        val respBody = response.body?.string() ?: ""
                                        if (respBody.trim().startsWith("<") || respBody.trim().startsWith("<!doctype")) {
                                            errorMessage = "Auth wall active. Download update from browser."
                                        } else {
                                            body = respBody
                                            finalUrl = appUrl
                                            success = true
                                        }
                                    } else {
                                        errorMessage = "Server error ${response.code}"
                                    }
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Network error"
                            }
                            if (success) break
                        }
                    }

                    if (success) {
                        resolvedAppUrl = finalUrl
                        Triple("SUCCESS", body, finalUrl)
                    } else {
                        val friendlyError = when {
                            errorMessage?.contains("404") == true -> "Update file not found on server"
                            errorMessage?.contains("timeout") == true -> "Connection timed out"
                            errorMessage?.contains("Unable to resolve host") == true -> "No internet connection"
                            else -> errorMessage ?: "Check failed"
                        }
                        Triple("ERROR", friendlyError, null)
                    }
                }

                when (result.first) {
                    "EMPTY_URL" -> {
                        _updateState.value = UpdateState.NoUpdate
                    }
                    "SUCCESS" -> {
                        val body = result.second ?: ""
                        val json = JSONObject(body)
                        val serverBuildTime = json.optLong("buildTime", 0L)
                        val currentBuildTime = BuildConfig.BUILD_TIME
                        if (serverBuildTime > currentBuildTime) {
                            _updateState.value = UpdateState.UpdateAvailable(serverBuildTime, result.third ?: "")
                        } else {
                            _updateState.value = UpdateState.NoUpdate
                            if (manualCheck && context != null) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "App is already up to date!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    else -> {
                        if (manualCheck) {
                            _updateState.value = UpdateState.Error(result.second ?: "Check failed")
                        } else {
                            _updateState.value = UpdateState.Idle
                        }
                    }
                }
            } catch (e: Exception) {
                if (manualCheck) {
                    _updateState.value = UpdateState.Error(e.message ?: "Unknown error")
                } else {
                    _updateState.value = UpdateState.Idle
                }
            }
        }
    }

    fun startDownloadAndInstall(context: Context) {
        val currentState = _updateState.value
        if (currentState is UpdateState.Downloading || currentState is UpdateState.Installing) {
            return
        }
        
        val appUrl = if (currentState is UpdateState.UpdateAvailable) {
            currentState.url
        } else {
            synchronized(this) { resolvedAppUrl } ?: BuildConfig.APP_URL.replace("ais-dev-", "ais-pre-")
        }

        if (appUrl.isEmpty()) {
            Toast.makeText(context, "Update URL not found. Please update via browser.", Toast.LENGTH_LONG).show()
            return
        }

        val apkUrl = if (appUrl.endsWith(".apk")) appUrl 
                     else if (appUrl.endsWith("/")) "${appUrl}app-release.apk"
                     else "$appUrl/app-release.apk"
        _updateState.value = UpdateState.Downloading(0f)

        scope.launch {
            try {
                val destinationFile = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(apkUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
                        val body = response.body ?: throw Exception("Empty response")
                        val totalBytes = body.contentLength()
                        val file = File(context.externalCacheDir ?: context.cacheDir, "update_new.apk")
                        if (file.exists()) file.delete()

                        body.byteStream().use { input ->
                            FileOutputStream(file).use { output ->
                                val buffer = ByteArray(8192)
                                var read: Int
                                var totalRead = 0L
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    totalRead += read
                                    if (totalBytes > 0) {
                                        val progress = totalRead.toFloat() / totalBytes.toFloat()
                                        withContext(Dispatchers.Main) {
                                            _updateState.value = UpdateState.Downloading(progress)
                                        }
                                    }
                                }
                            }
                        }
                        file
                    }
                }
                _updateState.value = UpdateState.Installing
                installApk(context, destinationFile)
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Toast.makeText(context, "APK file not found!", Toast.LENGTH_SHORT).show()
                return
            }

            // Check REQUEST_INSTALL_PACKAGES permission on Android Oreo+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "Please allow unknown app installation to update", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    
                    // Reset state so user can click Update again after granting permission
                    val serverTime = BuildConfig.BUILD_TIME
                    _updateState.value = UpdateState.Error("Please grant permission and retry")
                    return
                }
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting installation: ${e.message}", e)
            Toast.makeText(context, "Installation failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun AppUpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by AppUpdater.updateState.collectAsState()
    var dismissed by remember { mutableStateOf(false) }

    // Periodically check for updates every 10 minutes
    LaunchedEffect(Unit) {
        while (true) {
            AppUpdater.checkForUpdate(false)
            delay(600_000) // 10 minutes check interval
        }
    }

    // Reset dismissed state when check starts or new update found
    LaunchedEffect(state) {
        if (state is UpdateState.Checking || state is UpdateState.UpdateAvailable) {
            dismissed = false
        }
    }

    AnimatedVisibility(
        visible = (state is UpdateState.UpdateAvailable || 
                 state is UpdateState.Downloading || 
                 state is UpdateState.Installing ||
                 (state is UpdateState.Error && !dismissed)),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        if (!dismissed || state !is UpdateState.UpdateAvailable) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable {
                        if (state is UpdateState.UpdateAvailable) {
                            AppUpdater.startDownloadAndInstall(context)
                        } else if (state is UpdateState.Error) {
                            // Retry on click if it was an error
                            AppUpdater.checkForUpdate(true, context)
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when(state) {
                        is UpdateState.Error -> MaterialTheme.colorScheme.errorContainer
                        is UpdateState.Downloading -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                    contentColor = when(state) {
                        is UpdateState.Error -> MaterialTheme.colorScheme.onErrorContainer
                        is UpdateState.Downloading -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (state is UpdateState.Error) MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (state) {
                                        is UpdateState.Downloading -> Icons.Filled.CloudDownload
                                        is UpdateState.Error -> Icons.Filled.Warning
                                        else -> Icons.Filled.SystemUpdate
                                    },
                                    contentDescription = "Status Icon",
                                    tint = if (state is UpdateState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                val title = when (state) {
                                    is UpdateState.Checking -> "Checking for Updates"
                                    is UpdateState.Error -> "Update Check Failed"
                                    is UpdateState.Downloading -> "Downloading Update"
                                    is UpdateState.Installing -> "Installing Update"
                                    else -> "App Update Ready!"
                                }
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (state is UpdateState.Error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                val subtitle = when (val s = state) {
                                    is UpdateState.Checking -> "Please wait a moment..."
                                    is UpdateState.UpdateAvailable -> "New version ready. Tap to update."
                                    is UpdateState.Downloading -> "Downloading: ${(s.progress * 100).toInt()}%"
                                    is UpdateState.Installing -> "Almost there..."
                                    is UpdateState.Error -> s.message
                                    else -> "An update is available."
                                }
                                Text(
                                    text = subtitle,
                                    fontSize = 12.sp,
                                    color = (if (state is UpdateState.Error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer).copy(alpha = 0.8f)
                                )
                            }
                        }

                        if (state is UpdateState.UpdateAvailable) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { AppUpdater.startDownloadAndInstall(context) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = { dismissed = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                }
                            }
                        } else {
                            IconButton(onClick = { 
                                dismissed = true
                                if (state is UpdateState.Error) {
                                    // Reset to idle when dismissing error
                                    // But we can't easily do it from here without a method, 
                                    // so we'll just rely on the visibility logic
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close",
                                    tint = if (state is UpdateState.Error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    if (state is UpdateState.Downloading) {
                        val progress = (state as UpdateState.Downloading).progress
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }
}
