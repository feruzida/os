const { ipcRenderer } = require("electron");

/* ================= DOM ELEMENTS ================= */

// Login Elements
const user = document.getElementById("user");
const pass = document.getElementById("pass");
const errorBox = document.getElementById("errorBox");
const roleLabel = document.getElementById("roleLabel");


// Product Form Elements
const pSupplier = document.getElementById("pSupplier");
const pName = document.getElementById("pName");
const pCategory = document.getElementById("pCategory");
const pPrice = document.getElementById("pPrice");
const pQty = document.getElementById("pQty");

// Product Action Buttons
const addBtn = document.getElementById("addBtn");
const updateBtn = document.getElementById("updateBtn");
const deleteBtn = document.getElementById("deleteBtn");

// Dashboard Statistics Elements
const totalItemsEl = document.getElementById("totalItems");
const totalProductsEl = document.getElementById("totalProducts");
const totalValueEl = document.getElementById("totalValue");
const lowStockEl = document.getElementById("lowStock");
const categoriesEl = document.getElementById("categories");

// Inventory Statistics Elements
const totalItemsEl2 = document.getElementById("totalItems2");
const totalProductsEl2 = document.getElementById("totalProducts2");
const totalValueEl2 = document.getElementById("totalValue2");
const lowStockEl2 = document.getElementById("lowStock2");
const categoriesEl2 = document.getElementById("categories2");

// Category Filter
const categoryFilter = document.getElementById("categoryFilter");

/* ================= APPLICATION STATE ================= */
/**
 * Current active page (dashboard, products, suppliers, transactions, reports)
 */
let currentPage = "dashboard";
let currentRole = null; // Current authenticated user role (Admin, Stock Manager, Cashier)
let selectedProductId = null; // Currently selected product (for edit / update / transaction)
let pendingTransaction = null;
let selectedCategory = "ALL";
let selectedSupplierId = null;
//let currentUserId = null; //

// Products State
let allProducts = []; // Full product list loaded from server
let filteredProducts = []; // Products after applying search / filters

// Suppliers State
let allSuppliers = [];
let filteredSuppliers = [];

// Transactions State
let allTransactions = [];
let filteredTransactions = [];
let productsForTransaction = [];
let transactionFilter = "today";

// Reports state
let currentDateRange = 'month';
let reportStartDate = '';
let reportEndDate = '';
let currentStockTab = 'out_of_stock';
let stockLevelsData = { out_of_stock: [], low_stock: [], normal_stock: [] };

// Monitoring State
let allActiveSessions = [];
let allAuditLogs = [];
let filteredAuditLogs = [];
let monitoringInterval = null;


/**
 * Checks whether current user role is allowed to perform a specific action
 * Used for UI access control (buttons, modals, actions)
 *
 * @param {string} action - Action name (add, update, delete, purchase, sale)
 * @returns {boolean} true if action is permitted for current role
 */



/* ================= INITIALIZATION & SETUP ================= */
ipcRenderer.send("connect-server");

// Autofocus username on load
window.addEventListener('DOMContentLoaded', () => {
    if (user) user.focus();
});

// Enter key to log in
if (user && pass) {
    user.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') login();
    });
    pass.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') login();
    });
}

/* ================= AUTH ================= */
function login() {
    // Reset previous error state
    errorBox.innerText = "";
    errorBox.style.display = 'none';

    // Basic validation
    if (!user.value || !pass.value) {
        errorBox.innerText = "Please enter username and password";
        errorBox.style.display = 'block';
        return;
    }

    // Send login payload to backend
    ipcRenderer.send("send-data", JSON.stringify({
        action: "login",
        username: user.value,
        password: pass.value
    }));
}

function logout() {
    ipcRenderer.send("send-data", JSON.stringify({ action: "logout" }));
    location.reload(); // Full state reset
}

function resetAfterLogin() {
    selectedProductId = null;
    document.getElementById("productsTableBody").innerHTML = "";
    pName.value = "";
    pCategory.value = "";
    pPrice.value = "";
    pQty.value = "";
    errorBox.innerText = "";
    errorBox.style.display = 'none';
}

function resetUI() {
    currentRole = null;
    selectedProductId = null;
    allProducts = [];
    filteredProducts = []; // Reset
    selectedCategory = "ALL";
    currentPage = "dashboard";

    document.getElementById("productsTableBody").innerHTML = "";
    user.value = "";
    pass.value = "";
    roleLabel.innerText = "";
    errorBox.innerText = "";
    errorBox.style.display = 'none';

    showLogin();
}

/* ================= PERMISSIONS & ROLE MANAGEMENT ================= */

function can(action) {
    const permissions = {
        Admin: ['add', 'update', 'delete', 'purchase', 'sale'],
        Manager: ['add', 'update', 'delete', 'purchase', 'sale'],
        "Stock Manager": ['add', 'update', 'delete', 'purchase', 'sale'],
        Cashier: ['sale']
    };

    return permissions[currentRole]?.includes(action);
}

function applyRoleVisibility() {
    const reportsNav = document.querySelector('[data-page="reports"]');
    const monitoringNav = document.querySelector('[data-page="monitoring"]');

    //  Show for everyone
    if (reportsNav) reportsNav.classList.remove('hidden');
    if (monitoringNav) monitoringNav.classList.remove('hidden');

    // Disable for Cashier
    if (currentRole === 'Cashier') {
        if (reportsNav) reportsNav.classList.add('hidden');
        if (monitoringNav) monitoringNav.classList.add('hidden');
    }

    // Disable Monitoring Page
    if (currentRole !== 'Admin') {
        if (monitoringNav) monitoringNav.classList.add('hidden');
    }
}

function applyRolePermissions() {
    // Reset - show every element
    const addSupplierBtn = document.querySelector('[onclick="openSupplierModal()"]');
    if (addSupplierBtn) {
        addSupplierBtn.style.display = 'inline-flex';
    }

    // show all in Suppliers
    document.querySelectorAll('#suppliersPage .btn-destructive').forEach(btn => {
        btn.style.display = 'inline-flex';
    });

    document.querySelectorAll('#suppliersPage .action-btn').forEach(btn => {
        btn.style.display = 'inline-flex';
    });

    // not showing for Cashier
    if (currentRole === 'Cashier') {
        // ❌ Suppliers: prohibition CRUD
        if (addSupplierBtn) {
            addSupplierBtn.style.display = 'none';
        }

        document.querySelectorAll('#suppliersPage .btn-destructive').forEach(btn => {
            btn.style.display = 'none'; // delete
        });

        // unshod (emoji edit)
        document.querySelectorAll('#suppliersPage .action-btn[title="Edit Supplier"]').forEach(btn => {
            btn.style.display = 'none';
        });
    }
}

function applyReportsVisibility() {
    const userActivity = document.getElementById('userActivitySection');
    if (!userActivity) return;

    if (currentRole !== 'Admin') {
        userActivity.style.display = 'none';
    } else {
        userActivity.style.display = 'block';
    }
}

/* ================= NAVIGATION & PAGE SWITCHING ================= */

function switchPage(page) {
    if (page === 'reports' && currentRole === 'Cashier') return;
    if (page === 'monitoring' && currentRole !== 'Admin') return;
    currentPage = page;

    // always stop monitoring polling
    stopMonitoringAutoRefresh();

    // clear search
    if (currentPage !== 'transactions') {
        const transactionSearch = document.getElementById('transactionSearch');
        if (transactionSearch) {
            transactionSearch.value = '';
        }
    }

    if (currentPage !== 'suppliers') {
        const supplierSearch = document.getElementById('supplierSearch');
        if (supplierSearch) {
            supplierSearch.value = '';
        }
    }

    // Reset inventory filters when leaving inventory page
    if (currentPage !== 'inventory') {
        selectedCategory = 'ALL';
        const productSearch = document.getElementById('productSearch');
        if (productSearch) {
            productSearch.value = '';
        }
        if (categoryFilter) {
            categoryFilter.value = 'ALL';
        }
    }

    // hide all pages
    document.querySelectorAll('.page-content')
        .forEach(p => p.classList.add('hidden'));

    // show chosen page
    const targetPage = document.getElementById(page + 'Page');
    if (targetPage) {
        targetPage.classList.remove('hidden');
    }

    updateActiveNav(page);

    // update page
    if (page === 'suppliers') {
        loadSuppliers();

    } else if (page === 'dashboard') {
        loadProducts();
        ipcRenderer.send("send-data", JSON.stringify({ action: "get_all_transactions" }));

    } else if (page === 'inventory') {
        // Reset the filter when navigating through navigation
        if (selectedSupplierId !== null) {
            selectedSupplierId = null;

            const title = document.getElementById('inventoryTitle');
            if (title) title.innerText = 'Inventory Items';

            const clearBtn = document.getElementById('clearSupplierFilterBtn');
            if (clearBtn) clearBtn.style.display = 'none';
        }

        // Load all products
        loadProducts();
    }
    else if (page === 'transactions') {
        loadProductsForTransaction?.();
        loadTransactions?.();

    } else if (page === 'monitoring' && currentRole === 'Admin') {
        loadActiveSessions();
        loadAuditLogs();
        startMonitoringAutoRefresh();

    } else if (page === 'reports') {
        loadReportsData();
    }

    console.log('Switched to:', page);
}

// Function for updating the active navigation tab
function updateActiveNav(page) {
    const navItems = document.querySelectorAll('.nav-item');

    navItems.forEach(item => {
        item.classList.remove('active');

        if (item.getAttribute('data-page') === page) {
            item.classList.add('active');
        }
    });
}

