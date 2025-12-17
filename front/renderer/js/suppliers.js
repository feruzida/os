// suppliers.js - Suppliers management logic

let allSuppliers = [];
let supplierStats = {};
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
    await loadSuppliers();

    // Setup event listeners
    setupEventListeners();
});

// Setup event listeners
function setupEventListeners() {
    // Search
    document.getElementById('search-input').addEventListener('input', filterSuppliers);

    // Add supplier button
    document.getElementById('add-supplier-btn').addEventListener('click', () => {
        openSupplierModal();
    });

    // Save supplier button
    document.getElementById('save-supplier-btn').addEventListener('click', saveSupplier);
}

// Load suppliers from server
async function loadSuppliers() {
    try {
        const response = await socketClient.send({ action: 'get_all_suppliers' });

        if (response.success) {
            allSuppliers = response.data || [];
            await loadSupplierStats();
            displaySuppliers(allSuppliers);
        } else {
            showError('Failed to load suppliers: ' + response.message);
        }
    } catch (error) {
        console.error('Error loading suppliers:', error);
        showError('Failed to load suppliers');
    }
}

// Load supplier statistics (product count)
async function loadSupplierStats() {
    try {
        const response = await socketClient.send({ action: 'get_all_products' });

        if (response.success) {
            const products = response.data || [];
            supplierStats = {};

            products.forEach(product => {
                if (product.supplierId) {
                    supplierStats[product.supplierId] = (supplierStats[product.supplierId] || 0) + 1;
                }
            });
        }
    } catch (error) {
        console.error('Error loading supplier stats:', error);
    }
}

// Display suppliers in table
function displaySuppliers(suppliers) {
    const tbody = document.getElementById('suppliers-table-body');

    if (!suppliers || suppliers.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="text-center">
                    <div class="empty-state">
                        <div class="empty-state-icon">🏪</div>
                        <h3>No suppliers found</h3>
                        <p>Start by adding your first supplier</p>
                    </div>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = suppliers.map(supplier => {
        const productCount = supplierStats[supplier.supplierId] || 0;

        return `
            <tr>
                <td>#${supplier.supplierId}</td>
                <td><strong>${supplier.name}</strong></td>
                <td>${supplier.contactInfo || '-'}</td>
                <td>${supplier.email || '-'}</td>
                <td>${supplier.address || '-'}</td>
                <td><span class="badge badge-info">${productCount} products</span></td>
                <td>${formatDateTime(supplier.createdAt)}</td>
                <td>
                    <div class="table-actions">
                        <button class="btn btn-sm btn-primary btn-icon" onclick="editSupplier(${supplier.supplierId})">
                            ✏️
                        </button>
                        <button class="btn btn-sm btn-danger btn-icon" onclick="deleteSupplier(${supplier.supplierId})">
                            🗑️
                        </button>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

// Filter suppliers
function filterSuppliers() {
    const searchTerm = document.getElementById('search-input').value.toLowerCase();

    if (!searchTerm) {
        displaySuppliers(allSuppliers);
        return;
    }

    const filtered = allSuppliers.filter(s =>
        s.name.toLowerCase().includes(searchTerm) ||
        s.contactInfo?.toLowerCase().includes(searchTerm) ||
        s.email?.toLowerCase().includes(searchTerm) ||
        s.address?.toLowerCase().includes(searchTerm)
    );

    displaySuppliers(filtered);
}

// Open supplier modal
function openSupplierModal(supplier = null) {
    const modal = document.getElementById('supplier-modal');
    const title = document.getElementById('modal-title');
    const form = document.getElementById('supplier-form');

    form.reset();

    if (supplier) {
        // Edit mode
        title.textContent = 'Edit Supplier';
        document.getElementById('supplier-id').value = supplier.supplierId;
        document.getElementById('supplier-name').value = supplier.name;
        document.getElementById('supplier-contact').value = supplier.contactInfo || '';
        document.getElementById('supplier-email').value = supplier.email || '';
        document.getElementById('supplier-address').value = supplier.address || '';
    } else {
        // Add mode
        title.textContent = 'Add New Supplier';
        document.getElementById('supplier-id').value = '';
    }

    modal.classList.add('active');
}

// Close supplier modal
function closeSupplierModal() {
    const modal = document.getElementById('supplier-modal');
    modal.classList.remove('active');
}

// Save supplier (add or update)
async function saveSupplier() {
    const supplierId = document.getElementById('supplier-id').value;
    const name = document.getElementById('supplier-name').value.trim();
    const contactInfo = document.getElementById('supplier-contact').value.trim();
    const email = document.getElementById('supplier-email').value.trim();
    const address = document.getElementById('supplier-address').value.trim();

    if (!name || !contactInfo) {
        alert('Please fill all required fields');
        return;
    }

    const saveBtn = document.getElementById('save-supplier-btn');
    saveBtn.disabled = true;
    saveBtn.textContent = 'Saving...';

    try {
        const supplierData = {
            name,
            contactInfo,
            email: email || null,
            address: address || null
        };

        let response;
        if (supplierId) {
            // Update existing supplier
            response = await socketClient.send({
                action: 'update_supplier',
                supplierId: parseInt(supplierId),
                ...supplierData
            });
        } else {
            // Add new supplier
            response = await socketClient.send({
                action: 'add_supplier',
                ...supplierData
            });
        }

        if (response.success) {
            closeSupplierModal();
            await loadSuppliers();
            alert(supplierId ? 'Supplier updated successfully!' : 'Supplier added successfully!');
        } else {
            alert('Failed to save supplier: ' + response.message);
        }
    } catch (error) {
        console.error('Error saving supplier:', error);
        alert('Failed to save supplier');
    } finally {
        saveBtn.disabled = false;
        saveBtn.textContent = 'Save Supplier';
    }
}

// Edit supplier
window.editSupplier = function(supplierId) {
    const supplier = allSuppliers.find(s => s.supplierId === supplierId);
    if (supplier) {
        openSupplierModal(supplier);
    }
};

// Delete supplier
window.deleteSupplier = async function(supplierId) {
    const supplier = allSuppliers.find(s => s.supplierId === supplierId);
    if (!supplier) return;

    const productCount = supplierStats[supplierId] || 0;
    if (productCount > 0) {
        alert(`Cannot delete supplier "${supplier.name}" because it has ${productCount} associated product(s). Please reassign or delete those products first.`);
        return;
    }

    if (!confirm(`Are you sure you want to delete "${supplier.name}"?`)) {
        return;
    }

    try {
        const response = await socketClient.send({
            action: 'delete_supplier',
            supplierId: supplierId
        });

        if (response.success) {
            await loadSuppliers();
            alert('Supplier deleted successfully!');
        } else {
            alert('Failed to delete supplier: ' + response.message);
        }
    } catch (error) {
        console.error('Error deleting supplier:', error);
        alert('Failed to delete supplier');
    }
};

// Utility functions
function formatDateTime(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
    });
}

function showError(message) {
    alert('Error: ' + message);
}

// Close modal on outside click
window.onclick = function(event) {
    const modal = document.getElementById('supplier-modal');
    if (event.target === modal) {
        closeSupplierModal();
    }
};

// Make functions globally available
window.openSupplierModal = openSupplierModal;
window.closeSupplierModal = closeSupplierModal;