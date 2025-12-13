// reports.js - Reports and analytics logic

let allProducts = [];
let allSuppliers = [];
let allTransactions = [];
let currentUser = null;
let currentReport = 'sales';

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
    await loadAllData();

    // Setup event listeners
    setupEventListeners();

    // Show initial report
    showReport('sales');
});

// Setup event listeners
function setupEventListeners() {
    // Report selection
    document.querySelectorAll('.report-card').forEach(card => {
        card.addEventListener('click', (e) => {
            const reportType = e.currentTarget.dataset.report;
            showReport(reportType);
        });
    });

    // Sales period filter
    document.getElementById('sales-period').addEventListener('change', refreshSalesReport);
}

// Load all data
async function loadAllData() {
    try {
        const [productsRes, suppliersRes, transactionsRes] = await Promise.all([
            socketClient.send({ action: 'get_all_products' }),
            socketClient.send({ action: 'get_all_suppliers' }),
            socketClient.send({ action: 'get_today_transactions' })
        ]);

        allProducts = productsRes.success ? (productsRes.data || []) : [];
        allSuppliers = suppliersRes.success ? (suppliersRes.data || []) : [];
        allTransactions = transactionsRes.success ? (transactionsRes.data || []) : [];

    } catch (error) {
        console.error('Error loading data:', error);
    }
}

// Show selected report
function showReport(reportType) {
    currentReport = reportType;

    // Update active state
    document.querySelectorAll('.report-card').forEach(card => {
        card.classList.remove('active');
    });
    document.querySelector(`[data-report="${reportType}"]`).classList.add('active');

    // Hide all reports
    document.querySelectorAll('.report-section').forEach(section => {
        section.style.display = 'none';
    });

    // Show selected report
    const reportSection = document.getElementById(`${reportType}-report`);
    if (reportSection) {
        reportSection.style.display = 'block';

        // Load report data
        if (reportType === 'sales') {
            refreshSalesReport();
        } else if (reportType === 'inventory') {
            refreshInventoryReport();
        } else if (reportType === 'suppliers') {
            refreshSupplierReport();
        }
    }
}

