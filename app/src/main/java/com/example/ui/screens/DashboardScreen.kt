package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.R
import com.example.data.model.ProductItem
import com.example.data.model.ProductStatus
import com.example.data.model.SellerStore
import com.example.data.model.SellerVerification
import com.example.data.model.VerificationStatus
import com.example.ui.components.ProductStatusBadge
import com.example.ui.components.ShimmerBox
import com.example.ui.components.SignedRemoteImage
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedSoft
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft
import com.example.ui.theme.ZulvazaNavy
import com.example.ui.theme.ZulvazaOrange
import com.example.util.Formatters

@Composable
fun DashboardScreen(
    verification: SellerVerification,
    products: List<ProductItem>,
    isDataLoading: Boolean,
    resolveProductImage: (String) -> String?,
    onAddProduct: () -> Unit,
    onOpenProducts: () -> Unit,
    onFixVerification: () -> Unit,
    onOpenProductDetail: (ProductItem) -> Unit,
    onQuickRestock: (ProductItem, Int) -> Unit
) {
    val totalProducts = products.size
    val approvedCount = products.count { it.status == ProductStatus.APPROVED }
    val pendingCount = products.count { it.status == ProductStatus.PENDING }
    val lowStockProducts = products.filter { it.stock <= 5 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // Verification Alerts (only actionable pending/rejected states)
        if (verification.status == VerificationStatus.PENDING) {
            item {
                Surface(
                    color = WarningAmberSoft,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verification_pending_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.HourglassTop, contentDescription = null, tint = WarningAmber)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Verification under review",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF78350F),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Our KYC team is reviewing your CNIC & address. You can still draft and prepare listings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }
            }
        } else if (verification.status == VerificationStatus.REJECTED || verification.status == VerificationStatus.SUSPENDED) {
            item {
                Surface(
                    color = ErrorRedSoft,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (verification.status == VerificationStatus.REJECTED) "Verification Rejected" else "Verification Suspended",
                                style = MaterialTheme.typography.titleSmall,
                                color = ErrorRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!verification.reason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(verification.reason, style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                        }
                        if (verification.status == VerificationStatus.REJECTED) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onFixVerification,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Update & Resubmit KYC", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // 2x2 Bento KPI Grid
        if (isDataLoading && products.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShimmerBox(modifier = Modifier.weight(1f).height(94.dp))
                        ShimmerBox(modifier = Modifier.weight(1f).height(94.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShimmerBox(modifier = Modifier.weight(1f).height(94.dp))
                        ShimmerBox(modifier = Modifier.weight(1f).height(94.dp))
                    }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatKpiCard(
                            label = "All Products",
                            value = totalProducts.toString(),
                            icon = Icons.Default.ShoppingBag,
                            accent = ZulvazaNavy,
                            onClick = onOpenProducts,
                            modifier = Modifier.weight(1f)
                        )
                        StatKpiCard(
                            label = "Live Listings",
                            value = approvedCount.toString(),
                            icon = Icons.Default.CheckCircle,
                            accent = SuccessGreen,
                            onClick = onOpenProducts,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatKpiCard(
                            label = "Pending Review",
                            value = pendingCount.toString(),
                            icon = Icons.Default.HourglassTop,
                            accent = WarningAmber,
                            onClick = onOpenProducts,
                            modifier = Modifier.weight(1f)
                        )
                        StatKpiCard(
                            label = "Low Stock (≤5)",
                            value = lowStockProducts.size.toString(),
                            icon = Icons.Default.Inventory2,
                            accent = if (lowStockProducts.isNotEmpty()) ErrorRed else AccentTeal,
                            onClick = onOpenProducts,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Low-Stock Quick Restock Strip
        if (lowStockProducts.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Low Stock Alerts", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = onOpenProducts) { Text("Manage") }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        lowStockProducts.take(6).forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .width(220.dp)
                                    .clickable { onOpenProductDetail(item) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.stock} left in stock",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ErrorRed,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = ZulvazaOrange.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onQuickRestock(item, 5) }
                                    ) {
                                        Text(
                                            text = "+5",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ZulvazaOrange,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Listings
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Listings", style = MaterialTheme.typography.titleLarge)
                if (products.isNotEmpty()) {
                    TextButton(onClick = onOpenProducts) {
                        Text("All Products")
                    }
                }
            }
        }

        if (products.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_catalog_1795381797836),
                            contentDescription = "Empty product studio",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Ready to launch your first listing?", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Upload 2+ crisp photos, a showcase video, and set your PKR price.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onAddProduct,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Create First Product", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            items(products.take(4), key = { it.id }) { product ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProductDetail(product) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SignedRemoteImage(
                            url = product.imagePaths.firstOrNull()?.let(resolveProductImage),
                            contentDescription = product.name,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${Formatters.formatPkr(product.price)} • Stock ${product.stock}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ProductStatusBadge(status = product.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatKpiCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = value,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
