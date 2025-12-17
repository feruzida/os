const { ipcRenderer } = require("electron");

/* ================= DOM ELEMENTS ================= */
const user = document.getElementById("user");
const pass = document.getElementById("pass");
const errorBox = document.getElementById("errorBox");
let pendingTransaction = null;

const roleLabel = document.getElementById("roleLabel");

const pName = document.getElementById("pName");
const pCategory = document.getElementById("pCategory");
const pPrice = document.getElementById("pPrice");
const pQty = document.getElementById("pQty");

const addBtn = document.getElementById("addBtn");
const updateBtn = document.getElementById("updateBtn");
const deleteBtn = document.getElementById("deleteBtn");

/* Dashboard */
const totalItemsEl = document.getElementById("totalItems");
const totalProductsEl = document.getElementById("totalProducts");
const totalValueEl = document.getElementById("totalValue");
const lowStockEl = document.getElementById("lowStock");
const categoriesEl = document.getElementById("categories");

/* Category filter */
const categoryFilter = document.getElementById("categoryFilter");

/* ================= STATE ================= */
let currentRole = null;
let selectedProductId = null;
//let currentUserId = null; // ← ДОБАВИЛИ


let allProducts = [];
let selectedCategory = "ALL";


function can(action) {
    const permissions = {
        Admin: ['add', 'update', 'delete', 'purchase', 'sale'],
        Manager: ['add', 'update', 'delete', 'purchase', 'sale'],
        "Stock Manager": ['add', 'update', 'delete', 'purchase', 'sale'],
        Cashier: ['sale']
    };

    return permissions[currentRole]?.includes(action);
}




/* ================= INITIALIZATION ================= */
ipcRenderer.send("connect-server");

// Auto-focus username on load
window.addEventListener('DOMContentLoaded', () => {
    if (user) user.focus();
});

// Enter key to login
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
    errorBox.innerText = "";
    errorBox.style.display = 'none';

    if (!user.value || !pass.value) {
        errorBox.innerText = "Please enter username and password";
        errorBox.style.display = 'block';
        return;
    }

    ipcRenderer.send("send-data", JSON.stringify({
        action: "login",
        username: user.value,
        password: pass.value
    }));
}

function logout() {
    ipcRenderer.send("send-data", JSON.stringify({ action: "logout" }));
}

/* ================= PRODUCTS ================= */
function loadProducts() {
    ipcRenderer.send("send-data", JSON.stringify({ action: "get_all_products" }));
}

/* ================= TRANSACTIONS ================= */
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


function addProduct() {
    if (!pName.value || !pCategory.value || !pPrice.value || pQty.value === '') {
        alert('Please fill in all required fields');
        return;
    }

    ipcRenderer.send("send-data", JSON.stringify({
        action: "add_product",
        name: pName.value,
        category: pCategory.value,
        unitPrice: Number(pPrice.value),
        quantity: Number(pQty.value)
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
        quantity: Number(pQty.value)
    }));
    closeProductModal();
}

function testDelete() {
    if (!selectedProductId) return;

    if (confirm('Are you sure you want to delete this product? This action cannot be undone.')) {
        ipcRenderer.send("send-data", JSON.stringify({
            action: "delete_product",
            product_id: selectedProductId
        }));
        closeProductModal();
    }
}

/* ================= UI HELPERS ================= */
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
    selectedCategory = "ALL";

    document.getElementById("productsTableBody").innerHTML = "";
    user.value = "";
    pass.value = "";
    roleLabel.innerText = "";
    errorBox.innerText = "";
    errorBox.style.display = 'none';

    showLogin();
}

/* ================= MODAL ================= */
function openProductModal(isEdit = false) {
    // 🔐 ROLE CHECK
    if (!can(isEdit ? 'update' : 'add')) {
        return;
    }

    const modal = document.getElementById("productModal");
    modal.classList.remove("hidden");

    addBtn.style.display = isEdit ? "none" : "inline-flex";
    updateBtn.style.display = isEdit ? "inline-flex" : "none";
    deleteBtn.style.display = isEdit ? "inline-flex" : "none";

    document.getElementById("modalTitle").innerText =
        isEdit ? "Edit Item" : "Add New Item";

    if (!isEdit) {
        pName.value = "";
        pCategory.value = "";
        pPrice.value = "";
        pQty.value = "";
        selectedProductId = null;
    }
}



function closeProductModal() {
    const modal = document.getElementById("productModal");
    modal.classList.add("hidden");
    selectedProductId = null;
}

/* ================= CATEGORY FILTER ================= */
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

    const filtered =
        selectedCategory === "ALL"
            ? allProducts
            : allProducts.filter(p => p.category === selectedCategory);

    renderProducts(filtered);
}

