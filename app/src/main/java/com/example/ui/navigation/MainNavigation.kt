package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.theme.GeometricPrimary
import com.example.ui.viewmodel.ShopViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val outlinedIcon: ImageVector) {
    object Dashboard : Screen("dashboard", "Overview", Icons.Filled.Dashboard, Icons.Outlined.Dashboard)
    object Inventory : Screen("inventory", "Inventory", Icons.Filled.Inventory2, Icons.Outlined.Inventory2)
    object NewSale : Screen("new_sale", "New Sale", Icons.Filled.PointOfSale, Icons.Outlined.PointOfSale)
    object Customers : Screen("customers", "Customers", Icons.Filled.PeopleAlt, Icons.Outlined.PeopleAlt)
    object Reports : Screen("reports", "Reports", Icons.Filled.Assessment, Icons.Outlined.Assessment)

    object InvoiceDetail : Screen("invoice/{saleId}", "Invoice", Icons.Filled.Receipt, Icons.Outlined.Receipt) {
        fun createRoute(saleId: Long) = "invoice/$saleId"
    }
    object Purchases : Screen("purchases", "Purchases", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart)
    object Udhaar : Screen("udhaar", "Udhaar", Icons.Filled.PendingActions, Icons.Outlined.PendingActions)
    object Expenses : Screen("expenses", "Expenses", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    object Profile : Screen("profile", "Admin Profile", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle)
}

@Composable
fun MainAppNavigation(
    viewModel: ShopViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val shopProfile by viewModel.shopProfile.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.uiEventMessage) {
        viewModel.uiEventMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Gated Access: If user is not authenticated with Firebase, show LoginScreen
    if (authState !is com.example.data.firebase.FirebaseAuthService.AuthState.Authenticated) {
        LoginScreen(
            viewModel = viewModel,
            modifier = modifier
        )
        return
    }

    // Gated PIN Lock (if PIN lock security enabled)
    if (!isUnlocked) {
        AuthLockScreen(
            shopName = shopProfile?.shopName ?: "Puja Dry Fruits",
            onUnlockSuccess = { /* ViewModel unlocks */ },
            onValidatePin = { pin -> viewModel.unlockApp(pin) }
        )
        return
    }

    val bottomNavItems = listOf(
        Screen.Dashboard,
        Screen.Inventory,
        Screen.NewSale,
        Screen.Customers,
        Screen.Reports
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        val isBilling = screen == Screen.NewSale

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                if (isBilling) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White,
                                        modifier = Modifier.size(38.dp),
                                        shadowElevation = 2.dp
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = screen.title,
                                            modifier = Modifier.padding(7.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = if (isSelected) screen.icon else screen.outlinedIcon,
                                        contentDescription = screen.title
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("bottom_nav_${screen.route}")
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToNewSale = { navController.navigate(Screen.NewSale.route) },
                    onNavigateToInventory = { navController.navigate(Screen.Inventory.route) },
                    onNavigateToPurchases = { navController.navigate(Screen.Purchases.route) },
                    onNavigateToUdhaar = { navController.navigate(Screen.Udhaar.route) },
                    onNavigateToExpenses = { navController.navigate(Screen.Expenses.route) },
                    onNavigateToReports = { navController.navigate(Screen.Reports.route) },
                    onNavigateToInvoice = { saleId -> navController.navigate(Screen.InvoiceDetail.createRoute(saleId)) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
                )
            }

            composable(Screen.Inventory.route) {
                InventoryScreen(
                    viewModel = viewModel,
                    onNavigateBack = null
                )
            }

            composable(Screen.NewSale.route) {
                NewSaleScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onInvoiceCreated = { saleId ->
                        navController.navigate(Screen.InvoiceDetail.createRoute(saleId)) {
                            popUpTo(Screen.Dashboard.route)
                        }
                    }
                )
            }

            composable(Screen.Customers.route) {
                CustomerScreen(
                    viewModel = viewModel,
                    onNavigateBack = null,
                    onNavigateToInvoice = { saleId -> navController.navigate(Screen.InvoiceDetail.createRoute(saleId)) }
                )
            }

            composable(Screen.Reports.route) {
                ReportsScreen(
                    viewModel = viewModel,
                    onNavigateBack = null,
                    onNavigateToInvoice = { saleId -> navController.navigate(Screen.InvoiceDetail.createRoute(saleId)) }
                )
            }

            composable(
                route = Screen.InvoiceDetail.route,
                arguments = listOf(navArgument("saleId") { type = NavType.LongType })
            ) { backStackEntry ->
                val saleId = backStackEntry.arguments?.getLong("saleId") ?: 0L
                InvoiceViewScreen(
                    saleId = saleId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Purchases.route) {
                PurchaseScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Udhaar.route) {
                UdhaarScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Expenses.route) {
                ExpenseScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                ShopSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
                )
            }

            composable(Screen.Profile.route) {
                AdminProfileScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
