package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VaultCategory
import com.example.model.VaultItem
import com.example.security.BlackBoxSecurity
import com.example.ui.components.CategoryIcon
import com.example.ui.components.PasswordStrengthBar
import com.example.ui.theme.BlackBoxBorder
import com.example.ui.theme.BlackBoxPitch
import com.example.ui.theme.BlackBoxSurface
import com.example.ui.theme.BlackBoxSurfaceVariant
import com.example.ui.theme.CyanIce
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemEditSheet(
    item: VaultItem,
    isNew: Boolean,
    onSave: (VaultItem) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(item.title) }
    var category by remember { mutableStateOf(item.category) }
    var username by remember { mutableStateOf(item.username) }
    var password by remember { mutableStateOf(item.password) }
    var url by remember { mutableStateOf(item.url) }
    var totpSecret by remember { mutableStateOf(item.totpSecret) }
    var notes by remember { mutableStateOf(item.notes) }
    var cardHolder by remember { mutableStateOf(item.cardHolder) }
    var cardNumber by remember { mutableStateOf(item.cardNumber) }
    var cardExpiry by remember { mutableStateOf(item.cardExpiry) }
    var cardCvv by remember { mutableStateOf(item.cardCvv) }
    var keyType by remember { mutableStateOf(if (item.keyType.isNotEmpty()) item.keyType else "SSH-ED25519") }
    var keyValue by remember { mutableStateOf(item.keyValue) }
    var isFavorite by remember { mutableStateOf(item.isFavorite) }

    var passwordVisible by remember { mutableStateOf(false) }
    val passwordStrength = remember(password) { BlackBoxSecurity.evaluatePasswordStrength(password) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BlackBoxSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(category = category)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isNew) "NEW ENCRYPTED RECORD" else "EDIT RECORD",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_edit_sheet")) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Selector
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VaultCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldContainer,
                            selectedLabelColor = EmeraldNeon,
                            containerColor = BlackBoxSurfaceVariant,
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = BlackBoxBorder,
                            selectedBorderColor = EmeraldNeon,
                            enabled = true,
                            selected = category == cat
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title / Label *") },
                placeholder = { Text("e.g. GitHub, AWS Server, Bank Card") },
                singleLine = true,
                colors = defaultTextFieldColors(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_title_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Specific Fields
            when (category) {
                VaultCategory.LOGIN -> {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username or Email") },
                        singleLine = true,
                        colors = defaultTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_username_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Row {
                                IconButton(
                                    onClick = {
                                        password = BlackBoxSecurity.generatePassword(20)
                                    },
                                    modifier = Modifier.testTag("generate_inline_password")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Autorenew,
                                        contentDescription = "Generate Password",
                                        tint = CyanIce
                                    )
                                }
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        },
                        colors = defaultTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_password_input")
                    )

                    if (password.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        PasswordStrengthBar(strength = passwordStrength)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Website URL") },
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = defaultTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_url_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // TOTP Authenticator Seed Input
                    OutlinedTextField(
                        value = totpSecret,
                        onValueChange = { input ->
                            val parsed = BlackBoxSecurity.parseTotpUri(input)
                            totpSecret = parsed?.first ?: input.trim()
                        },
                        label = { Text("2FA TOTP Secret Key (Optional)") },
                        placeholder = { Text("Base32 Key or otpauth:// URI") },
                        singleLine = true,
                        colors = defaultTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_totp_secret_input")
                    )
                }
                VaultCategory.NOTE -> {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Secure Encrypted Note Content") },
                        minLines = 6,
                        maxLines = 12,
                        colors = defaultTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_notes_input")
                    )
                }
                VaultCategory.CARD -> {
                    OutlinedTextField(
                        value = cardHolder,
                        onValueChange = { cardHolder = it },
                        label = { Text("Cardholder Name") },
                        singleLine = true,
                        colors = defaultTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_cardholder_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { cardNumber = it },
                        label = { Text("Card Number") },
                        placeholder = { Text("1234 5678 9012 3456") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = defaultTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_cardnumber_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = cardExpiry,
                            onValueChange = { cardExpiry = it },
                            label = { Text("Expires (MM/YY)") },
                            singleLine = true,
                            colors = defaultTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("edit_cardexpiry_input")
                        )

                        OutlinedTextField(
                            value = cardCvv,
                            onValueChange = { cardCvv = it },
                            label = { Text("CVV / CVC") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = defaultTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("edit_cardcvv_input")
                        )
                    }
                }
                VaultCategory.KEY -> {
                    OutlinedTextField(
                        value = keyType,
                        onValueChange = { keyType = it },
                        label = { Text("Key Type (SSH-RSA, SSH-ED25519, PGP, API Key)") },
                        singleLine = true,
                        colors = defaultTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_keytype_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = keyValue,
                        onValueChange = { keyValue = it },
                        label = { Text("Private / Public Key or Secret Token") },
                        minLines = 5,
                        maxLines = 10,
                        colors = defaultTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_keyvalue_input")
                    )
                }
            }

            if (category != VaultCategory.NOTE) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Additional Encrypted Notes (Optional)") },
                    minLines = 2,
                    maxLines = 5,
                    colors = defaultTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            Button(
                onClick = {
                    val finalTitle = if (title.trim().isEmpty()) {
                        when (category) {
                            VaultCategory.LOGIN -> if (username.isNotEmpty()) username else "Untitled Login"
                            VaultCategory.NOTE -> "Untitled Note"
                            VaultCategory.CARD -> "Payment Card"
                            VaultCategory.KEY -> "$keyType Key"
                        }
                    } else title.trim()

                    onSave(
                        item.copy(
                            title = finalTitle,
                            category = category,
                            username = username,
                            password = password,
                            url = url,
                            totpSecret = totpSecret.trim(),
                            notes = notes,
                            cardHolder = cardHolder,
                            cardNumber = cardNumber,
                            cardExpiry = cardExpiry,
                            cardCvv = cardCvv,
                            keyType = keyType,
                            keyValue = keyValue,
                            isFavorite = isFavorite
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldNeon,
                    contentColor = Color(0xFF003816)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_vault_item_button")
            ) {
                Text(
                    text = "ENCRYPT & SAVE TO BLACK-BOX",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun defaultTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EmeraldNeon,
    unfocusedBorderColor = BlackBoxBorder,
    focusedContainerColor = BlackBoxSurfaceVariant,
    unfocusedContainerColor = BlackBoxSurfaceVariant,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = EmeraldNeon,
    unfocusedLabelColor = TextMuted
)
