import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir } from 'node:fs/promises';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.ITAM_PLAYWRIGHT_MODULE || 'playwright');
const output = new URL('../../../.tmp/recovery/', import.meta.url);
const base = process.env.ITAM_FRONTEND_URL || 'http://127.0.0.1:5173';
const pageData = content => ({ content, totalElements: content.length, totalPages: 1, pageNumber: 0 });
const candidates = [
  { assetId: 1, assetTag: 'LAP-001', name: 'Laptop', categoryCode: 'DEVICE', licenseAssignmentTypeCode: null, assignedToUserId: 7, assignedToFullName: 'Demo Returner' },
  { assetId: 2, assetTag: 'RAM-002', name: 'RAM', categoryCode: 'COMPONENT', licenseAssignmentTypeCode: null, assignedToUserId: 7, assignedToFullName: 'Demo Returner' },
  { assetId: 3, assetTag: 'OEM-003', name: 'OEM', categoryCode: 'LICENSE', licenseAssignmentTypeCode: 'OEM', assignedToUserId: 7, assignedToFullName: 'Demo Returner' },
  { assetId: 4, assetTag: 'LIC-004', name: 'Per User', categoryCode: 'LICENSE', allocationId: 40, deviceTag: 'LAP-001', seats: 1, licenseAssignmentTypeCode: 'PER_USER', assignedToUserId: 7, assignedToFullName: 'Demo Returner' },
];
const label = (language, key) => ({
  preview: language === 'en' ? 'Check' : 'Kiểm tra',
  complete: language === 'en' ? 'Complete Recovery' : 'Hoàn tất thu hồi',
}[key]);
const smartCheck = scenario => ({
  requiredAssets: scenario === 'A' ? [
    { assetId: 1, assetTag: 'LAP-001', name: 'Laptop', category: 'DEVICE', reason: 'Selected device' },
    { assetId: 3, assetTag: 'OEM-003', name: 'OEM', category: 'LICENSE', reason: 'Bundled OEM' },
  ] : scenario === 'C' ? [{ assetId: 2, assetTag: 'RAM-002', name: 'RAM', category: 'COMPONENT', reason: 'Selected component' }] : [],
  optionalAssets: scenario === 'B' ? [{ allocationId: 40, assetId: 4, assetTag: 'LIC-004', name: 'Per User', seats: 1, userId: 7, userName: 'Demo Returner', deviceId: 1, deviceTag: 'LAP-001', selected: true }] : [],
  blockedAssets: scenario === 'D' ? [{ assetId: 3, assetTag: 'OEM-003', name: 'OEM', category: 'LICENSE', reason: 'OEM cannot be recovered separately' }] : [],
  componentDecisions: scenario === 'C' ? [{ assetId: 2, assetTag: 'RAM-002', name: 'RAM', defaultAction: 'DETACH', options: [{ action: 'KEEP_ATTACHED', label: 'Keep attached', description: 'Keep' }, { action: 'DETACH', label: 'Detach', description: 'Detach' }] }] : [],
  warnings: [], fingerprint: `fingerprint-${scenario}`,
});

