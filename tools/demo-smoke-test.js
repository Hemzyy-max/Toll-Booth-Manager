/* ============================================================================
   demo-smoke-test.js - checks the STATIC DEMO build (deploy/firebase/public-demo)
   ----------------------------------------------------------------------------
   It loads the page in a simulated browser (jsdom) exactly like a phone would,
   but there is no Java server : demo-backend.js answers every /api/** request
   inside the browser. The test proves that

       * the page starts with no JavaScript error,
       * the demo warning is on the screen,
       * the normal login form works (admin / Admin@123),
       * the dashboard, the vehicle table and the notification centre are filled,
       * a toll can really be paid through the page.

   How to use it (Node.js must be installed) :
       npm install jsdom
       python3 -m http.server 4010 --directory deploy/firebase/public-demo
       node tools/demo-smoke-test.js                     # default : 4010
       node tools/demo-smoke-test.js http://127.0.0.1:5000

   This file is a development helper, it is not part of the application.
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

const BASE = (process.argv[2] || 'http://127.0.0.1:4010').replace(/\/$/, '');
const problems = [];
const checks = [];

const virtualConsole = new VirtualConsole();
virtualConsole.on('jsdomError', (error) => problems.push('jsdomError: ' + error.message));
virtualConsole.on('error', (message) => problems.push('console.error: ' + message));

function check(name, condition, extra) {
  checks.push({ name, ok: !!condition, extra: extra || '' });
  console.log((condition ? 'PASS  ' : 'FAIL  ') + name + (condition ? '' : '   ' + (extra || '')));
}

function wait(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function waitFor(getValue, description, timeoutMs) {
  const limit = Date.now() + (timeoutMs || 6000);
  while (Date.now() < limit) {
    const value = getValue();
    if (value) return value;
    await wait(100);
  }
  throw new Error('timed out while waiting for ' + description);
}

(async () => {
  console.log('Static demo build : ' + BASE + '\n');

  const dom = await JSDOM.fromURL(BASE + '/', {
    runScripts: 'dangerously',
    resources: 'usable',
    pretendToBeVisual: true,
    virtualConsole,
    beforeParse(window) {
      window.print = () => {};
      window.prompt = () => null;
      window.alert = () => {};
    }
  });

  const window = dom.window;
  const document = window.document;

  // The page loads its scripts after the document, so wait for the demo
  // backend to install itself before using the page.
  await waitFor(() => typeof window.fetch === 'function' && typeof window.EventSource === 'function',
    'demo-backend.js to install window.fetch and window.EventSource');
  check('demo backend replaced window.fetch', typeof window.fetch === 'function');
  check('demo backend replaced window.EventSource', typeof window.EventSource === 'function');

  const health = await window.fetch('/api/health').then((r) => r.json());
  check('GET /api/health answers UP', health.status === 'UP', JSON.stringify(health));

  const banner = await waitFor(
    () => Array.from(document.querySelectorAll('body *')).find(
      (element) => /DEMO MODE/i.test(element.textContent || '') && element.children.length === 0),
    'the DEMO MODE banner');
  check('the demo warning is displayed on the page', !!banner, banner ? '' : 'no banner found');

  // ---- sign in with the ordinary login form -----------------------------
  document.getElementById('username').value = 'admin';
  document.getElementById('password').value = 'Admin@123';
  document.getElementById('loginForm').dispatchEvent(
    new window.Event('submit', { bubbles: true, cancelable: true }));

  await waitFor(() => !document.getElementById('appView')?.hidden, 'the dashboard');
  check('login through the real form opened the dashboard',
    !document.getElementById('appView').hidden);
  check('the signed in user is shown',
    /Admin/i.test(document.getElementById('userName')?.textContent || ''),
    document.getElementById('userName')?.textContent || '(empty)');

  // ---- the data of the demo backend reaches the screen -------------------
  const navTo = (page) => document.querySelector('.nav-item[data-page="' + page + '"]').click();
  navTo('vehicles');
  await waitFor(() => document.getElementById('vehicleGrid')?.children.length, 'the vehicle cards');
  check('the eight vehicles are listed',
    document.getElementById('vehicleGrid').children.length === 8,
    'cards = ' + document.getElementById('vehicleGrid').children.length);
  navTo('dashboard');

  await waitFor(() => /[0-9]/.test(document.getElementById('kpiAmount')?.textContent || ''), 'the KPI cards');
  check('the collection KPI is filled',
    document.getElementById('kpiAmount').textContent.trim().length > 0,
    document.getElementById('kpiAmount').textContent);

  // ---- pay one toll through the form ------------------------------------
  const rfidSelect = document.getElementById('payRfid');
  await waitFor(() => rfidSelect.options.length > 0, 'the RFID list');
  rfidSelect.value = 'RFID1003';                       // Truck, FASTag, 150 - 10%
  rfidSelect.dispatchEvent(new window.Event('change', { bubbles: true }));

  const fastagRadio = document.querySelector('#payMode input[value="FASTag"]');
  fastagRadio.checked = true;
  fastagRadio.dispatchEvent(new window.Event('change', { bubbles: true }));

  document.getElementById('payForm').dispatchEvent(
    new window.Event('submit', { bubbles: true, cancelable: true }));

  await waitFor(() => document.getElementById('paySuccess')?.textContent
    && !document.getElementById('paySuccess').hidden, 'the payment receipt');
  check('a FASTag toll payment succeeded',
    /SUCCESS|paid|TXN/i.test(document.getElementById('paySuccess').textContent),
    document.getElementById('paySuccess').textContent);

  navTo('notifications');
  await waitFor(() => document.getElementById('notifPageList')?.children.length,
    'the notification centre');
  check('the notification centre received messages',
    document.getElementById('notifPageList').children.length > 0,
    'notifications = ' + document.getElementById('notifPageList').children.length);

  const payments = await window.fetch('/api/payments').then((r) => r.json());
  check('the ledger has the payment', payments.payments.length >= 1,
    'ledger = ' + payments.payments.length);
  check('the stats endpoint matches the ledger',
    payments.stats.todayCount === payments.payments.filter(
      (p) => new Date(p.paidAt).toDateString() === new Date().toDateString()).length,
    'todayCount = ' + payments.stats.todayCount);

  check('no JavaScript error happened', problems.length === 0, problems.join(' | '));

  const failed = checks.filter((item) => !item.ok).length;
  console.log('\n' + (checks.length - failed) + '/' + checks.length + ' demo checks passed');
  dom.window.close();
  process.exit(failed === 0 ? 0 : 1);
})().catch((error) => {
  console.error('\nTEST CRASHED : ' + error.message);
  if (problems.length) console.error(problems.join('\n'));
  process.exit(1);
});
