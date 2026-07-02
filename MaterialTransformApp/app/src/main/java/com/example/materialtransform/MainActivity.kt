package com.example.materialtransform

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

// The base URL of our free Hugging Face Space, following HF's convention:
// https://{username}-{spacename in lowercase, spaces as hyphens}.hf.space
private const val SPACE_BASE_URL = "https://3diiamond-materialtransform.hf.space"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedMaterial by remember { mutableStateOf<MaterialType?>(null) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            resultBitmap = null
            errorMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "تبدیل عکس به متریال",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "این اپ رایگان است و مستقیم از Hugging Face Space شخصی شما استفاده می‌کند.",
            fontSize = 11.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Image picker
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEFEFEF))
                .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                .clickable { imagePickerLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageUri != null) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = "عکس انتخاب‌شده",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("برای انتخاب عکس ضربه بزنید", color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "متریال را انتخاب کنید:",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(180.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(MaterialType.values()) { material ->
                val isSelected = material == selectedMaterial
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFF6750A4) else Color(0xFFF3F0F7))
                        .clickable { selectedMaterial = material }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = material.emoji, fontSize = 24.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = material.displayNameFa,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = if (isSelected) Color.White else Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                errorMessage = null
                when {
                    selectedImageUri == null -> errorMessage = "لطفاً یک عکس انتخاب کنید."
                    selectedMaterial == null -> errorMessage = "لطفاً یک متریال انتخاب کنید."
                    else -> {
                        val material = selectedMaterial!!
                        val uri = selectedImageUri!!
                        isLoading = true
                        scope.launch {
                            try {
                                val bitmap = loadBitmapFromUri(context, uri)
                                if (bitmap == null) {
                                    errorMessage = "خطا در بارگذاری عکس."
                                    isLoading = false
                                    return@launch
                                }
                                val client = HuggingFaceSpaceApiClient(SPACE_BASE_URL)
                                val result = client.transform(bitmap, material.gradioLabel())
                                when (result) {
                                    is TransformResult.Success -> resultBitmap = result.bitmap
                                    is TransformResult.Failure -> errorMessage = result.message
                                }
                            } catch (e: Exception) {
                                errorMessage = "خطای غیرمنتظره: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("در حال تبدیل... (ممکن است تا ۱ دقیقه طول بکشد)")
            } else {
                Text("تبدیل کن", fontSize = 16.sp)
            }
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
                textAlign = TextAlign.Center
            )
        }

        resultBitmap?.let { bmp ->
            Spacer(modifier = Modifier.height(20.dp))
            Text("نتیجه:", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "نتیجه تبدیل",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { saveBitmapToGallery(context, bmp) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("💾")
                Spacer(modifier = Modifier.width(8.dp))
                Text("ذخیره در گالری")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream)
        }
    } catch (e: Exception) {
        null
    }
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    try {
        val filename = "material_transform_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MaterialTransform")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                out.write(stream.toByteArray())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Toast.makeText(context, "تصویر در گالری ذخیره شد", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "ذخیره‌سازی ناموفق بود", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "خطا در ذخیره‌سازی: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
