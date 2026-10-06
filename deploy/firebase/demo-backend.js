/* ============================================================================
   demo-backend.js  -  OFFLINE DEMO MODE for Firebase Hosting (free plan)
   ----------------------------------------------------------------------------
   WHAT THIS FILE IS
   Firebase Hosting serves STATIC files only : it cannot run the Java server of
   this project. This file makes the exact same web page (index.html, app.js,
   styles.css) work without any backend, by replacing the two browser APIs the
   page uses :

       window.fetch        -> answered from data held in this file
       window.EventSource  -> simulated live events (payments, notifications)

   The page itself is NOT modified, so what you see in demo mode is the real
   user interface : login screen, dashboard with charts, toll payment form with
   the toll preview, receipt window, notification centre, audit list, user list.

   LIMITATIONS (be honest about them in a report or a viva) :
     * the login is checked in the browser, so it is NOT a real authentication
       system. The real one is the Java server (tollbooth.web.WebLauncher) with
       PBKDF2 hashes, session tokens and an audit file.
     * data lives in the memory of the tab (and in localStorage for the session),
       so nothing is shared between two visitors or two devices.
     * the toll rules, the toll rates and the FASTag discount ARE the same as the
       Java classes, so the numbers on the screen are correct.

   TO USE THE REAL BACKEND : deploy the Java container and add the
   "/api/**" -> Cloud Run rewrite (see docs/DEPLOY_PUBLIC.md, option A).
   ============================================================================ */
