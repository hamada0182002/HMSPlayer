package com.hms.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * The basic focusable/clickable surface of the whole app.
 * Touch  -> normal tap.
 * Remote -> D-pad focuses it: it grows slightly and gets an orange outline (animated).
 */
@Composable
fun FocusBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(150), label = "focusScale")
    val fr = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(fr)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .then(if (focused) Modifier.border(3.dp, Orange, shape) else Modifier),
        content = content
    )
}

@Composable
fun IconBtn(
    icon: ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Color.White,
    filled: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    FocusBox(modifier.size(size), CircleShape, onClick, focusRequester) {
        Box(
            Modifier.fillMaxSize().background(
                if (filled) Brush.linearGradient(listOf(OrangeSoft, OrangeDeep)) else SolidColor(Color.White.copy(alpha = .14f))
            )
        )
        Icon(
            icon, desc,
            tint = if (filled) Color.Black else tint,
            modifier = Modifier.align(Alignment.Center).size(size * 0.55f)
        )
    }
}

@Composable
fun HmsButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    onClick: () -> Unit
) {
    FocusBox(modifier.height(44.dp), RoundedCornerShape(22.dp), onClick) {
        Box(
            Modifier.matchParentSize().background(
                if (primary) Brush.linearGradient(listOf(OrangeSoft, Orange, OrangeDeep)) else SolidColor(Surface2)
            )
        )
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, null, tint = if (primary) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text, color = if (primary) Color.Black else Color.White,
                fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1
            )
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onClick = onBack)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextMain,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
fun LoadingBox(text: String = "جاري التحميل...") {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = Orange, strokeWidth = 4.dp)
        Spacer(Modifier.height(12.dp))
        Text(text, color = TextMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorBox(msg: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = Danger, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text(msg, color = TextMain, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        HmsButton("إعادة المحاولة", icon = Icons.Default.Refresh, onClick = onRetry)
    }
}

@Composable
fun EmptyBox(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = TextMuted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = .7f), contentColor = Color.White) {
        Text(text, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), textAlign = TextAlign.Center)
    }
}

/** Poster (movies / series / continue) or logo card (live channels). */
@Composable
fun Poster(
    title: String,
    img: String?,
    modifier: Modifier = Modifier,
    aspect: Float = 2f / 3f,
    fit: Boolean = false,
    progress: Float? = null,
    badge: String? = null,
    onClick: () -> Unit
) {
    Column(modifier) {
        FocusBox(Modifier.fillMaxWidth().aspectRatio(aspect), RoundedCornerShape(14.dp), onClick) {
            Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Surface2, Surface1))),
                contentAlignment = Alignment.Center
            ) {
                Text(title.take(1), color = TextMuted.copy(alpha = .6f), fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            if (!img.isNullOrBlank()) {
                AsyncImage(
                    model = img, contentDescription = title,
                    contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (fit) 10.dp else 0.dp)
                )
            }
            if (badge != null) {
                Surface(
                    Modifier.align(Alignment.TopStart).padding(6.dp),
                    shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = .72f)
                ) { Text(badge, fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) }
            }
            if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp),
                    color = if (progress >= 1f) Ok else Orange,
                    trackColor = Color.White.copy(alpha = .25f)
                )
            }
        }
        Text(
            title, color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
    }
}

/** Fade + slide-up entrance, delayed by [index] (nice staggered intro for tiles / cards). */
@Composable
fun StaggerIn(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var vis by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(70L * index); vis = true }
    AnimatedVisibility(
        visible = vis, modifier = modifier,
        enter = fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 6 }
    ) { content() }
}

/** Dark rounded dialog, always right-to-left. */
@Composable
fun DialogShell(
    title: String,
    onDismiss: () -> Unit,
    widthFraction: Float = 0.5f,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                Modifier.fillMaxWidth(if (isCompact()) maxOf(widthFraction, 0.92f) else widthFraction),
                shape = RoundedCornerShape(22.dp), color = Surface1, contentColor = TextMain
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}