function showLogin() {
    document.getElementById("loginSection").style.display = "flex";
    document.getElementById("appSection").style.display = "none";
    if (user) user.focus();
}

function showApp() {
    document.getElementById("loginSection").style.display = "none";
    document.getElementById("appSection").style.display = "flex";

    document.querySelectorAll('.nav-item').forEach(item => {
        item.classList.remove('active');
    });
    // Show Dashboard by default
    switchPage('dashboard');
}

/* ================= DASHBOARD PAGE ================= */

function updateDashboardSections(products, activeProducts) {
    updateLowStockSection(activeProducts);
    updateRecentActivity();
    updateTodaySummary();
    updateCategoryDistribution(activeProducts);
    updateTopProducts();
}

function updateLowStockSection(products) {
    const lowStockSection = document.getElementById('lowStockSection');
    const lowStockList = document.getElementById('lowStockList');
    const lowStockCount = document.getElementById('lowStockCount');

    const lowStockProducts = products.filter(p => p.quantity < 10);

    if (lowStockProducts.length === 0) {
        if (lowStockSection) lowStockSection.style.display = 'none';
        return;
    }

    if (lowStockSection) lowStockSection.style.display = 'block';
    if (lowStockCount) lowStockCount.innerText = `${lowStockProducts.length} products need restocking`;

    if (lowStockList) {
        lowStockList.innerHTML = lowStockProducts.map(p => `
            <div class="low-stock-item">
                <div class="low-stock-info">
                    <div class="low-stock-name">${escapeHtml(p.name)}</div>
                    <div class="low-stock-details">
                        Current: ${p.quantity} • Minimum: 10
                        <span class="low-stock-badge">
                            <svg class="icon" style="width: 12px; height: 12px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
                            </svg>
                            ${10 - p.quantity} needed
                        </span>
                    </div>
                </div>
            </div>
        `).join('');
    }
}

// Recent Activity
function updateRecentActivity() {
    const recentActivityList = document.getElementById('recentActivityList');
    if (!recentActivityList) return;

    const recentTransactions = allTransactions
        .sort((a, b) => new Date(b.txnDate) - new Date(a.txnDate))
        .slice(0, 5);

    if (recentTransactions.length === 0) {
        recentActivityList.innerHTML = '<p class="text-muted">No recent activity</p>';
        return;
    }

    recentActivityList.innerHTML = recentTransactions.map(t => {
        const product = allProducts.find(p => p.productId === t.productId)|| { name: 'Unknown Product' };
        const timeAgo = getTimeAgo(t.txnDate);
        const isSale = t.txnType === 'Sale';

        return `
            <div class="activity-item">
                <div class="activity-icon ${isSale ? 'sale' : 'purchase'}">
                    <svg class="icon" style="width: 18px; height: 18px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        ${isSale
            ? `
    <line x1="12" y1="1" x2="12" y2="23"></line>
    <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>
  `
            : `
    <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
    <line x1="3" y1="6" x2="21" y2="6"></line>
    <path d="M16 10a4 4 0 0 1-8 0"></path>
  `
        }
                    </svg>
                </div>
                <div class="activity-details">
                    <div class="activity-title">${isSale ? 'Sale' : 'Purchase'}: ${escapeHtml(product.name)} (${t.quantity})</div>
                    <div class="activity-meta">${timeAgo}</div>
                </div>
<div class="activity-amount">
  ${Number(t.totalPrice).toLocaleString()} UZS
</div>            </div>
        `;
    }).join('');
}

function updateTodaySummary() {
    const today = new Date();
    const todayStr = today.toISOString().split('T')[0];

    const todayTransactions = allTransactions.filter(t => {
        const txDate = new Date(t.txnDate).toISOString().split('T')[0];
        return txDate === todayStr;
    });

    const sales = todayTransactions.filter(t => t.txnType === 'Sale');
    const purchases = todayTransactions.filter(t => t.txnType === 'Purchase');

    const salesCount = sales.length;
    const salesAmount = sales.reduce((sum, t) => sum + Number(t.totalPrice), 0);

    const purchasesCount = purchases.length;
    const purchasesAmount = purchases.reduce(
        (sum, t) => sum + Number(t.totalPrice), 0
    );
    const netAmount = salesAmount - purchasesAmount;

    const todayDateEl = document.getElementById('todayDate');
    if (todayDateEl) {
        todayDateEl.innerText = today.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' });
    }

    const todaySalesCount = document.getElementById('todaySalesCount');
    const todaySalesAmount = document.getElementById('todaySalesAmount');
    const todayPurchasesCount = document.getElementById('todayPurchasesCount');
    const todayPurchasesAmount = document.getElementById('todayPurchasesAmount');
    const todayNetCount = document.getElementById('todayNetCount');
    const todayNetAmount = document.getElementById('todayNetAmount');

    if (todaySalesCount) todaySalesCount.innerText = salesCount;
    if (todaySalesAmount) todaySalesAmount.innerText = salesAmount.toLocaleString() + ' UZS';
    if (todayPurchasesCount) todayPurchasesCount.innerText = purchasesCount;
    if (todayPurchasesAmount) todayPurchasesAmount.innerText = purchasesAmount.toLocaleString() + ' UZS';
    if (todayNetCount) todayNetCount.innerText = salesCount + purchasesCount;
    if (todayNetAmount) todayNetAmount.innerText = netAmount.toLocaleString() + ' UZS';
}

// Category Distribution
function updateCategoryDistribution(products) {
    const categoryDistribution = document.getElementById('categoryDistribution');
    if (!categoryDistribution) return;

    const categoryData = {};
    let totalItems = 0;

    products.forEach(p => {
        if (!categoryData[p.category]) {
            categoryData[p.category] = 0;
        }
        categoryData[p.category] += p.quantity;
        totalItems += p.quantity;
    });

    const categories = Object.entries(categoryData)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5);

    if (categories.length === 0) {
        categoryDistribution.innerHTML = '<p class="text-muted">No categories available</p>';
        return;
    }

    categoryDistribution.innerHTML = categories.map(([category, count]) => {
        const percentage = totalItems > 0 ? (count / totalItems * 100) : 0;
        return `
            <div class="category-item">
                <div class="category-header">
                    <span class="category-name">${escapeHtml(category)}</span>
                    <span class="category-count">${count} items (${percentage.toFixed(0)}%)</span>
                </div>
                <div class="category-progress">
                    <div class="category-progress-bar" style="width: ${percentage}%"></div>
                </div>
            </div>
        `;
    }).join('');
}

// Top Products This Month
function updateTopProducts() {
    const topProductsList = document.getElementById('topProductsList');
    if (!topProductsList) return;

    const today = new Date();
    const firstDayOfMonth = new Date(today.getFullYear(), today.getMonth(), 1);

    const monthTransactions = allTransactions.filter(t => {
        const txDate = new Date(t.txnDate);
        return txDate >= firstDayOfMonth && t.txnType === 'Sale';
    });

    const productSales = {};
    monthTransactions.forEach(t => {
        if (!productSales[t.productId]) {
            productSales[t.productId] = 0;
        }
        productSales[t.productId] += t.quantity;
    });

    const topProducts = Object.entries(productSales)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5)
        .map(([productId, quantity]) => {
            const product = allProducts.find(p => p.productId === Number(productId));
            return { product, quantity };
        })
        .filter(item => item.product);

    if (topProducts.length === 0) {
        topProductsList.innerHTML = '<p class="text-muted">No sales data available</p>';
        return;
    }

    topProductsList.innerHTML = topProducts.map((item, index) => {
        const rankClass = index === 0 ? 'gold' : index === 1 ? 'silver' : index === 2 ? 'bronze' : 'default';
        return `
            <div class="top-product-item">
                <div class="top-product-rank ${rankClass}">${index + 1}</div>
                <div class="top-product-info">
                    <div class="top-product-name">${escapeHtml(item.product.name)}</div>
                    <div class="top-product-sales">${item.quantity} sold this month</div>
                </div>
            </div>
        `;
    }).join('');
}

// Quick Actions
function quickRecordSale() {
    switchPage('inventory');
    setTimeout(() => {
        const saleBtn = document.getElementById('saleProductBtn');
        if (saleBtn) saleBtn.click();
    }, 100);
}

function quickRecordPurchase() {
    switchPage('inventory');
    setTimeout(() => {
        const purchaseBtn = document.getElementById('purchaseProductBtn');
        if (purchaseBtn) purchaseBtn.click();
    }, 100);
}

/* ================= INVENTORY PAGE - PRODUCTS MANAGEMENT ================= */

function loadProducts() {
    ipcRenderer.send("send-data", JSON.stringify({ action: "get_all_products" }));
}

// Smart Refresh for Inventory Page
function refreshInventory() {
    // If the filter by supplier is active, we update only its products
    if (selectedSupplierId !== null) {
        loadProductsBySupplier(selectedSupplierId);
    } else {
        // Otherwise, load all products
        loadProducts();
    }
}

function loadProductsBySupplier(supplierId) {
    ipcRenderer.send("send-data", JSON.stringify({
        action: "get_products_by_supplier",
        supplierId: supplierId
    }));
}

function clearSupplierFilter() {
    selectedSupplierId = null;

    const title = document.getElementById('inventoryTitle');
    if (title) title.innerText = 'Inventory Items';

    const clearBtn = document.getElementById('clearSupplierFilterBtn');
    if (clearBtn) clearBtn.style.display = 'none';

    switchPage('suppliers');
    updateActiveNav('suppliers');
}

