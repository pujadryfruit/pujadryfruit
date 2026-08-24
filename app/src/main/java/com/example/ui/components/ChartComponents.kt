package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.dao.PaymentMethodSummary
import com.example.data.local.dao.TopProductSummary
import com.example.ui.theme.*
import com.example.util.CurrencyUtils

@Composable
fun SalesTrendMiniBarChart(
    dataPoints: List<Pair<String, Double>>, // Label to amount
    modifier: Modifier = Modifier,
    barColor: Color = GeometricPrimary,
    accentColor: Color = GeometricPrimaryContainer
) {
    if (dataPoints.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No sales data in this period",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        return
    }

    val maxVal = (dataPoints.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(100.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            dataPoints.forEach { item ->
                val ratio = (item.second / maxVal).toFloat().coerceIn(0.08f, 1.0f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .fillMaxHeight(ratio)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(GeometricPrimaryDark, barColor)
                                )
                            )
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dataPoints.forEach { item ->
                Text(
                    text = item.first,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TopSellingProductsCard(
    topProducts: List<TopProductSummary>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Top Selling Dry Fruits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "सबसे ज़्यादा बिक्री",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (topProducts.isEmpty()) {
                Text(
                    text = "No sales recorded for this date range.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                val maxRevenue = topProducts.maxOfOrNull { it.totalRevenue } ?: 1.0
                topProducts.forEach { item ->
                    val progress = (item.totalRevenue / maxRevenue).toFloat().coerceIn(0.05f, 1f)
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.productName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = CurrencyUtils.formatInr(item.totalRevenue),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = GeometricPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(GeometricPrimaryDark, GeometricPrimary)
                                            )
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "+${CurrencyUtils.formatInr(item.totalProfit, false)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentMethodBreakdownCard(
    breakdown: List<PaymentMethodSummary>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sales by Payment Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "भुगतान प्रकार",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (breakdown.isEmpty()) {
                Text(
                    text = "No payments recorded for this date range.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                val totalAmount = breakdown.sumOf { it.totalAmount }.coerceAtLeast(1.0)
                breakdown.forEach { item ->
                    val percent = (item.totalAmount / totalAmount * 100)
                    val modeColor = when (item.paymentMethod.lowercase()) {
                        "cash" -> CardOrdersText
                        "upi" -> GeometricPrimary
                        "credit / udhaar", "credit", "udhaar" -> CardUdhaarText
                        "card" -> AmberGold
                        else -> GeometricSecondary
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(modeColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = item.paymentMethod,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${item.transactionCount})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 11.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = CurrencyUtils.formatInr(item.totalAmount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = CurrencyUtils.formatPercent(percent),
                                style = MaterialTheme.typography.labelSmall,
                                color = modeColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