/* ================= SERVER RESPONSE ================= */
ipcRenderer.on("server-response", (e, data) => {
    const response = JSON.parse(data);
    console.log(response);

    if (!response.success) {
        errorBox.innerText = response.message;
        errorBox.style.display = 'block';
        return;
    }
    if (response.message === "Transaction recorded successfully") {
        loadProducts();
        return;
    }
    if (response.message === "Product deactivated") {
        loadProducts(); // ← АВТО ОБНОВЛЕНИЕ
        return;
    }
    // AFTER ADD PRODUCT
    if (response.success && response.message === "Product added successfully") {
        closeProductModal();
        loadProducts(); // ← авто обновление
        return;
    }

// AFTER UPDATE PRODUCT
    if (response.success && response.message === "Product updated successfully") {
        closeProductModal();
        loadProducts(); // ← авто обновление
        return;
    }


    errorBox.innerText = "";
    errorBox.style.display = 'none';

    /* LOGIN */
    if (response.data?.role) {
        currentRole = response.data.role;
        roleLabel.innerText = `${response.data.username} (${currentRole})`;

        // 🔐 ADD BUTTON PERMISSIONS (как Purchase/Delete)
        const addProductBtn = document.getElementById("addProductBtn");
        if (addProductBtn && !can('add')) {
            addProductBtn.classList.add("disabled");
            addProductBtn.setAttribute(
                "data-tooltip",
                "You don’t have permission to add items"
            );
            addProductBtn.onclick = (e) => e.stopPropagation();
        }

        resetAfterLogin();
        showApp();
        loadProducts();
        return;
    }




    /* LOGOUT */
    if (response.message === "Logged out successfully") {
        resetUI();
        return;
    }

    /* PRODUCTS */
    if (Array.isArray(response.data)) {
        allProducts = response.data;
        updateCategoryDatalist(allProducts);
        buildCategoryFilter(allProducts);
        applyCategoryFilter();
    }
});

/* ================= RENDER ================= */
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

    // 🔥 ВАЖНО: сортировка по ID
    //products.sort((a, b) => a.productId - b.productId);

    products.forEach(p => {
        const isLow = p.quantity < 10;
        const isDeleted = p.active === false;// если нет поля active, используй p.isActive
        const tr = document.createElement("tr");
        if (isDeleted) tr.classList.add("row-deleted");
        tr.onclick = () => {
            if (!can('update')) return;

            selectedProductId = p.productId;
            pName.value = p.name;
            pCategory.value = p.category;
            pPrice.value = p.unitPrice;
            pQty.value = p.quantity;
            openProductModal(true);
        };


        tr.innerHTML = `
            <td>${p.productId}</td>
            <td><strong>${escapeHtml(p.name)}</strong></td>
            <td><span class="badge badge-outline">${escapeHtml(p.category)}</span></td>
            <td>${Number(p.unitPrice).toLocaleString()}</td>
            <td>${p.quantity}</td>
            <td>
    <span class="badge ${isDeleted ? "badge-deleted" : (isLow ? "badge-low" : "badge-ok")}">
        ${isDeleted ? "Deleted" : (isLow ? "Low Stock" : "In Stock")}
    </span>
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
    class="action-btn action-btn-purchase ${!can('purchase') ? 'disabled' : ''}"
    ${
            !can('purchase')
                ? 'data-tooltip="You don’t have permission to purchase" onclick="event.stopPropagation()"'
                : `onclick="event.stopPropagation(); handleTransaction(${p.productId}, 'Purchase', '${escapeHtml(p.name)}', ${p.quantity})"`
        }
  >
    ➕
  </button>
</td>

<td class="text-center">
  <button
    class="action-btn action-btn-delete ${currentRole !== 'Admin' ? 'disabled' : ''}"
    ${currentRole !== 'Admin'
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

    totalItemsEl.innerText = totalQty.toLocaleString();
    totalProductsEl.innerText = `Across ${activeProducts.length} products`;
    totalValueEl.innerText = totalValue.toLocaleString() + " UZS";
    lowStockEl.innerText = lowStock;
    categoriesEl.innerText = categories.size;
}


function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/* ================= VISIBILITY ================= */
function showLogin() {
    document.getElementById("loginSection").style.display = "flex";
    document.getElementById("appSection").style.display = "none";
    if (user) user.focus();
}

function showApp() {
    document.getElementById("loginSection").style.display = "none";
    document.getElementById("appSection").style.display = "flex";
}

/* ================= KEYBOARD SHORTCUTS ================= */
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
