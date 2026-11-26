package app.hissa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayOutputStream

/** Decodes the picked photo (at most ~2000 px on its long side) and applies its EXIF rotation. */
private fun loadForCrop(ctx: Context, uri: Uri): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
    val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    val deg = ctx.contentResolver.openInputStream(uri)?.use {
        when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
        }
    } ?: 0f
    if (b != null && deg != 0f) Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg) }, true) else b
} catch (e: Exception) { null }

private fun encodeSquare(b: Bitmap, x: Int, y: Int, side: Int): String {
    val out = Bitmap.createScaledBitmap(Bitmap.createBitmap(b, x, y, side, side), 256, 256, true)
    val bytes = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}

/** Full-screen crop: drag and pinch the photo under a fixed square frame, then "Use photo" returns a 256 px JPEG data URL. */
@Composable
fun CropDialog(uri: Uri, onCancel: () -> Unit, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val bmp = remember(uri) { loadForCrop(ctx, uri) }
    if (bmp == null) { LaunchedEffect(uri) { onCancel() }; return }
    val img = remember(bmp) { bmp.asImageBitmap() }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val wPx = with(LocalDensity.current) { maxWidth.toPx() }
            val frame = wPx * 0.88f
            val base = frame / minOf(bmp.width, bmp.height)
            var zoom by remember { mutableFloatStateOf(1f) }
            var off by remember { mutableStateOf(Offset.Zero) }
            fun clamp(o: Offset, z: Float): Offset {
                val mx = maxOf(0f, (bmp.width * base * z - frame) / 2)
                val my = maxOf(0f, (bmp.height * base * z - frame) / 2)
                return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
            }
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTransformGestures { _, pan, gz, _ ->
                        zoom = (zoom * gz).coerceIn(1f, 6f)
                        off = clamp(off + pan, zoom)
                    }
                },
            ) {
                val s = base * zoom
                withTransform({ translate(size.width / 2 + off.x, size.height / 2 + off.y); scale(s, s, Offset.Zero) }) {
                    drawImage(
                        img, IntOffset.Zero, IntSize(bmp.width, bmp.height),
                        IntOffset(-bmp.width / 2, -bmp.height / 2), IntSize(bmp.width, bmp.height), filterQuality = FilterQuality.Medium,
                    )
                }
                val l = (size.width - frame) / 2
                val t = (size.height - frame) / 2
                val hole = Path().apply {
                    addRect(Rect(Offset.Zero, size)); addRect(Rect(Offset(l, t), Size(frame, frame))); fillType = PathFillType.EvenOdd
                }
                drawPath(hole, Color.Black.copy(alpha = 0.62f))
                drawRect(Color.White, Offset(l, t), Size(frame, frame), style = Stroke(2.dp.toPx()))
                for (i in 1..2) {
                    val g = Color.White.copy(alpha = 0.35f)
                    drawLine(g, Offset(l + frame * i / 3, t), Offset(l + frame * i / 3, t + frame), 1.dp.toPx())
                    drawLine(g, Offset(l, t + frame * i / 3), Offset(l + frame, t + frame * i / 3), 1.dp.toPx())
                }
            }
            Text("Drag and pinch to adjust", color = Color.White.copy(alpha = 0.8f), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
            // Sits under the frame (not at the screen bottom) so a dialog window taller than the screen can't clip it.
            Row(Modifier.align(Alignment.Center).offset(y = with(LocalDensity.current) { (frame / 2).toDp() } + 52.dp).fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onCancel, Modifier.height(52.dp)) { Text("Cancel", color = Color.White) }
                Spacer(Modifier.weight(1f))
                Button(
                    {
                        val s = base * zoom
                        val side = (frame / s).toInt().coerceIn(1, minOf(bmp.width, bmp.height))
                        val x = (bmp.width / 2f - off.x / s - side / 2f).toInt().coerceIn(0, bmp.width - side)
                        val y = (bmp.height / 2f - off.y / s - side / 2f).toInt().coerceIn(0, bmp.height - side)
                        onDone(encodeSquare(bmp, x, y, side))
                    },
                    Modifier.height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) { Text("Use photo") }
            }
        }
    }
}
