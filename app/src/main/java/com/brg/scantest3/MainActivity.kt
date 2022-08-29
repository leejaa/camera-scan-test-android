package com.brg.scantest3

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Log.VERBOSE
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.sharp.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : ComponentActivity() {

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let{
            File(it, resources.getString(R.string.app_name)).apply{mkdirs()}}
        return if(mediaDir != null && mediaDir.exists())
            mediaDir
        else
            filesDir
    }

    private fun getExecutor(): Executor {
        return ContextCompat.getMainExecutor(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()

            var granted by remember { mutableStateOf(false) }

            val launcher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    granted = isGranted
                    if (isGranted) {
                        navController.navigate("Scan")
                    }
                }

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                granted = true
            }

            Log.d("granted", granted.toString())

            NavHost(navController = navController, startDestination = "Home") {
                composable(route = "Home") {
//                    HomeScreen(
//                        onClick = {
//                            launcher.launch(Manifest.permission.CAMERA)
//                        }
//                    )
                    ScanScreen(navController = navController, getOutputDirectory = { getOutputDirectory() }, executor = getExecutor())
                }
                composable(route = "Scan") {
                    ScanScreen(navController = navController, getOutputDirectory = { getOutputDirectory() }, executor = getExecutor())
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    onClick: () -> Unit
) {

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "홈화면") })
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Button(
                onClick = onClick,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(text = "카드스캔")
            }
        }
    }
}

@Composable
fun ScanScreen(navController: NavController, getOutputDirectory: () -> File, executor: Executor) {

    // 1
    val lensFacing = CameraSelector.LENS_FACING_BACK
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }
    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()

    // 2
    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture
        )

        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "카드스캔") },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Home",
                        modifier = Modifier.clickable {
                            navController.popBackStack()
                        }
                    )
                }
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "카드에 사이즈를 맞춰주세요",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )

            Spacer(modifier = Modifier.height(30.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .border(1.dp, Color.Black)
                    .width(250.dp)
                    .height(370.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                CameraView(previewView)
            }

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = {
                    val outputDirectory = getOutputDirectory()

                    val photoFile = File(
                        outputDirectory,
                        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                            .format(System.currentTimeMillis()) + ".jpg"
                    )

                    val outputOptions = ImageCapture
                        .OutputFileOptions
                        .Builder(photoFile)
                        .build()

                    imageCapture.takePicture(
                        outputOptions,
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onError(exception: ImageCaptureException) {
                                Log.e("error", "Photo capture failed: ${exception.message}", exception)
                            }

                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                val msg = "Photo capture succeeded: ${outputFileResults.savedUri}"
                                Log.d("success", msg)
                            }
                        }
                    )
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(text = "카드스캔")
            }
        }
    }
}

@Composable
fun CameraView(previewView: PreviewView) {
    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
        AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }

