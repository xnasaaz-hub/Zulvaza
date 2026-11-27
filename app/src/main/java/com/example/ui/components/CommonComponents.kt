package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.ProductItem
import com.example.data.model.ProductStatus
import com.example.data.model.SellerStore
import com.example.data.model.SocialPlatform
import com.example.data.model.VerificationStatus
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedSoft
import com.example.ui.theme.SocialFacebook
import com.example.ui.theme.SocialInstagram
import com.example.ui.theme.SocialLinkedIn
import com.example.ui.theme.SocialTelegram
import com.example.ui.theme.SocialTikTok
import com.example.ui.theme.SocialWhatsApp
import com.example.ui.theme.SocialX
import com.example.ui.theme.SocialYouTube
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenSoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft
import com.example.ui.theme.ZulvazaNavy
import com.example.ui.theme.ZulvazaOrange
import com.example.util.Formatters
import java.net.URLEncoder

@Composable
fun ZulvazaLogoImage(
    modifier: Modifier = Modifier,
    onDarkSurface: Boolean = false,
    height: Dp = 36.dp
) {
    val wordmarkColor = if (onDarkSurface) Color.White else ZulvazaNavy
    val pillColor = Color(0xFFFC4801)
    Row(
        modifier = modifier.height(height),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_seller_mark),
            contentDescription = "ZULVAZA Seller Center",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(height)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = "ZULVAZA",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    fontSize = (height.value * 0.44f).coerceIn(13f, 20f).sp,
                    lineHeight = (height.value * 0.48f).coerceIn(14f, 22f).sp
                ),
                color = wordmarkColor,
                maxLines = 1
            )
            Surface(
                color = pillColor,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "Seller Center",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (height.value * 0.27f).coerceIn(9f, 12f).sp,
                        lineHeight = (height.value * 0.30f).coerceIn(10f, 13f).sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun OfflineBanner(isOnline: Boolean) {
    AnimatedVisibility(
        visible = !isOnline,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Surface(
            color = ErrorRed,
            contentColor = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = "Offline",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "No internet connection — showing offline cached data",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun VerificationStatusBadge(status: VerificationStatus, modifier: Modifier = Modifier) {
    val (bg, fg, icon) = when (status) {
        VerificationStatus.APPROVED -> Triple(SuccessGreenSoft, SuccessGreen, Icons.Default.CheckCircle)
        VerificationStatus.PENDING -> Triple(WarningAmberSoft, WarningAmber, Icons.Default.HourglassTop)
        VerificationStatus.REJECTED, VerificationStatus.SUSPENDED -> Triple(ErrorRedSoft, ErrorRed, Icons.Default.Error)
        VerificationStatus.UNSUBMITTED -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Error)
    }
    StatusPill(text = status.label, bg = bg, fg = fg, icon = icon, modifier = modifier)
}

@Composable
fun ProductStatusBadge(status: ProductStatus, modifier: Modifier = Modifier) {
    val (bg, fg, icon) = when (status) {
        ProductStatus.APPROVED -> Triple(SuccessGreenSoft, SuccessGreen, Icons.Default.CheckCircle)
        ProductStatus.PENDING -> Triple(WarningAmberSoft, WarningAmber, Icons.Default.HourglassTop)
        ProductStatus.REJECTED, ProductStatus.SUSPENDED -> Triple(ErrorRedSoft, ErrorRed, Icons.Default.Error)
    }
    StatusPill(text = status.label, bg = bg, fg = fg, icon = icon, modifier = modifier)
}

@Composable
fun StatusPill(
    text: String,
    bg: Color,
    fg: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp)
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = ZulvazaOrange.copy(alpha = 0.12f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ZulvazaOrange,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun OtpCodeBoxes(
    code: String,
    onCodeChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    enabled: Boolean = true,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = code,
            onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() }.take(6)
                val shouldSubmit = filtered.length == 6 && filtered != code
                onCodeChange(filtered)
                if (shouldSubmit && enabled) {
                    onComplete(filtered)
                }
            },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("otp_code_input"),
            decorationBox = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    repeat(6) { index ->
                        val char = code.getOrNull(index)?.toString() ?: ""
                        val isActive = enabled && index == code.length.coerceAtMost(5) && code.length < 6
                        val isFilled = char.isNotEmpty()
                        val boxBg = when {
                            isError -> ErrorRedSoft
                            isFilled -> ZulvazaOrange.copy(alpha = 0.09f)
                            isActive -> ZulvazaOrange.copy(alpha = 0.05f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                        val borderColor = when {
                            isError -> ErrorRed
                            isActive -> ZulvazaOrange
                            isFilled -> ZulvazaNavy.copy(alpha = 0.78f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = boxBg,
                            shadowElevation = if (isActive || isFilled) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                                .border(
                                    width = if (isActive || isError) 2.dp else 1.25.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(14.dp)
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isFilled) {
                                    Text(
                                        text = char,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isError) ErrorRed else ZulvazaNavy
                                    )
                                } else if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 12.dp)
                                            .width(18.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(ZulvazaOrange)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

// ======================= 5-DIGIT SECURITY PIN SYSTEM =======================

@Composable
fun PinDotsRow(
    enteredLength: Int,
    pinLength: Int = 5,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pinLength) { idx ->
            val filled = idx < enteredLength
            val fillCol = when {
                isError -> ErrorRed
                filled -> ZulvazaOrange
                else -> Color.Transparent
            }
            val borderCol = when {
                isError -> ErrorRed
                filled -> ZulvazaOrange
                else -> MaterialTheme.colorScheme.outline
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(fillCol)
                    .border(2.dp, borderCol, CircleShape)
            )
        }
    }
}

@Composable
fun ZulvazaPinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9')
    )
    Column(
        modifier = modifier.widthIn(max = 310.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { rowDigits ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
            ) {
                rowDigits.forEach { digit ->
                    PinKeyTile(
                        label = digit.toString(),
                        enabled = enabled,
                        onClick = { onDigit(digit) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pin_key_$digit")
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
        ) {
            Spacer(modifier = Modifier.weight(1f))
            PinKeyTile(
                label = "0",
                enabled = enabled,
                onClick = { onDigit('0') },
                modifier = Modifier
                    .weight(1f)
                    .testTag("pin_key_0")
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = enabled, onClick = onBackspace)
                    .testTag("pin_key_backspace")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = ZulvazaNavy,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PinKeyTile(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AppLockPinOverlay(
    onVerifyPin: (String, (Boolean) -> Unit) -> Unit,
    onSignOutFallback: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ZulvazaOrange.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, ZulvazaOrange.copy(alpha = 0.3f)),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "App locked",
                        tint = ZulvazaOrange,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ZULVAZA Security PIN",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter your 5-digit PIN to unlock Seller Center",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(22.dp))
            PinDotsRow(enteredLength = pin.length, isError = isError)
            Spacer(modifier = Modifier.height(10.dp))
            if (isError) {
                Text(
                    text = "Incorrect PIN — try again",
                    style = MaterialTheme.typography.labelMedium,
                    color = ErrorRed,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ZulvazaPinKeypad(
                onDigit = { digit ->
                    if (pin.length < 5) {
                        isError = false
                        val next = pin + digit
                        pin = next
                        if (next.length == 5) {
                            onVerifyPin(next) { ok ->
                                if (!ok) {
                                    isError = true
                                    pin = ""
                                }
                            }
                        }
                    }
                },
                onBackspace = {
                    if (pin.isNotEmpty()) {
                        isError = false
                        pin = pin.dropLast(1)
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onSignOutFallback) {
                Text("Switch Account / Log Out")
            }
        }
    }
}

@Composable
fun PinSetupModal(
    title: String = "Set 5-Digit Security PIN",
    subtitle: String = "Protect your seller account when opening ZULVAZA Seller Center.",
    allowSkip: Boolean = true,
    onCompleted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var stepConfirm by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = ZulvazaOrange.copy(alpha = 0.14f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = ZulvazaOrange, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (stepConfirm) "Confirm 5-Digit PIN" else title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (stepConfirm) "Re-enter the same 5-digit PIN to confirm" else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))

                val activeLen = if (stepConfirm) confirmPin.length else firstPin.length
                PinDotsRow(enteredLength = activeLen, isError = errorMsg != null)

                Spacer(modifier = Modifier.height(10.dp))
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = ErrorRed,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
                ZulvazaPinKeypad(
                    onDigit = { digit ->
                        errorMsg = null
                        if (!stepConfirm) {
                            if (firstPin.length < 5) {
                                firstPin += digit
                                if (firstPin.length == 5) {
                                    stepConfirm = true
                                    confirmPin = ""
                                }
                            }
                        } else {
                            if (confirmPin.length < 5) {
                                confirmPin += digit
                                if (confirmPin.length == 5) {
                                    if (confirmPin == firstPin) {
                                        onCompleted(firstPin)
                                    } else {
                                        errorMsg = "PINs did not match. Enter PIN again."
                                        firstPin = ""
                                        confirmPin = ""
                                        stepConfirm = false
                                    }
                                }
                            }
                        }
                    },
                    onBackspace = {
                        errorMsg = null
                        if (stepConfirm) {
                            if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                        } else {
                            if (firstPin.isNotEmpty()) firstPin = firstPin.dropLast(1)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (stepConfirm) {
                        TextButton(onClick = {
                            stepConfirm = false
                            firstPin = ""
                            confirmPin = ""
                            errorMsg = null
                        }) {
                            Text("Start Over")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(if (allowSkip) "Not Now" else "Cancel")
                    }
                }
            }
        }
    }
}

@Composable
fun VerifyCurrentPinModal(
    title: String,
    subtitle: String,
    onVerifyPin: (String, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = ZulvazaNavy.copy(alpha = 0.12f),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = ZulvazaNavy, modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                PinDotsRow(enteredLength = pin.length, isError = isError)
                Spacer(modifier = Modifier.height(10.dp))
                if (isError) {
                    Text(
                        text = "Incorrect PIN — try again",
                        style = MaterialTheme.typography.labelMedium,
                        color = ErrorRed,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                ZulvazaPinKeypad(
                    onDigit = { digit ->
                        if (pin.length < 5) {
                            isError = false
                            val next = pin + digit
                            pin = next
                            if (next.length == 5) {
                                onVerifyPin(next) { ok ->
                                    if (!ok) {
                                        isError = true
                                        pin = ""
                                    }
                                }
                            }
                        }
                    },
                    onBackspace = {
                        if (pin.isNotEmpty()) {
                            isError = false
                            pin = pin.dropLast(1)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 200.dp
) {
    val bitmap = remember(content) { Formatters.generateQrBitmap(content, 512) }
    if (bitmap != null) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 2.dp,
            modifier = modifier.size(sizeDp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Authenticator QR Code",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            )
        }
    }
}

fun socialPlatformStyle(platformKey: String): Triple<Color, String, ImageVector> {
    return when (SocialPlatform.fromKey(platformKey)) {
        SocialPlatform.FACEBOOK -> Triple(SocialFacebook, "f", Icons.Default.Public)
        SocialPlatform.INSTAGRAM -> Triple(SocialInstagram, "IG", Icons.Default.CameraAlt)
        SocialPlatform.WHATSAPP -> Triple(SocialWhatsApp, "WA", Icons.AutoMirrored.Filled.Chat)
        SocialPlatform.TIKTOK -> Triple(SocialTikTok, "TT", Icons.Default.MusicNote)
        SocialPlatform.YOUTUBE -> Triple(SocialYouTube, "YT", Icons.Default.PlayArrow)
        SocialPlatform.X -> Triple(SocialX, "X", Icons.Default.Close)
        SocialPlatform.LINKEDIN -> Triple(SocialLinkedIn, "in", Icons.Default.Work)
        SocialPlatform.WEBSITE -> Triple(ZulvazaOrange, "WEB", Icons.Default.Language)
    }
}

@Composable
fun SocialBrandBadge(
    platform: String,
    label: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sp = SocialPlatform.fromKey(platform)
    val (color, glyph, vecIcon) = socialPlatformStyle(platform)
    val svgRes = when (sp) {
        SocialPlatform.FACEBOOK -> R.drawable.ic_social_facebook
        SocialPlatform.INSTAGRAM -> R.drawable.ic_social_instagram
        SocialPlatform.WHATSAPP -> R.drawable.ic_whatsapp
        SocialPlatform.X -> R.drawable.ic_social_x
        else -> null
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                when {
                    svgRes == R.drawable.ic_whatsapp -> {
                        Icon(
                            painter = painterResource(id = svgRes),
                            contentDescription = sp.displayName,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    svgRes != null -> {
                        Icon(
                            painter = painterResource(id = svgRes),
                            contentDescription = sp.displayName,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = vecIcon,
                            contentDescription = glyph,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            if (!label.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareProductBottomSheet(
    product: ProductItem,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit
) {
    val shareUrl = remember(product) {
        Formatters.buildProductShareUrl(product.name, product.productCode)
    }
    val shareText = "Check out ${product.name} (${Formatters.formatPkr(product.price)}) on ZULVAZA: $shareUrl"
    ZulvazaShareSheet(
        sheetTitle = "Share Product",
        subjectTitle = product.name,
        shareUrl = shareUrl,
        shareText = shareText,
        onDismiss = onDismiss,
        onCopied = onCopied
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareStoreBottomSheet(
    store: SellerStore?,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit
) {
    val slugHandle = store?.slug?.takeIf { it.isNotBlank() }
    val storeName = store?.shopName?.takeIf { it.isNotBlank() } ?: "ZULVAZA Storefront"
    val shareUrl = if (slugHandle != null) "https://zulvaza.com/shop/$slugHandle" else "https://zulvaza.com"
    val shareText = "Shop $storeName on ZULVAZA: $shareUrl"
    ZulvazaShareSheet(
        sheetTitle = "Share Store",
        subjectTitle = storeName,
        shareUrl = shareUrl,
        shareText = shareText,
        onDismiss = onDismiss,
        onCopied = onCopied
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZulvazaShareSheet(
    sheetTitle: String,
    subjectTitle: String,
    shareUrl: String,
    shareText: String,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(sheetTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subjectTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Copy link box
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = shareUrl,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            copyToClipboard(context, sheetTitle, shareUrl)
                            onCopied("Link copied to clipboard")
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ShareChannelSvgTile(
                    title = "WhatsApp",
                    iconRes = R.drawable.ic_whatsapp,
                    color = SocialWhatsApp,
                    tintWhite = false,
                    onClick = {
                        val enc = URLEncoder.encode(shareText, "UTF-8")
                        openExternalUrl(context, "https://wa.me/?text=$enc")
                    }
                )
                ShareChannelSvgTile(
                    title = "Facebook",
                    iconRes = R.drawable.ic_social_facebook,
                    color = SocialFacebook,
                    tintWhite = true,
                    onClick = {
                        val enc = URLEncoder.encode(shareUrl, "UTF-8")
                        openExternalUrl(context, "https://www.facebook.com/sharer/sharer.php?u=$enc")
                    }
                )
                ShareChannelSvgTile(
                    title = "Instagram",
                    iconRes = R.drawable.ic_social_instagram,
                    color = SocialInstagram,
                    tintWhite = true,
                    onClick = {
                        copyToClipboard(context, sheetTitle, shareText)
                        onCopied("Link copied — share in Instagram")
                        val igIntent = context.packageManager.getLaunchIntentForPackage("com.instagram.android")
                        if (igIntent != null) {
                            runCatching { context.startActivity(igIntent) }
                        } else {
                            openExternalUrl(context, "https://www.instagram.com/")
                        }
                    }
                )
                ShareChannelSvgTile(
                    title = "X",
                    iconRes = R.drawable.ic_social_x,
                    color = SocialX,
                    tintWhite = true,
                    onClick = {
                        val encText = URLEncoder.encode(shareText, "UTF-8")
                        openExternalUrl(context, "https://twitter.com/intent/tweet?text=$encText")
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, subjectTitle)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share via"))
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("More sharing options")
            }
        }
    }
}

@Composable
private fun ShareChannelSvgTile(
    title: String,
    iconRes: Int,
    color: Color,
    tintWhite: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = if (tintWhite) Color.White else Color.Unspecified,
                modifier = Modifier.size(if (tintWhite) 24.dp else 34.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

fun openExternalUrl(context: Context, rawUrl: String) {
    val formatted = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
        rawUrl
    } else {
        "https://$rawUrl"
    }
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formatted))
        context.startActivity(intent)
    }
}
