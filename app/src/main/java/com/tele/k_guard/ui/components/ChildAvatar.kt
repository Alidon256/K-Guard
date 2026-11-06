package com.tele.k_guard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * A child's photo in a circle. Shows their initials on a colour picked from their name while the photo
 * loads, when there is no photo, and if it fails to load, so there is never an empty circle. Photos are
 * cached in memory and on disk by Coil.
 *
 * Only http(s) URLs are loaded: a `content://` address saved by older versions only ever worked on the
 * phone that picked it, so it is treated as "no photo".
 */
@Composable
fun ChildAvatar(
    name: String,
    photoUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    /** A photo just picked on this phone, shown before it has been uploaded. */
    localPreview: Any? = null
) {
    val model: Any? = localPreview ?: photoUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    var loaded by remember(model) { mutableStateOf(false) }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(colorFor(name)),
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) {
            Text(
                initials(name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp
            )
        }
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(model).crossfade(true).build(),
                contentDescription = "$name's photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                onState = { loaded = it is AsyncImagePainter.State.Success }
            )
        }
    }
}

fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

private val PALETTE = listOf(
    Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFF4511E),
    Color(0xFF3949AB), Color(0xFF6D4C41), Color(0xFFD81B60), Color(0xFF43A047)
)

private fun colorFor(name: String): Color = PALETTE[(name.trim().lowercase().hashCode() and 0x7fffffff) % PALETTE.size]

/** Avatar size presets used across the app. */
object AvatarSize {
    val Small = 32.dp
    val Medium = 48.dp
    val Large = 96.dp
}

@Suppress("unused")
private fun keepTheme() = MaterialTheme
