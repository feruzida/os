// dashboard.js - Main Dashboard Logic

let currentUser = null;
let statsInterval = null;

// Initialize dashboard on load
document.addEventListener('DOMContentLoaded', async () => {
    // Get user from session storage
    const userStr = sessionStorage.getItem('currentUser');
    if (!userStr) {
        window.location.href = 'login.html';
        return;
    }

    currentUser = JSON.parse(userStr);
    document.getElementById('username-display').textContent = currentUser.username;

    // Check connection
    if (!socketClient.isConnected()) {
        alert('Not connected to server. Redirecting to login...');
        window.location.href = 'login.html';
        return;
    }

    updateConnectionStatus(true);

    // Load dashboard data
    await loadDashboardData();

    // Setup auto-refresh
    statsInterval = setInterval(loadDashboardData, 30000); // Refresh every 30 seconds

    // Setup event listeners
    setupEventListeners();
});

// Setup event listeners
function setupEventListeners() {
    // Logout button
    document.getElementById('logout-btn').addEventListener('click', handleLogout);

    // Refresh button
    document.getElementById('refresh-btn').addEventListener('click', () => {
        loadDashboardData();
    });

    // Navigation buttons
    document.querySelectorAll('.nav-item').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const page = e.target.dataset.page;
            if (page === 'overview') return; // Already on overview

            // Navigate to other pages
            if (page === 'products') window.location.href = 'products.html';
            else if (page === 'suppliers') window.location.href = 'suppliers.html';
            else if (page === 'transactions') window.location.href = 'transactions.html';
            else if (page === 'reports') window.location.href = 'reports.html';
        });
    });
}

// Load dashboard data
async function loadDashboardData() {
    try {
        // Get statistics
        const [products, suppliers, transactions, lowStock] = await Promise.all([
            sendRequest({ action: 'get_all_products' }),
            sendRequest({ action: 'get_all_suppliers' }),
            sendRequest({ action: 'get_today_transactions' }),
            sendRequest({ action: 'get_low_stock', threshold: 50 })
        ]);

        // Update statistics
        updateStatistics(products.data, suppliers.data, transactions.data, lowStock.data);

        // Update recent transactions table
        updateRecentTransactions(transactions.data);

        // Update low stock alerts
        updateLowStockAlerts(lowStock.data);

    } catch (error) {
        console.error('Failed to load dashboard data:', error);
        showError('Failed to load dashboard data');
    }
}

// Update statistics cards
function updateStatistics(products, suppliers, transactions, lowStockItems) {
    // Total products
    document.getElementById('total-products').textContent = products?.length || 0;

    // Low stock count
    document.getElementById('low-stock').textContent = lowStockItems?.length || 0;

    // Total suppliers
    document.getElementById('total-suppliers').textContent = suppliers?.length || 0;

    // Today's sales
    const todaySales = transactions?.reduce((sum, txn) => {
        if (txn.txnType === 'Sale') {
            return sum + parseFloat(txn.totalPrice || 0);
        }
        return sum;
    }, 0) || 0;

    document.getElementById('today-sales').textContent = formatCurrency(todaySales);
}

// Update recent transactions table
function updateRecentTransactions(transactions) {
    const tbody = document.getElementById('recent-transactions');

    if (!transactions || transactions.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center">No transactions today</td></tr>';
        return;
    }

    // Sort by date (most recent first) and take top 10
    const recentTransactions = transactions
        .sort((a, b) => new Date(b.txnDate) - new Date(a.txnDate))
        .slice(0, 10);

    tbody.innerHTML = recentTransactions.map(txn => `
        <tr>
            <td>#${txn.txnId}</td>
            <td>${txn.productName || 'Product ID: ' + txn.productId}</td>
            <td>
                <span class="badge ${txn.txnType === 'Sale' ? 'badge-success' : 'badge-info'}">
                    ${txn.txnType}
                </span>
            </td>
            <td>${txn.quantity}</td>
            <td>${formatCurrency(txn.totalPrice)}</td>
            <td>${formatDateTime(txn.txnDate)}</td>
        </tr>
    `).join('');
}

// Update low stock alerts
function updateLowStockAlerts(lowStockItems) {
    const section = document.getElementById('low-stock-section');
    const list = document.getElementById('low-stock-list');

    if (!lowStockItems || lowStockItems.length === 0) {
        section.style.display = 'none';
        return;
    }

    section.style.display = 'block';
    list.innerHTML = lowStockItems.map(product => `
        <div class="alert alert-warning">
            <strong>${product.name}</strong> - Only ${product.quantity} units left
            <span class="alert-action">Restock needed</span>
        </div>
    `).join('');
}

// Send request to server using socketClient
async function sendRequest(data) {
    return new Promise((resolve, reject) => {
        socketClient.send(data)
            .then(response => {
                if (response.success) {
                    resolve(response);
                } else {
                    reject(new Error(response.message));
                }
            })
            .catch(error => {
                reject(error);
            });
    });
}

// Update connection status
function updateConnectionStatus(connected) {
    const statusEl = document.getElementById('connection-status');
    if (connected) {
        statusEl.textContent = '🟢 Connected';
        statusEl.style.color = '#10b981';
    } else {
        statusEl.textContent = '🔴 Disconnected';
        statusEl.style.color = '#ef4444';
    }
}

// Handle logout
function handleLogout() {
    if (confirm('Are you sure you want to logout?')) {
        clearInterval(statsInterval);
        sessionStorage.removeItem('currentUser');
        socketClient.disconnect();
        window.location.href = 'login.html';
    }
}

// Utility functions
function formatCurrency(amount) {
    return new Intl.NumberFormat('uz-UZ', {
        style: 'decimal',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
    }).format(amount) + ' UZS';
}

function formatDateTime(dateString) {
    const date = new Date(dateString);
    return date.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function showError(message) {
    // Simple error display - can be enhanced with a modal
    alert('Error: ' + message);
}

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    clearInterval(statsInterval);
});