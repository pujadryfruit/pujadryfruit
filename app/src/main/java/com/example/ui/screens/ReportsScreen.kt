package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.SaleEntity
import com.example.data.local.entities.ShopProfile
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ShopViewModel
import com.example.util.CurrencyUtils
import com.example.util.DateFilterRange
import com.example.util.DateUtils
import com.example.util.ShareUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ShopViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToInvoice: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shopProfile by viewModel.shopProfile.collectAsStateWithLifecycle()
    val dateFilter by viewModel.dashboardDateFilter.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val topProducts by viewModel.topSellingProducts.collectAsStateWithLifecycle()
    val paymentBreakdown by viewModel.paymentMethodBreakdown.collectAsStateWithLifecycle()
    val salesList by viewModel.recentSalesList.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }

    val statuses = listOf("ALL", "PAID", "PARTIAL", "UNPAID", "CANCELLED")

    val filteredSales = salesList.filter { sale ->
        val matchesSearch = searchQuery.isBlank() ||
                sale.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                sale.customerName.contains(searchQuery, ignoreCase = true) ||
                sale.customerMobile.contains(searchQuery)
        val matchesStatus = selectedStatusFilter == "ALL" || sale.paymentStatus.equals(selectedStatusFilter, ignoreCase = true)
        matchesSearch && matchesStatus
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Business Reports & P&L",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "बिक्री और मुनाफा रिपोर्ट • Puja Dry Fruits",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val text = ShareUtils.generateReportSummaryText(
                                shopProfile ?: ShopProfile(),
                                dateFilter.label,
                                metrics.totalSales,
                                metrics.totalCost,
                                metrics.grossProfit,
                                metrics.totalExpenses,
                                metrics.netProfit,
                                metrics.profitMarginPercent,
                                metrics.totalOrders
                            )
                            ShareUtils.shareText(context, "P&L Summary - ${dateFilter.label}", text)
                        },
                        modifier = Modifier.testTag("share_reports_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Report", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Date Filter
            item {
                DateFilterChips(
                    selectedRange = dateFilter,
                    onRangeSelected = { viewModel.setDashboardDateFilter(it) }
                )
            }

            // Profit & Loss Financial Statement Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Profit & Loss (${dateFilter.label})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "नफा-नुकसान खाता",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Line items
                        PlRowItem(label = "Total Sales Revenue (+)", amount = metrics.totalSales, color = MaterialTheme.colorScheme.onSurface, isBold = true)
                        PlRowItem(label = "Cost of Goods Sold / COGS (-)", amount = metrics.totalCost, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        PlRowItem(label = "Gross Profit (=)", amount = metrics.grossProfit, color = AmberGold, isBold = true)
                        PlRowItem(label = "Operating Expenses (-)", amount = metrics.totalExpenses, color = ErrorRed)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Net Business Profit",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (metrics.netProfit >= 0) SuccessGreen else ErrorRed
                                )
                                Text(
                                    text = "Margin: ${CurrencyUtils.formatPercent(metrics.profitMarginPercent)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                text = CurrencyUtils.formatInr(metrics.netProfit),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (metrics.netProfit >= 0) SuccessGreen else ErrorRed
                            )
                        }
                    }
                }
            }

            // Top Products
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    TopSellingProductsCard(topProducts = topProducts)
                }
            }

            // Payment Modes
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    PaymentMethodBreakdownCard(breakdown = paymentBreakdown)
                }
            }

            // Sales Invoices Header & Filter
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "All Sales Invoices (${filteredSales.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholderText = "Search invoice number, customer name..."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        statuses.forEach { st ->
                            FilterChip(
                                selected = st == selectedStatusFilter,
                                onClick = { selectedStatusFilter = st },
                                label = { Text(st, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            if (filteredSales.isEmpty()) {
                item {
                    Text(
                        text = "No invoices found matching criteria.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(filteredSales) { sale ->
                    RecentSaleItemRow(
                        sale = sale,
                        onClick = { onNavigateToInvoice(sale.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlRowItem(
    label: String,
    amount: Double,
    color: Color,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
        Text(
            text = CurrencyUtils.formatInr(amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = color
        )
    }
}
