// products.js - Products management logic

let allProducts = [];
let allSuppliers = [];
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
    await loadSuppliers();

    // Setup event listeners
    setupEventListeners();
});

// Setup event listeners
function setupEventListeners() {
    // Search
    document.getElementById('search-input').addEventListener('input', filterProducts);

    // Filters
    document.getElementById('category-filter').addEventListener('change', filterProducts);
    document.getElementById('stock-filter').addEventListener('change', filterProducts);

    // Add product button
    document.getElementById('add-product-btn').addEventListener('click', () => {
        openProductModal();
    });

    // Save product button
    document.getElementById('save-product-btn').addEventListener('click', saveProduct);
}

// Load products from server
async function loadProducts() {
    try {
        const response = await socketClient.send({ action: 'get_all_products' });

        if (response.success) {
            allProducts = response.data || [];
            displayProducts(allProducts);
        } else {
            showError('Failed to load products: ' + response.message);
        }
    } catch (error) {
        console.error('Error loading products:', error);
        showError('Failed to load products');
    }
}

// Load suppliers for dropdown
async function loadSuppliers() {
    try {
        const response = await socketClient.send({ action: 'get_all_suppliers' });

        if (response.success) {
            allSuppliers = response.data || [];
            populateSupplierDropdown();
        }
    } catch (error) {
        console.error('Error loading suppliers:', error);
    }
}

// Populate supplier dropdown
function populateSupplierDropdown() {
    const select = document.getElementById('product-supplier');
    select.innerHTML = '<option value="">No supplier</option>';

    allSuppliers.forEach(supplier => {
        const option = document.createElement('option');
        option.value = supplier.supplierId;
        option.textContent = supplier.name;
        select.appendChild(option);
    });
}

