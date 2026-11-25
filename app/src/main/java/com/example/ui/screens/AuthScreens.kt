package com.example.ui.screens

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PasswordStrength
import com.example.ui.components.OtpCodeBoxes
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.ZulvazaNavy
import com.example.ui.theme.ZulvazaOrange
import com.example.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SignUpScreen(
    otpPendingEmail: String?,
    isBusy: Boolean,
    onSignUp: (email: String, password: String) -> Unit,
    onVerifyOtp: (email: String, code: String) -> Unit,
    onResendOtp: (email: String) -> Unit,
    onCancelOtp: () -> Unit,
    onGoToLogin: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        if (otpPendingEmail != null) onCancelOtp() else onGoToLogin()
    }

    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    var otpCode by remember { mutableStateOf("") }
    var resendSeconds by remember { mutableIntStateOf(60) }

    LaunchedEffect(otpPendingEmail) {
        if (otpPendingEmail != null) {
            otpCode = ""
            resendSeconds = 60
            while (resendSeconds > 0) {
                delay(1000)
                resendSeconds--
            }
        }
    }

    val strength = remember(password) { Formatters.evaluatePasswordStrength(password) }
    val emailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordValid = password.length >= 8
    val hasLetterAndNumber = password.any { it.isDigit() } && password.any { it.isLetter() }
    val hasSymbol = password.any { !it.isLetterOrDigit() }
    val passwordsMatch = confirmPassword.isNotEmpty() && password == confirmPassword
    val canSubmit = emailValid && passwordValid && passwordsMatch && !isBusy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 520.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (otpPendingEmail != null) onCancelOtp() else onGoToLogin() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Login")
                }
            }

            AuthBrandHeader()

            Spacer(modifier = Modifier.height(18.dp))

            if (otpPendingEmail == null) {
                Text("Create Seller Account", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Register with email & password. We will verify your inbox with a 6-digit code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        val emailBringer = remember { BringIntoViewRequester() }
                        val passBringer = remember { BringIntoViewRequester() }
                        val confirmBringer = remember { BringIntoViewRequester() }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Seller email") },
                            placeholder = { Text("seller@store.pk") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            isError = email.isNotBlank() && !emailValid,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(emailBringer)
                                .onFocusEvent { if (it.isFocused) scope.launch { emailBringer.bringIntoView() } }
                                .testTag("signup_email_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Create password") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    AnimatedContent(targetState = showPassword, label = "eye1") { visible ->
                                        Icon(
                                            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (visible) "Hide password" else "Show password"
                                        )
                                    }
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(passBringer)
                                .onFocusEvent { if (it.isFocused) scope.launch { passBringer.bringIntoView() } }
                                .testTag("signup_password_input")
                        )

                        // Only show password strength meter when password is NOT empty
                        if (password.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            PasswordStrengthMeter(strength = strength, lengthOk = passwordValid)
                            Spacer(modifier = Modifier.height(8.dp))
                            PasswordChecklistRow(label = "Minimum 8 characters", met = passwordValid)
                            PasswordChecklistRow(label = "Letters & numbers recommended", met = hasLetterAndNumber)
                            PasswordChecklistRow(label = "Special symbol (!@#$%^&*)", met = hasSymbol)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showConfirm = !showConfirm }) {
                                    AnimatedContent(targetState = showConfirm, label = "eye2") { visible ->
                                        Icon(
                                            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (visible) "Hide confirm password" else "Show confirm password"
                                        )
                                    }
                                }
                            },
                            visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true,
                            isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(confirmBringer)
                                .onFocusEvent { if (it.isFocused) scope.launch { confirmBringer.bringIntoView() } }
                                .testTag("signup_confirm_password_input")
                        )

                        if (confirmPassword.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (passwordsMatch) Icons.Default.CheckCircle else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (passwordsMatch) SuccessGreen else ErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (passwordsMatch) "Passwords match" else "Passwords do not match",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (passwordsMatch) SuccessGreen else ErrorRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { onSignUp(email.trim(), password) },
                            enabled = canSubmit,
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .testTag("signup_submit_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = "Create Account & Verify Email",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                TextButton(
                    onClick = onGoToLogin,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Already have a seller account? Sign In")
                }
            } else {
                // 6-digit OTP verification step
                OtpVerifyCard(
                    title = "Verify Your Email",
                    subtitle = "Enter the 6-digit verification code sent via support.zulvaza.com to $otpPendingEmail. Verifies automatically once all 6 digits are entered.",
                    code = otpCode,
                    resendSeconds = resendSeconds,
                    isBusy = isBusy,
                    onCodeChange = { otpCode = it },
                    onVerify = { onVerifyOtp(otpPendingEmail, it) },
                    onResend = {
                        onResendOtp(otpPendingEmail)
                        resendSeconds = 60
                    },
                    onCancel = onCancelOtp,
                    verifyButtonTag = "otp_verify_button"
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(
    requiresMfa: Boolean,
    loginOtpPendingEmail: String?,
    recoveryPendingEmail: String?,
    isBusy: Boolean,
    onLogin: (email: String, password: String) -> Unit,
    onSendEmailOtp: (email: String) -> Unit,
    onVerifyEmailOtp: (email: String, code: String) -> Unit,
    onCancelEmailOtp: () -> Unit,
    onSendPasswordReset: (email: String) -> Unit,
    onVerifyRecoveryReset: (email: String, code: String, newPassword: String) -> Unit,
    onCancelRecovery: () -> Unit,
    onVerifyMfa: (code: String) -> Unit,
    onCancelMfa: () -> Unit,
    onGoToSignUp: () -> Unit,
    onBack: () -> Unit
) {
    var showForgotPasswordForm by remember { mutableStateOf(false) }

    val inSubFlow = requiresMfa || recoveryPendingEmail != null || showForgotPasswordForm
    if (inSubFlow) {
        BackHandler {
            when {
                requiresMfa -> onCancelMfa()
                recoveryPendingEmail != null -> onCancelRecovery()
                showForgotPasswordForm -> showForgotPasswordForm = false
            }
        }
    }

    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var totpCode by remember { mutableStateOf("") }

    var recoveryEmail by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }
    var recoveryNewPassword by remember { mutableStateOf("") }
    var showRecoveryPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 520.dp)) {
            if (inSubFlow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            when {
                                requiresMfa -> onCancelMfa()
                                recoveryPendingEmail != null -> onCancelRecovery()
                                showForgotPasswordForm -> showForgotPasswordForm = false
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            AuthBrandHeader()

            Spacer(modifier = Modifier.height(18.dp))

            when {
                requiresMfa -> {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ZulvazaOrange.copy(alpha = 0.14f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = ZulvazaOrange,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Two-Factor Authentication", style = MaterialTheme.typography.headlineSmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Enter the 6-digit code from your authenticator app to finish signing in.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            OtpCodeBoxes(
                                code = totpCode,
                                onCodeChange = { totpCode = it },
                                onComplete = { code -> if (!isBusy) onVerifyMfa(code) },
                                enabled = !isBusy
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = { onVerifyMfa(totpCode) },
                                enabled = totpCode.length == 6 && !isBusy,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                                    .testTag("mfa_verify_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Text("Verify Authenticator", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                recoveryPendingEmail != null -> {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ZulvazaOrange.copy(alpha = 0.14f),
                                modifier = Modifier.size(60.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.LockReset, contentDescription = null, tint = ZulvazaOrange, modifier = Modifier.size(30.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Reset Password", style = MaterialTheme.typography.headlineSmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Enter the 6-digit recovery code sent to $recoveryPendingEmail and choose your new password.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OtpCodeBoxes(
                                code = recoveryCode,
                                onCodeChange = { recoveryCode = it },
                                onComplete = {},
                                enabled = !isBusy
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            val recStrength = remember(recoveryNewPassword) {
                                Formatters.evaluatePasswordStrength(recoveryNewPassword)
                            }
                            OutlinedTextField(
                                value = recoveryNewPassword,
                                onValueChange = { recoveryNewPassword = it },
                                label = { Text("New Password (min 8 chars)") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { showRecoveryPassword = !showRecoveryPassword }) {
                                        Icon(
                                            imageVector = if (showRecoveryPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle password"
                                        )
                                    }
                                },
                                visualTransformation = if (showRecoveryPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (recoveryNewPassword.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                PasswordStrengthMeter(
                                    strength = recStrength,
                                    lengthOk = recoveryNewPassword.length >= 8
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    PasswordChecklistRow(
                                        label = "Minimum 8 characters",
                                        met = recoveryNewPassword.length >= 8
                                    )
                                    PasswordChecklistRow(
                                        label = "Letters & numbers recommended",
                                        met = recoveryNewPassword.any { it.isDigit() } && recoveryNewPassword.any { it.isLetter() }
                                    )
                                    PasswordChecklistRow(
                                        label = "Special symbol (!@#$%^&*)",
                                        met = recoveryNewPassword.any { !it.isLetterOrDigit() }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = {
                                    onVerifyRecoveryReset(recoveryPendingEmail, recoveryCode, recoveryNewPassword)
                                },
                                enabled = recoveryCode.length == 6 && recoveryNewPassword.length >= 8 && !isBusy,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Text("Reset Password & Sign In", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(onClick = onCancelRecovery) {
                                Text("Back to Sign In")
                            }
                        }
                    }
                }

                showForgotPasswordForm -> {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(22.dp)) {
                            Text("Forgot Password?", style = MaterialTheme.typography.headlineSmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Enter your registered seller email. We'll send a 6-digit recovery code to reset your password.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = recoveryEmail,
                                onValueChange = { recoveryEmail = it },
                                label = { Text("Seller email") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = {
                                    onSendPasswordReset(recoveryEmail.trim())
                                    showForgotPasswordForm = false
                                },
                                enabled = Patterns.EMAIL_ADDRESS.matcher(recoveryEmail.trim()).matches() && !isBusy,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                            ) {
                                Text("Send Recovery Code", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { showForgotPasswordForm = false },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                else -> {
                    Text("Seller Sign In", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Sign in with your ZULVAZA seller email and password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            val emailBringer = remember { BringIntoViewRequester() }
                            val passBringer = remember { BringIntoViewRequester() }

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Seller email") },
                                placeholder = { Text("seller@store.pk") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(emailBringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { emailBringer.bringIntoView() } }
                                    .testTag("login_email_input")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        AnimatedContent(targetState = showPassword, label = "loginEye") { visible ->
                                            Icon(
                                                imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (visible) "Hide password" else "Show password"
                                            )
                                        }
                                    }
                                },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(passBringer)
                                    .onFocusEvent { if (it.isFocused) scope.launch { passBringer.bringIntoView() } }
                                    .testTag("login_password_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        recoveryEmail = email
                                        showForgotPasswordForm = true
                                    },
                                    modifier = Modifier.testTag("forgot_password_button")
                                ) {
                                    Text("Forgot password?", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Button(
                                onClick = { onLogin(email.trim(), password) },
                                enabled = email.isNotBlank() && password.isNotBlank() && !isBusy,
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                                    .testTag("login_submit_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        text = "Sign In",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "New to ZULVAZA?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = onGoToSignUp,
                        border = BorderStroke(1.dp, ZulvazaOrange),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .testTag("login_create_seller_button")
                    ) {
                        Text(
                            text = "Create Seller Account",
                            style = MaterialTheme.typography.titleMedium,
                            color = ZulvazaOrange,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OtpVerifyCard(
    title: String,
    subtitle: String,
    code: String,
    resendSeconds: Int,
    isBusy: Boolean,
    onCodeChange: (String) -> Unit,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit,
    verifyButtonTag: String
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = ZulvazaOrange.copy(alpha = 0.14f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MarkEmailRead,
                        contentDescription = null,
                        tint = ZulvazaOrange,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(22.dp))

            OtpCodeBoxes(
                code = code,
                onCodeChange = onCodeChange,
                onComplete = { entered -> if (!isBusy) onVerify(entered) },
                enabled = !isBusy
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isBusy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifying code...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Button(
                    onClick = { onVerify(code) },
                    enabled = code.length == 6,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .testTag(verifyButtonTag)
                ) {
                    Text("Verify & Continue", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text("Change email")
                }
                TextButton(
                    onClick = onResend,
                    enabled = resendSeconds == 0 && !isBusy
                ) {
                    Text(
                        text = if (resendSeconds > 0) "Resend (${resendSeconds}s)" else "Resend code"
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordChecklistRow(label: String, met: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (met) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (met) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PasswordStrengthMeter(
    strength: PasswordStrength,
    lengthOk: Boolean
) {
    val activeColor = when (strength) {
        PasswordStrength.EMPTY -> MaterialTheme.colorScheme.outline
        PasswordStrength.WEAK -> ErrorRed
        PasswordStrength.MEDIUM -> WarningAmber
        PasswordStrength.STRONG -> SuccessGreen
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(3) { idx ->
                val filled = idx < strength.level
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(if (filled) activeColor else MaterialTheme.colorScheme.outline)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Strength: ${strength.label}",
                style = MaterialTheme.typography.labelSmall,
                color = activeColor
            )
            Text(
                text = if (lengthOk) "8+ chars ✓" else "Min 8 chars",
                style = MaterialTheme.typography.labelSmall,
                color = if (lengthOk) SuccessGreen else ErrorRed
            )
        }
    }
}

@Composable
private fun AuthBrandHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 3.dp,
            modifier = Modifier.size(88.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_auth_logo),
                contentDescription = "ZULVAZA Seller Center",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = ZulvazaOrange.copy(alpha = 0.12f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, ZulvazaOrange.copy(alpha = 0.28f))
        ) {
            Text(
                text = "ZULVAZA SELLER CENTER",
                style = MaterialTheme.typography.labelSmall,
                color = ZulvazaOrange,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}
