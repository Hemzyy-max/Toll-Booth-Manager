/* ============================================================================
   IoT Based Toll Booth Manager - real time control room
   app.js
   ----------------------------------------------------------------------------
   The whole front end is this one file. It talks to the Java server through
   plain fetch() calls and receives live updates through one EventSource
   (Server Sent Events) connection.

   Sections
     1.  state and small helpers
     2.  REST helpers (fetch with the session token)
     3.  login / logout / session restore
     4.  the live event stream (SSE)
     5.  navigation between pages
     6.  dashboard (KPIs and charts)
     7.  toll payments (form, validation, receipt)
     8.  vehicles and FASTag wallets
     9.  notification centre
     10. audit log and user accounts (administrators)
     11. my account (password change)
     12. start up
   ============================================================================ */
'use strict';

/* ------------------------------------------------------------------ */
/* 1. state and small helpers                                          */
/* ------------------------------------------------------------------ */
const TOKEN_KEY = 'tb_token';

const state = {
    token: null,
    user: null,
    stats: null,
    vehicles: [],
    payments: [],
    notifications: [],
    notifFilter: 'all',
    page: 'dashboard',
    currency: '\u20B9',
    unread: 0,
    stream: null
};

const $ = (id) => document.getElementById(id);
const money = (value) => state.currency + Number(value || 0).toLocaleString('en-IN');

