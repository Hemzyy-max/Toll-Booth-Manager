/* ============================================================================
   ui-smoke-test.js - optional browser level smoke test of the web page
   ----------------------------------------------------------------------------
   It loads the running application in a simulated browser (jsdom), signs in,
   collects a real toll payment, and checks the receipt window, the tables, the
   charts, the notification centre, the audit log and the user form.

   It is NOT needed to run the application. It is only a development helper.

   How to use it (Node.js must be installed) :
       npm install jsdom
       java -cp out tollbooth.web.WebLauncher --port 3000   # in another terminal
       node tools/ui-smoke-test.js
   ============================================================================ */
let jsdomModule = null;
try {
  jsdomModule = require('jsdom');
} catch (error) {
  try {
    jsdomModule = require(process.env.JSDOM_PATH || '/tmp/uicheck/node_modules/jsdom');
  } catch (secondError) {
    console.error('jsdom is not installed. Run :  npm install jsdom');
    process.exit(1);
  }
}
const { JSDOM, VirtualConsole } = jsdomModule;
const BASE = 'http://127.0.0.1:3000';

const problems = [];
const virtualConsole = new VirtualConsole();
virtualConsole.on('jsdomError', (e) => problems.push('jsdomError: ' + e.message));
virtualConsole.on('error', (m) => problems.push('console.error: ' + m));

(async () => {
  const dom = await JSDOM.fromURL(BASE + '/', {
    runScripts: 'dangerously',
    resources: 'usable',
    pretendToBeVisual: true,
    virtualConsole,
    beforeParse(window) {
      // Polyfill what a browser has but jsdom does not need for the test.
      window.fetch = (path, options) => fetch(new URL(path, BASE), options);
      window.EventSource = class {
        constructor(url) { this.url = url; this.listeners = {}; }
        addEventListener(name, fn) { (this.listeners[name] = this.listeners[name] || []).push(fn); }
        close() {}
        emit(name, data) { (this.listeners[name] || []).forEach((fn) => fn({ data: JSON.stringify(data) })); }
      };
      window.print = () => {};
      window.prompt = () => null;
    }
  });

  const { window } = dom;
  const $ = (id) => window.document.getElementById(id);
  const wait = (ms) => new Promise((r) => setTimeout(r, ms));

  // wait for app.js to boot (DOMContentLoaded handler)
  await wait(1200);
  console.log('1. login view visible :', !$('loginView').hidden && $('appView').hidden);

  // --- wrong password ------------------------------------------------
  $('username').value = 'admin';
  $('password').value = 'nope';
  $('loginForm').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));
  await wait(1200);
  console.log('2. wrong password message :', JSON.stringify($('loginError').textContent));

  // --- correct password ----------------------------------------------
  $('password').value = 'Admin@123';
  $('loginForm').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));
  await wait(2500);
  console.log('3. app visible :', !$('appView').hidden, '| user chip :', $('userName').textContent, '/', $('userRole').textContent);
  console.log('4. KPI today :', $('kpiToday').textContent, '| collected :', $('kpiAmount').textContent, '| vehicles :', $('kpiVehicles').textContent, '| barrier :', $('barrierState').textContent);
  console.log('5. hourly bars rendered :', $('chartHourly').children.length, '| doughnut circles :', $('chartModes').children.length);
  console.log('6. recent payments rows :', $('recentPaymentsBody').children.length);
  console.log('7. vehicle options in the payment form :', $('payRfid').options.length);
  console.log("8. admin-only menu visible :", !window.document.getElementById('navList').querySelector('[data-page="audit"]').hidden);

  // --- navigate to payments and exercise the preview -------------------
  window.document.querySelector('[data-page="payments"]').click();
  await wait(800);
  $('payRfid').value = 'RFID1003';
  $('payRfid').dispatchEvent(new window.Event('change', { bubbles: true }));
  await wait(300);
  console.log('9. toll preview (truck, cash) :', $('pvVehicle').textContent, '| base', $('pvBase').textContent, '| payable', $('pvPayable').textContent);
  const fastagRadio = window.document.querySelector('#payMode input[value="FASTag"]');
  fastagRadio.checked = true;
  fastagRadio.dispatchEvent(new window.Event('change', { bubbles: true }));
  await wait(300);
  console.log('10. toll preview (truck, FASTag) :', $('pvPayable').textContent, '| discount', $('pvDiscount').textContent);

  // --- really pay over HTTP -------------------------------------------
  $('payAmount').value = '200';
  $('payForm').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));
  await wait(2500);
  console.log('11. success message :', JSON.stringify($('paySuccess').textContent || '(hidden)'));
  console.log('12. receipt modal open :', !$('receiptModal').hidden, '| first receipt line :', $('receiptText').textContent.split('\n')[1].trim());
  console.log('13. toast count :', $('toastStack').children.length, '| toasts :', Array.from($('toastStack').children).map((t) => t.querySelector('strong').textContent).join(' | '));

  // --- live event pushed by the server while the page is open ----------
  const stream = window.__stream;
  console.log('14. payments table rows after paying :', $('paymentsTableBody').children.length);

  // --- a CAR must not be allowed to use FASTag -------------------------
  window.document.querySelector('[data-page="payments"]').click();
  $('payRfid').value = 'RFID1001';
  $('payRfid').dispatchEvent(new window.Event('change', { bubbles: true }));
  await wait(300);
  console.log('15. FASTag disabled for a car :', fastagRadio.disabled);

  // --- vehicles page ---------------------------------------------------
  window.document.querySelector('[data-page="vehicles"]').click();
  await wait(900);
  console.log('16. vehicle cards :', $('vehicleGrid').children.length, '| recharge buttons :', $('vehicleGrid').querySelectorAll('[data-recharge]').length);

  // --- notifications page ---------------------------------------------
  window.document.querySelector('[data-page="notifications"]').click();
  await wait(900);
  console.log('17. notification items :', $('notifPageList').children.length, '| meta :', $('notifMeta').textContent);

  // --- audit (admin) ----------------------------------------------------
  window.document.querySelector('[data-page="audit"]').click();
  await wait(900);
  console.log('18. audit lines :', $('auditList').children.length);

  // --- users page + create account via the form -------------------------
  window.document.querySelector('[data-page="users"]').click();
  await wait(900);
  console.log('19. user rows :', $('usersTableBody').children.length);
  $('newUsername').value = 'uitest';
  $('newDisplayName').value = 'UI Test Operator';
  $('newPassword').value = 'UiTest@2026';
  $('newRole').value = 'OPERATOR';
  $('createUserForm').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));
  await wait(1500);
  console.log('20. create user result :', JSON.stringify($('userSuccess').textContent || $('userError').textContent));

  console.log('\nJS PROBLEMS DETECTED:', problems.length ? problems : 'none');
  dom.window.close();
  process.exit(0);
})().catch((e) => { console.error('TEST CRASHED:', e); process.exit(1); });
