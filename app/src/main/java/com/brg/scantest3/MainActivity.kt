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
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : ComponentActivity() {

    lateinit var storage: FirebaseStorage

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

        FirebaseApp.initializeApp(this)

        storage = Firebase.storage("gs://image-upload-9e41f.appspot.com")

        setContent {

            val viewModel = viewModel<MainViewModel>()

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
                    HomeScreen(
                        onClick = {
                            launcher.launch(Manifest.permission.CAMERA)
                        }
                    )
//                    ScanScreen(navController = navController, getOutputDirectory = { getOutputDirectory() }, executor = getExecutor(), storage)
                }
                composable(route = "Scan") {
                    ScanScreen(navController = navController, getOutputDirectory = { getOutputDirectory() }, executor = getExecutor(), storage, viewModel)
                }
                composable(route = "Webview") {
                    WebViewScreen(url = viewModel.url.value.toString())
                }
            }
        }
    }
}

@Composable
fun rememberWebView(url: String): WebView {
    Log.d("url", url)
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
            loadUrl(url)
        }
    }
    return webView
}

@Composable
fun WebViewScreen(url: String) {
    val webview = rememberWebView(url)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { webview },
    )
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
fun ScanScreen(navController: NavController, getOutputDirectory: () -> File, executor: Executor, storage: FirebaseStorage, viewModel: MainViewModel) {

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

    val buttonText = if(viewModel.isLoading.value) "이미지를 업로드중입니다..." else "카드스캔"

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
                    viewModel.isLoading.value = true

                    val outputDirectory = getOutputDirectory()

                    val fileName = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                        .format(System.currentTimeMillis()) + ".jpg"

                    val photoFile = File(
                        outputDirectory,
                        fileName
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
                                val msg = "Photo capture succeeded: ${outputFileResults.savedUri.toString()}"
                                Log.d("success", msg)

                                val storageRef = storage.reference

                                var file = Uri.fromFile(outputFileResults.savedUri?.toFile())

                                val imageRef = storageRef.child("images/${file.lastPathSegment}")
                                var uploadTask = imageRef.putFile(file)

                                val ref = storageRef.child("images/${fileName}")
                                uploadTask = ref.putFile(file)

                                val urlTask = uploadTask.continueWithTask { task ->
                                    if (!task.isSuccessful) {
                                        task.exception?.let {
                                            throw it
                                        }
                                    }
                                    ref.downloadUrl
                                }.addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val downloadUri = task.result
                                        Log.d("downloadUri", downloadUri.toString())
                                        viewModel.url.value = "https://brg-test.vercel.app/webview?imageUrl=${downloadUri.toString()}"
                                        navController.navigate("Webview")
                                    }
                                    viewModel.isLoading.value = false
                                }
                            }
                        }
                    )
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(text = buttonText)
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

class MainViewModel : ViewModel() {
    val url = mutableStateOf("")
    val isLoading = mutableStateOf(false)
}
