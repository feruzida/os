
// transactions.js - Transactions management logic

let allTransactions = [];
let allProducts = [];
let currentUser = null;

// Initialize page
document.addEventListener('DOMContentLoaded', async () => {
    // Check authentication
    const userStr = sessionStorage.getItem('currentUser');
    if (!userStr) {
        window.location.href = 'login.html';
        return;
    }

    currentUser = JSON.parse(userStr);

    // Check connection
    if (!socketClient.isConnected()) {
        alert('Not connected to server. Redirecting to login...');
        window.location.href = 'login.html';
        return;
    }

    // Load data
    await loadProducts();
    await loadTransactions();

    // Setup event listeners
    setupEventListeners();
});

// Setup event listeners
function setupEventListeners() {
    // Filters
    document.getElementById('type-filter').addEventListener('change', filterTransactions);
    document.getElementById('date-filter').addEventListener('change', filterTransactions);

    // Transaction buttons
    document.getElementById('new-sale-btn').addEventListener('click', () => {
        openTransactionModal('Sale');
    });

    document.getElementById('new-purchase-btn').addEventListener('click', () => {
        openTransactionModal('Purchase');
    });

    // Product selection
    document.getElementById('transaction-product').addEventListener('change', updateProductInfo);

    // Quantity change
    document.getElementById('transaction-quantity').addEventListener('input', calculateTotal);

    // Save transaction
    document.getElementById('save-transaction-btn').addEventListener('click', saveTransaction);
}

// Load products
async function loadProducts() {
    try {
        const response = await socketClient.send({ action: 'get_all_products' });

        if (response.success) {
            allProducts = response.data || [];
            populateProductDropdown();
        }
    } catch (error) {
        console.error('Error loading products:', error);
    }
}

// Load transactions
async function loadTransactions() {
    try {
        const response = await socketClient.send({ action: 'get_today_transactions' });

        if (response.success) {
            allTransactions = response.data || [];
            displayTransactions(allTransactions);
            updateStats();
        } else {
            showError('Failed to load transactions: ' + response.message);
        }
    } catch (error) {
        console.error('Error loading transactions:', error);
        showError('Failed to load transactions');
    }
}

// Populate product dropdown
function populateProductDropdown() {
    const select = document.getElementById('transaction-product');
    select.innerHTML = '<option value="">Select product</option>';

    allProducts.forEach(product => {
        const option = document.createElement('option');
        option.value = product.productId;
        option.textContent = `${product.name} - ${formatCurrency(product.unitPrice)} (Stock: ${product.quantity})`;
        option.dataset.price = product.unitPrice;
        option.dataset.stock = product.quantity;
        select.appendChild(option);
    });
}

// Update product info when selected
function updateProductInfo() {
    const select = document.getElementById('transaction-product');
    const selectedOption = select.options[select.selectedIndex];
    const stockInfo = document.getElementById('current-stock');
    const priceInput = document.getElementById('transaction-price');
    const quantityInput = document.getElementById('transaction-quantity');
    const transactionType = document.getElementById('transaction-type').value;

    if (selectedOption.value) {
        const stock = parseInt(selectedOption.dataset.stock);
        const price = parseFloat(selectedOption.dataset.price);

        stockInfo.textContent = `Current stock: ${stock} units`;
        priceInput.value = price.toFixed(2);

        // Set max quantity for sales
        if (transactionType === 'Sale') {
            quantityInput.max = stock;
            if (stock === 0) {
                stockInfo.textContent = '⚠️ Out of stock!';
                stockInfo.style.color = '#ef4444';
                quantityInput.value = 0;
                quantityInput.disabled = true;
            } else {
                quantityInput.disabled = false;
                quantityInput.value = 1;
            }
        } else {
            quantityInput.removeAttribute('max');
            quantityInput.disabled = false;
            quantityInput.value = 1;
        }

        calculateTotal();
    } else {
        stockInfo.textContent = '';
        priceInput.value = '';
        document.getElementById('transaction-total').value = '';
    }
}

// Calculate total price
function calculateTotal() {
    const quantity = parseInt(document.getElementById('transaction-quantity').value) || 0;
    const price = parseFloat(document.getElementById('transaction-price').value) || 0;
    const total = quantity * price;

    document.getElementById('transaction-total').value = formatCurrency(total);
}

