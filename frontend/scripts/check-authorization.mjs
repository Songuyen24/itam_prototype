import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

// Run against Vite; Playwright may be supplied by the workspace runtime without a project dependency.
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.ITAM_PLAYWRIGHT_MODULE || 'playwright');
const baseUrl = process.env.ITAM_FRONTEND_URL || 'http://127.0.0.1:5173';
const output = new URL('../../.tmp_authorization/', import.meta.url);
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: process.env.ITAM_BROWSER_CHANNEL || 'msedge' });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const accounts = [
  { id: 1, email: 'admin@itam.example', fullName: 'Admin Test', role: 'ADMIN' },
  { id: 2, email: 'it01@itam.example', fullName: 'IT Test', role: 'IT_STAFF' },
  { id: 3, email: 'pur01@itam.example', fullName: 'Purchasing Test', role: 'PUR_STAFF' },
  { id: 4, email: 'user01@itam.example', fullName: 'User One', role: 'USER' },
  { id: 5, email: 'user02@itam.example', fullName: 'User Two', role: 'USER' },
];
const sessions = new Map();
const calls = [];
const errors = [];
let sequence = 0;
let holdInventory;
let holdLogin;
const deferred = () => {
  let resolve;
  const promise = new Promise(r => { resolve = r; });
  return { promise, resolve };
};
const pageData = rows => ({ content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1, last: true });
const asset = user => ({
  assetId: user.id * 10, assetTag: `ASSET-${user.role}-${user.id}`, name: `Device for ${user.fullName}`,
  assignedToUserId: user.id, assignedToFullName: user.fullName, typeName: 'Laptop', statusCode: 'IN_USE', statusName: 'In Use',
});
await context.addInitScript(() => { localStorage.setItem('itam_language', 'en'); });
await context.route('**/api/v1/**', async route => {
  const request = route.request();
  const url = new URL(request.url());
  const path = url.pathname.replace('/api', '');
  const token = request.headers().authorization?.replace('Bearer ', '');
  const user = sessions.get(token);
  calls.push({ path, user: user?.id, role: user?.role });
  const send = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ success: status === 200, data }) });
  if (path === '/v1/auth/login') {
    if (holdLogin) {
      const gate = holdLogin;
      holdLogin = undefined;
      gate.started.resolve();
      await gate.release.promise;
    }
    const target = accounts.find(a => a.email === request.postDataJSON().email);
    if (!target) return send(null, 401);
    const nextToken = `session-${++sequence}`;
    sessions.set(nextToken, target);
    return send({ token: nextToken, type: 'Bearer', user: target });
  }
  if (!user) return send(null, 401);
  if (path === '/v1/auth/me') return send({ ...user, role: undefined, roleCode: user.role });
  if (path === '/v1/auth/logout') return send(null);
  const manager = ['ADMIN', 'IT_STAFF'].includes(user.role);
  if (path === '/v1/assets') {
    if (!manager) return send(null, 403);
    if (holdInventory) {
      const gate = holdInventory;
      holdInventory = undefined;
      gate.started.resolve();
      await gate.release.promise;
      await send(pageData([{ ...asset(user), assetTag: 'STALE-PRIVATE-ASSET' }]));
      gate.finished.resolve();
      return;
    }
    return send(pageData([asset(user)]));
  }
  if (path === '/v1/users/me/assets') return send(pageData([asset(user)]));
  if (/^\/v1\/assets\/\d+$/.test(path)) return send(asset(user));
  return send(pageData([]), manager ? 200 : 403);
});
const page = await context.newPage();
page.on('pageerror', error => errors.push(error.message));
const switchTo = async role => {
  await page.locator('.role-switcher-trigger').click();
  await page.getByRole('menuitem').filter({ hasText: accounts.find(a => a.role === role).email }).click();
};
const expectOwn = async id => {
  await page.waitForURL('**/my-assets');
  await page.getByText(`ASSET-USER-${id}`, { exact: true }).waitFor();
  assert.equal(await page.locator('a[href="/assets"]').count(), 0);
  assert.equal(await page.locator('a[href="/catalogs"]').count(), 0);
  assert.equal(await page.getByRole('button', { name: /Add Asset|Import Excel/ }).count(), 0);
};
try {
  await page.goto(`${baseUrl}/catalogs`);
  await page.waitForURL('**/login');
  await page.locator('.quick-login-tile').filter({ hasText: 'admin@itam.example' }).click();
  await page.waitForURL('**/assets');
  await page.getByText('ASSET-ADMIN-1', { exact: true }).waitFor();
  assert.equal(await page.locator('a[href="/catalogs"]').count(), 1);
  await page.getByText('ASSET-ADMIN-1', { exact: true }).click();
  await page.locator('.modal-backdrop').waitFor();
  await page.locator('.btn-close').click();

  await switchTo('IT_STAFF');
  await page.getByText('ASSET-IT_STAFF-2', { exact: true }).waitFor();
  assert.equal(await page.getByText('ASSET-ADMIN-1', { exact: true }).count(), 0);
  assert.equal(await page.locator('.modal-backdrop').count(), 0);
  await page.locator('a[href="/catalogs"]').click();
  await page.getByRole('heading', { level: 1 }).waitFor();
  assert.equal(await page.getByRole('button', { name: /^\+ Add/ }).count(), 1);

  const inventoryGate = { started: deferred(), release: deferred(), finished: deferred() };
  holdInventory = inventoryGate;
  await page.locator('a[href="/assets"]').click();
  await inventoryGate.started.promise;
  const loginGate = { started: deferred(), release: deferred() };
  holdLogin = loginGate;
  await switchTo('USER');
  await loginGate.started.promise;
  await page.getByRole('status').waitFor();
  assert.equal(await page.locator('.role-switcher-trigger').count(), 0);
  assert.equal(await page.locator('.data-table').count(), 0);
  loginGate.release.resolve();
  await expectOwn(4);
  inventoryGate.release.resolve();
  await inventoryGate.finished.promise;
  await page.evaluate(() => new Promise(requestAnimationFrame));
  assert.equal(await page.getByText('STALE-PRIVATE-ASSET', { exact: true }).count(), 0);
  await page.getByText('ASSET-USER-4', { exact: true }).click();
  await page.locator('.modal-backdrop').waitFor();
  assert.equal(await page.locator('.modal-footer .btn-primary').count(), 0);
  await page.locator('.btn-close').click();

  for (const path of ['/assets?assignedTo=1', '/catalogs', '/users', '/assets/10']) {
    await page.goto(`${baseUrl}${path}`);
    await expectOwn(4);
  }
  const userCalls = calls.filter(call => call.role === 'USER');
  assert.ok(userCalls.every(call => ['/v1/auth/me', '/v1/users/me/assets', '/v1/assets/40'].includes(call.path)));

  await page.evaluate(() => {
    const forged = JSON.parse(localStorage.getItem('itam_auth_user'));
    localStorage.setItem('itam_auth_user', JSON.stringify({ ...forged, role: 'ADMIN' }));
  });
  await page.goto(`${baseUrl}/catalogs`);
  await expectOwn(4);
  await page.screenshot({ path: fileURLToPath(new URL('user-desktop.png', output)), fullPage: true });

  const secondTab = await context.newPage();
  await secondTab.goto(`${baseUrl}/account`);
  sessions.set('user-two-token', accounts[4]);
  await secondTab.evaluate(user => {
    localStorage.setItem('itam_auth_user', JSON.stringify(user));
    localStorage.setItem('itam_auth_token', 'user-two-token');
  }, accounts[4]);
  await expectOwn(5);
  assert.equal(await page.getByText('ASSET-USER-4', { exact: true }).count(), 0);
  await secondTab.close();

  await switchTo('PUR_STAFF');
  await page.waitForURL('**/account');
  await page.getByText('pur01@itam.example', { exact: true }).waitFor();
  for (const path of ['/assets', '/catalogs', '/users', '/my-assets']) {
    await page.goto(`${baseUrl}${path}`);
    await page.waitForURL('**/account');
    await page.getByText('pur01@itam.example', { exact: true }).waitFor();
  }
  assert.equal(await page.locator('.data-table').count(), 0);
  assert.ok(calls.filter(call => call.role === 'PUR_STAFF').every(call => call.path.startsWith('/v1/auth/')));
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: fileURLToPath(new URL('purchasing-mobile.png', output)), fullPage: true });
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
  await switchTo('USER');
  await expectOwn(4);
  await page.screenshot({ path: fileURLToPath(new URL('user-mobile.png', output)), fullPage: true });
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
  assert.deepEqual(errors, []);
  console.log('PASS: 4 role logins/switches, direct routes, restored-role validation, account isolation, pending switch and stale response, cross-tab USER switch, read-only detail, PUR API isolation.');
  console.log('Screenshots: .tmp_authorization/user-desktop.png and purchasing-mobile.png');
} finally {
  await browser.close();
}
