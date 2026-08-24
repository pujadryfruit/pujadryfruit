package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.ShopProfile
import com.example.ui.theme.*
import com.example.ui.viewmodel.ShopViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSettingsScreen(
    viewModel: ShopViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shopProfile by viewModel.shopProfile.collectAsStateWithLifecycle()
    val syncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val currentUserDoc by viewModel.currentUserDoc.collectAsStateWithLifecycle()

    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    var shopName by remember(shopProfile) { mutableStateOf(shopProfile?.shopName ?: "Puja Dry Fruits") }
    var shopHindiName by remember(shopProfile) { mutableStateOf(shopProfile?.shopHindiName ?: "पूजा ड्राई फ्रूट्स") }
    var tagline by remember(shopProfile) { mutableStateOf(shopProfile?.tagline ?: "Premium Quality Dry Fruits, Nuts & Spices") }
    var ownerName by remember(shopProfile) { mutableStateOf(shopProfile?.ownerName ?: "Puja") }
    var phone by remember(shopProfile) { mutableStateOf(shopProfile?.phone ?: "+91 98765 43210") }
    var email by remember(shopProfile) { mutableStateOf(shopProfile?.email ?: "info@pujadryfruits.com") }
    var address by remember(shopProfile) { mutableStateOf(shopProfile?.address ?: "Shop No. 12, Dry Fruit Market, Khari Baoli, Delhi - 110006") }
    var gstin by remember(shopProfile) { mutableStateOf(shopProfile?.gstin ?: "07AAAAA0000A1Z5") }
    var upiId by remember(shopProfile) { mutableStateOf(shopProfile?.upiId ?: "pujadryfruits@okaxis") }
    var invoicePrefix by remember(shopProfile) { mutableStateOf(shopProfile?.invoicePrefix ?: "PDF") }
    var taxRate by remember(shopProfile) { mutableStateOf(shopProfile?.taxRatePercent?.toString() ?: "5.0") }
    var thankYouMsg by remember(shopProfile) { mutableStateOf(shopProfile?.thankYouMessage ?: "Thank you for shopping at Puja Dry Fruits! Visit Again.") }
    
    var isPinEnabled by remember(shopProfile) { mutableStateOf(shopProfile?.isPinSecurityEnabled ?: false) }
    var pin by remember(shopProfile) { mutableStateOf(shopProfile?.securityPin ?: "1234") }
    var allowNegativeStock by remember(shopProfile) { mutableStateOf(shopProfile?.negativeStockAllowed ?: false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Shop Settings & Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            val updated = (shopProfile ?: ShopProfile()).copy(
                                shopName = shopName.trim(),
                                shopHindiName = shopHindiName.trim(),
                                tagline = tagline.trim(),
                                ownerName = ownerName.trim(),
                                phone = phone.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                gstin = gstin.trim(),
                                upiId = upiId.trim(),
                                invoicePrefix = invoicePrefix.trim(),
                                taxRatePercent = taxRate.toDoubleOrNull() ?: 5.0,
                                thankYouMessage = thankYouMsg.trim(),
                                isPinSecurityEnabled = isPinEnabled,
                                securityPin = pin.trim(),
                                negativeStockAllowed = allowNegativeStock,
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.updateShopProfile(updated)
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_settings_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Shop Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Firebase Cloud Backend & Synchronization
            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                var showEmailAuthDialog by remember { mutableStateOf(false) }
                var isSignUpMode by remember { mutableStateOf(false) }
                var authEmail by remember { mutableStateOf("pujadryfruit@gmail.com") }
                var authPassword by remember { mutableStateOf("") }
                var authDisplayName by remember { mutableStateOf("Puja Kumari") }

                if (showEmailAuthDialog) {
                    AlertDialog(
                        onDismissRequest = { showEmailAuthDialog = false },
                        title = {
                            Text(
                                if (isSignUpMode) "Create Firebase Account" else "Sign In to Firebase",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Firebase Project: puja-dryfruits\nPackage: com.puja.dryfruit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (isSignUpMode) {
                                    OutlinedTextField(
                                        value = authDisplayName,
                                        onValueChange = { authDisplayName = it },
                                        label = { Text("Full Name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                OutlinedTextField(
                                    value = authEmail,
                                    onValueChange = { authEmail = it },
                                    label = { Text("Email Address") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = authPassword,
                                    onValueChange = { authPassword = it },
                                    label = { Text("Password") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TextButton(
                                    onClick = { isSignUpMode = !isSignUpMode },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(if (isSignUpMode) "Already have an account? Sign In" else "New user? Create Account")
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showEmailAuthDialog = false
                                    if (isSignUpMode) {
                                        viewModel.signUpWithEmail(authEmail, authPassword, authDisplayName)
                                    } else {
                                        viewModel.signInWithEmail(authEmail, authPassword)
                                    }
                                }
                            ) {
                                Text(if (isSignUpMode) "Register" else "Sign In")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEmailAuthDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Firebase Cloud & Auth",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = when (syncState) {
                                    com.example.data.firebase.CloudSyncState.SUCCESS -> SuccessGreenLight
                                    com.example.data.firebase.CloudSyncState.SYNCING -> InfoBlueLight
                                    com.example.data.firebase.CloudSyncState.ERROR -> ErrorRedLight
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = when (syncState) {
                                        com.example.data.firebase.CloudSyncState.SUCCESS -> "Connected & Synced"
                                        com.example.data.firebase.CloudSyncState.SYNCING -> "Syncing..."
                                        com.example.data.firebase.CloudSyncState.ERROR -> "Offline Mode"
                                        else -> "Ready"
                                    },
                                    color = when (syncState) {
                                        com.example.data.firebase.CloudSyncState.SUCCESS -> SuccessGreen
                                        com.example.data.firebase.CloudSyncState.SYNCING -> GeometricPrimary
                                        com.example.data.firebase.CloudSyncState.ERROR -> ErrorRed
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Authenticated Account Info Box
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = (currentUserDoc?.displayName ?: "P").take(1).uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = currentUserDoc?.displayName ?: "Puja Kumari (Owner)",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = currentUserDoc?.email ?: "pujadryfruit@gmail.com",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = currentUserDoc?.role ?: "OWNER",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onNavigateToProfile,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_view_profile_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GeometricPrimary)
                                    ) {
                                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Admin Profile", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { showLogoutConfirmDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sign Out", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        if (showLogoutConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showLogoutConfirmDialog = false },
                                icon = {
                                    Icon(
                                        Icons.Default.Logout,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(30.dp)
                                    )
                                },
                                title = { Text("Confirm Logout", fontWeight = FontWeight.Bold) },
                                text = {
                                    Text("Are you sure you want to sign out from Puja Dry Fruits? You will need to log in again to view shop data.")
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showLogoutConfirmDialog = false
                                            viewModel.signOut(context)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Log Out", color = MaterialTheme.colorScheme.onError)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showLogoutConfirmDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        // Project & Schema Info
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Firebase Project: puja-dryfruits (748428498825)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Package: com.puja.dryfruit • Bucket: puja-dryfruits.firebasestorage.app",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Status: $syncMessage",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Tenant: ${com.example.data.firebase.FirebaseManager.DEFAULT_SHOP_ID}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.signOut(context) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Sign Out", fontSize = 12.sp)
                                }

                                FilledTonalButton(
                                    onClick = { viewModel.triggerFullCloudSync() },
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = syncState != com.example.data.firebase.CloudSyncState.SYNCING
                                ) {
                                    if (syncState == com.example.data.firebase.CloudSyncState.SYNCING) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text("Sync")
                                }
                            }
                        }
                    }
                }
            }

            // 1. Business Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Business Identity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            label = { Text("Shop Name") },
                            modifier = Modifier.fillMaxWidth().testTag("setting_shop_name")
                        )

                        OutlinedTextField(
                            value = shopHindiName,
                            onValueChange = { shopHindiName = it },
                            label = { Text("Shop Hindi Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = tagline,
                            onValueChange = { tagline = it },
                            label = { Text("Tagline / Subtitle") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            label = { Text("Owner / Manager Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 2. Contact & Billing Address
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Contact & Invoice Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Shop Address") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = gstin,
                                onValueChange = { gstin = it },
                                label = { Text("GSTIN") },
                                modifier = Modifier.weight(1.2f)
                            )
                            OutlinedTextField(
                                value = upiId,
                                onValueChange = { upiId = it },
                                label = { Text("UPI ID") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = invoicePrefix,
                                onValueChange = { invoicePrefix = it },
                                label = { Text("Invoice Prefix") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = taxRate,
                                onValueChange = { taxRate = it },
                                label = { Text("Tax / GST (%)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = thankYouMsg,
                            onValueChange = { thankYouMsg = it },
                            label = { Text("Receipt Thank You Note") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 3. Security & Inventory Configuration
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Security & App Controls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("4-Digit PIN Security Lock", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Requires PIN whenever app is opened", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Switch(checked = isPinEnabled, onCheckedChange = { isPinEnabled = it })
                        }

                        if (isPinEnabled) {
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { if (it.length <= 4) pin = it },
                                label = { Text("4-Digit Security PIN") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Allow Negative Stock", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Permit billing even if item stock reaches 0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Switch(checked = allowNegativeStock, onCheckedChange = { allowNegativeStock = it })
                        }
                    }
                }
            }
        }
    }
}