function addProduct() {
    if (!pName.value || !pCategory.value || !pPrice.value || pQty.value === '') {
        alert('Please fill in all required fields');
        return;
    }

    // If the filter is active, we force the use of selectedSupplierId
    const supplierId = selectedSupplierId !== null
        ? selectedSupplierId
        : (pSupplier.value ? Number(pSupplier.value) : null);

    ipcRenderer.send("send-data", JSON.stringify({
        action: "add_product",
        name: pName.value,
        category: pCategory.value,
        unitPrice: Number(pPrice.value),
        quantity: Number(pQty.value),
        supplierId: supplierId
    }));

    closeProductModal();
}

function testUpdate() {
    if (!selectedProductId) return;

    if (!pName.value || !pCategory.value || !pPrice.value || pQty.value === '') {
        alert('Please fill in all required fields');
        return;
    }

    ipcRenderer.send("send-data", JSON.stringify({
        action: "update_product",
        productId: selectedProductId,
        name: pName.value,
        category: pCategory.value,
        unitPrice: Number(pPrice.value),
        quantity: Number(pQty.value),
        supplierId: pSupplier.value ? Number(pSupplier.value) : null
    }));

    closeProductModal();
}

function testDelete() {
    if (!selectedProductId) return;

    if (confirm('Are you sure you want to delete this product? This action cannot be undone.')) {
        ipcRenderer.send("send-data", JSON.stringify({
            action: "delete_product",
            productId: selectedProductId
        }));
        closeProductModal();
    }
}

function openProductModal(isEdit = false) {
    // ROLE CHECK
    if (!can(isEdit ? 'update' : 'add')) {
        return;
    }

    const modal = document.getElementById("productModal");
    modal.classList.remove("hidden");

    addBtn.style.display = isEdit ? "none" : "inline-flex";
    updateBtn.style.display = isEdit ? "inline-flex" : "none";
    deleteBtn.style.display =
        isEdit && currentRole === 'Admin'
            ? "inline-flex"
            : "none";

    document.getElementById("modalTitle").innerText =
        isEdit ? "Edit Item" : "Add New Item";

    if (!isEdit) {
        pName.value = "";
        pCategory.value = "";
        pPrice.value = "";
        pQty.value = "";
        selectedProductId = null;

        // If the supplier filter is active, set it and block the selection
        if (selectedSupplierId !== null) {
            pSupplier.value = selectedSupplierId;
            pSupplier.disabled = true;

            // Add a hint
            const supplierLabel = document.querySelector('label[for="pSupplier"]');
            if (supplierLabel && !supplierLabel.querySelector('.supplier-locked-hint')) {
                const hint = document.createElement('span');
                hint.className = 'supplier-locked-hint';
                hint.style.cssText = 'color: var(--muted-foreground); font-size: 0.75rem; font-weight: normal; margin-left: 0.5rem;';
                hint.textContent = '(locked to current supplier)';
                supplierLabel.appendChild(hint);
            }
        } else {
            pSupplier.value = "";
            pSupplier.disabled = false;

            // Remove the hint if there was one
            const hint = document.querySelector('.supplier-locked-hint');
            if (hint) hint.remove();
        }
    }
}

function closeProductModal() {
    const modal = document.getElementById("productModal");
    modal.classList.add("hidden");
    selectedProductId = null;

    // Unlock supplier select and remove the hint
    pSupplier.disabled = false;
    const hint = document.querySelector('.supplier-locked-hint');
    if (hint) hint.remove();
}