function escapeHtml(text) {
    return String(text == null ? '' : text)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function clockText(isoString) {
    if (!isoString) return '--:--:--';
    const date = new Date(isoString);
    return isNaN(date) ? '--:--:--' : date.toLocaleTimeString();
}

function dateTimeText(isoString) {
    if (!isoString) return '--';
    const date = new Date(isoString);
    return isNaN(date) ? '--' : date.toLocaleString();
}

function show(element, visible) {
    if (element) element.hidden = !visible;
}

function setBusy(button, busy) {
    if (!button) return;
    button.disabled = busy;
    const spinner = button.querySelector('.spinner');
    show(spinner, busy);
}

/** Toast notification in the bottom right corner. */
function toast(level, title, message, seconds) {
    const stack = $('toastStack');
    if (!stack) return;
    const item = document.createElement('article');
    item.className = 'toast level-' + (level || 'info');
    item.innerHTML =
        '<strong>' + escapeHtml(title) + '</strong>' +
        '<p>' + escapeHtml(message || '') + '</p>' +
        '<time>' + new Date().toLocaleTimeString() + '</time>';
    stack.prepend(item);
    const life = (seconds || 6) * 1000;
    setTimeout(() => {
        item.classList.add('is-leaving');
        setTimeout(() => item.remove(), 220);
    }, life);
}

function pillForMode(mode) {
    const key = String(mode || '').toLowerCase();
    const css = key === 'cash' ? 'pill-cash' : key === 'upi' ? 'pill-upi' : 'pill-fastag';
    return '<span class="pill ' + css + '">' + escapeHtml(mode) + '</span>';
}

/* ------------------------------------------------------------------ */
/* 2. REST helpers                                                     */
/* ------------------------------------------------------------------ */

/**
 * Calls the API and returns the parsed JSON.
 * A 401 answer means "the session is gone", so the page returns to the login
 * screen instead of showing broken data.
 */
async function api(path, options) {
    const settings = Object.assign({ headers: {} }, options || {});
    settings.headers = Object.assign({}, settings.headers);
    if (state.token) settings.headers['Authorization'] = 'Bearer ' + state.token;
    if (settings.body && typeof settings.body !== 'string') {
        settings.headers['Content-Type'] = 'application/json';
        settings.body = JSON.stringify(settings.body);
    }

    const response = await fetch(path, settings);
    let data = null;
    const text = await response.text();
    if (text) {
        try { data = JSON.parse(text); } catch (e) { data = { error: text }; }
    }

    if (response.status === 401 && path !== '/api/login') {
        handleSessionLost();
        throw new Error((data && data.error) || 'Session expired');
    }
    if (!response.ok) {
        const error = new Error((data && data.error) || ('Request failed (' + response.status + ')'));
        error.status = response.status;
        error.data = data;
        throw error;
    }
    return data || {};
}

function handleSessionLost() {
    state.token = null;
    localStorage.removeItem(TOKEN_KEY);
    if (state.stream) { state.stream.close(); state.stream = null; }
    $('appView').hidden = true;
    $('loginView').hidden = false;
    const errorBox = $('loginError');
    errorBox.textContent = 'Your session ended. Please sign in again.';
    errorBox.hidden = false;
}

/* ------------------------------------------------------------------ */
/* 3. login / logout / session restore                                 */
/* ------------------------------------------------------------------ */

async function doLogin(event) {
    event.preventDefault();
    const button = $('loginBtn');
    const errorBox = $('loginError');
    errorBox.hidden = true;

    const username = $('username').value.trim();
    const password = $('password').value;
    if (!username || !password) {
        errorBox.textContent = 'Please type the username and the password.';
        errorBox.hidden = false;
        return;
    }

    setBusy(button, true);
    try {
        const data = await api('/api/login', {
            method: 'POST',
            body: { username: username, password: password }
        });
        state.token = data.token;
        state.user = data.user;
        localStorage.setItem(TOKEN_KEY, data.token);
        $('password').value = '';
        await enterApplication();
        toast('success', 'Welcome ' + data.user.displayName,
            'Signed in as ' + data.user.role + '. Live updates are active now.', 5);
    } catch (error) {
        errorBox.textContent = error.message;
        errorBox.hidden = false;
    } finally {
        setBusy(button, false);
    }
}

async function doLogout() {
    try {
        await api('/api/logout', { method: 'POST' });
    } catch (error) {
        /* the session may already be gone : that is fine */
    }
    state.token = null;
    state.user = null;
    localStorage.removeItem(TOKEN_KEY);
    if (state.stream) { state.stream.close(); state.stream = null; }
    $('appView').hidden = true;
    $('loginView').hidden = false;
    toast('info', 'Signed out', 'You are no longer receiving live updates.', 4);
}

/** Shows the application and loads everything it needs. */
async function enterApplication() {
    $('loginView').hidden = true;
    $('appView').hidden = false;

    const user = state.user;
    $('userName').textContent = user.displayName;
    $('userRole').textContent = user.role;
    $('userInitials').textContent = initialsOf(user.displayName);

    const isAdmin = user.role === 'ADMIN';
    document.querySelectorAll('.nav-admin').forEach((button) => { button.hidden = !isAdmin; });
    show($('clearAllNotifs'), isAdmin);
    show($('clearAllNotifsPage'), isAdmin);

    await Promise.all([refreshVehicles(), refreshStats(), refreshPayments(), refreshNotifications()]);
    connectStream();
    goto('dashboard');
}

function initialsOf(name) {
    const parts = String(name || '?').trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/** Tries to restore a previous session (for example after a page refresh). */
async function restoreSession() {
    const saved = localStorage.getItem(TOKEN_KEY);
    if (!saved) return false;
    state.token = saved;
    try {
        const data = await api('/api/session');
        state.user = data.user;
        await enterApplication();
        return true;
    } catch (error) {
        state.token = null;
        localStorage.removeItem(TOKEN_KEY);
        return false;
    }
}

/* ------------------------------------------------------------------ */
/* 4. the live event stream (Server Sent Events)                       */
/* ------------------------------------------------------------------ */

function connectStream() {
    if (state.stream) state.stream.close();
    const dot = $('connectionDot');
    const text = $('connectionText');

    // EventSource cannot send headers, so the token travels in the query string.
    const stream = new EventSource('/api/events?token=' + encodeURIComponent(state.token));
    state.stream = stream;

    stream.addEventListener('open', () => {
        dot.className = 'dot dot-on';
        text.textContent = 'Live stream: connected';
    });

    stream.addEventListener('error', () => {
        dot.className = 'dot dot-off';
        text.textContent = 'Live stream: reconnecting';
    });

    stream.addEventListener('hello', (event) => {
        const data = JSON.parse(event.data);
        text.textContent = 'Live stream: connected (' + data.clientId + ')';
        setUnread(data.pendingNotifications || 0);
    });

    stream.addEventListener('payment', (event) => {
        const payment = JSON.parse(event.data);
        state.payments.unshift(payment);
        state.payments = state.payments.slice(0, 200);
        renderPaymentsTable(state.payments, true);
        renderRecentPayments(state.payments.slice(0, 6));
        renderLastPayment(payment);
        refreshStats();
        if (payment.operatorUsername !== state.user.username) {
            toast('success', 'Toll collected on the reader lane',
                payment.vehicleNumber + ' (' + payment.vehicleType + ') paid ' +
                payment.paidAmountText + ' by ' + payment.paymentMode + '.', 6);
        }
    });

    stream.addEventListener('payment-failed', (event) => {
        const data = JSON.parse(event.data);
        toast('danger', 'Payment rejected - barrier stayed closed',
            data.vehicleNumber + ' : ' + data.reason, 9);
        refreshPayments();
        refreshStats();
    });

    stream.addEventListener('notification', (event) => {
        const notification = JSON.parse(event.data);
        state.notifications.unshift(notification);
        state.notifications = state.notifications.slice(0, 300);
        renderNotificationList();
        renderNotificationPage();
        if (!notification.read) setUnread(state.unread + 1);
        if (notification.level !== 'success') {
            toast(notification.level, notification.title, notification.message,
                notification.level === 'danger' ? 9 : 6);
        }
    });

    stream.addEventListener('notification-cleared', () => {
        state.notifications = [];
        setUnread(0);
        renderNotificationList();
        renderNotificationPage();
    });

    stream.addEventListener('notifications-read', () => {
        setUnread(0);
    });

    stream.addEventListener('login', (event) => {
        const data = JSON.parse(event.data);
        if (state.user && data.username !== state.user.username) {
            toast('info', 'User signed in', data.displayName + ' (' + data.role + ') is now online.', 5);
        }
        refreshStats();
    });

    stream.addEventListener('logout', (event) => {
        const data = JSON.parse(event.data);
        if (state.user && data.username !== state.user.username) {
            toast('info', 'User signed out', data.username + ' left the control room.', 4);
        }
        refreshStats();
    });

    stream.addEventListener('login-failed', (event) => {
        const data = JSON.parse(event.data);
        if (state.user && state.user.role === 'ADMIN') {
            toast('danger', 'Failed login attempt',
                'Wrong password for "' + data.username + '" from ' + data.clientAddress, 8);
        }
    });

    stream.addEventListener('rfid-scan', (event) => {
        const data = JSON.parse(event.data);
        $('scanHint').textContent = 'Reader detected ' + data.rfidTag + ' (by ' + data.scannedBy +
            ' at ' + clockText(data.at) + ').';
    });

    stream.addEventListener('vehicle-changed', () => refreshVehicles());
    stream.addEventListener('session-revoked', () => handleSessionLost());
    stream.addEventListener('shutdown', (event) => {
        const data = JSON.parse(event.data);
        toast('warning', 'Server is stopping', data.message || '', 20);
        dot.className = 'dot dot-off';
        text.textContent = 'Live stream: server stopped';
    });
}

/* ------------------------------------------------------------------ */
/* 5. navigation                                                       */
/* ------------------------------------------------------------------ */

const PAGE_TITLES = {
    dashboard: ['Dashboard', 'Live collection of the toll plaza'],
    payments: ['Toll payments', 'Collect a toll and see the history'],
    vehicles: ['Vehicles & FASTag', 'Registered vehicles, rates and wallets'],
    notifications: ['Notification centre', 'Payments, security and device alerts'],
    audit: ['Security audit log', 'Every action written to the audit file'],
    users: ['User accounts', 'Create operators and administrators'],
    account: ['My account', 'Profile and password']
};

function goto(page) {
    state.page = page;
    document.querySelectorAll('.page').forEach((section) => {
        section.hidden = (section.id !== 'page-' + page);
    });
    document.querySelectorAll('.nav-item').forEach((button) => {
        button.classList.toggle('is-active', button.dataset.page === page);
    });
    const title = PAGE_TITLES[page] || PAGE_TITLES.dashboard;
    $('pageTitle').textContent = title[0];
    $('pageSubtitle').textContent = title[1];
    $('sidebar').classList.remove('is-open');

    if (page === 'payments') refreshPayments();
    if (page === 'vehicles') refreshVehicles();
    if (page === 'notifications') { refreshNotifications(); markAllRead(false); }
    if (page === 'audit') refreshAudit();
    if (page === 'users') refreshUsers();
    if (page === 'account') renderAccount();
    if (page === 'dashboard') refreshStats();
}

/* ------------------------------------------------------------------ */
/* 6. dashboard                                                        */
/* ------------------------------------------------------------------ */

async function refreshStats() {
    try {
        const stats = await api('/api/stats');
        state.stats = stats;
        renderKpis(stats);
        renderHourlyChart(stats.hourly || []);
        renderModeChart(stats.modes || []);
        renderTypeList(stats.vehicleTypes || []);
        if (stats.lastPayment) renderLastPayment(stats.lastPayment);
        $('onlineCount').textContent = (stats.tabsConnected != null)
            ? stats.tabsConnected : (stats.sessions ? stats.sessions.length : 1);
        $('serverClock').textContent = clockText(stats.serverTime);
    } catch (error) {
        /* the page keeps the old numbers if the request fails */
    }
}

function renderKpis(stats) {
    $('kpiToday').textContent = stats.todayCount;
    $('kpiTodayFoot').textContent = stats.todayFailed + ' failed attempt(s) today';
    $('kpiAmount').textContent = stats.todayCollectionText;
    $('kpiAvg').textContent = stats.averageTicketText;
    $('kpiVehicles').textContent = stats.registeredVehicles;
    $('kpiScans').textContent = stats.rfidScans;
    $('barrierState').textContent = stats.barrierOpen ? 'OPEN' : 'CLOSED';
    $('kpiEngineTotal').textContent = stats.engineCollectionText +
        ' (' + stats.engineTransactions + ' transactions in transactions.txt)';
}

function renderHourlyChart(hourly) {
    const container = $('chartHourly');
    const maximum = Math.max(1, ...hourly.map((item) => item.count));
    const currentHour = new Date().getHours();
    container.innerHTML = hourly.map((item) => {
        const height = Math.round((item.count / maximum) * 100);
        return '<div class="bar-col" title="' + item.label + ' : ' + item.count +
            ' payment(s), ' + money(item.amount) + '">' +
            '<div class="bar' + (item.hour === currentHour ? ' is-now' : '') +
            '" style="height:' + Math.max(height, 2) + '%"></div>' +
            '<span class="bar-label">' + item.label.substring(0, 2) + '</span>' +
            '</div>';
    }).join('');
}

const MODE_COLORS = { Cash: '#34d399', UPI: '#a78bfa', FASTag: '#22d3ee' };

function renderModeChart(modes) {
    const svg = $('chartModes');
    const total = modes.reduce((sum, item) => sum + item.count, 0);
    svg.innerHTML = '';
    const radius = 15.9;
    const circumference = 2 * Math.PI * radius;

    if (total === 0) {
        svg.innerHTML = '<circle cx="21" cy="21" r="' + radius +
            '" fill="none" stroke="rgba(148,173,214,0.2)" stroke-width="6"></circle>' +
            '<text x="21" y="22.6" text-anchor="middle" font-size="4.4" fill="#9db0d0">no data</text>';
    } else {
        let offset = 0;
        modes.forEach((item) => {
            const share = item.count / total;
            const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
            circle.setAttribute('cx', '21');
            circle.setAttribute('cy', '21');
            circle.setAttribute('r', String(radius));
            circle.setAttribute('fill', 'none');
            circle.setAttribute('stroke', MODE_COLORS[item.mode] || '#94a3b8');
            circle.setAttribute('stroke-width', '6');
            circle.setAttribute('stroke-dasharray', (share * circumference) + ' ' + circumference);
            circle.setAttribute('stroke-dashoffset', String(-offset * circumference));
            svg.appendChild(circle);
            offset += share;
        });
    }

    $('modeLegend').innerHTML = modes.map((item) =>
        '<li><span class="swatch" style="background:' + (MODE_COLORS[item.mode] || '#94a3b8') + '"></span>' +
        escapeHtml(item.mode) + ' &middot; ' + item.count + ' payment(s)</li>').join('');
}

function renderTypeList(types) {
    $('typeList').innerHTML = types.length
        ? types.map((item) => '<li><span class="pill pill-neutral">' + escapeHtml(item.type) +
            '</span> ' + item.count + '</li>').join('')
        : '<li class="muted small">No vehicle crossed yet today.</li>';
}

function renderLastPayment(payment) {
    $('lastPaymentTime').textContent = clockText(payment.paidAt);
    $('lastPaymentCard').classList.remove('muted');
    $('lastPaymentCard').innerHTML =
        '<strong>' + escapeHtml(payment.vehicleNumber) + ' &middot; ' + escapeHtml(payment.vehicleType) +
        ' paid ' + escapeHtml(payment.paidAmountText) + '</strong>' +
        '<div class="last-grid">' +
        '<div><span class="muted small">Receipt</span><span>' + escapeHtml(payment.receiptId) + '</span></div>' +
        '<div><span class="muted small">Transaction</span><span>' + escapeHtml(payment.transactionId) + '</span></div>' +
        '<div><span class="muted small">Mode</span><span>' + pillForMode(payment.paymentMode) + '</span></div>' +
        '<div><span class="muted small">Base toll</span><span>' + escapeHtml(payment.baseTollText) + '</span></div>' +
        '<div><span class="muted small">Discount</span><span>' + money(payment.discount) + '</span></div>' +
        '<div><span class="muted small">Operator</span><span>' + escapeHtml(payment.operator) + '</span></div>' +
        '</div>';
}

/* ------------------------------------------------------------------ */
/* 7. toll payments                                                    */
/* ------------------------------------------------------------------ */

async function refreshVehicles() {
    try {
        const data = await api('/api/vehicles');
        state.vehicles = data.vehicles || [];
        if (state.vehicles.length && state.vehicles[0].baseTollText) {
            // take the currency symbol from the server (₹ or Rs.)
            state.currency = state.vehicles[0].baseTollText.replace(/[0-9.,\s]/g, '') || '\u20B9';
        }
        renderVehicleOptions();
        renderVehicleGrid(state.vehicles);
        updateTollPreview();
    } catch (error) {
        /* keep the previous list */
    }
}

function renderVehicleOptions() {
    const select = $('payRfid');
    const previous = select.value;
    select.innerHTML = state.vehicles.map((vehicle) =>
        '<option value="' + escapeHtml(vehicle.rfidTag) + '">' +
        escapeHtml(vehicle.rfidTag) + ' &middot; ' + escapeHtml(vehicle.vehicleNumber) +
        ' (' + escapeHtml(vehicle.vehicleType) + ')</option>').join('');
    if (previous) select.value = previous;
}

function currentVehicle() {
    const tag = $('payRfid').value;
    return state.vehicles.find((vehicle) => vehicle.rfidTag === tag) || null;
}

function selectedMode() {
    const checked = document.querySelector('#payMode input[name="mode"]:checked');
    return checked ? checked.value : 'Cash';
}

/** Shows the toll the vehicle will pay, before the operator confirms. */
function updateTollPreview() {
    const vehicle = currentVehicle();
    if (!vehicle) {
        $('pvVehicle').textContent = '--';
        $('pvBase').textContent = '--';
        $('pvDiscount').textContent = '--';
        $('pvPayable').textContent = '--';
        return;
    }
    const mode = selectedMode();
    const fastagAllowed = vehicle.fastagEnabled && (vehicle.vehicleType === 'Truck' || vehicle.vehicleType === 'Bus');

    // A car or a bike can never be charged to a FASTag in this system.
    document.querySelectorAll('#payMode input[value="FASTag"]').forEach((input) => {
        input.disabled = !fastagAllowed;
    });
    if (!fastagAllowed && mode === 'FASTag') {
        document.querySelector('#payMode input[value="Cash"]').checked = true;
    }

    const activeMode = selectedMode();
    const base = Number(vehicle.baseToll);
    const discount = activeMode === 'FASTag' ? Math.round(base * 10) / 100 : 0;
    const payable = Math.round((base - discount) * 100) / 100;

    $('pvVehicle').textContent = vehicle.vehicleNumber + ' (' + vehicle.vehicleType + ')';
    $('pvBase').textContent = money(base);
    $('pvDiscount').textContent = discount > 0
        ? '-' + money(discount) + ' (FASTag 10%)'
        : (activeMode === 'FASTag' ? '0 (wallet empty)' : 'none');
    $('pvPayable').textContent = money(payable);

    show($('cashRow'), activeMode === 'Cash');
    if (activeMode === 'Cash' && !$('payAmount').value) {
        $('payAmount').value = payable;
    }
    return { base: base, payable: payable };
}

async function doPayment(event) {
    event.preventDefault();
    const button = $('payBtn');
    const errorBox = $('payError');
    const successBox = $('paySuccess');
    errorBox.hidden = true;
    successBox.hidden = true;

    const vehicle = currentVehicle();
    if (!vehicle) {
        errorBox.textContent = 'Please choose a vehicle first.';
        errorBox.hidden = false;
        return;
    }

    const body = {
        rfidTag: vehicle.rfidTag,
        paymentMode: selectedMode(),
        rfidSource: 'manual-entry'
    };
    if (body.paymentMode === 'Cash') {
        body.amount = Number($('payAmount').value);
    }

    setBusy(button, true);
    try {
        const data = await api('/api/payments', { method: 'POST', body: body });
        const payment = data.payment;
        successBox.textContent = 'Payment successful : ' + payment.paidAmountText +
            ' collected from ' + payment.vehicleNumber + '. Barrier opened and closed.';
        successBox.hidden = false;
        openReceipt(data.receipt);
        toast('success', 'Toll collected', payment.vehicleNumber + ' paid ' +
            payment.paidAmountText + ' by ' + payment.paymentMode + '.', 5);
        $('payAmount').value = '';
        if (body.paymentMode === 'Cash') $('payAmount').value = payment.paidAmount;
        await Promise.all([refreshPayments(), refreshStats(), refreshVehicles()]);
    } catch (error) {
        // The server refused the payment : the barrier stayed closed.
        errorBox.textContent = error.message;
        errorBox.hidden = false;
        toast('danger', 'Payment not accepted', error.message, 8);
    } finally {
        setBusy(button, false);
    }
}

async function refreshPayments() {
    try {
        const search = $('paymentSearch') ? $('paymentSearch').value.trim() : '';
        const query = '/api/payments?limit=60' + (search ? '&search=' + encodeURIComponent(search) : '');
        const data = await api(query);
        state.payments = data.payments || [];
        renderPaymentsTable(state.payments, false);
        renderRecentPayments(state.payments.slice(0, 6));
        if (data.stats) {
            state.stats = data.stats;
            renderKpis(data.stats);
        }
    } catch (error) {
        /* ignore */
    }
}

function paymentsRows(payments) {
    if (!payments.length) {
        return '<tr><td colspan="8" class="muted center">No payment yet.</td></tr>';
    }
    return payments.map((payment) =>
        '<tr data-receipt="' + escapeHtml(payment.receiptId) + '">' +
        '<td>' + escapeHtml(payment.receiptId) + '</td>' +
        '<td>' + escapeHtml(payment.transactionId) + '</td>' +
        '<td>' + clockText(payment.paidAt) + '</td>' +
        '<td>' + escapeHtml(payment.vehicleNumber) + ' <span class="muted small">' +
        escapeHtml(payment.vehicleType) + '</span></td>' +
        '<td>' + escapeHtml(payment.rfidTag) + '</td>' +
        '<td>' + pillForMode(payment.paymentMode) + '</td>' +
        '<td class="right">' + escapeHtml(payment.paidAmountText) + '</td>' +
        '<td>' + escapeHtml(payment.operator) + '</td>' +
        '</tr>').join('');
}

function renderPaymentsTable(payments, highlightFirst) {
    const body = $('paymentsTableBody');
    if (!body) return;
    body.innerHTML = paymentsRows(payments);
    if (highlightFirst && body.rows.length) {
        body.rows[0].classList.add('is-new');
    }
}

function renderRecentPayments(payments) {
    const body = $('recentPaymentsBody');
    if (!body) return;
    body.innerHTML = payments.map((payment) =>
        '<tr>' +
        '<td>' + escapeHtml(payment.receiptId) + '</td>' +
        '<td>' + clockText(payment.paidAt) + '</td>' +
        '<td>' + escapeHtml(payment.vehicleNumber) + '</td>' +
        '<td>' + escapeHtml(payment.vehicleType) + '</td>' +
        '<td>' + pillForMode(payment.paymentMode) + '</td>' +
        '<td class="right">' + escapeHtml(payment.baseTollText) + '</td>' +
        '<td class="right">' + escapeHtml(payment.paidAmountText) + '</td>' +
        '<td>' + escapeHtml(payment.operator) + '</td>' +
        '</tr>').join('');
}

/** Asks the RFID reader for a tag (the reader detects by itself). */
async function simulateScan() {
    try {
        const data = await api('/api/rfid/simulate');
        $('payRfid').value = data.rfidTag;
        $('scanHint').textContent = 'Reader detected ' + data.rfidTag +
            ' - the vehicle was found in the booth database.';
        const modeInput = document.querySelector('#payMode input[value="' +
            (data.suggestedPaymentMode === 'FASTag' ? 'FASTag' : data.suggestedPaymentMode) + '"]');
        if (modeInput && !modeInput.disabled) modeInput.checked = true;
        updateTollPreview();
        toast('info', 'RFID tag detected', data.rfidTag + ' is in front of the reader.', 4);
    } catch (error) {
        toast('warning', 'Reader problem', error.message, 6);
    }
}

function openReceipt(text) {
    $('receiptText').textContent = text;
    $('receiptModal').hidden = false;
}

/* ------------------------------------------------------------------ */
/* 8. vehicles and FASTag wallets                                      */
/* ------------------------------------------------------------------ */

function renderVehicleGrid(vehicles) {
    const grid = $('vehicleGrid');
    if (!grid) return;
    const search = ($('vehicleSearch') ? $('vehicleSearch').value : '').trim().toLowerCase();
    const list = search
        ? vehicles.filter((vehicle) =>
            vehicle.vehicleNumber.toLowerCase().includes(search) ||
            vehicle.ownerName.toLowerCase().includes(search) ||
            vehicle.rfidTag.toLowerCase().includes(search) ||
            vehicle.vehicleType.toLowerCase().includes(search))
        : vehicles;

    if (!list.length) {
        grid.innerHTML = '<p class="muted small">No vehicle matches this search.</p>';
        return;
    }

    grid.innerHTML = list.map((vehicle) => {
        const wallet = (vehicle.vehicleType === 'Truck' || vehicle.vehicleType === 'Bus');
        return '<article class="vehicle-card">' +
            '<header><span class="plate">' + escapeHtml(vehicle.vehicleNumber) + '</span>' +
            '<span class="pill pill-neutral">' + escapeHtml(vehicle.vehicleType) + '</span></header>' +
            '<div class="vehicle-meta">' +
            '<span>Owner : ' + escapeHtml(vehicle.ownerName) + '</span>' +
            '<span>RFID : ' + escapeHtml(vehicle.rfidTag) + '</span>' +
            '<span>Toll : ' + escapeHtml(vehicle.baseTollText) + ' &middot; trips : ' + vehicle.trips + '</span>' +
            '</div>' +
            (wallet
                ? '<div class="vehicle-meta"><span>FASTag wallet : ' + escapeHtml(vehicle.fastagBalanceText) +
                  ' &middot; ' + (vehicle.fastagEnabled ? 'active' : 'blocked') + '</span></div>' +
                  '<div class="wallet-row">' +
                  '<input class="input" type="number" min="1" step="50" value="500" aria-label="Recharge amount">' +
                  '<button class="btn btn-ghost" data-recharge="' + escapeHtml(vehicle.rfidTag) + '">Recharge</button>' +
                  '<button class="btn btn-ghost" data-toggle="' + escapeHtml(vehicle.rfidTag) + '">' +
                  (vehicle.fastagEnabled ? 'Block' : 'Unblock') + '</button>' +
                  '</div>'
                : '<div class="vehicle-meta"><span class="muted">No FASTag : pays by Cash or UPI (a two wheeler never gets the FASTag discount).</span></div>') +
            '</article>';
    }).join('');
}

async function handleVehicleAction(event) {
    const rechargeButton = event.target.closest('[data-recharge]');
    const toggleButton = event.target.closest('[data-toggle]');
    if (!rechargeButton && !toggleButton) return;

    const button = rechargeButton || toggleButton;
    const rfidTag = rechargeButton ? rechargeButton.dataset.recharge : toggleButton.dataset.toggle;

    try {
        let body;
        if (rechargeButton) {
            const amount = Number(rechargeButton.parentElement.querySelector('input').value);
            body = { rfidTag: rfidTag, action: 'recharge', amount: amount };
        } else {
            const vehicle = state.vehicles.find((item) => item.rfidTag === rfidTag);
            body = { rfidTag: rfidTag, action: vehicle && vehicle.fastagEnabled ? 'disable' : 'enable' };
        }
        const data = await api('/api/vehicles/fastag', { method: 'POST', body: body });
        toast('success', 'FASTag updated',
            data.vehicle.vehicleNumber + ' wallet : ' + data.vehicle.fastagBalanceText, 5);
        await refreshVehicles();
    } catch (error) {
        toast('danger', 'FASTag action refused', error.message, 7);
    }
}

/* ------------------------------------------------------------------ */
/* 9. notification centre                                              */
/* ------------------------------------------------------------------ */

async function refreshNotifications() {
    try {
        const data = await api('/api/notifications?limit=80');
        state.notifications = data.notifications || [];
        setUnread(data.unread || 0);
        renderNotificationList();
        renderNotificationPage();
    } catch (error) {
        /* ignore */
    }
}

function setUnread(count) {
    state.unread = count;
    const badge = $('notifBadge');
    badge.textContent = count > 99 ? '99+' : count;
    badge.hidden = count <= 0;
    const nav = $('navNotifCount');
    nav.textContent = count;
    nav.hidden = count <= 0;
}

function notificationItemHtml(notification) {
    return '<li class="notif-item' + (notification.read ? '' : ' unread') + '">' +
        '<span class="notif-level level-' + escapeHtml(notification.level) + '"></span>' +
        '<div><strong>' + escapeHtml(notification.title) + '</strong>' +
        '<p>' + escapeHtml(notification.message) + '</p>' +
        '<time>' + dateTimeText(notification.createdAt) + ' &middot; ' +
        escapeHtml(notification.category) + '</time></div></li>';
}

function renderNotificationList() {
    const list = $('notifList');
    const items = state.notifications.slice(0, 12);
    list.innerHTML = items.length
        ? items.map(notificationItemHtml).join('')
        : '<li class="notif-empty muted small">No notification yet.</li>';
}

function renderNotificationPage() {
    const list = $('notifPageList');
    const filter = state.notifFilter;
    const items = filter === 'all'
        ? state.notifications
        : state.notifications.filter((notification) => notification.level === filter);
    list.innerHTML = items.length
        ? items.map(notificationItemHtml).join('')
        : '<li class="muted small">No notification in this filter.</li>';
    $('notifMeta').textContent = state.notifications.length + ' notification(s), ' +
        state.unread + ' unread';
}

async function markAllRead(showToast) {
    try {
        const data = await api('/api/notifications/read', { method: 'POST' });
        state.notifications.forEach((notification) => { notification.read = true; });
        setUnread(0);
        renderNotificationList();
        renderNotificationPage();
        if (showToast && data.markedRead) {
            toast('info', 'Notifications marked as read', data.markedRead + ' message(s).', 3);
        }
    } catch (error) {
        /* ignore */
    }
}

async function clearNotifications() {
    try {
        const data = await api('/api/notifications/clear', { method: 'POST' });
        state.notifications = [];
        setUnread(0);
        renderNotificationList();
        renderNotificationPage();
        toast('info', 'Notification centre emptied', data.removed + ' message(s) removed.', 4);
    } catch (error) {
        toast('danger', 'Only an administrator may clear the centre', error.message, 6);
    }
}

/* ------------------------------------------------------------------ */
/* 10. audit log and user accounts                                     */
/* ------------------------------------------------------------------ */

async function refreshAudit() {
    try {
        const data = await api('/api/audit?limit=120');
        $('auditFilePath').textContent = data.file;
        const list = $('auditList');
        list.innerHTML = (data.entries || []).map((line) => {
            const event = line.split('|')[1] ? line.split('|')[1].trim() : '';
            return '<li data-event="' + escapeHtml(event) + '">' + escapeHtml(line) + '</li>';
        }).join('') || '<li class="muted small">The audit file is empty.</li>';
    } catch (error) {
        toast('danger', 'Audit log unavailable', error.message, 5);
    }
}

async function refreshUsers() {
    try {
        const data = await api('/api/users');
        const body = $('usersTableBody');
        body.innerHTML = (data.users || []).map((user) =>
            '<tr>' +
            '<td>' + escapeHtml(user.displayName) + '<br><span class="muted small">' +
            escapeHtml(user.username) + '</span></td>' +
            '<td><span class="pill ' + (user.role === 'ADMIN' ? 'pill-fastag' : 'pill-neutral') + '">' +
            escapeHtml(user.role) + '</span></td>' +
            '<td>' + (user.active
                ? '<span class="pill pill-cash">active</span>'
                : '<span class="pill pill-neutral">disabled</span>') +
            (user.mustChangePassword ? ' <span class="pill pill-neutral">must change</span>' : '') + '</td>' +
            '<td>' + (user.lastLoginAt ? dateTimeText(user.lastLoginAt) : 'never') + '</td>' +
            '<td>' + user.loginCount + '</td>' +
            '<td class="row">' +
            '<button class="ghost-small" data-user-action="status" data-username="' + escapeHtml(user.username) +
            '" data-active="' + (!user.active) + '">' + (user.active ? 'Disable' : 'Enable') + '</button>' +
            '<button class="ghost-small" data-user-action="role" data-username="' + escapeHtml(user.username) +
            '" data-role="' + (user.role === 'ADMIN' ? 'OPERATOR' : 'ADMIN') + '">Make ' +
            (user.role === 'ADMIN' ? 'operator' : 'admin') + '</button>' +
            '<button class="ghost-small" data-user-action="reset" data-username="' + escapeHtml(user.username) +
            '">Reset password</button>' +
            '</td></tr>').join('');
    } catch (error) {
        $('usersTableBody').innerHTML = '<tr><td colspan="6" class="muted center">' +
            escapeHtml(error.message) + '</td></tr>';
    }
}

async function createUser(event) {
    event.preventDefault();
    const errorBox = $('userError');
    const successBox = $('userSuccess');
    errorBox.hidden = true;
    successBox.hidden = true;
    try {
        const data = await api('/api/users', {
            method: 'POST',
            body: {
                action: 'create',
                username: $('newUsername').value.trim(),
                displayName: $('newDisplayName').value.trim(),
                password: $('newPassword').value,
                role: $('newRole').value,
                mustChangePassword: $('newMustChange').checked
            }
        });
        successBox.textContent = 'Account "' + data.user.username + '" created as ' + data.user.role + '.';
        successBox.hidden = false;
        $('createUserForm').reset();
        $('newMustChange').checked = true;
        await refreshUsers();
    } catch (error) {
        errorBox.textContent = error.message;
        errorBox.hidden = false;
    }
}

async function handleUserAction(event) {
    const button = event.target.closest('[data-user-action]');
    if (!button) return;
    const username = button.dataset.username;
    const action = button.dataset.userAction;
    try {
        if (action === 'status') {
            await api('/api/users', {
                method: 'POST',
                body: { action: 'status', username: username, active: button.dataset.active === 'true' }
            });
        } else if (action === 'role') {
            await api('/api/users', {
                method: 'POST',
                body: { action: 'role', username: username, role: button.dataset.role }
            });
        } else if (action === 'reset') {
            const password = window.prompt('New temporary password for ' + username +
                ' (at least 8 characters with a letter and a digit) :', 'Toll@2026');
            if (!password) return;
            await api('/api/users', {
                method: 'POST',
                body: { action: 'reset', username: username, password: password }
            });
        }
        toast('success', 'Account updated', username + ' : ' + action + ' done.', 4);
        await refreshUsers();
    } catch (error) {
        toast('danger', 'Account action refused', error.message, 7);
    }
}

/* ------------------------------------------------------------------ */
/* 11. my account                                                      */
/* ------------------------------------------------------------------ */

function renderAccount() {
    const user = state.user;
    if (!user) return;
    $('accountInfo').innerHTML =
        '<dt>Username</dt><dd>' + escapeHtml(user.username) + '</dd>' +
        '<dt>Display name</dt><dd>' + escapeHtml(user.displayName) + '</dd>' +
        '<dt>Role</dt><dd>' + escapeHtml(user.role) + '</dd>' +
        '<dt>Account created</dt><dd>' + dateTimeText(user.createdAt) + '</dd>' +
        '<dt>Last login</dt><dd>' + (user.lastLoginAt ? dateTimeText(user.lastLoginAt) : 'this is the first login') + '</dd>' +
        '<dt>Logins</dt><dd>' + user.loginCount + '</dd>' +
        '<dt>Password change</dt><dd>' +
        (user.mustChangePassword ? 'required at this login' : 'not required') + '</dd>';
}

async function changePassword(event) {
    event.preventDefault();
    const errorBox = $('passwordError');
    const successBox = $('passwordSuccess');
    errorBox.hidden = true;
    successBox.hidden = true;

    const currentPassword = $('currentPassword').value;
    const newPassword = $('newPassword1').value;
    const repeated = $('newPassword2').value;

    if (newPassword !== repeated) {
        errorBox.textContent = 'The two new passwords are not the same.';
        errorBox.hidden = false;
        return;
    }
    if (newPassword.length < 8 || !/[A-Za-z]/.test(newPassword) || !/[0-9]/.test(newPassword)) {
        errorBox.textContent = 'The new password needs at least 8 characters with a letter and a digit.';
        errorBox.hidden = false;
        return;
    }

    try {
        const data = await api('/api/password', {
            method: 'POST',
            body: { currentPassword: currentPassword, newPassword: newPassword }
        });
        successBox.textContent = data.message;
        successBox.hidden = false;
        $('passwordForm').reset();
        if (state.user) state.user.mustChangePassword = false;
        renderAccount();
    } catch (error) {
        errorBox.textContent = error.message;
        errorBox.hidden = false;
    }
}

/* ------------------------------------------------------------------ */
/* 12. start up : wire every button once                               */
/* ------------------------------------------------------------------ */

function wireEvents() {
    $('loginForm').addEventListener('submit', doLogin);
    $('logoutBtn').addEventListener('click', doLogout);
    $('showPassword').addEventListener('click', () => {
        const field = $('password');
        field.type = field.type === 'password' ? 'text' : 'password';
        $('showPassword').textContent = field.type === 'password' ? 'Show' : 'Hide';
    });

    $('navList').addEventListener('click', (event) => {
        const button = event.target.closest('.nav-item');
        if (button) goto(button.dataset.page);
    });
    document.querySelectorAll('[data-goto]').forEach((button) => {
        button.addEventListener('click', () => goto(button.dataset.goto));
    });
    $('menuToggle').addEventListener('click', () => $('sidebar').classList.toggle('is-open'));

    $('bellBtn').addEventListener('click', () => {
        const panel = $('notifPanel');
        panel.hidden = !panel.hidden;
        $('bellBtn').setAttribute('aria-expanded', String(!panel.hidden));
        if (!panel.hidden) refreshNotifications();
    });
    $('markAllRead').addEventListener('click', () => markAllRead(true));
    $('markAllReadPage').addEventListener('click', () => markAllRead(true));
    $('clearAllNotifs').addEventListener('click', clearNotifications);
    $('clearAllNotifsPage').addEventListener('click', clearNotifications);
    $('openNotifPage').addEventListener('click', () => {
        $('notifPanel').hidden = true;
        goto('notifications');
    });
    $('notifFilters').addEventListener('click', (event) => {
        const chip = event.target.closest('.chip');
        if (!chip) return;
        document.querySelectorAll('#notifFilters .chip').forEach((item) => item.classList.remove('is-active'));
        chip.classList.add('is-active');
        state.notifFilter = chip.dataset.level;
        renderNotificationPage();
    });

    $('payForm').addEventListener('submit', doPayment);
    $('payRfid').addEventListener('change', updateTollPreview);
    document.querySelectorAll('#payMode input[name="mode"]').forEach((input) => {
        input.addEventListener('change', updateTollPreview);
    });
    $('simulateRfidBtn').addEventListener('click', simulateScan);
    $('paymentSearch').addEventListener('input', () => {
        window.clearTimeout(state.searchTimer);
        state.searchTimer = window.setTimeout(refreshPayments, 260);
    });
    $('refreshPayments').addEventListener('click', refreshPayments);

    $('vehicleSearch').addEventListener('input', () => renderVehicleGrid(state.vehicles));
    $('refreshVehicles').addEventListener('click', refreshVehicles);
    $('vehicleGrid').addEventListener('click', handleVehicleAction);

    $('refreshAudit').addEventListener('click', refreshAudit);
    $('refreshUsers').addEventListener('click', refreshUsers);
    $('createUserForm').addEventListener('submit', createUser);
    $('usersTableBody').addEventListener('click', handleUserAction);
    $('passwordForm').addEventListener('submit', changePassword);

    $('closeReceipt').addEventListener('click', () => { $('receiptModal').hidden = true; });
    $('closeReceiptBtn').addEventListener('click', () => { $('receiptModal').hidden = true; });
    $('printReceipt').addEventListener('click', () => window.print());

    document.addEventListener('click', (event) => {
        if (!event.target.closest('.bell-wrap')) $('notifPanel').hidden = true;
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            $('notifPanel').hidden = true;
            $('receiptModal').hidden = true;
        }
    });
}

window.addEventListener('DOMContentLoaded', async () => {
    wireEvents();
    // The clock ticks every second, the server confirms it on every refresh.
    window.setInterval(() => {
        const clock = $('serverClock');
        if (clock && !clock.dataset.frozen) clock.textContent = new Date().toLocaleTimeString();
    }, 1000);
    const restored = await restoreSession();
    if (!restored) {
        $('loginView').hidden = false;
    }
});