await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: process.env.ITAM_BROWSER_CHANNEL || 'msedge' });
const errors = [];
try {
  for (const [scenario, language, width] of [['A', 'en', 1440], ['B', 'vi', 390], ['C', 'en', 1440], ['D', 'vi', 390]]) {
    const context = await browser.newContext({ viewport: { width, height: 1000 } });
    const user = { id: 9, fullName: 'Demo IT', email: 'it@itam.example', role: 'IT_STAFF' };
    await context.addInitScript(({ language, user }) => {
      localStorage.setItem('itam_auth_token', 't16-ui-demo');
      localStorage.setItem('itam_auth_user', JSON.stringify(user));
      localStorage.setItem('itam_language', language);
    }, { language, user });
    let completed = 0;
    await context.route('**/api/v1/**', async route => {
      const url = new URL(route.request().url());
      const path = url.pathname.replace('/api', '');
      const send = (data, status = 200, message = 'Success') => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ success: status < 400, message, data }) });
      if (path === '/v1/auth/me') return send({ ...user, roleCode: user.role });
      if (path === '/v1/users') return send(pageData([{ id: 7, fullName: 'Demo Returner', email: 'returner@example.com', accountStatus: 'ACTIVE' }]));
      if (path === '/v1/locations') return send(pageData([{ locationId: 2, name: 'Demo Office', isActive: true }]));
      if (path === '/v1/assets/recovery-candidates') return send(pageData(candidates));
      if (path === '/v1/recoveries/smart-check') {
        const body = route.request().postDataJSON();
        assert.equal(body.returnerUserId, 7); assert.equal(body.reason, 'Replacement');
        assert.deepEqual(body.assetIds, scenario === 'A' ? [1] : scenario === 'B' ? [] : scenario === 'C' ? [2] : [3]);
        assert.deepEqual(body.allocationIds, scenario === 'B' ? [40] : []);
        return send(smartCheck(scenario));
      }
      if (path === '/v1/recoveries' && route.request().method() === 'POST') {
        completed++;
        const body = route.request().postDataJSON();
        assert.equal(body.reason, 'Replacement'); assert.equal(body.expectedFingerprint, `fingerprint-${scenario}`);
        assert.deepEqual(body.assetIds, scenario === 'A' ? [1] : scenario === 'B' ? [] : scenario === 'C' ? [2] : [3]);
        assert.deepEqual(body.allocationIds, scenario === 'B' ? [40] : []);
        if (scenario === 'B') assert.equal(body.perUserActions['40'], true);
        if (scenario === 'C') assert.equal(body.componentActions['2'].componentAction, 'DETACH');
        return send({ transactionId: 10, transactionCode: 'RC-10', completedAt: '2026-09-17T00:00:00Z', returnerUserId: 7, returnerName: 'Demo Returner', returnerEmail: 'returner@example.com', receivingLocationId: 2, receivingLocationName: 'Demo Office', recoveryDate: '2026-09-17', reason: 'Replacement', fingerprint: `fingerprint-${scenario}`, lines: [] }, 201);
      }
      if (path === '/v1/transactions/10/publication') return send({
        transactionId: 10, transactionCode: 'RC-10', transactionType: 'RECOVERY',
        pdf: { documentId: null, fileName: null, version: null, templateVersion: null, issuedAt: null, status: 'NOT_GENERATED' }, emails: [],
      });
      return send(null, 403);
    });

    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(`${base}/recoveries`);
    await page.getByRole('button', { name: /Demo Returner/ }).click();
    await page.getByRole('button', { name: 'Demo Office' }).click();
    const selectedTag = scenario === 'A' ? 'LAP-001' : scenario === 'B' ? 'LIC-004' : scenario === 'C' ? 'RAM-002' : 'OEM-003';
    await page.getByRole('checkbox', { name: new RegExp(`^${selectedTag}`) }).check();
    assert.equal(await page.getByText('{{count}}', { exact: false }).count(), 0);
    await page.locator('textarea').fill('Replacement');
    await page.getByRole('button', { name: label(language, 'preview'), exact: true }).click();
    const dialog = page.getByRole('dialog'); await dialog.waitFor();
    assert.equal(await page.locator('textarea').isDisabled(), true);
    assert.equal(await page.locator('input[type="date"]').isDisabled(), true);
    assert.equal(await page.getByRole('button', { name: /Demo Returner/ }).isDisabled(), true);
    if (scenario === 'A') await dialog.getByText('OEM-003', { exact: true }).waitFor();
    if (scenario === 'B') {
      const allocation = dialog.getByRole('checkbox'); assert.equal(await allocation.isChecked(), true); assert.equal(await allocation.isDisabled(), true);
      await dialog.getByText('License Per-User phải thu hồi').waitFor(); await dialog.getByText('Lượt cấp phát được chọn riêng sẽ được thu hồi.').waitFor();
    }
    if (scenario === 'C') { const detach = dialog.locator('input[type="radio"][value="DETACH"]'); assert.equal(await detach.isChecked(), true); }
    const complete = dialog.getByRole('button', { name: label(language, 'complete'), exact: true });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    await page.screenshot({ path: new URL(`dialog-${scenario}-${language}-${width}.png`, output).pathname.replace(/^\/(.:)/, '$1'), fullPage: true });
    if (scenario === 'D') {
      assert.equal(await complete.isDisabled(), true); await dialog.getByText('OEM-003', { exact: true }).waitFor();
    } else {
      await complete.click(); await page.getByText('RC-10', { exact: true }).waitFor(); assert.equal(completed, 1);
    }
    await context.close();
  }
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const user = { id: 9, fullName: 'Demo IT', email: 'it@itam.example', role: 'IT_STAFF' };
  await context.addInitScript(user => {
    localStorage.setItem('itam_auth_token', 't16-page-reset'); localStorage.setItem('itam_auth_user', JSON.stringify(user)); localStorage.setItem('itam_language', 'en');
  }, user);
  const candidateRequests = [];
  await context.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url()), path = url.pathname.replace('/api', '');
    const send = data => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ success: true, data }) });
    if (path === '/v1/auth/me') return send({ ...user, roleCode: user.role });
    if (path === '/v1/users') return send(pageData([{ id: 7, fullName: 'Returner A', email: 'a@example.com', accountStatus: 'ACTIVE' }, { id: 8, fullName: 'Returner B', email: 'b@example.com', accountStatus: 'ACTIVE' }]));
    if (path === '/v1/locations') return send(pageData([{ locationId: 2, name: 'Demo Office', isActive: true }]));
    if (path === '/v1/assets/recovery-candidates') {
      const candidateUser = Number(url.searchParams.get('userId')), pageNumber = Number(url.searchParams.get('page'));
      candidateRequests.push({ userId: candidateUser, page: pageNumber });
      return send({ content: [{ assetId: candidateUser * 100 + pageNumber, assetTag: `${candidateUser === 7 ? 'A' : 'B'}-${pageNumber}`, name: 'Device', categoryCode: 'DEVICE', licenseAssignmentTypeCode: null, assignedToUserId: candidateUser, assignedToFullName: candidateUser === 7 ? 'Returner A' : 'Returner B' }], totalElements: candidateUser === 7 ? 21 : 1, totalPages: candidateUser === 7 ? 2 : 1, pageNumber });
    }
    return send(null);
  });
  const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
  await page.goto(`${base}/recoveries`);
  await page.getByRole('button', { name: /Returner A/ }).click(); await page.getByText('A-0', { exact: false }).waitFor();
  await page.getByRole('button', { name: 'Next', exact: true }).click(); await page.getByText('A-1', { exact: false }).waitFor();
  await page.getByRole('button', { name: /Returner B/ }).click(); await page.getByText('B-0', { exact: false }).waitFor();
  assert.deepEqual(candidateRequests.at(-1), { userId: 8, page: 0 });
  await context.close();
  assert.deepEqual(errors, []);
  console.log('T16 browser checks passed: A/B/C/D controls, original payloads, frozen form, EN desktop and VI mobile.');
} finally { await browser.close(); }
