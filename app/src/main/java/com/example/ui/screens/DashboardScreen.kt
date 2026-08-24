package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entities.ProductEntity
import com.example.data.local.entities.SaleEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ShopViewModel
import com.example.util.CurrencyUtils
import com.example.util.DateFilterRange
import com.example.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ShopViewModel,
    onNavigateToNewSale: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToPurchases: () -> Unit,
    onNavigateToUdhaar: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToInvoice: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentUserDoc by viewModel.currentUserDoc.collectAsStateWithLifecycle()
    val shopProfile by viewModel.shopProfile.collectAsStateWithLifecycle()
    val syncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val dateFilter by viewModel.dashboardDateFilter.collectAsStateWithLifecycle()
    val dateRange by viewModel.currentDashboardDateRange.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val topProducts by viewModel.topSellingProducts.collectAsStateWithLifecycle()
    val paymentBreakdown by viewModel.paymentMethodBreakdown.collectAsStateWithLifecycle()
    val recentSales by viewModel.recentSalesList.collectAsStateWithLifecycle()
    val lowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()

    var showCustomDateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_puja_logo),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Column {
                            Text(
                                text = shopProfile?.shopName ?: "Puja Dry Fruits",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = shopProfile?.shopHindiName ?: "पूजा ड्राई फ्रूट्स",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.triggerFullCloudSync() },
                        modifier = Modifier.testTag("dashboard_cloud_sync_button")
                    ) {
                        if (syncState == com.example.data.firebase.CloudSyncState.SYNCING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Cloud Backup & Sync",
                                tint = if (syncState == com.example.data.firebase.CloudSyncState.SUCCESS) SuccessGreen else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("dashboard_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Storefront,
                            contentDescription = "Shop Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier.testTag("dashboard_admin_profile_button")
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val initial = (currentUserDoc?.displayName ?: "R").take(1).uppercase()
                                Text(
                                    text = initial,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // 1. Hero Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(115.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_shop_banner),
                            contentDescription = "Banner",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Black.copy(alpha = 0.55f)
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Puja Dry Fruits Management",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Wholesale & Counter Billing System",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFEADDFF)
                            )
                        }
                    }
                }
            }

            // 2. Date Filter Row
            item {
                DateFilterChips(
                    selectedRange = dateFilter,
                    onRangeSelected = { filter ->
                        if (filter == DateFilterRange.CUSTOM) {
                            showCustomDateDialog = true
                        } else {
                            viewModel.setDashboardDateFilter(filter)
                        }
                    }
                )
            }

            // 3. Low Stock Warning Alert Banner (if any)
            if (metrics.lowStockCount > 0) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onNavigateToInventory() },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CardExpensesBg)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ErrorRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Low Stock Alert",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${metrics.lowStockCount} Dry Fruit items low in stock!",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CardExpensesText
                                )
                                Text(
                                    text = "Tap to review catalog and reorder from supplier",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CardExpensesText.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = CardExpensesText
                            )
                        }
                    }
                }
            }

            // 4. Primary Key Metrics Grid - Geometric Balance Pastel Cards
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Row 1: Sales & Net Profit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "${dateFilter.label} Sales",
                            hindiTitle = "कुल बिक्री",
                            value = CurrencyUtils.formatInr(metrics.totalSales),
                            subtitle = "${metrics.totalOrders} Orders",
                            icon = Icons.Default.TrendingUp,
                            cardBgColor = CardSalesBg,
                            contentColor = CardSalesText,
                            iconBgColor = GeometricPrimary,
                            iconTintColor = Color.White,
                            modifier = Modifier.weight(1f).testTag("metric_card_sales")
                        )
                        MetricCard(
                            title = "Net Profit",
                            hindiTitle = "शुद्ध मुनाफा",
                            value = CurrencyUtils.formatInr(metrics.netProfit),
                            subtitle = "Margin: ${CurrencyUtils.formatPercent(metrics.profitMarginPercent)}",
                            icon = Icons.Default.MonetizationOn,
                            cardBgColor = CardProfitBg,
                            contentColor = CardProfitText,
                            iconBgColor = GeometricSecondary,
                            iconTintColor = Color.White,
                            modifier = Modifier.weight(1f).testTag("metric_card_net_profit")
                        )
                    }

                    // Row 2: Gross Profit & Expenses
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Gross Profit",
                            hindiTitle = "सकल लाभ",
                            value = CurrencyUtils.formatInr(metrics.grossProfit),
                            subtitle = "COGS: ${CurrencyUtils.formatInr(metrics.totalCost, false)}",
                            icon = Icons.Default.AccountBalanceWallet,
                            cardBgColor = CardOrdersBg,
                            contentColor = CardOrdersText,
                            iconBgColor = GeometricTertiary,
                            iconTintColor = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Expenses",
                            hindiTitle = "कुल खर्चे",
                            value = CurrencyUtils.formatInr(metrics.totalExpenses),
                            subtitle = "Shop & staff expenses",
                            icon = Icons.Default.ReceiptLong,
                            cardBgColor = CardExpensesBg,
                            contentColor = CardExpensesText,
                            iconBgColor = ErrorRed,
                            iconTintColor = Color.White,
                            modifier = Modifier.weight(1f).clickable { onNavigateToExpenses() }
                        )
                    }

                    // Row 3: Udhaar & Catalog Stock Value
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Udhaar Balance",
                            hindiTitle = "उधार बाक़ी",
                            value = CurrencyUtils.formatInr(metrics.totalOutstandingCredit),
                            subtitle = "Customer credit ledger",
                            icon = Icons.Default.PendingActions,
                            cardBgColor = CardUdhaarBg,
                            contentColor = CardUdhaarText,
                            iconBgColor = PurpleUdhaar,
                            iconTintColor = Color.White,
                            modifier = Modifier.weight(1f).clickable { onNavigateToUdhaar() }
                        )
                        MetricCard(
                            title = "Stock Value",
                            hindiTitle = "स्टॉक मूल्य",
                            value = CurrencyUtils.formatInr(metrics.totalStockValue),
                            subtitle = "${metrics.totalProducts} active products",
                            icon = Icons.Default.Inventory2,
                            cardBgColor = CardStockBg,
                            contentColor = CardStockText,
                            iconBgColor = GeometricSecondary,
                            iconTintColor = Color.White,
                            modifier = Modifier.weight(1f).clickable { onNavigateToInventory() }
                        )
                    }
                }
            }

            // 5. Quick Actions Row
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionButton(
                            label = "+ New Bill",
                            hindi = "नया बिल",
                            icon = Icons.Default.PointOfSale,
                            containerColor = GeometricPrimary,
                            contentColor = Color.White,
                            modifier = Modifier.weight(1f).testTag("quick_new_sale_btn"),
                            onClick = onNavigateToNewSale
                        )
                        QuickActionButton(
                            label = "+ Stock In",
                            hindi = "खरीद माल",
                            icon = Icons.Default.AddShoppingCart,
                            containerColor = GeometricSecondaryContainer,
                            contentColor = GeometricOnSecondaryContainer,
                            modifier = Modifier.weight(1f).testTag("quick_stock_in_btn"),
                            onClick = onNavigateToPurchases
                        )
                        QuickActionButton(
                            label = "+ Expense",
                            hindi = "खर्च जोड़ें",
                            icon = Icons.Default.AddCard,
                            containerColor = CardExpensesBg,
                            contentColor = CardExpensesText,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToExpenses
                        )
                        QuickActionButton(
                            label = "Udhaar",
                            hindi = "खाता",
                            icon = Icons.Default.PeopleAlt,
                            containerColor = CardUdhaarBg,
                            contentColor = CardUdhaarText,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToUdhaar
                        )
                    }
                }
            }

            // 6. Top Selling Products
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    TopSellingProductsCard(topProducts = topProducts)
                }
            }

            // 7. Payment Modes Breakdown
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    PaymentMethodBreakdownCard(breakdown = paymentBreakdown)
                }
            }

            // 8. Recent Sales / Invoices Section
            item {
                SectionHeader(
                    title = "Recent Invoices",
                    hindiSubtitle = "ताज़ा बिक्री बिल",
                    actionLabel = "View All",
                    onActionClick = onNavigateToReports
                )
            }

            if (recentSales.isEmpty()) {
                item {
                    Text(
                        text = "No sales in the selected period. Tap '+ New Bill' to create an invoice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(recentSales.take(5)) { sale ->
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
fun QuickActionButton(
    label: String,
    hindi: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(80.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                fontSize = 11.sp
            )
            Text(
                text = hindi,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f),
                fontSize = 9.sp
            )
        }
    }
}

@Composable
fun RecentSaleItemRow(
    sale: SaleEntity,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() }
            .testTag("sale_row_${sale.invoiceNumber}"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sale.invoiceNumber,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = GeometricPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(status = sale.paymentStatus)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${sale.customerName} • ${DateUtils.formatDateTime(sale.saleDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyUtils.formatInr(sale.grandTotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = sale.paymentMethod,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp
                )
            }
        }
    }
}
