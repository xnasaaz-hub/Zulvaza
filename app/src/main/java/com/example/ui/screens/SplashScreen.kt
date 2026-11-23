package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ZulvazaNavy
import com.example.ui.theme.ZulvazaOrange

@Composable
fun SplashScreen(
    currentStep: Int = 0,
    statusText: String = ""
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val logoScale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.82f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "splashLogoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 480),
        label = "splashLogoAlpha"
    )

    val pulseTransition = rememberInfiniteTransition(label = "splashPulse")
    val glowScale by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .padding(32.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ZulvazaOrange.copy(alpha = 0.11f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.45f),
                    radius = size.minDimension * 0.55f
                )
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(164.dp)
                        .scale(glowScale)
                        .clip(CircleShape)
                        .background(ZulvazaOrange.copy(alpha = 0.08f))
                )
                Image(
                    painter = painterResource(id = R.drawable.img_auth_logo),
                    contentDescription = "ZULVAZA Seller Center",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(138.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "ZULVAZA",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = ZulvazaNavy,
                letterSpacing = 1.5.sp,
                modifier = Modifier.alpha(logoAlpha)
            )
            Text(
                text = "Seller Center",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = ZulvazaOrange,
                modifier = Modifier.alpha(logoAlpha)
            )
        }

        CircularProgressIndicator(
            color = ZulvazaOrange,
            strokeWidth = 2.5.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .size(26.dp)
                .alpha(logoAlpha)
        )
    }
}
