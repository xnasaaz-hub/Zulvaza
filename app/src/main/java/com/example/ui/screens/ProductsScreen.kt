package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ProductDraft
import com.example.data.model.ProductItem
import com.example.data.model.ProductStatus
import com.example.ui.components.ProductImageCarousel
import com.example.ui.components.ProductStatusBadge
import com.example.ui.components.ProductVideoPlayer
import com.example.ui.components.ShareProductBottomSheet
import com.example.ui.components.SignedRemoteImage
import com.example.ui.components.copyToClipboard
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedSoft
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ZulvazaOrange
import com.example.util.Formatters

private enum class CatalogSort(val label: String) {
    NEWEST("Newest"),
    PRICE_LOW("Price: Low → High"),
    PRICE_HIGH("Price: High → Low"),
    TOP_SALES("Best Selling"),
    LOW_STOCK("Low Stock First")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    products: List<ProductItem>,
    drafts: List<ProductDraft>,
    resolveImageUrl: (String) -> String?,
    resolveVideoUrl: (String) -> String?,
    onAddProduct: () -> Unit,
    onEditProduct: (ProductItem) -> Unit,
    onAdjustStock: (ProductItem, Int) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onResumeDraft: (ProductDraft) -> Unit,
    onDeleteDraft: (String) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf<ProductStatus?>(null) }
    var lowStockOnly by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(CatalogSort.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    var viewingProduct by remember { mutableStateOf<ProductItem?>(null) }
    var sharingProduct by remember { mutableStateOf<ProductItem?>(null) }
    var deletingProduct by remember { mutableStateOf<ProductItem?>(null) }
    var showDraftsSheet by remember { mutableStateOf(false) }

    if (viewingProduct != null) {
        ProductDetailView(
            product = viewingProduct!!,
            resolveImageUrl = resolveImageUrl,
            resolveVideoUrl = resolveVideoUrl,
            onBack = { viewingProduct = null },
            onEdit = {
                val p = viewingProduct!!
                viewingProduct = null
                onEditProduct(p)
            },
            onShare = { sharingProduct = viewingProduct },
            onCopyCode = { code ->
                copyToClipboard(context, "Product ID", code)
                onToast("Product ID $code copied")
            }
        )
    }

    if (sharingProduct != null) {
        ShareProductBottomSheet(
            product = sharingProduct!!,
            onDismiss = { sharingProduct = null },
            onCopied = { msg -> onToast(msg) }
        )
    }

    if (deletingProduct != null) {
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("Delete Product?") },
            text = { Text("Are you sure you want to delete \"${deletingProduct!!.name}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(deletingProduct!!.id)
                        deletingProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingProduct = null }) { Text("Cancel") }
            }
        )
    }

    if (showDraftsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDraftsSheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text("Saved Drafts (${drafts.size})", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(12.dp))
                if (drafts.isEmpty()) {
                    Text(
                        "No drafts saved yet. Drafts autosave while creating a listing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    drafts.forEach { draft ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clickable {
                                    showDraftsSheet = false
                                    onResumeDraft(draft)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = draft.name.ifBlank { "Untitled Draft" },
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${draft.imagePaths.size} photos • ${Formatters.formatIsoTimestamp(draft.updatedAt ?: draft.createdAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteDraft(draft.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete draft", tint = ErrorRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewingProduct != null) return

    val filteredProducts = remember(products, selectedFilter, lowStockOnly, sortOption) {
        val base = products.filter { item ->
            val matchesFilter = selectedFilter == null || item.status == selectedFilter
            val matchesStock = !lowStockOnly || item.stock <= 5
            matchesFilter && matchesStock
        }
        when (sortOption) {
            CatalogSort.NEWEST -> base
            CatalogSort.PRICE_LOW -> base.sortedBy { it.price }
            CatalogSort.PRICE_HIGH -> base.sortedByDescending { it.price }
            CatalogSort.TOP_SALES -> base.sortedByDescending { it.sales }
            CatalogSort.LOW_STOCK -> base.sortedBy { it.stock }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header + View Toggle + Sort + Drafts
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("All Products (${products.size})", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "${sortOption.label} • ${if (isGridView) "Grid" else "List"} view",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                            contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view"
                        )
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort products")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            CatalogSort.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label) },
                                    onClick = {
                                        sortOption = opt
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { showDraftsSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("products_drafts_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (drafts.isNotEmpty()) {
                                    Badge { Text(drafts.size.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Drafts, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Drafts",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null && !lowStockOnly,
                    onClick = {
                        selectedFilter = null
                        lowStockOnly = false
                    },
                    label = { Text("All (${products.size})") }
                )
                FilterChip(
                    selected = lowStockOnly,
                    onClick = { lowStockOnly = !lowStockOnly },
                    label = { Text("Low Stock (${products.count { it.stock <= 5 }})") }
                )
                ProductStatus.entries.forEach { st ->
                    val count = products.count { it.status == st }
                    FilterChip(
                        selected = selectedFilter == st,
                        onClick = { selectedFilter = if (selectedFilter == st) null else st },
                        label = { Text("${st.label} ($count)") }
                    )
                }
            }
        }

        if (filteredProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_catalog_1795381797836),
                            contentDescription = "No matching products",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (products.isEmpty()) "No products yet" else "No matching products",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (products.isEmpty()) {
                                "Publish your first product with 2+ photos, a showcase video, and PKR price."
                            } else {
                                "Try clearing your status filter."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (products.isEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onAddProduct,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Add Product", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredProducts, key = { it.id }) { item ->
                    ProductGridCard(
                        product = item,
                        thumbnailUrl = item.imagePaths.firstOrNull()?.let(resolveImageUrl),
                        onClick = { viewingProduct = item },
                        onEdit = { onEditProduct(item) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredProducts, key = { it.id }) { item ->
                    ProductListCard(
                        product = item,
                        thumbnailUrl = item.imagePaths.firstOrNull()?.let(resolveImageUrl),
                        onView = { viewingProduct = item },
                        onEdit = { onEditProduct(item) },
                        onAdjustStock = { delta -> onAdjustStock(item, delta) },
                        onDelete = { deletingProduct = item },
                        onShare = { sharingProduct = item },
                        onCopyId = {
                            copyToClipboard(context, "Product ID", item.productCode)
                            onToast("Copied ID: ${item.productCode}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductGridCard(
    product: ProductItem,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
            ) {
                SignedRemoteImage(
                    url = thumbnailUrl,
                    contentDescription = product.name,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    ProductStatusBadge(status = product.status)
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Formatters.formatPkr(product.price),
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelLarge,
                    color = ZulvazaOrange,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stock ${product.stock}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (product.stock <= 5) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit product", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductListCard(
    product: ProductItem,
    thumbnailUrl: String?,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onAdjustStock: (Int) -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onCopyId: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onView)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                SignedRemoteImage(
                    url = thumbnailUrl,
                    contentDescription = product.name,
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProductStatusBadge(status = product.status)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onCopyId)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = product.productCode.ifBlank { "—" },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy 10-digit ID",
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = Formatters.formatPkr(product.price),
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.titleSmall,
                                color = ZulvazaOrange,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sales: ${product.sales}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Inline Quick Stock Stepper
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = product.stock > 0) { onAdjustStock(-1) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease stock", modifier = Modifier.size(15.dp))
                                }
                                Text(
                                    text = "${product.stock}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (product.stock <= 5) ErrorRed else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .clickable { onAdjustStock(1) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase stock", modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (product.status == ProductStatus.REJECTED && !product.rejectionReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = ErrorRedSoft,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Rejection reason: ${product.rejectionReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onView,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share product", tint = ZulvazaOrange)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete product", tint = ErrorRed)
                }
            }
        }
    }
}

@Composable
fun ProductDetailView(
    product: ProductItem,
    resolveImageUrl: (String) -> String?,
    resolveVideoUrl: (String) -> String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onCopyCode: (String) -> Unit
) {
    BackHandler { onBack() }

    val imageUrls = product.imagePaths.map { resolveImageUrl(it) }
    val videoUrl = product.videoPath?.let { resolveVideoUrl(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to products")
                }
                Text("Product Details", style = MaterialTheme.typography.titleLarge)
            }
            Row {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share product", tint = ZulvazaOrange)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit product")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        ProductImageCarousel(imageUrls = imageUrls)

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductStatusBadge(status = product.status)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onCopyCode(product.productCode) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ID: ${product.productCode}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy ID", modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(product.name, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = Formatters.formatPkr(product.price),
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.headlineSmall,
            color = ZulvazaOrange,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (product.category.isNotBlank()) {
                Surface(
                    color = ZulvazaOrange.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = product.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = ZulvazaOrange,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = "Stock: ${product.stock}  •  Sales: ${product.sales}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!videoUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Product Video", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            ProductVideoPlayer(videoUrl = videoUrl)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Description", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = product.description.ifBlank { "No description provided." },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (product.attributes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Specifications", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    product.attributes.forEach { (k, v) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
