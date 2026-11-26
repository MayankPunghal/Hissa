package app.hissa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a picked or captured photo into a JPEG data URL small enough to store with an expense
 * (long side at most 1280 px, under about 450 KB as text). Returns null when the file can't be read.
 */
suspend fun encodePhoto(ctx: Context, uri: Uri): String? = withContext(Dispatchers.Default) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
        var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@withContext null
        val deg = ctx.contentResolver.openInputStream(uri)?.use {
            when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        } ?: 0f
        if (deg != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
        val long = maxOf(bmp.width, bmp.height)
        if (long > 1280) { val s = 1280f / long; bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true) }
        var quality = 72
        var out: String? = null
        while (out == null) {
            val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            val text = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (text.length < 450_000) out = text
            else if (quality > 45) quality -= 10
            else bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * 0.8f).toInt(), (bmp.height * 0.8f).toInt(), true)
        }
        out
    } catch (e: Exception) { null }
}

/** Full-screen photo: tap anywhere or the close button to leave. */
@Composable
fun PhotoViewer(dataUrl: String, onClose: () -> Unit) {
    val bmp = decodeGroupImage(dataUrl)
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)).clickable(MutableInteractionSource(), null, onClick = onClose), Alignment.Center) {
            if (bmp != null) Image(bmp, contentDescription = "Expense photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
            IconButton(onClose, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White) }
        }
    }
}