function renderProducts(products) {
    const tbody = document.getElementById("productsTableBody");
    const emptyState = document.getElementById("emptyState");

    if (products.length === 0) {
        tbody.style.display = 'none';
        emptyState.style.display = 'block';
        updateStatistics([]);
        return;
    }

    tbody.style.display = '';
    emptyState.style.display = 'none';
    tbody.innerHTML = "";

    //products.sort((a, b) => a.productId - b.productId);

    products.forEach(p => {
        const isDeleted = p.active === false; // First, we determine isDeleted

        const isOutOfStock = p.quantity === 0;
        const isLowStock = p.quantity > 0 && p.quantity < 10;
        const isInStock = p.quantity >= 10;

        let statusBadge = '';
        let statusText = '';

        if (isDeleted) {
            statusBadge = 'badge-deleted';
            statusText = 'Deleted';
        } else if (isOutOfStock) {
            statusBadge = 'badge-out';
            statusText = 'Out of Stock';
        } else if (isLowStock) {
            statusBadge = 'badge-low';
            statusText = 'Low Stock';
        } else {
            statusBadge = 'badge-ok';
            statusText = 'In Stock';
        }
        const tr = document.createElement("tr");
        if (isDeleted) tr.classList.add("row-deleted");
        tr.onclick = () => {

            if (p.active === false) return;
            if (!can('update')) return;

            selectedProductId = p.productId;
            pName.value = p.name;
            pCategory.value = p.category;
            pPrice.value = p.unitPrice;
            pQty.value = p.quantity;
            pSupplier.value = p.supplierId ?? "";
            openProductModal(true);
        };

        tr.innerHTML = `
            <td>${p.productId}</td>
            <td><strong>${escapeHtml(p.name)}</strong></td>
            <td><span class="badge badge-outline">${escapeHtml(p.category)}</span></td>
            <td>${Number(p.unitPrice).toLocaleString()}</td>
            <td>${p.quantity}</td>
            <td>${getSupplierName(p.supplierId)}</td>
            <td>
    <span class="badge ${statusBadge}">${statusText}</span>
</td>
            <td class="text-center">
    <button class="action-btn action-btn-sale" 
        ${isDeleted ? 'disabled style="opacity: 0.3; cursor: not-allowed;"' : ''}
        onclick="event.stopPropagation(); if (!this.disabled) handleTransaction(${p.productId}, 'Sale', '${escapeHtml(p.name)}', ${p.quantity})">
        ➖
    </button>
</td>
<td class="text-center">
  <button
  class="action-btn action-btn-purchase ${(!can('purchase') || isDeleted) ? 'disabled' : ''}"
  ${
            (!can('purchase') || isDeleted)
                ? 'data-tooltip="Action not allowed" onclick="event.stopPropagation()"'
                : `onclick="event.stopPropagation(); handleTransaction(${p.productId}, 'Purchase', '${escapeHtml(p.name)}', ${p.quantity})"`
        }
>
  ➕
</button>

</td>

<td class="text-center">
  <button
    class="action-btn action-btn-delete ${currentRole !== 'Admin' || isDeleted ? 'disabled' : ''}"
    ${currentRole !== 'Admin' || isDeleted
            ? 'data-tooltip="You don’t have permission to delete" onclick="event.stopPropagation()"'
            : `onclick="event.stopPropagation(); if (confirm('Delete ${escapeHtml(p.name)}?')) {
              selectedProductId = ${p.productId};
              testDelete();
           }"`
        }
  >
    🗑️
  </button>
</td>
        `;
        tbody.appendChild(tr);
    });

    updateStatistics(products);
}

function updateStatistics(products) {
    const activeProducts = products.filter(p => p.active);

    let totalQty = 0;
    let totalValue = 0;
    let lowStock = 0;
    const categories = new Set();

    activeProducts.forEach(p => {
        totalQty += p.quantity;
        totalValue += p.unitPrice * p.quantity;
        if (p.quantity < 10) lowStock++;
        categories.add(p.category);
    });

    // Update both statistics (Dashboard and Inventory)
    const updates = [
        { items: totalItemsEl, products: totalProductsEl, value: totalValueEl, low: lowStockEl, cats: categoriesEl },
        { items: totalItemsEl2, products: totalProductsEl2, value: totalValueEl2, low: lowStockEl2, cats: categoriesEl2 }
    ];

    updates.forEach(els => {
        if (els.items) els.items.innerText = totalQty.toLocaleString();
        if (els.products) els.products.innerText = `Across ${activeProducts.length} products`;
        if (els.value) els.value.innerText = totalValue.toLocaleString() + " UZS";
        if (els.low) els.low.innerText = lowStock;
        if (els.cats) els.cats.innerText = categories.size;
    });

    // Updating additional Dashboard sections
    if (currentPage === 'dashboard') {
        updateDashboardSections(products, activeProducts);
    }
}

// Filters & Search
function buildCategoryFilter(products) {
    if (!categoryFilter) return;

    const currentValue = categoryFilter.value;
    categoryFilter.innerHTML = `<option value="ALL">All Categories</option>`;

    const cats = [...new Set(products.map(p => p.category))].sort();
    cats.forEach(cat => {
        const opt = document.createElement("option");
        opt.value = cat;
        opt.textContent = cat;
        categoryFilter.appendChild(opt);
    });

    // Restore selection if still valid
    if (currentValue && [...categoryFilter.options].some(opt => opt.value === currentValue)) {
        categoryFilter.value = currentValue;
    }
}

function applyCategoryFilter() {
    if (!categoryFilter) return;

    selectedCategory = categoryFilter.value;

    const searchInput = document.getElementById("productSearch");
    if (searchInput) {
        searchInput.value = "";
    }

    // Otherwise - from allProducts
    const baseProducts = selectedSupplierId !== null
        ? filteredProducts
        : allProducts;

    const filtered =
        selectedCategory === "ALL"
            ? baseProducts
            : baseProducts.filter(p => p.category === selectedCategory);

    renderProducts(filtered);
}

function searchProducts() {
    const searchInput = document.getElementById("productSearch");
    if (!searchInput) return;

    const searchTerm = searchInput.value.toLowerCase();

    if (!allProducts || allProducts.length === 0) {
        return;
    }

    const baseProducts = selectedSupplierId !== null
        ? filteredProducts
        : allProducts;

    if (searchTerm === "") {
        const toRender = selectedCategory === "ALL"
            ? baseProducts
            : baseProducts.filter(p => p.category === selectedCategory);

        renderProducts(toRender);
    } else {
        const categoryFiltered = selectedCategory === "ALL"
            ? baseProducts
            : baseProducts.filter(p => p.category === selectedCategory);

        const searchFiltered = categoryFiltered.filter(p => {
            // Get supplier name
            const supplier = allSuppliers.find(s => s.supplierId === p.supplierId);
            const supplierName = supplier ? supplier.name.toLowerCase() : '';

            // Get status
            const isOutOfStock = p.quantity === 0;
            const isLowStock = p.quantity > 0 && p.quantity < 10;
            const isInStock = p.quantity >= 10;

            let status = '';
            if (isOutOfStock) status = 'out of stock';
            else if (isLowStock) status = 'low stock';
            else if (isInStock) status = 'in stock';

            // Search across all fields
            return (
                (p.name && p.name.toLowerCase().includes(searchTerm)) ||
                (p.category && p.category.toLowerCase().includes(searchTerm)) ||
                (p.unitPrice != null && p.unitPrice.toString().includes(searchTerm)) ||
                (p.quantity != null && p.quantity.toString().includes(searchTerm)) ||
                supplierName.includes(searchTerm) ||
                status.includes(searchTerm)
            );
        });

        renderProducts(searchFiltered);
    }
}

function updateCategoryDatalist(products) {
    const datalist = document.getElementById("categoryList");
    if (!datalist) return;

    datalist.innerHTML = "";

    const categories = [...new Set(products.map(p => p.category))].sort();

    categories.forEach(cat => {
        const option = document.createElement("option");
        option.value = cat;
        datalist.appendChild(option);
    });
}

/* ================= TRANSACTIONS (QUICK MODAL) ================= */

function handleTransaction(productId, txnType, productName, currentQty) {
    const isDecrease = txnType === 'Sale';
    const actionVerb = isDecrease ? 'sell' : 'purchase';

    document.getElementById("txnTitle").innerText =
        txnType === 'Sale' ? "Sell Product" : "Purchase Product";

    document.getElementById("txnInfo").innerText =
        `How many units of "${productName}" to ${actionVerb}?\nCurrent stock: ${currentQty}`;

    const qtyInput = document.getElementById("txnQty");
    qtyInput.value = "1";
    qtyInput.max = currentQty;

    pendingTransaction = {
        productId,
        txnType,
        currentQty
    };

    document.getElementById("txnModal").classList.remove("hidden");
}
function closeTxnModal() {
    document.getElementById("txnModal").classList.add("hidden");
    pendingTransaction = null;
}

function confirmTransaction() {
    if (!pendingTransaction) return;

    const qty = parseInt(document.getElementById("txnQty").value);

    if (isNaN(qty) || qty <= 0) {
        alert("Enter a valid quantity");
        return;
    }

    if (
        pendingTransaction.txnType === "Sale" &&
        qty > pendingTransaction.currentQty
    ) {
        alert("Not enough stock");
        return;
    }

    ipcRenderer.send("send-data", JSON.stringify({
        action: "record_transaction",
        transaction: {
            productId: pendingTransaction.productId,
            txnType: pendingTransaction.txnType,
            quantity: qty,
            notes: "Transaction via inventory UI"
        }
    }));

    closeTxnModal();
}

/* ================= SUPPLIERS PAGE ================= */

// Data Loading
function loadSuppliers() {
    ipcRenderer.send("send-data", JSON.stringify({ action: "get_all_suppliers" }));
}

// CRUD Operations
function addSupplier() {
    const sName = document.getElementById("sName");
    const sContact = document.getElementById("sContact");
    const sEmail = document.getElementById("sEmail");
    const sAddress = document.getElementById("sAddress");

    if (!sName.value || !sContact.value) {
        alert('Please fill in all required fields');
        return;
    }

    ipcRenderer.send("send-data", JSON.stringify({
        action: "add_supplier",
        supplier: {
            name: sName.value,
            contactInfo: sContact.value,
            email: sEmail.value || "",
            address: sAddress.value || ""
        }
    }));
    closeSupplierModal();
}

function updateSupplier() {
    if (!selectedSupplierId) return;

    const sName = document.getElementById("sName");
    const sContact = document.getElementById("sContact");
    const sEmail = document.getElementById("sEmail");
    const sAddress = document.getElementById("sAddress");

    if (!sName.value || !sContact.value) {
        alert('Please fill in all required fields');
        return;
    }

    ipcRenderer.send("send-data", JSON.stringify({
        action: "update_supplier",
        supplier: {
            supplierId: selectedSupplierId,
            name: sName.value,
            contactInfo: sContact.value,
            email: sEmail.value || "",
            address: sAddress.value || ""
        }
    }));
    closeSupplierModal();
}

function deleteSupplier() {
    if (currentRole !== 'Admin') {
        alert('Only admins can delete suppliers');
        return;
    }
    if (!selectedSupplierId) return;

    if (confirm('Are you sure you want to delete this supplier? This action cannot be undone.')) {
        ipcRenderer.send("send-data", JSON.stringify({
            action: "delete_supplier",
            supplierId: selectedSupplierId
        }));
        closeSupplierModal();
    }
}

// Modal Management
function openSupplierModal(isEdit = false) {
    const modal = document.getElementById("supplierModal");
    modal.classList.remove("hidden");

    const addSupplierBtn = document.getElementById("addSupplierBtn");
    const updateSupplierBtn = document.getElementById("updateSupplierBtn");
    const deleteSupplierBtn = document.getElementById("deleteSupplierBtn");

    addSupplierBtn.style.display = isEdit ? "none" : "inline-flex";
    updateSupplierBtn.style.display = isEdit ? "inline-flex" : "none";
    deleteSupplierBtn.style.display =
        isEdit && currentRole === 'Admin'
            ? "inline-flex"
            : "none";

    document.getElementById("supplierModalTitle").innerText =
        isEdit ? "Edit Supplier" : "Add New Supplier";

    if (!isEdit) {
        document.getElementById("sName").value = "";
        document.getElementById("sContact").value = "";
        document.getElementById("sEmail").value = "";
        document.getElementById("sAddress").value = "";
        selectedSupplierId = null;
    }
}

function closeSupplierModal() {
    const modal = document.getElementById("supplierModal");
    modal.classList.add("hidden");
    selectedSupplierId = null;
}

//Rendering
function renderSuppliers(suppliers) {
    const tbody = document.getElementById("suppliersTableBody");
    const emptyState = document.getElementById("suppliersEmptyState");

    if (suppliers.length === 0) {
        tbody.style.display = 'none';
        emptyState.style.display = 'block';
        return;
    }

    tbody.style.display = '';
    emptyState.style.display = 'none';
    tbody.innerHTML = "";

    suppliers
        .sort((a, b) => {
            // Active up, Deleted down
            if (a.active !== b.active) {
                return b.active - a.active; // true > false
            }
            return a.supplierId - b.supplierId;
        })
        .forEach(s => {

            const isDeleted = s.active === false;

            const tr = document.createElement("tr");
            if (isDeleted) tr.classList.add("row-deleted");

            if (currentRole !== 'Cashier' && s.active !== false) {
                tr.onclick = () => {
                    selectedSupplierId = s.supplierId;
                    document.getElementById("sName").value = s.name;
                    document.getElementById("sContact").value = s.contactInfo;
                    document.getElementById("sEmail").value = s.email || "";
                    document.getElementById("sAddress").value = s.address || "";
                    openSupplierModal(true);
                };
            }

            tr.innerHTML = `
    <td>${s.supplierId}</td>
    <td><strong>${escapeHtml(s.name)}</strong></td>
    <td>${escapeHtml(s.contactInfo)}</td>
    <td>${s.email ? escapeHtml(s.email) : '-'}</td>
    <td>${s.address ? escapeHtml(s.address) : '-'}</td>
    <td class="text-center">
        <span class="badge badge-outline">${s.productCount || 0}</span>
    </td>
    <td class="text-center">
        ${
                isDeleted
                    ? `<span style="opacity:0.3; cursor:not-allowed;" data-tooltip="Supplier is deleted">📦</span>`
                    : `<button
            class="action-btn action-btn-primary"
            title="View Products"
            onclick="event.stopPropagation();
            selectedSupplierId=${s.supplierId};
            loadProductsBySupplier(${s.supplierId});"
        >📦</button>`
            }
    </td>
    <td class="text-center">
      ${
                currentRole === 'Cashier' || isDeleted
                    ? `<span style="opacity:0.3; cursor:not-allowed;" data-tooltip="${isDeleted ? 'Supplier is deleted' : 'Action not allowed'}">✏️</span>`
                    : `<button
        class="action-btn"
        title="Edit Supplier"
        onclick="event.stopPropagation();
        selectedSupplierId=${s.supplierId};
        document.getElementById('sName').value='${escapeHtml(s.name)}';
        document.getElementById('sContact').value='${escapeHtml(s.contactInfo)}';
        document.getElementById('sEmail').value='${s.email ? escapeHtml(s.email) : ''}';
        document.getElementById('sAddress').value='${s.address ? escapeHtml(s.address) : ''}';
        openSupplierModal(true);"
    >✏️</button>`
            }
    </td>
    <td class="text-center">
      ${
                currentRole !== 'Admin' || isDeleted
                    ? `<span style="opacity:0.3; cursor:not-allowed;" data-tooltip="Action not allowed">🗑️</span>`
                    : `<button
        class="action-btn action-btn-delete"
        title="Delete Supplier"
        onclick="event.stopPropagation();
        if (confirm('Delete ${escapeHtml(s.name)}?')) {
            selectedSupplierId=${s.supplierId};
            deleteSupplier();
        }"
    >🗑️</button>`
            }
    </td>
`;
            tbody.appendChild(tr);
        });
}

// Search
function searchSuppliers() {
    const searchInput = document.getElementById("supplierSearch");
    if (!searchInput) return;

    const searchTerm = searchInput.value.toLowerCase();

    // If the providers are not loaded yet
    if (!allSuppliers || allSuppliers.length === 0) {
        return;
    }

    if (searchTerm === "") {
        filteredSuppliers = allSuppliers;
    } else {
        filteredSuppliers = allSuppliers.filter(s =>
            s.name.toLowerCase().includes(searchTerm) ||
            s.contactInfo.toLowerCase().includes(searchTerm) ||
            (s.email && s.email.toLowerCase().includes(searchTerm)) ||
            (s.address && s.address.toLowerCase().includes(searchTerm))
        );
    }

    renderSuppliers(filteredSuppliers);
}

// Helper
function buildSupplierSelect() {
    if (!pSupplier) return;
    if (!allSuppliers.length) {
        pSupplier.innerHTML = `<option value="">No suppliers available</option>`;
        return;
    }

    pSupplier.innerHTML = `<option value="">No supplier</option>`;

    allSuppliers
        .filter(s => s.active !== false)
        .forEach(s => {
            const opt = document.createElement("option");
            opt.value = s.supplierId;
            opt.textContent = s.name;
            pSupplier.appendChild(opt);
        });
}

/* ================= TRANSACTIONS PAGE ================= */

// Load transactions based on current filter
function loadTransactions() {
    const action = transactionFilter === 'today'
        ? 'get_today_transactions'
        : 'get_all_transactions';

    ipcRenderer.send("send-data", JSON.stringify({ action }));
}

function loadTransactionsByRange() {
    const startDate = document.getElementById('txnStartDate').value;
    const endDate = document.getElementById('txnEndDate').value;

    if (!startDate || !endDate) {
        alert('Please select both start and end dates');
        return;
    }

    if (new Date(startDate) > new Date(endDate)) {
        alert('Start date cannot be after end date');
        return;
    }

    ipcRenderer.send("send-data", JSON.stringify({
        action: "get_transactions_by_date_range",
        startDate: startDate,
        endDate: endDate
    }));
}

function loadProductsForTransaction() {
    // Products will be loaded from allProducts state
    const select = document.getElementById('txnProduct');
    if (!select) return;

    select.innerHTML = '<option value="">Select product...</option>';

    const activeProducts = allProducts.filter(p => p.active !== false);
    activeProducts.forEach(p => {
        const opt = document.createElement('option');
        opt.value = p.productId;
        opt.textContent = `${p.name} (Stock: ${p.quantity})`;
        opt.dataset.price = p.unitPrice;
        opt.dataset.quantity = p.quantity;
        select.appendChild(opt);
    });
}

// Transaction Form
function onProductChange() {
    const select = document.getElementById('txnProduct');
    const selectedOption = select.options[select.selectedIndex];

    const unitPriceInput = document.getElementById('txnUnitPrice');
    const availableStockSpan = document.getElementById('availableStock');
    const availableStockGroup = document.getElementById('availableStockGroup');
    const txnQuantity = document.getElementById('txnQuantity');

    if (select.value) {
        const price = selectedOption.dataset.price;
        const quantity = selectedOption.dataset.quantity;

        unitPriceInput.value = Number(price).toLocaleString();
        availableStockSpan.textContent = quantity;

        // Show/hide available stock based on transaction type
        const isSale = document.getElementById('txnTypeSale').checked;
        availableStockGroup.style.display = isSale ? 'block' : 'none';

        // Reset quantity
        txnQuantity.value = '';
    } else {
        unitPriceInput.value = '';
        availableStockSpan.textContent = '—';
        txnQuantity.value = '';
    }

    calculateTotal();
}

// Calculate total price
function calculateTotal() {
    const select = document.getElementById('txnProduct');
    const selectedOption = select.options[select.selectedIndex];
    const quantity = document.getElementById('txnQuantity').value;
    const totalPriceInput = document.getElementById('txnTotalPrice');

    if (select.value && quantity) {
        const unitPrice = Number(selectedOption.dataset.price);
        const total = unitPrice * Number(quantity);
        totalPriceInput.value = total.toLocaleString();
    } else {
        totalPriceInput.value = '';
    }
}

// Reset transaction form
function resetTransactionForm() {
    document.getElementById('txnTypeSale').checked = true;
    document.getElementById('txnProduct').value = '';
    document.getElementById('txnQuantity').value = '';
    document.getElementById('txnUnitPrice').value = '';
    document.getElementById('txnTotalPrice').value = '';
    document.getElementById('txnNotes').value = '';
    document.getElementById('availableStock').textContent = '—';
    document.getElementById('availableStockGroup').style.display = 'block';
}

// Confirm transaction from transactions page
function confirmTransactionFromPage() {
    const txnType = document.getElementById('txnTypeSale').checked ? 'Sale' : 'Purchase';

    if (currentRole === 'Cashier' && txnType === 'Purchase') {
        alert('You do not have permission to perform purchases');
        return;
    }

    const productSelect = document.getElementById('txnProduct');
    const productId = productSelect.value;
    const quantity = Number(document.getElementById('txnQuantity').value);
    const notes = document.getElementById('txnNotes').value;

    if (!productId) {
        alert('Please select a product');
        return;
    }

    if (!quantity || quantity <= 0) {
        alert('Please enter a valid quantity');
        return;
    }

    // Check stock for sale
    if (txnType === 'Sale') {
        const selectedOption = productSelect.options[productSelect.selectedIndex];
        const availableStock = Number(selectedOption.dataset.quantity);

        if (quantity > availableStock) {
            alert(`Not enough stock. Available: ${availableStock}`);
            return;
        }
    }

    // Send transaction to server
    ipcRenderer.send("send-data", JSON.stringify({
        action: "record_transaction",
        transaction: {
            productId: Number(productId),
            txnType: txnType,
            quantity: quantity,
            notes: notes || ""
        }
    }));

    // Reset form
    resetTransactionForm();
}

// Filters
function applyTransactionFilter() {
    const filterToday = document.getElementById('filterToday');
    const filterAll = document.getElementById('filterAll');
    const filterCustom = document.getElementById('filterCustom');
    const dateRangeInputs = document.getElementById('dateRangeInputs');

    if (filterToday.checked) {
        transactionFilter = 'today';
        if (dateRangeInputs) dateRangeInputs.style.display = 'none';
        loadTransactions();
    } else if (filterAll.checked) {
        transactionFilter = 'all';
        if (dateRangeInputs) dateRangeInputs.style.display = 'none';
        loadTransactions();
    } else if (filterCustom.checked) {
        transactionFilter = 'custom';
        if (dateRangeInputs) dateRangeInputs.style.display = 'flex';

        const today = new Date().toISOString().split('T')[0];
        document.getElementById('txnStartDate').value = today;
        document.getElementById('txnEndDate').value = today;
    }
}

function resetTransactionFilters() {
    // Reset buttons
    document.getElementById("filterToday").checked = true;

    // Hide custom range
    const dateRange = document.getElementById("dateRangeInputs");
    if (dateRange) dateRange.style.display = "none";

    // Clear dates
    document.getElementById("txnStartDate").value = "";
    document.getElementById("txnEndDate").value = "";

    // Clear search
    document.getElementById("transactionSearch").value = "";

    // Load default transactions
    loadTransactions();
}

// Rendering
function renderTransactions(transactions) {
    const tbody = document.getElementById('transactionsTableBody');
    const emptyState = document.getElementById('transactionsEmptyState');

    if (!transactions || transactions.length === 0) {
        if (tbody) tbody.style.display = 'none';
        if (emptyState) emptyState.style.display = 'block';
        return;
    }

    if (tbody) tbody.style.display = '';
    if (emptyState) emptyState.style.display = 'none';

    tbody.innerHTML = '';

    transactions.forEach(txn => {
        const tr = document.createElement('tr');
        tr.style.cursor = 'default';

        // Format date
        const date = new Date(txn.txnDate);
        const dateStr = date.toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric'
        });
        const timeStr = date.toLocaleTimeString('en-US', {
            hour: '2-digit',
            minute: '2-digit'
        });

        // Get product name
        const product = allProducts.find(p => p.productId === txn.productId);
        const productName = product ? product.name : `Product #${txn.productId}`;

        // Badge class based on type
        const badgeClass = txn.txnType === 'Sale' ? 'badge-sale' : 'badge-purchase';

        tr.innerHTML = `
    <td>
        <div style="line-height: 1.3;">
            <div>${dateStr}</div>
            <div style="font-size: 0.75rem; color: var(--muted-foreground);">${timeStr}</div>
        </div>
    </td>
    <td class="text-center">
        <span class="badge ${badgeClass}">${txn.txnType}</span>
    </td>
            <td><strong>${escapeHtml(productName)}</strong></td>
            <td class="text-center">${txn.quantity}</td>
            <td class="text-center">${Number(txn.totalPrice).toLocaleString()}</td>
            <td>${txn.userName || 'System'}</td>
            <td style="max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
                ${txn.notes || '—'}
            </td>
        `;

        tbody.appendChild(tr);
    });
}

