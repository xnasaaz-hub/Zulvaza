package com.example.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.ZulvazaLogoImage
import com.example.ui.theme.ZulvazaNavy
import com.example.ui.theme.ZulvazaOrange
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    val badge: String,
    val title: String,
    val subtitle: String,
    val drawableRes: Int,
    val highlights: List<Pair<ImageVector, String>>
)

@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onLogin: () -> Unit
) {
    val slides = listOf(
        OnboardingSlide(
            badge = "SELLER STUDIO • PKR",
            title = "Build Your Branded Pakistani Storefront",
            subtitle = "Manage inventory, PKR pricing, and nationwide product discovery from one sleek studio.",
            drawableRes = R.drawable.img_onboarding_studio_1795381798975,
            highlights = listOf(
                Icons.Default.Payments to "Live PKR revenue & inventory tracking",
                Icons.Default.Analytics to "All Products management & stock alerts"
            )
        ),
        OnboardingSlide(
            badge = "VERIFIED TRUST",
            title = "Instant Credibility With Verified Seller KYC",
            subtitle = "Complete live CNIC verification once to unlock verified storefront badges and buyer trust.",
            drawableRes = R.drawable.img_seller_hero,
            highlights = listOf(
                Icons.Default.CameraAlt to "Guided live CNIC capture & GPS address",
                Icons.Default.VerifiedUser to "Verified badge shown across your store"
            )
        ),
        OnboardingSlide(
            badge = "SOCIAL COMMERCE",
            title = "Share Products & Social Bio Links Effortlessly",
            subtitle = "Showcase video reels, custom @handles, WhatsApp, Instagram, and TikTok links in seconds.",
            drawableRes = R.drawable.img_empty_catalog_1795381797836,
            highlights = listOf(
                Icons.Default.Storefront to "Custom banner, circular logo & social links",
                Icons.Default.Security to "2FA Authenticator & Passwordless OTP login"
            )
        )
    )

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 560.dp)) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZulvazaLogoImage(height = 32.dp)
                if (pagerState.currentPage < slides.lastIndex) {
                    TextButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(slides.lastIndex) }
                        }
                    ) {
                        Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Surface(
                        color = ZulvazaOrange.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "🇵🇰 Pakistan • PKR",
                            style = MaterialTheme.typography.labelMedium,
                            color = ZulvazaOrange,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Interactive Onboarding Carousel
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val slide = slides[page]
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(225.dp)
                        ) {
                            Image(
                                painter = painterResource(id = slide.drawableRes),
                                contentDescription = slide.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                ZulvazaNavy.copy(alpha = 0.72f)
                                            )
                                        )
                                    )
                            )
                            Surface(
                                color = ZulvazaOrange,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = slide.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = slide.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = slide.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                slide.highlights.forEach { (icon, text) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = ZulvazaOrange.copy(alpha = 0.12f),
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = ZulvazaOrange,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Pill Page Indicator + Next arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(slides.size) { index ->
                        val active = pagerState.currentPage == index
                        val pillWidth by animateDpAsState(
                            targetValue = if (active) 28.dp else 8.dp,
                            label = "pagerIndicator"
                        )
                        Box(
                            modifier = Modifier
                                .width(pillWidth)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) ZulvazaOrange
                                    else MaterialTheme.colorScheme.outline
                                )
                        )
                    }
                }

                if (pagerState.currentPage < slides.lastIndex) {
                    TextButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    ) {
                        Text("Next", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next slide",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Ready to launch",
                        style = MaterialTheme.typography.labelMedium,
                        color = ZulvazaOrange,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = onCreateAccount,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZulvazaOrange),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("welcome_create_account_button")
            ) {
                Text("Create Seller Account", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onLogin,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("welcome_login_button")
            ) {
                Text("Sign In to Existing Store", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