// Refresh Sales Report
async function refreshSalesReport() {
    const period = document.getElementById('sales-period').value;

    // Get transactions for selected period
    let transactions = allTransactions;

    // Filter sales only
    const sales = transactions.filter(t => t.txnType === 'Sale');

    // Calculate stats
    const totalTransactions = sales.length;
    const totalRevenue = sales.reduce((sum, t) => sum + parseFloat(t.totalPrice), 0);
    const averageSale = totalTransactions > 0 ? totalRevenue / totalTransactions : 0;

    // Update stats
    document.getElementById('total-transactions').textContent = totalTransactions;
    document.getElementById('total-revenue').textContent = formatCurrency(totalRevenue);
    document.getElementById('average-sale').textContent = formatCurrency(averageSale);

    // Calculate top products
    const productSales = {};
    sales.forEach(sale => {
        if (!productSales[sale.productId]) {
            productSales[sale.productId] = {
                productId: sale.productId,
                quantity: 0,
                revenue: 0
            };
        }
        productSales[sale.productId].quantity += sale.quantity;
        productSales[sale.productId].revenue += parseFloat(sale.totalPrice);
    });

    // Sort by revenue
    const topProducts = Object.values(productSales)
        .sort((a, b) => b.revenue - a.revenue)
        .slice(0, 10);

    // Display top products
    const tbody = document.getElementById('top-products-body');
    if (topProducts.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center">No sales data available</td></tr>';
        return;
    }

    tbody.innerHTML = topProducts.map((item, index) => {
        const product = allProducts.find(p => p.productId === item.productId);
        const productName = product ? product.name : `Product #${item.productId}`;
        const percentage = totalRevenue > 0 ? (item.revenue / totalRevenue * 100).toFixed(1) : 0;

        return `
            <tr>
                <td>${index + 1}</td>
                <td><strong>${productName}</strong></td>
                <td>${item.quantity} units</td>
                <td>${formatCurrency(item.revenue)}</td>
                <td>
                    <div style="display: flex; align-items: center; gap: 10px;">
                        <div style="flex: 1; background: #e5e7eb; border-radius: 4px; height: 8px;">
                            <div style="width: ${percentage}%; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); height: 100%; border-radius: 4px;"></div>
                        </div>
                        <span>${percentage}%</span>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

// Refresh Inventory Report
async function refreshInventoryReport() {
    // Calculate stats
    const totalProducts = allProducts.length;
    const totalStockValue = allProducts.reduce((sum, p) => sum + (p.unitPrice * p.quantity), 0);
    const lowStockItems = allProducts.filter(p => p.quantity < 50);

    // Update stats
    document.getElementById('total-products-count').textContent = totalProducts;
    document.getElementById('total-stock-value').textContent = formatCurrency(totalStockValue);
    document.getElementById('low-stock-count').textContent = lowStockItems.length;

    // Display low stock items
    const lowStockBody = document.getElementById('low-stock-body');
    if (lowStockItems.length === 0) {
        lowStockBody.innerHTML = '<tr><td colspan="4" class="text-center">✅ All products are well stocked!</td></tr>';
    } else {
        lowStockBody.innerHTML = lowStockItems.map(product => {
            let statusBadge;
            if (product.quantity === 0) {
                statusBadge = '<span class="badge badge-danger">Out of Stock</span>';
            } else if (product.quantity < 20) {
                statusBadge = '<span class="badge badge-danger">Critical</span>';
            } else {
                statusBadge = '<span class="badge badge-warning">Low Stock</span>';
            }

            return `
                <tr>
                    <td><strong>${product.name}</strong></td>
                    <td>${product.category || '-'}</td>
                    <td>${product.quantity} units</td>
                    <td>${statusBadge}</td>
                </tr>
            `;
        }).join('');
    }

    // Calculate stock by category
    const categoryStats = {};
    allProducts.forEach(product => {
        const category = product.category || 'Other';
        if (!categoryStats[category]) {
            categoryStats[category] = {
                products: 0,
                totalUnits: 0,
                value: 0
            };
        }
        categoryStats[category].products++;
        categoryStats[category].totalUnits += product.quantity;
        categoryStats[category].value += product.unitPrice * product.quantity;
    });

    // Display category stats
    const categoryBody = document.getElementById('category-stock-body');
    const categories = Object.entries(categoryStats).sort((a, b) => b[1].value - a[1].value);

    if (categories.length === 0) {
        categoryBody.innerHTML = '<tr><td colspan="4" class="text-center">No products available</td></tr>';
    } else {
        categoryBody.innerHTML = categories.map(([category, stats]) => `
            <tr>
                <td><strong>${category}</strong></td>
                <td>${stats.products} products</td>
                <td>${stats.totalUnits} units</td>
                <td>${formatCurrency(stats.value)}</td>
            </tr>
        `).join('');
    }
}

// Refresh Supplier Report
async function refreshSupplierReport() {
    // Calculate stats
    const totalSuppliers = allSuppliers.length;
    const activeSuppliers = allSuppliers.filter(s => {
        return allProducts.some(p => p.supplierId === s.supplierId);
    }).length;

    // Update stats
    document.getElementById('total-suppliers-count').textContent = totalSuppliers;
    document.getElementById('active-suppliers').textContent = activeSuppliers;

    // Calculate supplier performance
    const supplierStats = {};
    allSuppliers.forEach(supplier => {
        supplierStats[supplier.supplierId] = {
            supplier: supplier,
            productCount: 0,
            totalUnits: 0
        };
    });

    allProducts.forEach(product => {
        if (product.supplierId && supplierStats[product.supplierId]) {
            supplierStats[product.supplierId].productCount++;
            supplierStats[product.supplierId].totalUnits += product.quantity;
        }
    });

    // Sort by product count
    const supplierPerformance = Object.values(supplierStats)
        .sort((a, b) => b.productCount - a.productCount);

    // Display supplier performance
    const tbody = document.getElementById('supplier-performance-body');
    if (supplierPerformance.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="text-center">No suppliers available</td></tr>';
    } else {
        tbody.innerHTML = supplierPerformance.map(item => `
            <tr>
                <td><strong>${item.supplier.name}</strong></td>
                <td>${item.productCount} products</td>
                <td>${item.totalUnits} units</td>
                <td>${item.supplier.contactInfo || '-'}</td>
            </tr>
        `).join('');
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

// Make refresh functions globally available
window.refreshSalesReport = refreshSalesReport;
window.refreshInventoryReport = refreshInventoryReport;
window.refreshSupplierReport = refreshSupplierReport;