// Search
function searchTransactions() {
    const input = document.getElementById('transactionSearch');
    if (!input) return;

    const term = input.value.toLowerCase();

    if (!term) {
        renderTransactions(allTransactions);
        return;
    }

    const filtered = allTransactions.filter(txn => {
        const product = allProducts.find(p => p.productId === txn.productId);
        const productName = product?.name?.toLowerCase() || '';

        return (
            productName.includes(term) ||
            txn.txnType.toLowerCase().includes(term) ||
            (txn.userName && txn.userName.toLowerCase().includes(term)) ||
            (txn.notes && txn.notes.toLowerCase().includes(term))
        );
    });

    renderTransactions(filtered);
}

/* ================= MONITORING PAGE (ADMIN ONLY) ================= */

// Data Loading
function loadActiveSessions() {
    if (currentRole !== 'Admin') {
        console.log('Only admins can view monitoring data');
        return;
    }

    ipcRenderer.send("send-data", JSON.stringify({
        action: "get_connected_clients"
    }));
}

function loadAuditLogs() {
    if (currentRole !== 'Admin') {
        console.log('Only admins can view audit logs');
        return;
    }

    ipcRenderer.send("send-data", JSON.stringify({
        action: "get_audit_logs"
    }));
}

// Auto-refresh
function startMonitoringAutoRefresh() {
    if (monitoringInterval) return;

    monitoringInterval = setInterval(() => {
        if (currentPage === 'monitoring' && currentRole === 'Admin') {
            loadActiveSessions();
            loadAuditLogs();
        }
    }, 3000); // Every 5 secs
}

