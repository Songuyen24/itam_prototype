import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir } from 'node:fs/promises';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.ITAM_PLAYWRIGHT_MODULE || 'playwright');
const output = new URL('../../.tmp_handover/', import.meta.url);
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: process.env.ITAM_BROWSER_CHANNEL || 'msedge' });
const base = process.env.ITAM_FRONTEND_URL || 'http://127.0.0.1:5173';
const pageData = content => ({ content, totalElements: content.length, totalPages: content.length ? 1 : 0, pageNumber: 0 });
const candidates = [
  { assetId: 1, assetTag: 'LAP-001', name: 'Demo laptop', category: 'DEVICE', availableSeats: null },
  { assetId: 4, assetTag: 'OFFICE-001', name: 'Office Per-User', category: 'LICENSE', availableSeats: 10 },
];
const errors = [];
try {
  for (const [language, width] of [['en', 1440], ['vi', 390]]) {
    const context = await browser.newContext({ viewport: { width, height: 1000 } });
    const user = { id: 2, fullName: 'Demo IT', email: 'it@itam.example', role: 'IT_STAFF' };
    await context.addInitScript(({ user, language }) => {
      localStorage.setItem('itam_auth_token', 't15-ui-demo'); localStorage.setItem('itam_auth_user', JSON.stringify(user)); localStorage.setItem('itam_language', language);
    }, { user, language });
    let failCompletion = true, completed = null, previews = 0, completions = 0;
    const snapshot = { transactionId: null, transactionCode: null, completedAt: null, recipientUserId: 7, recipientName: 'Demo User', recipientEmail: 'demo@example.com', destinationLocationId: 2, destinationLocationName: 'Demo office', handoverDate: '2026-09-10', notes: '', fingerprint: 'bundle-reviewed', lines: [
      { assetId: 1, assetTag: 'LAP-001', name: 'Demo laptop', category: 'DEVICE', parentAssetId: null, seats: 0, allocations: [], details: { actualRam: '32 GB' } },
      { assetId: 2, assetTag: 'RAM-001', name: 'Attached RAM', category: 'COMPONENT', parentAssetId: 1, seats: 0, allocations: [], details: {} },
      { assetId: 3, assetTag: 'OEM-001', name: 'Reserved OEM', category: 'LICENSE', parentAssetId: null, seats: 1, allocations: [{ allocationId: 88, deviceId: 1, seats: 1, assignmentType: 'OEM' }], details: {} },
      { assetId: 4, assetTag: 'OFFICE-001', name: 'Office Per-User', category: 'LICENSE', parentAssetId: null, seats: 1, allocations: [{ allocationId: null, deviceId: 1, seats: 1, assignmentType: 'PER_USER' }], details: {} },
    ] };
    await context.route('**/api/v1/**', async route => {
      const url = new URL(route.request().url()), path = url.pathname.replace('/api', '');
      const send = (data, status = 200, message = 'Success') => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ success: status < 400, message, data }) });
      if (path === '/v1/auth/me') return send({ ...user, roleCode: user.role });
      if (path === '/v1/users') return send(pageData([{ id: 7, fullName: 'Demo User', email: 'demo@example.com', accountStatus: 'ACTIVE' }]));
      if (path === '/v1/locations') return send(pageData([{ locationId: 2, name: 'Demo office', isActive: true }]));
      if (path === '/v1/handovers/candidates') return send(pageData(url.searchParams.get('keyword') === 'nothing' ? [] : candidates));
      if (path === '/v1/handovers/preview') {
        previews++;
        const request = route.request().postDataJSON();
        assert.deepEqual(request.assetIds, [1]); assert.equal(request.recipientUserId, 7);
        assert.deepEqual(request.licenses, [{ assetId: 4, seats: 1, deviceId: 1 }]);
        return send(snapshot);
      }
      if (path === '/v1/handovers' && route.request().method() === 'POST') {
        completions++;
        assert.equal(route.request().postDataJSON().expectedFingerprint, 'bundle-reviewed');
        if (failCompletion) return send(null, 409, 'Bundle changed. Preview again.');
        completed = { ...snapshot, transactionId: 10, transactionCode: 'HO-DEMO-10', completedAt: '2026-09-10T02:00:00Z' };
        return send(completed, 201);
      }
      if (path === '/v1/transactions') return send(pageData(completed ? [{ transactionId: 10, transactionCode: 'HO-DEMO-10', type: 'HANDOVER', status: 'COMPLETED', createdAt: '2026-09-10T02:00:00Z' }] : []));
      if (path === '/v1/handovers/10') return send(completed);
      return send(null, 403);
    });
    const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message));
    await page.goto(`${base}/handovers`);
    const assetSearch = page.locator('.handover-picker input').first();
    await assetSearch.fill('nothing'); await page.getByText(language === 'en' ? 'No results.' : 'Không có kết quả.').waitFor();
    await assetSearch.fill('');
    await page.getByRole('button', { name: /LAP-001/ }).click();
    await page.getByRole('button', { name: /OFFICE-001/ }).click();
    await page.getByRole('button', { name: /Demo User/ }).click();
    await page.getByRole('button', { name: 'Demo office' }).click();
    await page.locator('.handover-selected select').selectOption('1');
    await page.screenshot({ path: new URL(`form-${language}-${width}.png`, output).pathname.replace(/^\/(.:)/, '$1'), fullPage: true });
    const preview = page.getByRole('button', { name: language === 'en' ? 'Preview asset bundle' : 'Xem trước bộ tài sản', exact: true });
    await preview.click(); await page.getByRole('dialog').waitFor();
    await page.getByText('OEM-001', { exact: true }).waitFor(); await page.getByText('RAM-001', { exact: true }).waitFor();
    await page.screenshot({ path: new URL(`preview-${language}-${width}.png`, output).pathname.replace(/^\/(.:)/, '$1'), fullPage: true });
    const complete = () => page.getByRole('button', { name: language === 'en' ? 'Complete handover' : 'Hoàn tất bàn giao', exact: true });
    await complete().click(); await page.getByRole('alert').filter({ hasText: 'Bundle changed' }).waitFor();
    assert.equal(completions, 1); assert.equal(await page.getByRole('dialog').count(), 0);
    failCompletion = false;
    await preview.click(); await complete().click(); await page.getByRole('heading', { name: 'HO-DEMO-10' }).waitFor();
    assert.equal(previews, 2); assert.equal(completions, 2);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    await page.screenshot({ path: new URL(`completed-${language}-${width}.png`, output).pathname.replace(/^\/(.:)/, '$1'), fullPage: true });
    await context.close();
  }
  for (const role of ['PUR_STAFF', 'USER']) {
    const context = await browser.newContext(); let handoverCalls = 0;
    const user = { id: 5, fullName: 'Demo account', email: 'demo@example.com', role };
    await context.addInitScript(user => { localStorage.setItem('itam_auth_token', 'demo'); localStorage.setItem('itam_auth_user', JSON.stringify(user)); }, user);
    await context.route('**/api/v1/**', route => {
      if (route.request().url().includes('/handovers')) handoverCalls++;
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ success: true, data: route.request().url().includes('/auth/me') ? { ...user, roleCode: role } : pageData([]) }) });
    });
    const page = await context.newPage(); await page.goto(`${base}/handovers`);
    await page.waitForURL(role === 'USER' ? '**/my-assets' : '**/account');
    assert.equal(handoverCalls, 0); assert.equal(await page.locator('a[href="/handovers"]').count(), 0);
    await context.close();
  }
  assert.deepEqual(errors, []); console.log('T15 browser checks passed: EN/VI, desktop/mobile, bundle preview, conflict/retry, completion and role guards.');
} finally { await browser.close(); }