// Display transactions
function displayTransactions(transactions) {
    const tbody = document.getElementById('transactions-table-body');

    if (!transactions || transactions.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="9" class="text-center">
                    <div class="empty-state">
                        <div class="empty-state-icon">💰</div>
                        <h3>No transactions found</h3>
                        <p>Start by recording your first transaction</p>
                    </div>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = transactions.map(txn => {
        const product = allProducts.find(p => p.productId === txn.productId);
        const productName = product ? product.name : `Product #${txn.productId}`;
        const unitPrice = txn.totalPrice / txn.quantity;

        return `
            <tr>
                <td>#${txn.txnId}</td>
                <td>
                    <span class="badge ${txn.txnType === 'Sale' ? 'badge-success' : 'badge-warning'}">
                        ${txn.txnType === 'Sale' ? '💵' : '📦'} ${txn.txnType}
                    </span>
                </td>
                <td><strong>${productName}</strong></td>
                <td>${txn.quantity}</td>
                <td>${formatCurrency(unitPrice)}</td>
                <td><strong>${formatCurrency(txn.totalPrice)}</strong></td>
                <td>${txn.username || 'User #' + txn.userId}</td>
                <td>${formatDateTime(txn.txnDate)}</td>
                <td>${txn.notes || '-'}</td>
            </tr>
        `;
    }).join('');
}

// Update statistics
function updateStats() {
    const sales = allTransactions.filter(t => t.txnType === 'Sale');
    const purchases = allTransactions.filter(t => t.txnType === 'Purchase');

    const totalSales = sales.length;
    const totalPurchases = purchases.length;
    const revenue = sales.reduce((sum, t) => sum + parseFloat(t.totalPrice), 0);

    document.getElementById('total-sales').textContent = totalSales;
    document.getElementById('total-purchases').textContent = totalPurchases;
    document.getElementById('revenue-today').textContent = formatCurrency(revenue);
}

// Filter transactions
function filterTransactions() {
    const typeFilter = document.getElementById('type-filter').value;
    const dateFilter = document.getElementById('date-filter').value;

    let filtered = allTransactions;

    // Type filter
    if (typeFilter) {
        filtered = filtered.filter(t => t.txnType === typeFilter);
    }

    // Date filter (for now just showing today's data)
    // You can implement date range filtering based on dateFilter value

    displayTransactions(filtered);
}

// Open transaction modal
function openTransactionModal(type) {
    const modal = document.getElementById('transaction-modal');
    const title = document.getElementById('modal-title');
    const form = document.getElementById('transaction-form');

    form.reset();
    document.getElementById('transaction-type').value = type;

    if (type === 'Sale') {
        title.textContent = '💵 New Sale';
        document.getElementById('save-transaction-btn').textContent = 'Complete Sale';
        document.getElementById('save-transaction-btn').className = 'btn btn-success';
    } else {
        title.textContent = '📦 New Purchase';
        document.getElementById('save-transaction-btn').textContent = 'Complete Purchase';
        document.getElementById('save-transaction-btn').className = 'btn btn-warning';
    }

    document.getElementById('transaction-total').value = '';
    document.getElementById('current-stock').textContent = '';

    modal.classList.add('active');
}

// Close transaction modal
function closeTransactionModal() {
    const modal = document.getElementById('transaction-modal');
    modal.classList.remove('active');
}

// Save transaction
async function saveTransaction() {
    const type = document.getElementById('transaction-type').value;
    const productId = document.getElementById('transaction-product').value;
    const quantity = parseInt(document.getElementById('transaction-quantity').value);
    const price = parseFloat(document.getElementById('transaction-price').value);
    const notes = document.getElementById('transaction-notes').value.trim();

    if (!productId || !quantity || !price) {
        alert('Please fill all required fields');
        return;
    }

    // Check stock for sales
    if (type === 'Sale') {
        const product = allProducts.find(p => p.productId == productId);
        if (product && quantity > product.quantity) {
            alert(`Insufficient stock! Only ${product.quantity} units available.`);
            return;
        }
    }

    const saveBtn = document.getElementById('save-transaction-btn');
    saveBtn.disabled = true;
    saveBtn.textContent = 'Processing...';

    try {
        const totalPrice = quantity * price;

        const response = await socketClient.send({
            action: 'record_transaction',
            productId: parseInt(productId),
            txnType: type,
            quantity: quantity,
            totalPrice: totalPrice,
            notes: notes || null
        });

        if (response.success) {
            closeTransactionModal();
            await loadProducts(); // Refresh product quantities
            await loadTransactions();
            alert(`${type} recorded successfully!`);
        } else {
            alert('Failed to record transaction: ' + response.message);
        }
    } catch (error) {
        console.error('Error saving transaction:', error);
        alert('Failed to record transaction');
    } finally {
        saveBtn.disabled = false;
        saveBtn.textContent = type === 'Sale' ? 'Complete Sale' : 'Complete Purchase';
    }
}

// Utility functions
function formatCurrency(amount) {
    return new Intl.NumberFormat('uz-UZ', {
        style: 'decimal',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
    }).format(amount);
}

function formatDateTime(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function showError(message) {
    alert('Error: ' + message);
}

// Close modal on outside click
window.onclick = function(event) {
    const modal = document.getElementById('transaction-modal');
    if (event.target === modal) {
        closeTransactionModal();
    }
};

// Make functions globally available
window.openTransactionModal = openTransactionModal;
window.closeTransactionModal = closeTransactionModal;