function stopMonitoringAutoRefresh() {
    if (monitoringInterval) {
        clearInterval(monitoringInterval);
        monitoringInterval = null;
    }
}

// Rendering
function renderActiveSessions(sessions) {
    const tbody = document.getElementById('activeSessionsTableBody');
    const emptyState = document.getElementById('activeSessionsEmptyState');
    const activeUsersCount = document.getElementById('activeUsersCount');

    if (!sessions || sessions.length === 0) {
        if (tbody) tbody.style.display = 'none';
        if (emptyState) emptyState.style.display = 'block';
        if (activeUsersCount) activeUsersCount.textContent = '0';
        return;
    }

    if (tbody) tbody.style.display = '';
    if (emptyState) emptyState.style.display = 'none';
    if (activeUsersCount) activeUsersCount.textContent = sessions.length;

    tbody.innerHTML = '';

    sessions.forEach(session => {
        const tr = document.createElement('tr');
        tr.style.cursor = 'default';

        const connectedDate = new Date(session.connectedAt);
        const dateStr = connectedDate.toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });

        let roleBadgeClass = 'badge-outline';

        if (session.role === 'Admin') {
            roleBadgeClass = 'badge-admin';
        } else if (session.role === 'Cashier') {
            roleBadgeClass = 'badge-cashier';
        } else if (session.role === 'Manager' || session.role === 'Stock Manager') {
            roleBadgeClass = 'badge-stock';
        }

        tr.innerHTML = `
            <td>
                <span class="status-online">
                    ${session.username || 'Guest'}
                </span>
            </td>
            <td>
                ${session.role ? `<span class="badge ${roleBadgeClass}">${session.role}</span>` : '—'}
            </td>
            <td>${session.ip || '—'}</td>
            <td>${dateStr}</td>
        `;

        tbody.appendChild(tr);
    });
}

function renderAuditLogs(logs) {
    const tbody = document.getElementById('auditLogTableBody');
    const emptyState = document.getElementById('auditLogEmptyState');
    const actionsTodayCount = document.getElementById('actionsTodayCount');

    if (!logs || logs.length === 0) {
        if (tbody) tbody.style.display = 'none';
        if (emptyState) emptyState.style.display = 'block';
        if (actionsTodayCount) actionsTodayCount.textContent = '0';
        return;
    }

    if (tbody) tbody.style.display = '';
    if (emptyState) emptyState.style.display = 'none';

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayActions = logs.filter(log => {
        const logDate = new Date(log.timestamp);
        logDate.setHours(0, 0, 0, 0);
        return logDate.getTime() === today.getTime();
    }).length;

    if (actionsTodayCount) {
        actionsTodayCount.textContent = todayActions;
    }

    tbody.innerHTML = '';

    logs.forEach(log => {
        const tr = document.createElement('tr');
        tr.style.cursor = 'default';

        const date = new Date(log.timestamp);
        const dateStr = date.toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric'
        });
        const timeStr = date.toLocaleTimeString('en-US', {
            hour: '2-digit',
            minute: '2-digit'
        });

        let badgeClass = 'badge-outline';
        const actionUpper = (log.action || '').toUpperCase();

        if (actionUpper.includes('LOGIN')) {
            badgeClass = 'badge-login';
        } else if (actionUpper.includes('LOGOUT')) {
            badgeClass = 'badge-logout';
        } else if (actionUpper.includes('ADD') || actionUpper.includes('REGISTER')) {
            badgeClass = 'badge-add';
        } else if (actionUpper.includes('UPDATE') || actionUpper.includes('CHANGE')) {
            badgeClass = 'badge-update';
        } else if (actionUpper.includes('DELETE') || actionUpper.includes('DEACTIVATE')) {
            badgeClass = 'badge-delete';
        } else if (actionUpper.includes('RECORD') || actionUpper.includes('TRANSACTION')) {
            badgeClass = 'badge-record';
        }

        tr.innerHTML = `
            <td>
                <div style="line-height: 1.3;">
                    <div>${dateStr}</div>
                    <div style="font-size: 0.75rem; color: var(--muted-foreground);">${timeStr}</div>
                </div>
            </td>
            <td>${log.username || 'System'}</td>
            <td>
                <span class="badge ${badgeClass}">${log.action || 'N/A'}</span>
            </td>
            <td style="max-width: 300px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
                ${log.details || '—'}
            </td>
        `;

        tbody.appendChild(tr);
    });
}

// Search
function searchAuditLogs() {
    const searchTerm = document.getElementById("auditLogSearch").value.toLowerCase();

    if (searchTerm === "") {
        filteredAuditLogs = allAuditLogs;
    } else {
        filteredAuditLogs = allAuditLogs.filter(log =>
            (log.username && log.username.toLowerCase().includes(searchTerm)) ||
            (log.action && log.action.toLowerCase().includes(searchTerm)) ||
            (log.details && log.details.toLowerCase().includes(searchTerm))
        );
    }

    renderAuditLogs(filteredAuditLogs);
}


/* ================= REPORTS PAGE ================= */

// Main Reports Functions
function loadReportsData() {
    if (!reportStartDate || !reportEndDate) {
        changeDateRange('month');
        return;
    }

    loadSalesSummary();
    loadTopProducts();
    loadCategorySales();
    loadInventoryStatus();
    loadStockLevels();
    loadTransactionStats();
    loadSupplierPerformance();

    // Only for ADMIN
    if (currentRole === 'Admin') {
        loadUserActivity();
    }
}

function changeDateRange(range) {
    currentDateRange = range;
    const today = new Date();

    if (range === 'today') {
        reportStartDate = formatDate(today);
        reportEndDate = formatDate(today);
    } else if (range === 'week') {
        const weekStart = new Date(today);
        const day = weekStart.getDay();
        const diff = weekStart.getDate() - day + (day === 0 ? -6 : 1);
        weekStart.setDate(diff);
        reportStartDate = formatDate(weekStart);
        reportEndDate = formatDate(today);
    } else if (range === 'month') {
        const monthStart = new Date(today.getFullYear(), today.getMonth(), 1);
        reportStartDate = formatDate(monthStart);
        reportEndDate = formatDate(today);
    }

    loadReportsData();
}

function changeStockTab(tab) {
    currentStockTab = tab;
    renderStockLevelsTable();
}

// Individual Report Loaders
function loadSalesSummary() {
    ipcRenderer.send('send-data', JSON.stringify({
        action: 'get_sales_summary',
        startDate: reportStartDate,
        endDate: reportEndDate
    }));
}

function loadTopProducts() {
    ipcRenderer.send('send-data', JSON.stringify({
        action: 'get_top_selling_products',
        limit: 10,
        startDate: reportStartDate,
        endDate: reportEndDate
    }));
}

function loadCategorySales() {
    ipcRenderer.send('send-data', JSON.stringify({
        action: 'get_sales_by_category',
        startDate: reportStartDate,
        endDate: reportEndDate
    }));
}

function loadInventoryStatus() {
    ipcRenderer.send('send-data', JSON.stringify({
        action: 'get_inventory_status'
    }));
}

function loadStockLevels() {
    ipcRenderer.send('send-data', JSON.stringify({
        action: 'get_products_by_stock_level'
    }));
}

function loadTransactionStats() {
    ipcRenderer.send('send-data', JSON.stringify({
        action: 'get_transaction_stats',
        startDate: reportStartDate,
        endDate: reportEndDate
    }));
}