// Display products in table
function displayProducts(products) {
    const tbody = document.getElementById('products-table-body');

    if (!products || products.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="text-center">
                    <div class="empty-state">
                        <div class="empty-state-icon">📦</div>
                        <h3>No products found</h3>
                        <p>Start by adding your first product</p>
                    </div>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = products.map(product => {
        const stockBadge = getStockBadge(product.quantity);
        const supplierName = getSupplierName(product.supplierId);

        return `
            <tr>
                <td>#${product.productId}</td>
                <td><strong>${product.name}</strong></td>
                <td>${product.category || '-'}</td>
                <td>${formatCurrency(product.unitPrice)}</td>
                <td>${stockBadge}</td>
                <td>${supplierName}</td>
                <td>${formatDateTime(product.lastUpdated)}</td>
                <td>
                    <div class="table-actions">
                        <button class="btn btn-sm btn-primary btn-icon" onclick="editProduct(${product.productId})">
                            ✏️
                        </button>
                        <button class="btn btn-sm btn-danger btn-icon" onclick="deleteProduct(${product.productId})">
                            🗑️
                        </button>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

// Get stock badge HTML
function getStockBadge(quantity) {
    if (quantity === 0) {
        return `<span class="badge badge-danger">Out of Stock (0)</span>`;
    } else if (quantity < 50) {
        return `<span class="badge badge-warning">Low Stock (${quantity})</span>`;
    } else {
        return `<span class="badge badge-success">In Stock (${quantity})</span>`;
    }
}

// Get supplier name by ID
function getSupplierName(supplierId) {
    if (!supplierId) return '-';
    const supplier = allSuppliers.find(s => s.supplierId === supplierId);
    return supplier ? supplier.name : `Supplier #${supplierId}`;
}

// Filter products
function filterProducts() {
    const searchTerm = document.getElementById('search-input').value.toLowerCase();
    const category = document.getElementById('category-filter').value;
    const stockFilter = document.getElementById('stock-filter').value;

    let filtered = allProducts;

    // Search filter
    if (searchTerm) {
        filtered = filtered.filter(p =>
            p.name.toLowerCase().includes(searchTerm) ||
            p.category?.toLowerCase().includes(searchTerm)
        );
    }

    // Category filter
    if (category) {
        filtered = filtered.filter(p => p.category === category);
    }

    // Stock filter
    if (stockFilter === 'in-stock') {
        filtered = filtered.filter(p => p.quantity >= 50);
    } else if (stockFilter === 'low-stock') {
        filtered = filtered.filter(p => p.quantity > 0 && p.quantity < 50);
    } else if (stockFilter === 'out-of-stock') {
        filtered = filtered.filter(p => p.quantity === 0);
    }

    displayProducts(filtered);
}

// Open product modal
function openProductModal(product = null) {
    const modal = document.getElementById('product-modal');
    const title = document.getElementById('modal-title');
    const form = document.getElementById('product-form');

    form.reset();

    if (product) {
        // Edit mode
        title.textContent = 'Edit Product';
        document.getElementById('product-id').value = product.productId;
        document.getElementById('product-name').value = product.name;
        document.getElementById('product-category').value = product.category || '';
        document.getElementById('product-price').value = product.unitPrice;
        document.getElementById('product-quantity').value = product.quantity;
        document.getElementById('product-supplier').value = product.supplierId || '';
    } else {
        // Add mode
        title.textContent = 'Add New Product';
        document.getElementById('product-id').value = '';
    }

    modal.classList.add('active');
}

// Close product modal
function closeProductModal() {
    const modal = document.getElementById('product-modal');
    modal.classList.remove('active');
}

// Save product (add or update)
async function saveProduct() {
    const productId = document.getElementById('product-id').value;
    const name = document.getElementById('product-name').value.trim();
    const category = document.getElementById('product-category').value;
    const price = parseFloat(document.getElementById('product-price').value);
    const quantity = parseInt(document.getElementById('product-quantity').value);
    const supplierId = document.getElementById('product-supplier').value;

    if (!name || !category || isNaN(price) || isNaN(quantity)) {
        alert('Please fill all required fields correctly');
        return;
    }

    const saveBtn = document.getElementById('save-product-btn');
    saveBtn.disabled = true;
    saveBtn.textContent = 'Saving...';

    try {
        const productData = {
            name,
            category,
            unitPrice: price,
            quantity,
            supplierId: supplierId ? parseInt(supplierId) : null
        };

        let response;
        if (productId) {
            // Update existing product
            response = await socketClient.send({
                action: 'update_product',
                productId: parseInt(productId),
                ...productData
            });
        } else {
            // Add new product
            response = await socketClient.send({
                action: 'add_product',
                ...productData
            });
        }

        if (response.success) {
            closeProductModal();
            await loadProducts();
            alert(productId ? 'Product updated successfully!' : 'Product added successfully!');
        } else {
            alert('Failed to save product: ' + response.message);
        }
    } catch (error) {
        console.error('Error saving product:', error);
        alert('Failed to save product');
    } finally {
        saveBtn.disabled = false;
        saveBtn.textContent = 'Save Product';
    }
}

// Edit product
window.editProduct = function(productId) {
    const product = allProducts.find(p => p.productId === productId);
    if (product) {
        openProductModal(product);
    }
};

// Delete product
window.deleteProduct = async function(productId) {
    const product = allProducts.find(p => p.productId === productId);
    if (!product) return;

    if (!confirm(`Are you sure you want to delete "${product.name}"?`)) {
        return;
    }

    try {
        const response = await socketClient.send({
            action: 'delete_product',
            productId: productId
        });

        if (response.success) {
            await loadProducts();
            alert('Product deleted successfully!');
        } else {
            alert('Failed to delete product: ' + response.message);
        }
    } catch (error) {
        console.error('Error deleting product:', error);
        alert('Failed to delete product');
    }
};

// Utility functions
function formatCurrency(amount) {
    return new Intl.NumberFormat('uz-UZ', {
        style: 'decimal',
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
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
    const modal = document.getElementById('product-modal');
    if (event.target === modal) {
        closeProductModal();
    }
};

// Make functions globally available
window.openProductModal = openProductModal;
window.closeProductModal = closeProductModal;