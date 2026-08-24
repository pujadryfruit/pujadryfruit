package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GeometricPrimary

@Composable
fun AuthLockScreen(
    shopName: String,
    onUnlockSuccess: () -> Unit,
    onValidatePin: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_puja_logo),
            contentDescription = "Logo",
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = shopName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Enter Security PIN to access shop system",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4 PIN Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 4) {
                val isFilled = i < enteredPin.length
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isError -> ErrorRed
                                isFilled -> GeometricPrimary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
            }
        }

        if (isError) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Incorrect PIN. Try again.",
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Keypad Grid
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "DEL")
        )

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            keys.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Box(modifier = Modifier.size(68.dp))
                        } else if (key == "DEL") {
                            IconButton(
                                onClick = {
                                    if (enteredPin.isNotEmpty()) {
                                        enteredPin = enteredPin.dropLast(1)
                                        isError = false
                                    }
                                },
                                modifier = Modifier.size(68.dp)
                            ) {
                                Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clickable {
                                        if (enteredPin.length < 4) {
                                            val newPin = enteredPin + key
                                            enteredPin = newPin
                                            isError = false
                                            if (newPin.length == 4) {
                                                if (onValidatePin(newPin)) {
                                                    onUnlockSuccess()
                                                } else {
                                                    isError = true
                                                    enteredPin = ""
                                                }
                                            }
                                        }
                                    }
                                    .testTag("pin_key_$key"),
                                shape = CircleShape,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "Default PIN: 1234 • puja-dryfruits cloud",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
