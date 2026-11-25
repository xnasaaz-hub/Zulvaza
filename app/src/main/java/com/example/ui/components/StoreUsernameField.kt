package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.ZulvazaOrange
import com.example.util.Formatters
import kotlinx.coroutines.delay

enum class StoreUsernameStatus {
    IDLE,
    CHECKING,
    AVAILABLE,
    OWN,
    TAKEN,
    RESERVED,
    INVALID,
    ERROR
}

data class StoreUsernameUiState(
    val status: StoreUsernameStatus = StoreUsernameStatus.IDLE,
    val isChecking: Boolean = false,
    val canProceed: Boolean = false,
    val message: String = ""
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreUsernameField(
    username: String,
    storeName: String,
    initialSavedUsername: String = "",
    recheckTrigger: Int = 0,
    onUsernameChange: (String) -> Unit,
    onUserEdited: () -> Unit,
    onCheckUsername: suspend (String) -> String,
    onStateChange: (StoreUsernameUiState) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    testTag: String = "store_username_input"
) {
    var latestSeq by remember { mutableIntStateOf(0) }
    var uiState by remember { mutableStateOf(StoreUsernameUiState()) }

    fun publishState(next: StoreUsernameUiState) {
        uiState = next
        onStateChange(next)
    }

    val normalizedSaved = remember(initialSavedUsername) {
        initialSavedUsername.trim().lowercase()
    }

    LaunchedEffect(username, recheckTrigger, normalizedSaved) {
        val trimmed = username.trim().lowercase()
        when {
            trimmed.isEmpty() -> {
                latestSeq++
                publishState(
                    StoreUsernameUiState(
                        status = StoreUsernameStatus.IDLE,
                        isChecking = false,
                        canProceed = false,
                        message = "3–30 characters: lowercase letters, numbers, or hyphens"
                    )
                )
            }
            normalizedSaved.isNotEmpty() && trimmed == normalizedSaved -> {
                latestSeq++
                publishState(
                    StoreUsernameUiState(
                        status = StoreUsernameStatus.OWN,
                        isChecking = false,
                        canProceed = true,
                        message = ""
                    )
                )
            }
            !Formatters.isValidStoreUsernameFormat(trimmed) -> {
                latestSeq++
                publishState(
                    StoreUsernameUiState(
                        status = StoreUsernameStatus.INVALID,
                        isChecking = false,
                        canProceed = false,
                        message = when {
                            trimmed.length < 3 -> "Username must be at least 3 characters"
                            trimmed.startsWith("-") || trimmed.endsWith("-") -> "Cannot start or end with a hyphen"
                            trimmed.contains("--") -> "Cannot use consecutive hyphens (--)"
                            else -> "Use 3–30 lowercase letters, numbers, or single hyphens"
                        }
                    )
                )
            }
            Formatters.isReservedStoreUsername(trimmed) -> {
                latestSeq++
                publishState(
                    StoreUsernameUiState(
                        status = StoreUsernameStatus.RESERVED,
                        isChecking = false,
                        canProceed = false,
                        message = "\"@$trimmed\" is a reserved system username"
                    )
                )
            }
            else -> {
                val seq = ++latestSeq
                publishState(
                    StoreUsernameUiState(
                        status = StoreUsernameStatus.CHECKING,
                        isChecking = true,
                        canProceed = false,
                        message = "Checking availability for @$trimmed..."
                    )
                )
                delay(350)
                if (seq != latestSeq) return@LaunchedEffect
                try {
                    val result = onCheckUsername(trimmed)
                    if (seq != latestSeq) return@LaunchedEffect
                    val next = when (result) {
                        "available" -> StoreUsernameUiState(
                            status = StoreUsernameStatus.AVAILABLE,
                            isChecking = false,
                            canProceed = true,
                            message = "Available • zulvaza.com/shop/$trimmed"
                        )
                        "own" -> StoreUsernameUiState(
                            status = StoreUsernameStatus.OWN,
                            isChecking = false,
                            canProceed = true,
                            message = ""
                        )
                        "taken" -> StoreUsernameUiState(
                            status = StoreUsernameStatus.TAKEN,
                            isChecking = false,
                            canProceed = false,
                            message = "This username is taken"
                        )
                        "reserved" -> StoreUsernameUiState(
                            status = StoreUsernameStatus.RESERVED,
                            isChecking = false,
                            canProceed = false,
                            message = "\"@$trimmed\" is reserved by ZULVAZA"
                        )
                        "invalid" -> StoreUsernameUiState(
                            status = StoreUsernameStatus.INVALID,
                            isChecking = false,
                            canProceed = false,
                            message = "Use 3–30 lowercase letters, numbers, or single hyphens"
                        )
                        else -> StoreUsernameUiState(
                            status = StoreUsernameStatus.ERROR,
                            isChecking = false,
                            canProceed = false,
                            message = "Unexpected response ($result). Please try again."
                        )
                    }
                    publishState(next)
                } catch (_: Exception) {
                    if (seq != latestSeq) return@LaunchedEffect
                    publishState(
                        StoreUsernameUiState(
                            status = StoreUsernameStatus.ERROR,
                            isChecking = false,
                            canProceed = false,
                            message = "Could not verify username. Check connection and try again."
                        )
                    )
                }
            }
        }
    }

    val isErrorState = uiState.status in setOf(
        StoreUsernameStatus.TAKEN,
        StoreUsernameStatus.RESERVED,
        StoreUsernameStatus.INVALID,
        StoreUsernameStatus.ERROR
    )
    val isSuccessState = uiState.status == StoreUsernameStatus.AVAILABLE ||
        uiState.status == StoreUsernameStatus.OWN

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = username,
            onValueChange = { raw ->
                onUserEdited()
                onUsernameChange(Formatters.sanitizeStoreUsernameTyping(raw))
            },
            label = { Text("Store username") },
            placeholder = { Text("your-store-name") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.AlternateEmail,
                    contentDescription = null,
                    tint = if (isSuccessState) SuccessGreen else ZulvazaOrange
                )
            },
            trailingIcon = {
                when {
                    uiState.isChecking -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = ZulvazaOrange
                        )
                    }
                    isSuccessState && uiState.message.isNotBlank() -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Username available",
                            tint = SuccessGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    isErrorState -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Username unavailable",
                            tint = ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            isError = isErrorState,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = fieldModifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag(testTag)
        )

        if (uiState.message.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = uiState.message,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    isSuccessState -> SuccessGreen
                    isErrorState -> ErrorRed
                    uiState.isChecking -> ZulvazaOrange
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (isSuccessState || isErrorState) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("${testTag}_status")
            )
        }

        if (uiState.status == StoreUsernameStatus.TAKEN) {
            val suggestions = remember(storeName, username) {
                Formatters.buildUsernameSuggestions(storeName, username)
            }
            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ZulvazaOrange.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, ZulvazaOrange.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    onUserEdited()
                                    onUsernameChange(suggestion)
                                }
                                .testTag("username_suggestion_$suggestion")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "@$suggestion",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ZulvazaOrange,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
