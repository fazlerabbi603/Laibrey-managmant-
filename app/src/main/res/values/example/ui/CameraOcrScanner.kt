package com.example.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraOcrScannerDialog(
    onDismiss: () -> Unit,
    onTitleDetected: (String) -> Unit
) {
    val context = LocalContext.current
    var isSimulatedScanner by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F0F))
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
                Text(
                    text = "📷 BOOK Ocr SCANNER",
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp
                )
                // Switch Mode
                Button(
                    onClick = { isSimulatedScanner = !isSimulatedScanner },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSimulatedScanner) Color(0xFF198754) else Color(0xFF1E1E1E)
                    ),
                    modifier = Modifier.testTag("switch_ocr_mode")
                ) {
                    Text(
                        text = if (isSimulatedScanner) "Live View" else "Simulate",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp)
            ) {
                if (isSimulatedScanner) {
                    OcrSimulatorPanel(
                        onTitleSelected = { title ->
                            onTitleDetected(title)
                            onDismiss()
                        }
                    )
                } else {
                    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

                    LaunchedEffect(Unit) {
                        if (!cameraPermissionState.status.isGranted) {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    }

                    if (cameraPermissionState.status.isGranted) {
                        LiveCameraScannerView(
                            onTextDetected = { res ->
                                onTitleDetected(res)
                                onDismiss()
                            }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera Required",
                                tint = Color.LightGray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Camera dynamic parameters required to read real book covers.",
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { cameraPermissionState.launchPermissionRequest() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D6EFD))
                            ) {
                                Text("Grant Camera Permission")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveCameraScannerView(onTextDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var detectedLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, OcrImageAnalyzer { list ->
                                detectedLines = list
                            })
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalyzer
                        )
                    } catch (exc: Exception) {
                        Log.e("CameraScanner", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay focusing grid
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .align(Alignment.Center)
                    .border(2.dp, Color(0xFF0D6EFD), RoundedCornerShape(16.dp))
                    .background(Color.Transparent)
            ) {
                // Moving laser Scanner effect
                var offsetVal by remember { mutableStateOf(0f) }
                LaunchedEffect(Unit) {
                    while (true) {
                        offsetVal = 0f
                        delay(20)
                        for (i in 0..100) {
                            offsetVal = i * 2.8f
                            delay(15)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .offset(y = offsetVal.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color(0xFFFFC107), Color.Transparent)
                            )
                        )
                )
            }
        }

        // Live list of words detected
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xFF161616).copy(alpha = 0.9f))
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tap any detected title to apply automatically:",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (detectedLines.isEmpty()) {
                    Text(
                        text = "Point camera at Arabic, Bengali or English book titles... (or switch to simulated mode for immediate testing!)",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .height(130.dp)
                            .fillMaxWidth()
                    ) {
                        items(detectedLines) { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTextDetected(line) }
                                    .padding(vertical = 8.dp, horizontal = 12.dp)
                                    .background(Color(0xFF222222), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = line,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Select",
                                    tint = Color(0xFF198754),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class OcrImageAnalyzer(private val onLinesDetected: (List<String>) -> Unit) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Extract unique non-blank text lines length > 2
                    val lines = visionText.textBlocks.flatMap { block ->
                        block.lines.map { it.text.trim() }
                    }.filter { it.length > 2 && it.isNotBlank() }.distinct().take(8)

                    onLinesDetected(lines)
                }
                .addOnFailureListener {
                    // Ignore failures in real-time
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

@Composable
fun OcrSimulatorPanel(onTitleSelected: (String) -> Unit) {
    val sampleProducts = listOf(
        Pair("الموسوعة الفقهية", "ইসলামী আইনশাস্ত্রের এক অনন্য ও রাজকীয় বিশ্বকোষ (কুয়েত ওয়াকফ)"),
        Pair("Sahih al-Bukhari", "হাদীস শাস্ত্রের বিশুদ্ধ অনন্য রাজকীয় বাঁধাইকৃত সংস্করণ"),
        Pair("সোনার বাংলা কাব্যগ্রন্থ", "বাঙালির আত্মপরিচয় ও নান্দনিক চমৎকার ডিজাইনের কাব্য সংকলন"),
        Pair("রিয়াদুস সালেহীন", "বিশুদ্ধ হাদীসের অসাধারণ বাঁধাইকৃত প্রিমিয়াম সংস্করণ"),
        Pair("تفسير ابن كثير", "তাফসির শাস্ত্রের এক অতুলনীয় কালজয়ী রাজকীয় বিশ্বকোষ")
    )

    var simulatedScanInProgress by remember { mutableStateOf<String?>(null) }
    var scanProgress by remember { mutableStateOf(0f) }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E2E))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📢 প্রফেশনাল ব্রান্ড প্রমোশন এডিশন",
                    color = Color(0xFFFFC107),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "সম্মানিত গ্রাহকবৃন্দ! আমাদের লাইব্রেরিতে সংযোজিত হয়েছে বিশ্বখ্যাত রাজকীয় সব সংস্করণের বিশুদ্ধতম ও নান্দনিকভাবে বাঁধাইকৃত পণ্যসমূহ। নিচে দেওয়া যেকোনো প্রিমিয়াম সংস্করণের বইয়ের কভারে ক্লিক করে জাদুকরী OCR স্ক্যানিং প্রযুক্তি ও ডেটা এক্সট্রাকশন লাইভ টেস্ট করুন!",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        if (simulatedScanInProgress != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0D6EFD)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "স্ক্যান করা হচ্ছে: \"${simulatedScanInProgress}\"",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = scanProgress,
                        color = Color(0xFF0D6EFD),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${(scanProgress * 100).toInt()}% সম্পন্ন - এআই প্রসেসিং...",
                        color = Color(0xFFFFC107),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Text(
                text = "টেস্ট করার জন্য নীচের কভারবই গুলোর উপর ক্লিক করুন:",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sampleProducts) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E2E)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                simulatedScanInProgress = item.first
                                scanProgress = 0f
                                coroutineScope.launch {
                                    for (p in 1..100) {
                                        delay(15)
                                        scanProgress = p / 100f
                                    }
                                    onTitleSelected(item.first)
                                    simulatedScanInProgress = null
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(Color(0xFF121212), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = "Book",
                                    tint = Color(0xFF0D6EFD),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.first,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.second,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan",
                                tint = Color(0xFF198754),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
