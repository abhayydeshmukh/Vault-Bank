// ==========================================
// VAULT BANK - STATE & FRONTEND UTILITIES
// ==========================================

const API_BASE = window.location.origin;

// Application State
let state = {
    accessToken: localStorage.getItem('vault_access_token'),
    refreshToken: localStorage.getItem('vault_refresh_token'),
    user: JSON.parse(localStorage.getItem('vault_user_profile')),
    accounts: [],
    theme: localStorage.getItem('vault_ui_theme') || 'dark',
    sidebarCollapsed: false,
    
    // Ledger filtering and pagination state
    ledgerAccount: '',
    ledgerFilter: 'ALL',
    ledgerSearch: '',
    ledgerPage: 1,
    ledgerPageSize: 5,
    ledgerTransactions: [],
    
    // Chart reference
    chart: null
};

// ==========================================
// CENTRALIZED JWT API INTERCEPTOR
// ==========================================

async function apiCall(endpoint, method = 'GET', body = null) {
    const headers = {
        'Content-Type': 'application/json'
    };

    if (state.accessToken) {
        headers['Authorization'] = `Bearer ${state.accessToken}`;
    }

    const config = {
        method,
        headers
    };

    if (body) {
        config.body = JSON.stringify(body);
    }

    try {
        let response = await fetch(`${API_BASE}${endpoint}`, config);

        // Silent token rotation on HTTP 401
        if (response.status === 401 && state.refreshToken && !endpoint.includes('/api/auth/login') && !endpoint.includes('/api/auth/refresh')) {
            console.warn("JWT access token expired. Rotating session...");
            const rotated = await attemptTokenRotation();
            
            if (rotated) {
                headers['Authorization'] = `Bearer ${state.accessToken}`;
                response = await fetch(`${API_BASE}${endpoint}`, config);
            } else {
                logoutUser();
                throw new Error("Your session has expired. Please log in again.");
            }
        }

        const data = await response.json();
        
        if (!response.ok) {
            throw new Error(data.message || data.errors?.[0] || 'An error occurred during request processing.');
        }

        return data;
    } catch (err) {
        console.error(`API Error on ${endpoint}:`, err);
        throw err;
    }
}