(function () {
    'use strict';

    // ------------------------------------------------------------------
    // 1. demo accounts (same names as the Java application)
    // ------------------------------------------------------------------
    var ACCOUNTS = {
        admin: { password: 'Admin@123', role: 'ADMIN', displayName: 'Toll Booth Administrator' },
        operator: { password: 'Operator@123', role: 'OPERATOR', displayName: 'Booth Operator' }
    };

    var CURRENCY = '\u20B9';
    var FASTAG_DISCOUNT_PERCENT = 10;
    var FASTAG_VEHICLE_TYPES = ['Truck', 'Bus'];
    var LOW_BALANCE_ALERT = 150;
    var MILESTONE_STEP = 1000;

    // ------------------------------------------------------------------
    // 2. demo data : the same eight vehicles as the Java application
    // ------------------------------------------------------------------
    var state = {
        vehicles: [
            { rfidTag: 'RFID1001', vehicleNumber: 'TN01AB1234', vehicleType: 'Car',   ownerName: 'Hemachandran',    baseToll: 50,  fastagBalance: 0,    fastagEnabled: false, trips: 0 },
            { rfidTag: 'RFID1002', vehicleNumber: 'TN02CD5678', vehicleType: 'Bike',  ownerName: 'Arun Kumar',      baseToll: 30,  fastagBalance: 0,    fastagEnabled: false, trips: 0 },
            { rfidTag: 'RFID1003', vehicleNumber: 'TN03EF9012', vehicleType: 'Truck', ownerName: 'Suresh Logistics', baseToll: 150, fastagBalance: 1500, fastagEnabled: true,  trips: 0 },
            { rfidTag: 'RFID1004', vehicleNumber: 'TN04GH3456', vehicleType: 'Bus',   ownerName: 'KPN Travels',     baseToll: 100, fastagBalance: 1200, fastagEnabled: true,  trips: 0 },
            { rfidTag: 'RFID1005', vehicleNumber: 'TN05IJ7890', vehicleType: 'Car',   ownerName: 'Divya Bharathi',  baseToll: 50,  fastagBalance: 0,    fastagEnabled: false, trips: 0 },
            { rfidTag: 'RFID1006', vehicleNumber: 'TN06PQ3456', vehicleType: 'Car',   ownerName: 'Karthik Raja',    baseToll: 50,  fastagBalance: 0,    fastagEnabled: false, trips: 0 },
            { rfidTag: 'RFID1007', vehicleNumber: 'TN07RS7891', vehicleType: 'Truck', ownerName: 'Meena Transport', baseToll: 150, fastagBalance: 2000, fastagEnabled: true,  trips: 0 },
            { rfidTag: 'RFID1008', vehicleNumber: 'TN08TU2345', vehicleType: 'Bus',   ownerName: 'Parveen Travels', baseToll: 100, fastagBalance: 900,  fastagEnabled: true,  trips: 0 }
        ],
        payments: [],
        notifications: [],
        audit: [],
        users: [
            { username: 'admin', displayName: 'Toll Booth Administrator', role: 'ADMIN', active: true, createdAt: new Date().toISOString(), lastLoginAt: null, loginCount: 0, mustChangePassword: false },
            { username: 'operator', displayName: 'Booth Operator', role: 'OPERATOR', active: true, createdAt: new Date().toISOString(), lastLoginAt: null, loginCount: 0, mustChangePassword: false }
        ],
        paymentSequence: 1000,
        receiptSequence: 1000,
        notificationSequence: 1000,
        auditSequence: 0,
        rfidScans: 0,
        loginCount: 0,
        session: null,
        // day counters, kept the same way as in TollPaymentService.java
        dayKey: new Date().toDateString(),
        failedToday: 0,
        failedTotal: 0,
        lastMilestone: 0,
        autoPayments: 0
    };

    // ------------------------------------------------------------------
    // 3. small helpers
    // ------------------------------------------------------------------
    function money(value) {
        var rounded = Math.round(Number(value || 0) * 100) / 100;
        if (rounded === Math.round(rounded)) return CURRENCY + String(Math.round(rounded));
        return CURRENCY + rounded.toFixed(2);
    }

    function round2(value) {
        return Math.round(Number(value) * 100) / 100;
    }

    function clockText(date) {
        return date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    }

    function isFastagVehicle(type) {
        return FASTAG_VEHICLE_TYPES.indexOf(type) >= 0;
    }

    function findVehicleByTag(tag) {
        var wanted = String(tag || '').trim().toUpperCase();
        for (var i = 0; i < state.vehicles.length; i++) {
            if (state.vehicles[i].rfidTag === wanted) return state.vehicles[i];
        }
        return null;
    }

    function todayPayments() {
        var today = new Date().toDateString();
        return state.payments.filter(function (p) { return new Date(p.paidAt).toDateString() === today; });
    }

    /** Resets the daily counters when the date changes (like rolloverDayIfNeeded in Java). */
    function rolloverDayIfNeeded() {
        var today = new Date().toDateString();
        if (state.dayKey !== today) {
            state.dayKey = today;
            state.failedToday = 0;
            state.lastMilestone = 0;
        }
    }

    function vehicleJson(vehicle) {
        return {
            rfidTag: vehicle.rfidTag,
            vehicleNumber: vehicle.vehicleNumber,
            vehicleType: vehicle.vehicleType,
            ownerName: vehicle.ownerName,
            baseToll: vehicle.baseToll,
            baseTollText: money(vehicle.baseToll),
            fastagBalance: vehicle.fastagBalance,
            fastagBalanceText: money(vehicle.fastagBalance),
            fastagEnabled: vehicle.fastagEnabled,
            trips: vehicle.trips
        };
    }

    // ------------------------------------------------------------------
    // 4. the live event stream (stands in for the Java EventHub / SSE)
    // ------------------------------------------------------------------
    var streams = [];
    var recentFrames = [];

    function DemoEventSource(url) {
        this.url = url;
        this.listeners = {};
        this.closed = false;
        streams.push(this);
        var self = this;
        setTimeout(function () {
            self.emit('open', {});
            self.emit('hello', {
                clientId: 'demo-client-' + (streams.length),
                serverTime: new Date().toISOString(),
                pendingNotifications: unreadCount()
            });
        }, 40);
    }

    DemoEventSource.prototype.addEventListener = function (type, handler) {
        (this.listeners[type] = this.listeners[type] || []).push(handler);
    };

    DemoEventSource.prototype.close = function () {
        this.closed = true;
        var index = streams.indexOf(this);
        if (index >= 0) streams.splice(index, 1);
    };

    DemoEventSource.prototype.emit = function (type, payload) {
        var self = this;
        if (this.closed) return;
        (this.listeners[type] || []).forEach(function (handler) {
            try {
                handler({ data: JSON.stringify(payload) });
            } catch (error) {
                // a listener that throws must not stop the others
                if (window.console) window.console.warn('demo stream listener failed', error);
            }
        });
    };

    function broadcast(type, payload) {
        recentFrames.push({ type: type, payload: payload });
        if (recentFrames.length > 60) recentFrames.shift();
        streams.forEach(function (stream) { stream.emit(type, payload); });
    }

    // ------------------------------------------------------------------
    // 5. notifications and audit lines
    // ------------------------------------------------------------------
    function unreadCount() {
        return state.notifications.filter(function (n) { return !n.read; }).length;
    }

    function publish(level, title, message, category, audience) {
        var notification = {
            id: 'N' + (++state.notificationSequence),
            level: level,
            title: title,
            message: message,
            category: category || 'system',
            audience: audience || 'ALL',
            targetUsername: null,
            createdAt: new Date().toISOString(),
            read: false
        };
        state.notifications.unshift(notification);
        if (state.notifications.length > 300) state.notifications.pop();
        broadcast('notification', notification);
        audit('NOTIFY', 'demo', '[' + level.toUpperCase() + '] ' + title + ' : ' + message);
        return notification;
    }

    function audit(event, actor, details) {
        var stamp = new Date();
        var line = stamp.getFullYear() + '-' + pad(stamp.getMonth() + 1) + '-' + pad(stamp.getDate()) + ' '
            + clockText(stamp) + ' | ' + pad(event, 18) + ' | ' + pad(actor, 18) + ' | demo-mode        | ' + details;
        state.audit.unshift(line);
        if (state.audit.length > 400) state.audit.pop();
        state.auditSequence++;
    }

    function pad(text, width) {
        var value = String(text == null ? '' : text);
        while (value.length < width) value += ' ';
        return value.substring(0, width);
    }

    // ------------------------------------------------------------------
    // 6. the toll workflow (same rules as TollBooth + TollPaymentService)
    // ------------------------------------------------------------------
    function processPayment(operatorUsername, operatorDisplayName, rfidTag, paymentMode, cashTendered, rfidSource) {
        rolloverDayIfNeeded();
        var vehicle = findVehicleByTag(rfidTag);
        if (!vehicle) {
            return {
                status: 400,
                body: { error: 'RFID problem : No vehicle is registered with the RFID tag ' + rfidTag + '.' }
            };
        }

        var mode = String(paymentMode || 'Cash');
        if (/^fastag$/i.test(mode)) mode = 'FASTag';
        else if (/^upi$/i.test(mode)) mode = 'UPI';
        else mode = 'Cash';

        if (mode === 'FASTag' && !isFastagVehicle(vehicle.vehicleType)) {
            return refuse(vehicle, mode, cashTendered,
                'A ' + vehicle.vehicleType + ' (' + vehicle.vehicleNumber + ') does not carry a FASTag. '
                + 'Please pay by Cash or UPI.', operatorUsername);
        }
        if (mode === 'FASTag' && vehicle.fastagBalance <= 0) {
            return refuse(vehicle, mode, cashTendered,
                'The FASTag wallet of ' + vehicle.vehicleNumber + ' is empty. '
                + 'Please recharge it or pay by Cash / UPI.', operatorUsername);
        }

        var baseToll = vehicle.baseToll;
        var payable = (mode === 'FASTag')
            ? round2(baseToll - (baseToll * FASTAG_DISCOUNT_PERCENT / 100))
            : baseToll;

        if (mode === 'Cash' && cashTendered > 0 && cashTendered < payable) {
            return refuse(vehicle, mode, payable,
                'Cash given (' + money(cashTendered) + ') is less than the toll amount (' + money(payable)
                + '). Shortage : ' + money(payable - cashTendered), operatorUsername);
        }
        if (mode === 'FASTag' && vehicle.fastagBalance < payable) {
            return refuse(vehicle, mode, payable,
                'FASTag wallet balance (' + money(vehicle.fastagBalance) + ') is not enough for ' + money(payable)
                + '. Please recharge the FASTag.', operatorUsername);
        }

        // money collected
        if (mode === 'FASTag') {
            vehicle.fastagBalance = round2(vehicle.fastagBalance - payable);
            if (vehicle.fastagBalance <= 0) vehicle.fastagEnabled = false;
        }
        vehicle.trips++;

        var now = new Date();
        var payment = {
            receiptId: 'RCPT' + (++state.receiptSequence),
            transactionId: 'TXN' + (++state.paymentSequence),
            rfidTag: vehicle.rfidTag,
            vehicleNumber: vehicle.vehicleNumber,
            vehicleType: vehicle.vehicleType,
            baseToll: baseToll,
            paidAmount: payable,
            discount: round2(baseToll - payable),
            paidAmountText: money(payable),
            baseTollText: money(baseToll),
            paymentMode: mode,
            operator: operatorDisplayName,
            operatorUsername: operatorUsername,
            rfidSource: rfidSource || 'manual-entry',
            paidAt: now.toISOString(),
            paidTime: clockText(now),
            vehiclePassed: true
        };
        state.payments.unshift(payment);
        if (state.payments.length > 200) state.payments.pop();

        broadcast('payment', payment);
        publish('success', 'Toll collected - ' + mode,
            vehicle.vehicleNumber + ' (' + vehicle.vehicleType + ') paid ' + money(payable)
            + ' at ' + payment.paidTime + ' by ' + operatorDisplayName, 'payment', 'ALL');
        audit('PAYMENT', operatorUsername,
            payment.receiptId + ' ' + vehicle.vehicleNumber + ' (' + vehicle.vehicleType + ') '
            + money(payable) + ' by ' + mode + ' | tag ' + vehicle.rfidTag + ' | barrier OPENED and CLOSED');

        if (isFastagVehicle(vehicle.vehicleType) && vehicle.fastagBalance > 0
            && vehicle.fastagBalance < LOW_BALANCE_ALERT) {
            publish('danger', 'FASTag wallet almost empty',
                vehicle.vehicleNumber + ' has only ' + money(vehicle.fastagBalance)
                + ' left in the FASTag wallet. Recharge it soon.', 'fastag', 'ADMIN');
        }

        var collected = todayPayments().reduce(function (sum, p) { return sum + p.paidAmount; }, 0);
        var milestone = Math.floor(collected / MILESTONE_STEP);
        if (milestone > state.lastMilestone && milestone > 0) {
            state.lastMilestone = milestone;
            publish('info', 'Collection milestone reached',
                'The booth has collected ' + money(milestone * MILESTONE_STEP)
                + ' since the last milestone.', 'system', 'ALL');
        }

        broadcast('vehicle-changed', { vehicle: vehicleJson(vehicle) });

        return {
            status: 201,
            body: {
                ok: true,
                payment: payment,
                receipt: buildReceipt(payment)
            }
        };
    }

    function refuse(vehicle, mode, amount, reason, operatorUsername) {
        broadcast('payment-failed', {
            vehicleNumber: vehicle.vehicleNumber,
            vehicleType: vehicle.vehicleType,
            rfidTag: vehicle.rfidTag,
            paymentMode: mode,
            amount: amount,
            reason: reason,
            operator: operatorUsername,
            barrier: 'CLOSED'
        });
        publish('warning', 'Payment failed - barrier stayed closed',
            vehicle.vehicleNumber + ' (' + vehicle.vehicleType + ') : ' + reason, 'payment', 'ALL');
        audit('PAYMENT-FAILED', operatorUsername,
            vehicle.vehicleNumber + ' ' + mode + ' : ' + reason);
        return { status: 402, body: { error: 'Payment refused : ' + reason } };
    }

    function buildReceipt(payment) {
        var line = '========================================\n';
        return line
            + '          TRANSACTION RECEIPT           \n' + line
            + 'Receipt No     : ' + payment.receiptId + '\n'
            + 'Transaction ID : ' + payment.transactionId + '\n'
            + 'Vehicle Number : ' + payment.vehicleNumber + '\n'
            + 'Vehicle Type   : ' + payment.vehicleType + '\n'
            + 'RFID Tag       : ' + payment.rfidTag + '\n'
            + 'Toll Amount    : ' + payment.baseTollText + '\n'
            + (payment.discount > 0 ? 'FASTag Discount: -' + money(payment.discount) + '\n' : '')
            + 'Amount Paid    : ' + payment.paidAmountText + '\n'
            + 'Payment Mode   : ' + payment.paymentMode + '\n'
            + 'Operator       : ' + payment.operator + '\n'
            + 'Date & Time    : ' + payment.paidAt + '\n'
            + 'Status         : SUCCESS\n' + line
            + 'Barrier        : OPENED and CLOSED\n'
            + 'Vehicle Passed : YES\n' + line
            + 'DEMO MODE : this receipt was created in the browser,\n'
            + 'no Java backend is connected in this copy.\n' + line;
    }

    // ------------------------------------------------------------------
    // 7. dashboard numbers
    // ------------------------------------------------------------------
    function stats() {
        rolloverDayIfNeeded();
        var todays = todayPayments();
        var collected = todays.reduce(function (sum, p) { return sum + p.paidAmount; }, 0);
        var hourly = [];
        for (var hour = 0; hour < 24; hour++) {
            var list = todays.filter(function (p) { return new Date(p.paidAt).getHours() === hour; });
            hourly.push({
                hour: hour,
                label: (hour < 10 ? '0' : '') + hour + ':00',
                count: list.length,
                amount: round2(list.reduce(function (sum, p) { return sum + p.paidAmount; }, 0))
            });
        }
        var modes = [
            { mode: 'Cash', count: todays.filter(function (p) { return p.paymentMode === 'Cash'; }).length },
            { mode: 'UPI', count: todays.filter(function (p) { return p.paymentMode === 'UPI'; }).length },
            { mode: 'FASTag', count: todays.filter(function (p) { return p.paymentMode === 'FASTag'; }).length }
        ];
        var typeMap = {};
        todays.forEach(function (p) { typeMap[p.vehicleType] = (typeMap[p.vehicleType] || 0) + 1; });
        var types = Object.keys(typeMap).map(function (key) { return { type: key, count: typeMap[key] }; });

        return {
            today: new Date().toDateString(),
            todayCount: todays.length,
            todayCollection: round2(collected),
            todayCollectionText: money(collected),
            averageTicket: todays.length ? round2(collected / todays.length) : 0,
            averageTicketText: money(todays.length ? collected / todays.length : 0),
            todayFailed: state.failedToday,
            failedTotal: state.failedTotal,
            hourly: hourly,
            modes: modes,
            vehicleTypes: types,
            lastPayment: state.payments[0] || null,
            engineTransactions: state.payments.length,
            engineCollectionText: money(state.payments.reduce(function (sum, p) { return sum + p.paidAmount; }, 0)),
            barrierOpen: false,
            registeredVehicles: state.vehicles.length,
            rfidScans: state.rfidScans,
            simulatedPayments: state.autoPayments || 0,
            ledgerSize: state.payments.length,
            serverTime: new Date().toISOString(),
            tabsConnected: streams.length,
            userCount: state.users.length,
            simulatorOn: true
        };
    }

    // ------------------------------------------------------------------
    // 8. the API router : answers the same paths as the Java handlers
    // ------------------------------------------------------------------
    function requireSession() {
        if (!state.session) {
            return { status: 401, body: { error: 'Please log in to continue.' } };
        }
        return null;
    }

    function handleApi(path, query, method, body) {
        var session = state.session;
        var account;

        // ---- health and log in -----------------------------------------
        if (path === '/api/health' || path === '/health' || path === '/healthz' || path === '/api/ping') {
            return { status: 200, body: { status: 'UP', application: 'IoT Based Toll Booth Manager (demo mode)', vehicles: state.vehicles.length } };
        }

        if (path === '/api/login') {
            var username = String((body && body.username) || '').trim().toLowerCase();
            var password = String((body && body.password) || '');
            account = ACCOUNTS[username];
            var user = state.users.filter(function (u) { return u.username === username; })[0];

            // A disabled account is refused before the password is even checked,
            // exactly like UserStore.authenticate() in the Java server.
            if (user && !user.active) {
                publish('danger', 'Disabled account refused',
                    'The account "' + username + '" is disabled, so the sign in was refused.',
                    'security', 'ADMIN');
                return { status: 401, body: { error: 'This account is disabled.' } };
            }
            if (!account || account.password !== password) {
                var left = 4 - (state.loginFails || 0);
                state.loginFails = (state.loginFails || 0) + 1;
                publish('danger', 'Failed login attempt',
                    'Wrong password for "' + username + '" (demo mode, checked in the browser).',
                    'security', 'ADMIN');
                broadcast('login-failed', { username: username, attemptsLeft: Math.max(0, left), clientAddress: 'browser' });
                return { status: 401, body: { error: 'Invalid username or password. ' + Math.max(0, left) + ' attempt(s) left.' } };
            }
            state.loginFails = 0;
            var token = 'demo-' + Math.random().toString(36).slice(2) + Date.now().toString(36);
            state.session = {
                token: token,
                username: username,
                displayName: account.displayName,
                role: account.role,
                startedAt: new Date().toISOString()
            };
            state.users.forEach(function (u) {
                if (u.username === username) {
                    u.lastLoginAt = new Date().toISOString();
                    u.loginCount++;
                }
            });
            state.loginCount++;
            audit('LOGIN', username, 'Login successful (demo mode)');
            broadcast('login', { username: username, displayName: account.displayName, role: account.role, at: new Date().toISOString() });
            publish('info', 'User signed in',
                account.displayName + ' (' + account.role + ') signed in (demo mode).', 'security', 'ALL');
            var currentUser = state.users.filter(function (u) { return u.username === username; })[0];
            return {
                status: 200,
                body: {
                    token: token,
                    user: currentUser,
                    expiresInMinutes: 30,
                    onlineUsers: [account.displayName]
                }
            };
        }

        // ---- everything below needs a session ---------------------------
        var unauthorized = requireSession();
        if (unauthorized) return unauthorized;
        account = ACCOUNTS[session.username];
        var displayName = session.displayName;

        if (path === '/api/logout') {
            audit('LOGOUT', session.username, 'Session closed (demo mode)');
            broadcast('logout', { username: session.username, at: new Date().toISOString() });
            state.session = null;
            return { status: 200, body: { ok: true } };
        }

        if (path === '/api/session') {
            var me = state.users.filter(function (u) { return u.username === session.username; })[0];
            return {
                status: 200,
                body: {
                    user: me,
                    onlineUsers: [displayName],
                    tabCount: streams.length,
                    notifications: unreadCount(),
                    serverTime: new Date().toISOString(),
                    sessionStartedAt: session.startedAt
                }
            };
        }

        if (path === '/api/password') {
            var current = String((body && body.currentPassword) || '');
            var next = String((body && body.newPassword) || '');
            if (!account || account.password !== current) {
                return { status: 400, body: { error: 'The current password is not correct.' } };
            }
            if (next.length < 8 || !/[A-Za-z]/.test(next) || !/[0-9]/.test(next)) {
                return { status: 400, body: { error: 'The new password needs at least 8 characters with a letter and a digit.' } };
            }
            ACCOUNTS[session.username].password = next;
            audit('PASSWORD-CHANGED', session.username, 'Password changed (demo mode)');
            publish('success', 'Password changed',
                displayName + ' changed the password (demo mode, kept in this browser tab only).', 'security', 'ADMIN');
            return { status: 200, body: { ok: true, message: 'Password changed (demo mode : only for this browser session).' } };
        }

        if (path === '/api/payments' && method === 'GET') {
            var limit = Number(query.limit || 60);
            var search = (query.search || '').toLowerCase();
            var list = state.payments;
            if (search) {
                list = list.filter(function (p) {
                    return p.vehicleNumber.toLowerCase().indexOf(search) >= 0
                        || p.receiptId.toLowerCase().indexOf(search) >= 0
                        || p.transactionId.toLowerCase().indexOf(search) >= 0
                        || p.rfidTag.toLowerCase().indexOf(search) >= 0
                        || p.paymentMode.toLowerCase().indexOf(search) >= 0;
                });
            }
            return { status: 200, body: { payments: list.slice(0, limit), stats: stats() } };
        }

        if (path === '/api/payments' && method === 'POST') {
            var rfidTag = String((body && body.rfidTag) || '').toUpperCase();
            var mode = (body && body.paymentMode) || 'Cash';
            var cash = Number((body && body.amount) || 0);
            var result = processPayment(session.username, displayName, rfidTag, mode, cash,
                (body && body.rfidSource) || 'manual-entry');
            if (result.status === 402) {
                state.failedToday++;
                state.failedTotal++;
            }
            return result;
        }

        if (path === '/api/stats') {
            var payload = stats();
            payload.sessions = [{ username: session.username, displayName: displayName, role: session.role, clientAddress: 'browser', startedAt: session.startedAt, idleSeconds: 0 }];
            payload.clients = streams.map(function (stream, index) {
                return { clientId: 'demo-client-' + (index + 1), username: session.username, role: session.role, clientAddress: 'browser', connectedSeconds: 1, eventsSent: state.auditSequence };
            });
            return { status: 200, body: payload };
        }

        if (path === '/api/vehicles') {
            return {
                status: 200,
                body: {
                    vehicles: state.vehicles.map(vehicleJson),
                    count: state.vehicles.length,
                    fastagVehicleTypes: FASTAG_VEHICLE_TYPES,
                    rates: { Bike: 30, Car: 50, Bus: 100, Truck: 150, fastagDiscountPercent: FASTAG_DISCOUNT_PERCENT }
                }
            };
        }

        if (path === '/api/vehicles/fastag' && method === 'POST') {
            var target = findVehicleByTag(body && body.rfidTag);
            if (!target) return { status: 400, body: { error: 'Unknown RFID tag : ' + (body && body.rfidTag) + '.' } };
            if (!isFastagVehicle(target.vehicleType)) {
                return { status: 400, body: { error: 'A ' + target.vehicleType + ' does not carry a FASTag. Only trucks and buses do.' } };
            }
            var action = String((body && body.action) || 'recharge').toLowerCase();
            if (action === 'recharge') {
                var amount = Number((body && body.amount) || 0);
                if (amount <= 0 || amount > 100000) {
                    return { status: 400, body: { error: 'Please enter a recharge amount between 1 and 100000.' } };
                }
                target.fastagBalance = round2(target.fastagBalance + amount);
                target.fastagEnabled = true;
                publish('info', 'FASTag recharged',
                    target.vehicleNumber + ' wallet recharged by ' + money(amount) + '. New balance : ' + money(target.fastagBalance) + '.',
                    'fastag', 'ALL');
                audit('FASTAG-RECHARGE', session.username, target.vehicleNumber + ' +' + money(amount));
            } else if (action === 'enable' || action === 'disable') {
                if (session.role !== 'ADMIN') {
                    return { status: 403, body: { error: 'Only an administrator may switch a FASTag wallet on or off.' } };
                }
                target.fastagEnabled = (action === 'enable');
                audit('FASTAG-' + action.toUpperCase(), session.username, target.vehicleNumber);
            } else {
                return { status: 400, body: { error: 'Unknown FASTag action : ' + action } };
            }
            broadcast('vehicle-changed', { vehicle: vehicleJson(target) });
            return { status: 200, body: { ok: true, vehicle: vehicleJson(target) } };
        }

        if (path === '/api/rfid/simulate') {
            var picked = state.vehicles[Math.floor(Math.random() * state.vehicles.length)];
            state.rfidScans++;
            broadcast('rfid-scan', { rfidTag: picked.rfidTag, scannedBy: displayName, at: new Date().toISOString() });
            return {
                status: 200,
                body: {
                    rfidTag: picked.rfidTag,
                    vehicle: vehicleJson(picked),
                    suggestedPaymentMode: (picked.fastagEnabled && picked.fastagBalance >= picked.baseToll)
                        ? 'FASTag' : (Math.random() < 0.5 ? 'Cash' : 'UPI')
                }
            };
        }

        if (path === '/api/notifications') {
            var mine = state.notifications.filter(function (n) {
                return n.audience === 'ALL' || (n.audience === 'ADMIN' && session.role === 'ADMIN');
            });
            var limitN = Number(query.limit || 60);
            return {
                status: 200,
                body: {
                    notifications: mine.slice(0, limitN),
                    unread: unreadCount(),
                    totalCreated: state.notificationSequence - 1000
                }
            };
        }

        if (path === '/api/notifications/read' && method === 'POST') {
            var changed = 0;
            state.notifications.forEach(function (n) {
                if (!n.read) { n.read = true; changed++; }
            });
            broadcast('notifications-read', { count: changed });
            return { status: 200, body: { ok: true, markedRead: changed } };
        }

        if (path === '/api/notifications/clear' && method === 'POST') {
            if (session.role !== 'ADMIN') {
                return { status: 403, body: { error: 'Only an administrator may empty the notification centre.' } };
            }
            var removed = state.notifications.length;
            state.notifications = [];
            broadcast('notification-cleared', { by: displayName, removed: removed });
            return { status: 200, body: { ok: true, removed: removed } };
        }

        if (path === '/api/users') {
            if (session.role !== 'ADMIN') {
                return { status: 403, body: { error: 'Only an administrator may use this part of the system.' } };
            }
            if (method === 'GET') {
                return { status: 200, body: { users: state.users, count: state.users.length, minPasswordLength: 8 } };
            }
            var userAction = String((body && body.action) || 'create').toLowerCase();
            if (userAction === 'create') {
                var newUsername = String((body && body.username) || '').trim().toLowerCase();
                var newPassword = String((body && body.password) || '');
                if (!/^[a-z0-9][a-z0-9._-]{2,31}$/.test(newUsername)) {
                    return { status: 400, body: { error: 'Username must be 3 to 32 characters : small letters, digits, dot, dash or underscore.' } };
                }
                var exists = state.users.some(function (u) { return u.username === newUsername; });
                if (exists) {
                    return { status: 400, body: { error: 'The username ' + newUsername + ' already exists.' } };
                }
                if (newPassword.length < 8 || !/[A-Za-z]/.test(newPassword) || !/[0-9]/.test(newPassword)) {
                    return { status: 400, body: { error: 'Password must have at least 8 characters with a letter and a digit.' } };
                }
                var created = {
                    username: newUsername,
                    displayName: (body && body.displayName) || newUsername,
                    role: String((body && body.role) || 'OPERATOR').toUpperCase(),
                    active: true,
                    createdAt: new Date().toISOString(),
                    lastLoginAt: null,
                    loginCount: 0,
                    mustChangePassword: !!(body && body.mustChangePassword)
                };
                state.users.push(created);
                ACCOUNTS[newUsername] = { password: newPassword, role: created.role, displayName: created.displayName };
                audit('USER-CREATED', session.username, created.username + ' as ' + created.role);
                publish('info', 'New ' + created.role.toLowerCase() + ' account created',
                    displayName + ' created the account "' + created.username + '".', 'security', 'ALL');
                return { status: 201, body: { ok: true, user: created } };
            }
            var targetUser = state.users.filter(function (u) { return u.username === String((body && body.username) || '').toLowerCase(); })[0];
            if (!targetUser) return { status: 400, body: { error: 'Unknown user.' } };
            if (userAction === 'status') {
                targetUser.active = !!(body && body.active);
                audit((targetUser.active ? 'USER-ENABLED' : 'USER-DISABLED'), session.username, targetUser.username);
                return { status: 200, body: { ok: true, sessionsClosed: targetUser.active ? 0 : 1 } };
            }
            if (userAction === 'role') {
                targetUser.role = String((body && body.role) || 'OPERATOR').toUpperCase();
                if (ACCOUNTS[targetUser.username]) ACCOUNTS[targetUser.username].role = targetUser.role;
                audit('USER-ROLE', session.username, targetUser.username + ' -> ' + targetUser.role);
                return { status: 200, body: { ok: true } };
            }
            if (userAction === 'reset') {
                var resetPassword = String((body && body.password) || '');
                if (ACCOUNTS[targetUser.username]) ACCOUNTS[targetUser.username].password = resetPassword;
                targetUser.mustChangePassword = true;
                audit('PASSWORD-RESET', session.username, 'Password reset for ' + targetUser.username);
                return { status: 200, body: { ok: true } };
            }
            return { status: 400, body: { error: 'Unknown user action : ' + userAction } };
        }

        if (path === '/api/audit') {
            if (session.role !== 'ADMIN') {
                return { status: 403, body: { error: 'Only an administrator may use this part of the system.' } };
            }
            return {
                status: 200,
                body: { entries: state.audit.slice(0, Number(query.limit || 60)), file: 'demo mode : kept in the browser, not a file' }
            };
        }

        return { status: 404, body: { error: 'Unknown API end point (demo mode) : ' + path } };
    }

    // ------------------------------------------------------------------
    // 9. replace window.fetch
    // ------------------------------------------------------------------
    var realFetch = window.fetch ? window.fetch.bind(window) : null;

    /**
     * Builds a fetch style answer. A real browser has the Response class, but
     * some test browsers do not, so a small object with the same three members
     * the page uses (status, ok, text) is returned in that case.
     */
    function makeResponse(status, payload) {
        var text = JSON.stringify(payload);
        if (typeof window.Response === 'function') {
            return new window.Response(text, {
                status: status,
                headers: { 'Content-Type': 'application/json' }
            });
        }
        return {
            ok: status >= 200 && status < 300,
            status: status,
            statusText: String(status),
            headers: { get: function (name) { return /content-type/i.test(name) ? 'application/json' : null; } },
            text: function () { return Promise.resolve(text); },
            json: function () { return Promise.resolve(JSON.parse(text)); }
        };
    }

    window.fetch = function (input, options) {
        var url = (typeof input === 'string') ? input : (input && input.url) || '';
        var settings = options || {};

        // Only the API calls of this page are answered locally.
        if (url.indexOf('/api/') !== 0 && url.indexOf('/health') !== 0) {
            return realFetch ? realFetch(input, options) : Promise.reject(new Error('network unavailable in demo mode'));
        }

        var parts = url.split('?');
        var path = parts[0];
        var query = {};
        if (parts[1]) {
            parts[1].split('&').forEach(function (pair) {
                var bits = pair.split('=');
                if (bits[0]) query[decodeURIComponent(bits[0])] = decodeURIComponent(bits[1] || '');
            });
        }

        var body = null;
        if (settings.body) {
            try { body = JSON.parse(settings.body); } catch (error) { body = null; }
        }
        var method = String(settings.method || 'GET').toUpperCase();

        // The delay makes the spinners behave like a real server.
        return new Promise(function (resolve) {
            setTimeout(function () {
                var answer;
                try {
                    answer = handleApi(path, query, method, body);
                } catch (error) {
                    answer = { status: 500, body: { error: 'Demo backend error : ' + error.message } };
                }
                resolve(makeResponse(answer.status, answer.body));
            }, 120);
        });
    };

    // ------------------------------------------------------------------
    // 10. replace window.EventSource
    // ------------------------------------------------------------------
    window.EventSource = DemoEventSource;

    // ------------------------------------------------------------------
    // 11. automatic lane : one vehicle every few seconds (like the Java
    //     simulator), so the dashboard is alive when somebody opens the link
    // ------------------------------------------------------------------
    function automaticVehicle() {
        var vehicle = state.vehicles[Math.floor(Math.random() * state.vehicles.length)];
        var canFastag = vehicle.fastagEnabled && isFastagVehicle(vehicle.vehicleType)
            && vehicle.fastagBalance >= vehicle.baseToll;
        var modes = canFastag ? ['FASTag', 'Cash', 'UPI'] : ['Cash', 'UPI'];
        var mode = modes[Math.floor(Math.random() * modes.length)];
        state.rfidScans++;
        state.autoPayments = (state.autoPayments || 0) + 1;
        processPayment('SYSTEM-AUTO', 'Automatic lane reader', vehicle.rfidTag, mode, 0, 'reader-simulation');
    }

    setInterval(function () {
        if (document.visibilityState === 'hidden') return;   // save battery
        automaticVehicle();
    }, 8000);

    // A few payments right at the start, so the charts are not empty.
    setTimeout(automaticVehicle, 1500);
    setTimeout(automaticVehicle, 3000);

    // ------------------------------------------------------------------
    // 12. make it obvious that this copy is a demo
    // ------------------------------------------------------------------
    function addDemoBanner() {
        var style = document.createElement('style');
        style.textContent = ''
            + '.demo-banner{position:fixed;left:50%;transform:translateX(-50%);bottom:14px;z-index:200;'
            + 'background:#1b2540;border:1px solid rgba(251,191,36,.55);color:#ffe9b8;padding:9px 16px;'
            + 'border-radius:999px;font:600 12.5px/1.4 "Segoe UI",Roboto,Arial,sans-serif;'
            + 'box-shadow:0 10px 26px rgba(3,8,20,.5);max-width:92vw;text-align:center}'
            + '.demo-banner b{color:#fbbf24}';
        document.head.appendChild(style);

        var banner = document.createElement('div');
        banner.className = 'demo-banner';
        banner.innerHTML = '<b>DEMO MODE</b> &middot; static Firebase Hosting copy &middot; no Java backend connected '
            + '&middot; sign in with <b>admin / Admin@123</b>';
        document.body.appendChild(banner);

        var hint = document.getElementById('loginHint');
        if (hint) {
            hint.innerHTML = 'Demo mode : the login is checked inside the browser with <strong>admin / Admin@123</strong> '
                + 'or <strong>operator / Operator@123</strong>. The real login system (PBKDF2 password hashes, session '
                + 'tokens, audit file) runs in the Java server &mdash; see <em>docs/DEPLOY_PUBLIC.md</em>, option A.';
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', addDemoBanner);
    } else {
        addDemoBanner();
    }

    if (window.console) {
        window.console.info('DEMO MODE active : the API calls are answered inside the browser (demo-backend.js).');
    }
})();