function loadSupplierPerformance() {
    ipcRenderer.send('send-data', JSON.stringify({
        action: 'get_supplier_performance',
        startDate: reportStartDate,
        endDate: reportEndDate
    }));
}

function loadUserActivity() {
    ipcRenderer.send('send-data', JSON.stringify({
        action: 'get_user_activity_report',
        startDate: reportStartDate,
        endDate: reportEndDate
    }));
}

// Report Renderers
function renderTopProductsTable(products) {
    const tbody = document.getElementById('topProductsTableBody');
    if (!tbody) return;

    tbody.innerHTML = '';

    if (!products || products.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--muted-foreground); padding: 2rem;">No sales data for selected period</td></tr>';
        return;
    }

    products.forEach((product, index) => {
        const row = document.createElement('tr');
        row.innerHTML = `
        <td>
            ${index < 3
            ? `<span class="badge badge-secondary" style="width: 24px; height: 24px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; padding: 0;">${index + 1}</span>`
            : `<span style="display: inline-block; width: 24px; text-align: center;">${index + 1}</span>`
        }
        </td>
        <td>${product.name}</td>
        <td>${product.category}</td>
        <td class="text-right">${product.totalSold}</td>
        <td class="text-right">${formatNumber(product.totalRevenue)}</td>
    `;
        tbody.appendChild(row);
    });
}

// Render category sales chart (simple bar representation)
function renderCategorySalesChart(categories) {
    const chartDiv = document.getElementById('categorySalesChart');
    if (!chartDiv) return;

    if (!categories || categories.length === 0) {
        chartDiv.innerHTML = '<div style="text-align: center; color: var(--muted-foreground);">No category sales data</div>';
        return;
    }

    // Find max revenue for scaling
    const maxRevenue = Math.max(...categories.map(c => c.totalRevenue || 0));

    let html = '<div style="display: flex; flex-direction: column; gap: 1rem; padding: 1rem;">';

    categories.forEach(cat => {
        const percentage = maxRevenue > 0 ? (cat.totalRevenue / maxRevenue) * 100 : 0;
        html += `
            <div>
                <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">
                    <span style="font-weight: 500;">${cat.category}</span>
                    <span style="color: var(--muted-foreground);">${formatNumber(cat.totalRevenue)} (${cat.totalQuantity} items)</span>
                </div>
                <div style="width: 100%; height: 24px; background: var(--muted); border-radius: 4px; overflow: hidden;">
                    <div style="width: ${percentage}%; height: 100%; background: linear-gradient(90deg, #3b82f6, #8b5cf6); transition: width 0.3s;"></div>
                </div>
            </div>
        `;
    });

    html += '</div>';
    chartDiv.innerHTML = html;
}

function renderStockLevelsTable() {
    const tbody = document.getElementById('stockLevelsTableBody');
    if (!tbody) return;

    tbody.innerHTML = '';

    const products = stockLevelsData[currentStockTab] || [];

    if (products.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" style="text-align: center; color: var(--muted-foreground); padding: 2rem;">No products in this category</td></tr>';
        return;
    }

    products.slice(0, 20).forEach(product => {
        const totalValue = product.quantity * product.unitPrice;
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${product.name}</td>
            <td>${product.category}</td>
            <td class="text-right">${product.quantity}</td>
            <td class="text-right">${formatNumber(totalValue)}</td>
        `;
        tbody.appendChild(row);
    });
}

function renderSupplierPerformanceTable(suppliers) {
    const tbody = document.getElementById('supplierPerformanceTableBody');
    if (!tbody) return;

    tbody.innerHTML = '';

    if (!suppliers || suppliers.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" style="text-align: center; color: var(--muted-foreground); padding: 2rem;">No supplier data</td></tr>';
        return;
    }

    suppliers.forEach(supplier => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${supplier.name}</td>
            <td class="text-center">${supplier.productCount}</td>
            <td class="text-center">${supplier.totalItemsSold}</td>
            <td class="text-right">${formatNumber(supplier.totalRevenue)}</td>
        `;
        tbody.appendChild(row);
    });
}

function renderUserActivityTable(users) {
    const tbody = document.getElementById('userActivityTableBody');
    if (!tbody) return;

    tbody.innerHTML = '';

    if (!users || users.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" style="text-align: center; color: var(--muted-foreground); padding: 2rem;">No user activity data</td></tr>';
        return;
    }

    users.forEach(user => {
        const row = document.createElement('tr');

        // Role badge styling based on role type
        let roleBadgeStyle = '';

        if (user.role === 'Admin') {
            roleBadgeStyle = 'background-color: #dbeafe; color: #3b82f6; border: 1px solid #93c5fd;';
        } else if (user.role === 'Stock Manager') {
            roleBadgeStyle = 'background-color: #fef3c7; color: #f59e0b; border: 1px solid #fcd34d;';
        } else if (user.role === 'Cashier') {
            roleBadgeStyle = 'background-color: #d1fae5; color: #10b981; border: 1px solid #6ee7b7;';
        } else {
            roleBadgeStyle = 'background-color: var(--muted); color: var(--muted-foreground); border: 1px solid var(--border);';
        }

        const topUserBadge = user.actionCount > 100 ? '<span class="badge badge-secondary" style="margin-left: 0.01rem;"> 🏆Top User</span>' : '';
        const lastActivity = user.lastAction ? new Date(user.lastAction).toLocaleString('uz-UZ') : 'No activity';

        row.innerHTML = `
            <td>
  <div style="display: flex; align-items: center; gap: 0.5rem;">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
         style="width: 1rem; height: 1rem; color: var(--muted-foreground);">
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
      <circle cx="12" cy="7" r="4"></circle>
    </svg>

    <span>
      ${user.username}
      ${topUserBadge}
    </span>
  </div>
</td>

            <td style="text-align: center;"><span style="${roleBadgeStyle} padding: 0.125rem 0.5rem; border-radius: 0.75rem; font-size: 0.75rem; font-weight: 500; display: inline-block;">${user.role}</span></td>
            <td class="text-right">${user.actionCount}</td>
            <td>${lastActivity}</td>
        `;
        tbody.appendChild(row);
    });

}
/* ================= SERVER COMMUNICATION ================= */

let buffer = "";

ipcRenderer.on("server-response", (_, chunk) => {
    buffer += chunk;

    const messages = buffer.split("\n");
    buffer = messages.pop();

    for (const msg of messages) {
        if (!msg.trim()) continue;

        try {
            const response = JSON.parse(msg);
            console.log("SERVER RESPONSE:", response);
            handleServerResponse(response);
        } catch (err) {
            console.error("JSON parse error:", err, msg);
        }
    }
});