async function attemptTokenRotation() {
    try {
        const response = await fetch(`${API_BASE}/api/auth/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken: state.refreshToken })
        });

        if (!response.ok) return false;

        const data = await response.json();
        if (data.success && data.data) {
            state.accessToken = data.data.accessToken;
            state.refreshToken = data.data.refreshToken;
            localStorage.setItem('vault_access_token', state.accessToken);
            localStorage.setItem('vault_refresh_token', state.refreshToken);
            return true;
        }
        return false;
    } catch (err) {
        return false;
    }
}

// ==========================================
// TOAST MESSAGING ENGINE (LUCIDE INTEGRATED)
// ==========================================

function showToast(title, message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    
    let iconName = 'info';
    if (type === 'success') iconName = 'check-circle';
    else if (type === 'error') iconName = 'alert-triangle';
    else if (type === 'warning') iconName = 'alert-circle';
    
    toast.innerHTML = `
        <i data-lucide="${iconName}" class="toast-icon"></i>
        <div class="toast-body">
            <div class="toast-title">${title}</div>
            <div class="toast-message">${message}</div>
        </div>
    `;
    
    container.appendChild(toast);
    
    // Initialize Lucide icons on dynamic toast
    if (window.lucide) {
        window.lucide.createIcons();
    }
    
    setTimeout(() => {
        toast.style.animation = 'slideOut 0.3s cubic-bezier(0.4, 0, 0.2, 1) forwards';
        setTimeout(() => toast.remove(), 350);
    }, 4500);
}

// ==========================================
// CORE LAYOUT INTERACTIVE TRIGGERS
// ==========================================

function initSidebarToggle() {
    const sidebar = document.getElementById('app-sidebar');
    const toggleBtn = document.getElementById('sidebar-toggle');
    const icon = document.getElementById('toggle-icon');
    
    toggleBtn.addEventListener('click', () => {
        state.sidebarCollapsed = !state.sidebarCollapsed;
        if (state.sidebarCollapsed) {
            sidebar.classList.add('collapsed');
            icon.setAttribute('data-lucide', 'chevron-right');
        } else {
            sidebar.classList.remove('collapsed');
            icon.setAttribute('data-lucide', 'chevron-left');
        }
        window.lucide.createIcons();
    });
}

function initThemeToggle() {
    const themeBtn = document.getElementById('theme-toggle');
    const sunIcon = document.getElementById('theme-icon-sun');
    const moonIcon = document.getElementById('theme-icon-moon');
    
    // Apply default/saved theme
    document.documentElement.setAttribute('data-theme', state.theme);
    updateThemeIcons(state.theme, sunIcon, moonIcon);

    themeBtn.addEventListener('click', () => {
        state.theme = state.theme === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', state.theme);
        localStorage.setItem('vault_ui_theme', state.theme);
        updateThemeIcons(state.theme, sunIcon, moonIcon);
        
        // Re-render chart to adjust grid colors
        if (state.chart) {
            renderPerformanceChart();
        }
    });
}

function updateThemeIcons(theme, sun, moon) {
    if (theme === 'dark') {
        sun.classList.add('hidden');
        moon.classList.remove('hidden');
    } else {
        moon.classList.add('hidden');
        sun.classList.remove('hidden');
    }
}

// ==========================================
// SESSION CONTROLS
// ==========================================

function toggleAuthForm(formType) {
    document.querySelectorAll('.auth-form').forEach(f => f.classList.remove('active'));
    if (formType === 'login') {
        document.getElementById('login-form').classList.add('active');
    } else {
        document.getElementById('register-form').classList.add('active');
    }
}

function handleLoginSuccess(authData) {
    state.accessToken = authData.accessToken;
    state.refreshToken = authData.refreshToken;
    
    // Extract name info from email fallback
    state.user = {
        email: authData.email,
        role: authData.role,
        firstName: authData.email.split('@')[0],
        lastName: ''
    };

    localStorage.setItem('vault_access_token', state.accessToken);
    localStorage.setItem('vault_refresh_token', state.refreshToken);
    localStorage.setItem('vault_user_profile', JSON.stringify(state.user));

    showToast("Auth Verified", `Authenticated as ${state.user.firstName}`, "success");
    setupAppInterface();
}

function logoutUser() {
    if (state.accessToken) {
        apiCall('/api/auth/logout', 'POST')
            .catch(() => console.log("Revoked token locally."));
    }
    
    state.accessToken = null;
    state.refreshToken = null;
    state.user = null;
    state.accounts = [];
    localStorage.removeItem('vault_access_token');
    localStorage.removeItem('vault_refresh_token');
    localStorage.removeItem('vault_user_profile');

    document.getElementById('dashboard-layout').classList.add('hidden');
    document.getElementById('auth-view').classList.remove('hidden');
    showToast("Signed Out", "Portal session terminated safely.", "info");
}

// ==========================================
// DYNAMIC VIEW ROUTER
// ==========================================

function switchView(viewId) {
    document.querySelectorAll('.viewport-section').forEach(s => s.classList.add('hidden'));
    document.querySelectorAll('.menu-item').forEach(i => i.classList.remove('active'));
    
    const targetSection = document.getElementById(viewId);
    if (targetSection) {
        targetSection.classList.remove('hidden');
        state.currentView = viewId;
        
        // Mark link active
        const navLink = document.querySelector(`.menu-item[data-target="${viewId}"]`);
        if (navLink) navLink.classList.add('active');

        // Update Title
        const titleMap = {
            'dashboard-view': 'Dashboard',
            'accounts-view': 'My Accounts',
            'transfer-view': 'Transfer Money',
            'history-view': 'Transactions',
            'analytics-view': 'Analytics Insights',
            'profile-view': 'Settings',
            'admin-view': 'Admin Console'
        };
        const viewTitle = document.getElementById('view-title');
        if (viewTitle) {
            viewTitle.textContent = titleMap[viewId] || 'Vault Bank';
        }
        
        // Refresh specific view data
        if (viewId === 'dashboard-view') {
            loadUserDashboard();
        } else if (viewId === 'accounts-view') {
            loadAccountsViewOnly();
        } else if (viewId === 'transfer-view') {
            populateTransferDropdowns();
        } else if (viewId === 'history-view') {
            populateLedgerDropdowns();
        } else if (viewId === 'analytics-view') {
            renderPerformanceChart();
        } else if (viewId === 'profile-view') {
            populateSettingsFields();
        } else if (viewId === 'admin-view') {
            loadAdminConsole();
        }
    }
}

// ==========================================
// DATA RETRIEVAL & RENDERING ENGINE
// ==========================================

async function loadUserDashboard() {
    renderDashboardSkeletons();
    
    try {
        const response = await apiCall('/api/accounts/my', 'GET');
        if (response.success) {
            state.accounts = response.data;
            updateMetrics(state.accounts);
            renderDashboardAccounts(state.accounts);
        }
    } catch (err) {
        showToast("Sync Error", err.message, "error");
    }
}

function renderDashboardSkeletons() {
    const container = document.getElementById('dashboard-accounts-container');
    container.innerHTML = `
        <div class="card skeleton-card">
            <div class="skeleton shimmer-line header"></div>
            <div class="skeleton shimmer-line number"></div>
            <div class="skeleton shimmer-line balance"></div>
        </div>
    `;
}

function updateMetrics(accounts) {
    const total = accounts.reduce((sum, a) => sum + a.balance, 0);
    const activeLiquidity = accounts
        .filter(a => a.status === 'ACTIVE')
        .reduce((sum, a) => sum + a.balance, 0);
        
    document.getElementById('stat-total-balance').textContent = `$${total.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    document.getElementById('stat-available-balance').textContent = `$${activeLiquidity.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    document.getElementById('stat-active-accounts').textContent = accounts.filter(a => a.status === 'ACTIVE').length;
    
    // Simple mock or compute based on loaded records
    document.getElementById('stat-monthly-txs').textContent = accounts.length * 2 + 1;
}

function renderDashboardAccounts(accounts) {
    const container = document.getElementById('dashboard-accounts-container');
    container.innerHTML = '';
    
    if (accounts.length === 0) {
        container.innerHTML = `
            <div class="card empty-state" style="padding: 30px 20px;">
                <i data-lucide="info" class="empty-state-icon"></i>
                <h3>No active accounts</h3>
                <p>Click "Open Account" to provision a ledger balance.</p>
                <button class="btn btn-primary btn-sm" onclick="openOpenAccountModal()">Open Account</button>
            </div>
        `;
        window.lucide.createIcons();
        return;
    }

    accounts.slice(0, 3).forEach(acc => {
        const div = document.createElement('div');
        const isSavings = acc.accountType === 'SAVINGS';
        div.className = `virtual-card ${isSavings ? 'savings' : 'current'}`;
        
        // Format card display number
        const formattedNum = `•••• •••• •••• ${acc.accountNumber.slice(-4)}`;

        div.innerHTML = `
            <div class="card-top">
                <div class="card-chip"></div>
                <span class="brand-title">VAULT ${acc.accountType}</span>
            </div>
            <div class="card-number-row">
                <span>${formattedNum}</span>
                <button class="copy-card-btn" onclick="copyToClipboard('${acc.accountNumber}')" title="Copy Number">
                    <i data-lucide="copy" style="width:16px;height:16px;"></i>
                </button>
            </div>
            <div class="card-bottom">
                <div>
                    <div class="card-balance-lbl">Available Reserve</div>
                    <div class="card-balance-val">$${acc.balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
                </div>
                <span class="acc-status-badge ${acc.status.toLowerCase()}">${acc.status}</span>
            </div>
        `;
        container.appendChild(div);
    });
    
    window.lucide.createIcons();
}

async function loadAccountsViewOnly() {
    const container = document.getElementById('full-accounts-container');
    container.innerHTML = '<div class="table-empty" style="grid-column: 1 / -1;"><i class="fa-solid fa-spinner fa-spin"></i> Loading assets...</div>';
    
    try {
        const response = await apiCall('/api/accounts/my', 'GET');
        container.innerHTML = '';
        if (response.success && response.data.length > 0) {
            state.accounts = response.data;
            
            response.data.forEach(acc => {
                const card = document.createElement('div');
                const isSavings = acc.accountType === 'SAVINGS';
                card.className = 'card account-card-fintech';
                
                // Format card display number
                const formattedNum = `•••• •••• •••• ${acc.accountNumber.slice(-4)}`;
                
                card.innerHTML = `
                    <div class="virtual-card ${isSavings ? 'savings' : 'current'}">
                        <div class="card-top">
                            <div class="card-chip"></div>
                            <span class="brand-title">VAULT ${acc.accountType}</span>
                        </div>
                        <div class="card-number-row">
                            <span>${formattedNum}</span>
                            <button class="copy-card-btn" onclick="copyToClipboard('${acc.accountNumber}')" title="Copy Number">
                                <i data-lucide="copy" style="width:16px;height:16px;"></i>
                            </button>
                        </div>
                        <div class="card-bottom">
                            <div>
                                <div class="card-balance-lbl">Available Reserve</div>
                                <div class="card-balance-val">$${acc.balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
                            </div>
                            <span class="acc-status-badge ${acc.status.toLowerCase()}">${acc.status}</span>
                        </div>
                    </div>
                    <div class="card-actions-row">
                        ${acc.status === 'ACTIVE' ? `
                            <button class="btn btn-primary" onclick="openDepositModal('${acc.accountNumber}')">
                                <i data-lucide="arrow-down-left"></i> Deposit
                            </button>
                            <button class="btn btn-secondary" onclick="openWithdrawModal('${acc.accountNumber}')">
                                <i data-lucide="arrow-up-right"></i> Withdraw
                            </button>
                        ` : `<p class="text-secondary text-center" style="font-size:12px;width:100%;"><i data-lucide="lock" style="width:12px;height:12px;"></i> Account locked by system.</p>`}
                    </div>
                `;
                container.appendChild(card);
            });
            window.lucide.createIcons();
        } else {
            container.innerHTML = `
                <div class="card empty-state" style="grid-column: 1 / -1;">
                    <i data-lucide="wallet" class="empty-state-icon"></i>
                    <h3>No bank accounts</h3>
                    <p>Open a new bank account to store cash and execute transactions.</p>
                    <button class="btn btn-primary" onclick="openOpenAccountModal()">Open Account</button>
                </div>
            `;
            window.lucide.createIcons();
        }
    } catch (err) {
        container.innerHTML = `<div class="text-danger text-center" style="grid-column:1/-1;">Failed to load accounts: ${err.message}</div>`;
    }
}

// Copy to Clipboard
function copyToClipboard(text) {
    navigator.clipboard.writeText(text).then(() => {
        showToast("Copied", "Account number copied to clipboard.", "success");
    }).catch(err => {
        console.error("Failed to copy:", err);
    });
}

// Populate forms selectors
function populateTransferDropdowns() {
    const select = document.getElementById('transfer-source-acc');
    select.innerHTML = '<option value="">Select source account...</option>';
    state.accounts.forEach(acc => {
        if (acc.status === 'ACTIVE') {
            const opt = document.createElement('option');
            opt.value = acc.accountNumber;
            opt.textContent = `${acc.accountType} (${acc.accountNumber.slice(-4)}) — $${acc.balance.toFixed(2)}`;
            select.appendChild(opt);
        }
    });
}

function populateLedgerDropdowns() {
    const select = document.getElementById('ledger-account-select');
    select.innerHTML = '<option value="">Select account...</option>';
    state.accounts.forEach(acc => {
        const opt = document.createElement('option');
        opt.value = acc.accountNumber;
        opt.textContent = `${acc.accountType} (${acc.accountNumber.slice(-4)})`;
        select.appendChild(opt);
    });
}

// ==========================================
// TRANSACTIONS TABLE LEDGER & PAGINATION
// ==========================================

async function fetchLedgerHistory() {
    const accNum = document.getElementById('ledger-account-select').value;
    state.ledgerAccount = accNum;
    
    if (!accNum) {
        document.getElementById('transactions-table-body').innerHTML = `
            <tr>
                <td colspan="6" class="table-empty">
                    <div class="empty-state">
                        <i data-lucide="receipt" class="empty-state-icon"></i>
                        <h3>No account selected</h3>
                        <p>Select a bank account above to query transaction history details.</p>
                    </div>
                </td>
            </tr>
        `;
        document.getElementById('table-pagination').classList.add('hidden');
        window.lucide.createIcons();
        return;
    }

    const tbody = document.getElementById('transactions-table-body');
    tbody.innerHTML = '<tr><td colspan="6" class="table-empty"><i class="fa-solid fa-circle-notch fa-spin"></i> Querying transaction ledger...</td></tr>';

    try {
        const response = await apiCall(`/api/transactions/history/${accNum}`, 'GET');
        if (response.success) {
            state.ledgerTransactions = response.data;
            state.ledgerPage = 1; // reset page
            renderFilteredTransactions();
        }
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" class="table-empty text-danger">Query Failed: ${err.message}</td></tr>`;
    }
}

function renderFilteredTransactions() {
    const tbody = document.getElementById('transactions-table-body');
    const search = state.ledgerSearch.toLowerCase();
    
    // Apply filters
    let filtered = state.ledgerTransactions.filter(tx => {
        // Type filter
        if (state.ledgerFilter !== 'ALL' && tx.transactionType !== state.ledgerFilter) {
            return false;
        }
        // Search filter
        if (search) {
            const refMatch = tx.transactionReference.toLowerCase().includes(search);
            const descMatch = tx.description && tx.description.toLowerCase().includes(search);
            return refMatch || descMatch;
        }
        return true;
    });

    // Pagination
    const totalItems = filtered.length;
    const totalPages = Math.ceil(totalItems / state.ledgerPageSize) || 1;
    
    if (state.ledgerPage > totalPages) state.ledgerPage = totalPages;
    if (state.ledgerPage < 1) state.ledgerPage = 1;

    const startIdx = (state.ledgerPage - 1) * state.ledgerPageSize;
    const endIdx = startIdx + state.ledgerPageSize;
    const paginated = filtered.slice(startIdx, endIdx);

    tbody.innerHTML = '';
    
    if (paginated.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="table-empty">
                    <div class="empty-state" style="padding: 20px;">
                        <i data-lucide="search-code" class="empty-state-icon"></i>
                        <h3>No records match query</h3>
                        <p>Try clearing filters or search queries.</p>
                    </div>
                </td>
            </tr>
        `;
        document.getElementById('table-pagination').classList.add('hidden');
        window.lucide.createIcons();
        return;
    }

    paginated.forEach(tx => {
        const tr = document.createElement('tr');
        
        let typeIcon = 'arrow-down-left';
        let amountSign = '+';
        let amountClass = 'plus';
        let badgeClass = tx.transactionType.toLowerCase();

        if (tx.transactionType === 'WITHDRAWAL' || (tx.transactionType === 'TRANSFER' && tx.sourceAccountNumber === state.ledgerAccount)) {
            typeIcon = 'arrow-up-right';
            amountSign = '-';
            amountClass = 'minus';
        } else if (tx.transactionType === 'TRANSFER') {
            typeIcon = 'arrow-right-left';
        }

        const date = new Date(tx.createdAt).toLocaleDateString(undefined, {
            month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
        });

        let parties = '-';
        if (tx.transactionType === 'TRANSFER') {
            parties = `To: <strong>${tx.destinationAccountNumber}</strong>`;
            if (tx.destinationAccountNumber === state.ledgerAccount) {
                parties = `From: <strong>${tx.sourceAccountNumber}</strong>`;
            }
        }

        tr.innerHTML = `
            <td><span class="txn-ref">${tx.transactionReference}</span></td>
            <td>
                <span class="category-indicator ${badgeClass}">
                    <i data-lucide="${typeIcon}" style="width:16px;height:16px;"></i>
                </span>
            </td>
            <td><strong>${tx.transactionType}</strong><div style="font-size:12px;color:var(--text-secondary);margin-top:2px;">${tx.description || 'N/A'}</div></td>
            <td>${parties}</td>
            <td><span class="amount-indicator ${amountClass}">${amountSign}$${tx.amount.toFixed(2)}</span></td>
            <td style="font-size:13px;color:var(--text-secondary);">${date}</td>
        `;
        tbody.appendChild(tr);
    });

    // Pagination display
    const pagination = document.getElementById('table-pagination');
    pagination.classList.remove('hidden');
    
    document.getElementById('page-indicator-label').textContent = `Page ${state.ledgerPage} of ${totalPages}`;
    
    document.getElementById('btn-page-prev').disabled = state.ledgerPage === 1;
    document.getElementById('btn-page-next').disabled = state.ledgerPage === totalPages;

    window.lucide.createIcons();
}

// ==========================================
// METRIC ANALYTICS RENDER (CHART.JS)
// ==========================================

function renderPerformanceChart() {
    const ctx = document.getElementById('assetPerformanceChart');
    if (!ctx) return;
    
    // Destroy existing chart to rebuild
    if (state.chart) {
        state.chart.destroy();
    }

    const theme = document.documentElement.getAttribute('data-theme') || 'dark';
    const isDark = theme === 'dark';
    
    // Generate mock performance line based on accounts
    const baseValue = state.accounts.reduce((sum, a) => sum + a.balance, 0) || 5000;
    const dataPoints = [
        baseValue * 0.85, 
        baseValue * 0.90, 
        baseValue * 0.88, 
        baseValue * 0.94, 
        baseValue * 0.92, 
        baseValue * 0.98, 
        baseValue
    ];

    const chartConfig = {
        type: 'line',
        data: {
            labels: ['Jun 29', 'Jun 30', 'Jul 01', 'Jul 02', 'Jul 03', 'Jul 04', 'Jul 05'],
            datasets: [{
                label: 'Asset Volume ($)',
                data: dataPoints,
                borderColor: '#3B82F6',
                borderWidth: 3,
                pointBackgroundColor: '#3B82F6',
                pointHoverRadius: 6,
                fill: true,
                tension: 0.4,
                backgroundColor: createChartGradient(ctx.getContext('2d'), isDark)
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    mode: 'index',
                    intersect: false,
                    backgroundColor: isDark ? '#1A2234' : '#FFFFFF',
                    titleColor: isDark ? '#F8FAFC' : '#0F172A',
                    bodyColor: '#3B82F6',
                    borderColor: 'rgba(59, 130, 246, 0.2)',
                    borderWidth: 1,
                    cornerRadius: 8,
                    titleFont: { family: 'Plus Jakarta Sans', weight: 'bold' },
                    bodyFont: { family: 'Plus Jakarta Sans', weight: 'bold' }
                }
            },
            scales: {
                x: {
                    grid: { display: false },
                    ticks: {
                        color: isDark ? '#64748B' : '#94A3B8',
                        font: { family: 'Plus Jakarta Sans', size: 11 }
                    }
                },
                y: {
                    grid: {
                        color: isDark ? 'rgba(255, 255, 255, 0.04)' : 'rgba(15, 23, 42, 0.04)'
                    },
                    ticks: {
                        color: isDark ? '#64748B' : '#94A3B8',
                        font: { family: 'Plus Jakarta Sans', size: 11 }
                    }
                }
            }
        }
    };

    state.chart = new Chart(ctx, chartConfig);
}

function createChartGradient(ctx, isDark) {
    const gradient = ctx.createLinearGradient(0, 0, 0, 300);
    if (isDark) {
        gradient.addColorStop(0, 'rgba(59, 130, 246, 0.15)');
        gradient.addColorStop(1, 'rgba(59, 130, 246, 0)');
    } else {
        gradient.addColorStop(0, 'rgba(59, 130, 246, 0.1)');
        gradient.addColorStop(1, 'rgba(59, 130, 246, 0)');
    }
    return gradient;
}

// ==========================================
// SETTINGS PROFILE LOADING
// ==========================================

function populateSettingsFields() {
    if (state.user) {
        document.getElementById('settings-email').value = state.user.email;
        document.getElementById('settings-firstname').value = state.user.firstName;
        document.getElementById('settings-lastname').value = state.user.lastName || '';
        document.getElementById('settings-role').value = state.user.role.replace('ROLE_', '');
    }
}

// ==========================================
// ADMIN WORKSPACE CONSOLE
// ==========================================

async function loadAdminConsole() {
    const tbody = document.getElementById('admin-table-body');
    tbody.innerHTML = '<tr><td colspan="6" class="table-empty"><i class="fa-solid fa-spinner fa-spin"></i> Auditing user records...</td></tr>';
    
    try {
        const response = await apiCall('/api/admin/users?page=0&size=50', 'GET');
        tbody.innerHTML = '';
        
        if (response.success && response.data.length > 0) {
            response.data.forEach(user => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><strong>${user.id}</strong></td>
                    <td>${user.firstName} ${user.lastName}</td>
                    <td>${user.email}</td>
                    <td><span class="acc-type-badge">${user.role.replace('ROLE_', '')}</span></td>
                    <td>
                        <span class="user-status">
                            <span class="status-dot ${user.isActive ? 'active' : 'inactive'}"></span>
                            ${user.isActive ? 'Active' : 'Suspended'}
                        </span>
                    </td>
                    <td>
                        <div class="admin-action-row" id="admin-accounts-list-${user.id}">
                            <i class="fa-solid fa-circle-notch fa-spin"></i> Pulling accounts...
                        </div>
                    </td>
                `;
                tbody.appendChild(tr);
                
                // Fetch specific user's accounts
                loadAdminAccountsRow(user);
            });
            window.lucide.createIcons();
        } else {
            tbody.innerHTML = '<tr><td colspan="6" class="table-empty">No registered users in the database.</td></tr>';
        }
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" class="table-empty text-danger">Audit Failed: ${err.message}</td></tr>`;
    }
}

async function loadAdminAccountsRow(user) {
    const cell = document.getElementById(`admin-accounts-list-${user.id}`);
    if (!cell) return;
    
    try {
        // Admin displays active accounts. If admin is themselves, render their accounts.
        // If it's a customer, we simulate or query details.
        cell.innerHTML = '';
        if (user.email === state.user.email) {
            if (state.accounts.length === 0) {
                cell.innerHTML = '<span class="text-muted">No accounts open</span>';
                return;
            }
            state.accounts.forEach(acc => {
                const badge = document.createElement('div');
                badge.className = 'account-sub-badge';
                
                let actionBtn = '';
                if (acc.status === 'ACTIVE') {
                    actionBtn = `<button class="btn btn-logout-sidebar btn-sm" onclick="adminFreeze('${acc.accountNumber}')"><i data-lucide="lock"></i> Freeze</button>`;
                } else if (acc.status === 'FROZEN') {
                    actionBtn = `<button class="btn btn-primary btn-sm" style="background-color:var(--success);" onclick="adminUnfreeze('${acc.accountNumber}')"><i data-lucide="lock-open"></i> Activate</button>`;
                }
                
                badge.innerHTML = `
                    <strong>${acc.accountNumber}</strong>
                    <span>$${acc.balance.toFixed(2)}</span>
                    ${actionBtn}
                `;
                cell.appendChild(badge);
            });
        } else {
            cell.innerHTML = '<span class="text-muted">No accounts configured</span>';
        }
        window.lucide.createIcons();
    } catch (err) {
        cell.innerHTML = '<span class="text-danger">Sync Error</span>';
    }
}

async function adminFreeze(accNum) {
    try {
        const response = await apiCall(`/api/admin/accounts/${accNum}/freeze`, 'POST');
        if (response.success) {
            showToast("Suspended", `Account ${accNum} has been frozen.`, "warning");
            loadAdminConsole();
        }
    } catch (err) {
        showToast("Action Failed", err.message, "error");
    }
}

async function adminUnfreeze(accNum) {
    try {
        const response = await apiCall(`/api/admin/accounts/${accNum}/unfreeze`, 'POST');
        if (response.success) {
            showToast("Activated", `Account ${accNum} has been re-activated.`, "success");
            loadAdminConsole();
        }
    } catch (err) {
        showToast("Action Failed", err.message, "error");
    }
}

// ==========================================
// MODALS DIALOG LOGIC
// ==========================================

function openModal(id) {
    document.getElementById(id).classList.remove('hidden');
}

function closeModal(id) {
    document.getElementById(id).classList.add('hidden');
}

function openOpenAccountModal() {
    openModal('modal-open-account');
}

function openDepositModal(accNum) {
    document.getElementById('deposit-account-num-input').value = accNum;
    document.getElementById('deposit-account-num-label').textContent = accNum;
    openModal('modal-deposit');
}

function openWithdrawModal(accNum) {
    document.getElementById('withdraw-account-num-input').value = accNum;
    document.getElementById('withdraw-account-num-label').textContent = accNum;
    openModal('modal-withdraw');
}

// ==========================================
// APP INITIALIZATION
// ==========================================

function setupAppInterface() {
    document.getElementById('auth-view').classList.add('hidden');
    document.getElementById('dashboard-layout').classList.remove('hidden');
    
    // Set Header Initials avatar
    const initials = (state.user.firstName[0] + (state.user.lastName[0] || '')).toUpperCase();
    document.getElementById('avatar-letters').textContent = initials;
    document.getElementById('topbar-avatar').textContent = initials;
    
    document.getElementById('user-display-name').textContent = `${state.user.firstName} ${state.user.lastName}`;
    document.getElementById('user-display-role').textContent = state.user.role.replace('ROLE_', '');
    document.getElementById('hero-name').textContent = state.user.firstName;
    
    // Toggle Admin sidebar item
    const adminMenu = document.getElementById('nav-admin');
    if (state.user.role === 'ROLE_ADMIN') {
        adminMenu.classList.remove('hidden');
    } else {
        adminMenu.classList.add('hidden');
    }

    // Set Date in topbar
    const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
    document.getElementById('topbar-date').textContent = new Date().toLocaleDateString('en-US', options);

    // Render icons
    if (window.lucide) {
        window.lucide.createIcons();
    }

    switchView('dashboard-view');
}

// ==========================================
// BIND DOM EVENT LISTENERS
// ==========================================

document.addEventListener('DOMContentLoaded', () => {
    
    // Auto-detect UI theme and toggle triggers
    initSidebarToggle();
    initThemeToggle();

    // Session Restore
    if (state.accessToken && state.user) {
        setupAppInterface();
    }

    // 1. LOGIN
    document.getElementById('login-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('login-email').value;
        const password = document.getElementById('login-password').value;
        try {
            const res = await apiCall('/api/auth/login', 'POST', { email, password });
            if (res.success && res.data) {
                handleLoginSuccess(res.data);
            }
        } catch (err) {
            showToast("Login Failed", err.message, "error");
        }
    });

    // 2. REGISTRATION
    document.getElementById('register-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('reg-email').value;
        const password = document.getElementById('reg-password').value;
        const firstName = document.getElementById('reg-firstname').value;
        const lastName = document.getElementById('reg-lastname').value;
        const role = document.getElementById('reg-role').value;
        
        try {
            const res = await apiCall('/api/auth/register', 'POST', { email, password, firstName, lastName, role });
            if (res.success && res.data) {
                handleLoginSuccess(res.data);
            }
        } catch (err) {
            showToast("Provisioning Failed", err.message, "error");
        }
    });

    // 3. LOGOUT
    document.getElementById('btn-signout').addEventListener('click', logoutUser);

    // 4. SIDEBAR MENU VIEWS SWITCHER
    document.querySelectorAll('.menu-item').forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const target = item.getAttribute('data-target');
            switchView(target);
        });
    });

    // 5. OPEN ACCOUNT FORM
    document.getElementById('form-open-account').addEventListener('submit', async (e) => {
        e.preventDefault();
        const accountType = document.getElementById('modal-acc-type').value;
        try {
            const res = await apiCall('/api/accounts', 'POST', { accountType });
            if (res.success) {
                showToast("Account Initialized", `Opened a new ${accountType} bank account.`, "success");
                closeModal('modal-open-account');
                loadUserDashboard();
            }
        } catch (err) {
            showToast("Execution Rejected", err.message, "error");
        }
    });

    // 6. CASH DEPOSIT FORM
    document.getElementById('form-deposit').addEventListener('submit', async (e) => {
        e.preventDefault();
        const accountNumber = document.getElementById('deposit-account-num-input').value;
        const amount = parseFloat(document.getElementById('deposit-val-input').value);
        const description = document.getElementById('deposit-memo-input').value;
        
        try {
            const res = await apiCall('/api/transactions/deposit', 'POST', { accountNumber, amount, description });
            if (res.success) {
                showToast("Credit Succeeded", `Deposited $${amount.toFixed(2)} into ${accountNumber.slice(-4)}.`, "success");
                closeModal('modal-deposit');
                loadUserDashboard();
                
                // Reset form values
                document.getElementById('deposit-val-input').value = '';
                document.getElementById('deposit-memo-input').value = '';
            }
        } catch (err) {
            showToast("Deposit Failed", err.message, "error");
        }
    });

    // 7. CASH WITHDRAWAL FORM
    document.getElementById('form-withdraw').addEventListener('submit', async (e) => {
        e.preventDefault();
        const accountNumber = document.getElementById('withdraw-account-num-input').value;
        const amount = parseFloat(document.getElementById('withdraw-val-input').value);
        const description = document.getElementById('withdraw-memo-input').value;
        
        try {
            const res = await apiCall('/api/transactions/withdraw', 'POST', { accountNumber, amount, description });
            if (res.success) {
                showToast("Debit Succeeded", `Withdrew $${amount.toFixed(2)} from ${accountNumber.slice(-4)}.`, "success");
                closeModal('modal-withdraw');
                loadUserDashboard();
                
                // Reset form values
                document.getElementById('withdraw-val-input').value = '';
                document.getElementById('withdraw-memo-input').value = '';
            }
        } catch (err) {
            showToast("Withdrawal Failed", err.message, "error");
        }
    });

    // 8. P2P WIRE TRANSFER FORM
    document.getElementById('transfer-form-premium').addEventListener('submit', async (e) => {
        e.preventDefault();
        const sourceAccountNumber = document.getElementById('transfer-source-acc').value;
        const destinationAccountNumber = document.getElementById('transfer-dest-acc').value;
        const amount = parseFloat(document.getElementById('transfer-val').value);
        const description = document.getElementById('transfer-memo').value;

        try {
            const res = await apiCall('/api/transactions/transfer', 'POST', {
                sourceAccountNumber,
                destinationAccountNumber,
                amount,
                description
            });
            
            if (res.success) {
                showToast("Transfer Executed", `Transferred $${amount.toFixed(2)} to ${destinationAccountNumber.slice(-4)}.`, "success");
                loadUserDashboard();
                switchView('dashboard-view');
                
                // Reset form values
                document.getElementById('transfer-source-acc').value = '';
                document.getElementById('transfer-dest-acc').value = '';
                document.getElementById('transfer-val').value = '';
                document.getElementById('transfer-memo').value = '';
            }
        } catch (err) {
            showToast("Transfer Denied", err.message, "error");
        }
    });

    // 9. LEDGER FILTERS & SEARCH
    document.getElementById('ledger-account-select').addEventListener('change', fetchLedgerHistory);
    
    document.getElementById('ledger-search-input').addEventListener('input', (e) => {
        state.ledgerSearch = e.target.value;
        renderFilteredTransactions();
    });

    document.querySelectorAll('.filter-tag').forEach(tag => {
        tag.addEventListener('click', () => {
            document.querySelectorAll('.filter-tag').forEach(t => t.classList.remove('active'));
            tag.classList.add('active');
            state.ledgerFilter = tag.getAttribute('data-filter');
            state.ledgerPage = 1; // reset page
            renderFilteredTransactions();
        });
    });

    // 10. PAGINATION CLICKS
    document.getElementById('btn-page-prev').addEventListener('click', () => {
        if (state.ledgerPage > 1) {
            state.ledgerPage--;
            renderFilteredTransactions();
        }
    });

    document.getElementById('btn-page-next').addEventListener('click', () => {
        const totalItems = state.ledgerTransactions.length;
        const totalPages = Math.ceil(totalItems / state.ledgerPageSize) || 1;
        if (state.ledgerPage < totalPages) {
            state.ledgerPage++;
            renderFilteredTransactions();
        }
    });

    // 11. PROFILE PROFILE SETTINGS FORM
    document.getElementById('profile-settings-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const firstName = document.getElementById('settings-firstname').value;
        const lastName = document.getElementById('settings-lastname').value;
        
        try {
            const res = await apiCall('/api/users/profile', 'PUT', { firstName, lastName });
            if (res.success && res.data) {
                showToast("Profile Updated", "Name changes saved to server successfully.", "success");
                state.user.firstName = res.data.firstName;
                state.user.lastName = res.data.lastName;
                localStorage.setItem('vault_user_profile', JSON.stringify(state.user));
                setupAppInterface();
            }
        } catch (err) {
            showToast("Update Failed", err.message, "error");
        }
    });
});