// Response Handler
function handleServerResponse(response) {
    /* ===== ERROR ===== */
    if (!response.success) {
        errorBox.innerText = response.message;
        errorBox.style.display = 'block';
        return;
    }

    errorBox.innerText = "";
    errorBox.style.display = 'none';

    /* ===== AUTH ===== */
    if (response.data?.role) {
        const currentUser = response.data.username;
        currentRole = response.data.role;

        roleLabel.innerText = `${currentUser} (${currentRole})`;

        const purchaseOption = document.getElementById('purchaseOption');
        const txnTypeSale = document.getElementById('txnTypeSale');

        if (purchaseOption) {
            purchaseOption.style.display = 'block';
        }

        if (currentRole === 'Cashier') {
            if (purchaseOption) purchaseOption.style.display = 'none';
            if (txnTypeSale) txnTypeSale.checked = true;
        }

        applyRoleVisibility();
        applyReportsVisibility();
        applyRolePermissions();

        const addProductBtn = document.getElementById("addProductBtn");
        if (addProductBtn) {
            addProductBtn.classList.remove("disabled");
            addProductBtn.removeAttribute("data-tooltip");
            addProductBtn.onclick = null;
            addProductBtn.style.pointerEvents = '';
            addProductBtn.style.opacity = '';

            if (!can('add')) {
                addProductBtn.classList.add("disabled");
                addProductBtn.setAttribute(
                    "data-tooltip",
                    "You don't have permission to add items"
                );
                addProductBtn.onclick = (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                };
                addProductBtn.style.pointerEvents = 'none';
                addProductBtn.style.opacity = '0.5';
            } else {
                addProductBtn.style.pointerEvents = 'auto';
                addProductBtn.style.opacity = '1';
            }
        }

        resetAfterLogin();
        loadSuppliers();
        showApp();
        loadProducts();
        return;
    }

    /* ===== LOGOUT ===== */
    if (response.message === "Logged out successfully") {
        resetUI();
        return;
    }

    /* ===== TRANSACTIONS ===== */
    if (response.message === "Transaction recorded successfully") {
        refreshInventory();
        loadTransactions();
        return;
    }

    /* ===== PRODUCTS CRUD MESSAGES ===== */
    if (
        [
            "Product added successfully",
            "Product updated successfully",
            "Product deactivated"
        ].includes(response.message)
    ) {
        closeProductModal();
        refreshInventory();
        return;
    }

    /* ===== SUPPLIERS CRUD MESSAGES ===== */
    if (
        [
            "Supplier added successfully",
            "Supplier updated successfully",
            "Supplier deleted successfully"
        ].includes(response.message)
    ) {
        loadSuppliers();
        return;
    }

    /* ===== SUPPLIERS DATA ===== */
    if (response.message === "Suppliers retrieved") {
        allSuppliers = response.data || [];
        filteredSuppliers = allSuppliers;
        renderSuppliers(filteredSuppliers);
        buildSupplierSelect();
        return;
    }

    /* ===== PRODUCTS DATA ===== */
    if (response.message === "Products retrieved") {
        allProducts = response.data || [];
        filteredProducts = allProducts;

        updateCategoryDatalist(allProducts);
        buildCategoryFilter(allProducts);
        applyCategoryFilter();

        if (currentPage === 'transactions') {
            loadProductsForTransaction();
            onProductChange();
        }

        return;
    }
    /* ===== TRANSACTIONS DATA ===== */
    if (response.message === "Transactions retrieved" ||
        response.message === "Today's transactions retrieved" ||
        response.message === "Transactions retrieved for date range") {
        allTransactions = response.data || [];
        if (typeof renderTransactions === 'function') {
            renderTransactions(allTransactions);
        }
        if (currentPage === 'dashboard') {
            updateRecentActivity();
            updateTodaySummary();
            updateTopProducts();
        }
        return;
    }

    /* ===== MONITORING DATA ===== */
    if (response.message === "Connected clients") {
        allActiveSessions = response.data || [];
        if (typeof renderActiveSessions === 'function') {
            renderActiveSessions(allActiveSessions);
        }
        return;
    }

    if (response.message === "Audit logs retrieved") {
        allAuditLogs = response.data || [];
        filteredAuditLogs = allAuditLogs;
        if (typeof renderAuditLogs === 'function') {
            renderAuditLogs(filteredAuditLogs);
        }
        return;
    }

    /* ===== REPORTS DATA ===== */
    // Sales Summary
    if (response.message === "Sales summary retrieved") {
        const data = response.data || {};
        const reportTotalRevenue = document.getElementById('reportTotalRevenue');
        const reportTotalTxns = document.getElementById('reportTotalTxns');
        const reportTotalItems = document.getElementById('reportTotalItems');
        const reportTotalTransactions = document.getElementById('reportTotalTransactions');

        if (reportTotalRevenue) reportTotalRevenue.textContent = formatNumber(data.totalRevenue || 0);
        if (reportTotalTxns) reportTotalTxns.textContent = data.totalTransactions || 0;
        if (reportTotalItems) reportTotalItems.textContent = data.totalItemsSold || 0;
        if (reportTotalTransactions) reportTotalTransactions.textContent = `${data.totalTransactions || 0} transactions`;
        return;
    }

    // Top Selling Products
    if (response.message === "Top selling products retrieved") {
        renderTopProductsTable(response.data || []);
        return;
    }

    // Sales by Category
    if (response.message === "Sales by category retrieved") {
        renderCategorySalesChart(response.data || []);
        return;
    }

    // Inventory Status
    if (response.message === "Inventory status retrieved") {
        const data = response.data || {};
        const invTotalProducts = document.getElementById('invTotalProducts');
        const invTotalItems = document.getElementById('invTotalItems');
        const invTotalValue = document.getElementById('invTotalValue');
        const invLowStock = document.getElementById('invLowStock');
        const invOutOfStock = document.getElementById('invOutOfStock');

        if (invTotalProducts) invTotalProducts.textContent = data.totalProducts || 0;
        if (invTotalItems) invTotalItems.textContent = data.totalItems || 0;
        if (invTotalValue) invTotalValue.textContent = formatNumber(data.totalValue || 0);
        if (invLowStock) invLowStock.textContent = data.lowStockCount || 0;
        if (invOutOfStock) invOutOfStock.textContent = data.outOfStockCount || 0;
        return;
    }

    // Stock Levels
    if (response.message === "Products by stock level retrieved") {
        stockLevelsData = response.data || { out_of_stock: [], low_stock: [], normal_stock: [] };

        const outOfStockCount = document.getElementById('outOfStockCount');
        const lowStockCountTab = document.getElementById('lowStockCountTab');
        const normalStockCount = document.getElementById('normalStockCount');

        if (outOfStockCount) outOfStockCount.textContent = stockLevelsData.out_of_stock.length;
        if (lowStockCountTab) lowStockCountTab.textContent = stockLevelsData.low_stock.length;
        if (normalStockCount) normalStockCount.textContent = stockLevelsData.normal_stock.length;

        renderStockLevelsTable();
        return;
    }

    if (response.message === "Products by supplier retrieved") {
        // Just updating filteredProducts for display
        const supplierProducts = response.data || [];
        filteredProducts = supplierProducts;

        stopMonitoringAutoRefresh();

        document.querySelectorAll('.page-content').forEach(p => p.classList.add('hidden'));

        const inventoryPage = document.getElementById('inventoryPage');
        if (inventoryPage) inventoryPage.classList.remove('hidden');

        currentPage = 'inventory';

        updateActiveNav('inventory');

        const title = document.getElementById('inventoryTitle');
        const clearBtn = document.getElementById('clearSupplierFilterBtn');

        if (title && selectedSupplierId) {
            const supplier = allSuppliers.find(s => s.supplierId === selectedSupplierId);
            title.innerText = supplier
                ? `Inventory — ${supplier.name}`
                : 'Inventory Items';
        }

        if (clearBtn && selectedSupplierId) {
            clearBtn.style.display = 'inline-flex';
        }

        buildCategoryFilter(supplierProducts);

        renderProducts(supplierProducts);

        return;
    }

    // Transaction Statistics
    if (response.message === "Transaction statistics retrieved") {
        const stats = response.data || {};
        const sales = stats.Sale || { count: 0, totalQuantity: 0, totalAmount: 0 };
        const purchases = stats.Purchase || { count: 0, totalQuantity: 0, totalAmount: 0 };

        const salesTxnCount = document.getElementById('salesTxnCount');
        const salesTotalQty = document.getElementById('salesTotalQty');
        const salesTotalAmount = document.getElementById('salesTotalAmount');
        const purchaseTxnCount = document.getElementById('purchaseTxnCount');
        const purchaseTotalQty = document.getElementById('purchaseTotalQty');
        const purchaseTotalAmount = document.getElementById('purchaseTotalAmount');

        if (salesTxnCount) salesTxnCount.textContent = sales.count;
        if (salesTotalQty) salesTotalQty.textContent = sales.totalQuantity;
        if (salesTotalAmount) salesTotalAmount.textContent = formatNumber(sales.totalAmount);
        if (purchaseTxnCount) purchaseTxnCount.textContent = purchases.count;
        if (purchaseTotalQty) purchaseTotalQty.textContent = purchases.totalQuantity;
        if (purchaseTotalAmount) purchaseTotalAmount.textContent = formatNumber(purchases.totalAmount);
        return;
    }

    // Supplier Performance
    if (response.message === "Supplier performance retrieved") {
        renderSupplierPerformanceTable(response.data || []);
        return;
    }

    // User Activity
    if (response.message === "User activity report retrieved") {
        renderUserActivityTable(response.data || []);
        return;
    }
}
// Navigation Events
document.addEventListener('DOMContentLoaded', () => {
    const navItems = document.querySelectorAll('.nav-item');

    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            navItems.forEach(nav => nav.classList.remove('active'));
            item.classList.add('active');
            const page = item.getAttribute('data-page');
            switchPage(page);
        });
    });

    // Event listener for Add Product Button
    const addProductBtn = document.getElementById('addProductBtn');
    if (addProductBtn) {
        addProductBtn.addEventListener('click', () => {
            if (can('add')) {
                openProductModal(false);
            }
        });
    }

    // Event listener for Apply Date Range Button
    const applyDateRangeBtn = document.getElementById('applyDateRangeBtn');
    if (applyDateRangeBtn) {
        applyDateRangeBtn.addEventListener('click', () => {
            loadTransactionsByRange();
        });
    }
});

// Listen for transaction type change to show/hide available stock
document.addEventListener('DOMContentLoaded', () => {
    const txnTypeSale = document.getElementById('txnTypeSale');
    const txnTypePurchase = document.getElementById('txnTypePurchase');
    const availableStockGroup = document.getElementById('availableStockGroup');

    if (txnTypeSale && txnTypePurchase && availableStockGroup) {
        txnTypeSale.addEventListener('change', () => {
            availableStockGroup.style.display = 'block';
        });

        txnTypePurchase.addEventListener('change', () => {
            availableStockGroup.style.display = 'none';
        });
    }
});

// Keyboard Shortcuts
document.addEventListener('keydown', (e) => {
    // ESC to close modal
    if (e.key === 'Escape') {
        closeProductModal();
    }

    // Ctrl/Cmd + K to add new product
    if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        if (
            document.getElementById("appSection").style.display !== "none" &&
            currentRole === 'Admin'
        ) {
            openProductModal();
        }
    }

});

/* ================= UTILITY FUNCTIONS ================= */

// Format number with thousand separators (Uzbek sum format)
function formatNumber(num) {
    return Math.round(num || 0).toLocaleString('uz-UZ');
}

// Format date to YYYY-MM-DD
function formatDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Time
function getTimeAgo(timestamp) {
    const now = new Date();
    const then = new Date(timestamp);
    const diffMs = now - then;
    const diffMins = Math.floor(diffMs / 60000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins} min ago`;

    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;

    const diffDays = Math.floor(diffHours / 24);
    return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;
}

// Helpers
function getSupplierName(supplierId) {
    if (!supplierId) {
        return '<span style="color: var(--muted-foreground); font-style: italic;">No supplier</span>';
    }

    const supplier = allSuppliers.find(s => s.supplierId === supplierId);
    return supplier ? escapeHtml(supplier.name) : '<span style="color: var(--muted-foreground);">Unknown</span>';
}
