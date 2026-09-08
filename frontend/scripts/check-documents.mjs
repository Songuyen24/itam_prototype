import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.ITAM_PLAYWRIGHT_MODULE || 'playwright');
const baseUrl = process.env.ITAM_FRONTEND_URL || 'http://127.0.0.1:5173';
const output = new URL('../../.tmp_documents/', import.meta.url);
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: process.env.ITAM_BROWSER_CHANNEL || 'msedge' });
const sampleName = 'sample-invoice-fictional-supplier-and-purchase-order-with-a-long-filename-2026.pdf';
const transactions = [
  { transactionId: 101, transactionCode: 'IMPORT-DEMO-001', type: 'IMPORT', status: 'PENDING' },
  { transactionId: 102, transactionCode: 'IMPORT-DEMO-002', type: 'IMPORT', status: 'REJECTED' },
].map(item => ({ ...item, createdAt: '2026-09-08T01:00:00Z', documentsEditable: false, editBlockedReason: 'DOCUMENT_WORKFLOW_NOT_READY' }));
const documents = [{ documentId: 201, transactionId: 101, documentType: 'INVOICE', originalFileName: sampleName,
  mimeType: 'application/pdf', fileSize: 512, uploadedByName: 'Purchasing Sample', createdAt: '2026-09-08T01:00:00Z', locked: false }];
const pageData = rows => ({ content: rows, pageNumber: 0, pageSize: 20, totalElements: rows.length, totalPages: rows.length ? 1 : 0, last: true });
const errors = [];
const calls = [];

try {
  for (const [role, language, width, height] of [
    ['ADMIN', 'en', 1440, 1000], ['PUR_STAFF', 'vi', 390, 844], ['USER', 'en', 1280, 800],
  ]) {
    const context = await browser.newContext({ viewport: { width, height }, acceptDownloads: true });
    const user = { id: 12, email: 'sample@itam.example', fullName: 'Document Sample', role };
    await context.addInitScript(({ user, language }) => {
      localStorage.setItem('itam_auth_token', 'document-ui-test');
      localStorage.setItem('itam_auth_user', JSON.stringify(user));
      localStorage.setItem('itam_language', language);
    }, { user, language });
    let failDocuments = false;
    await context.route('**/api/v1/**', async route => {
      const url = new URL(route.request().url());
      const path = url.pathname.replace('/api', '');
      calls.push({ role, path });
      const send = (data, status = 200) => route.fulfill({ status, contentType: 'application/json',
        body: JSON.stringify({ success: status === 200, message: status === 200 ? 'Success' : 'Document request failed', data }) });
      if (path === '/v1/auth/me') return send({ ...user, roleCode: role });
      if (path === '/v1/transactions') {
        const keyword = url.searchParams.get('keyword') || '';
        return send(pageData(transactions.filter(item => item.transactionCode.includes(keyword))));
      }
      if (path.startsWith('/v1/transactions/')) return send(transactions.find(item => path.endsWith('/' + item.transactionId)));
      if (path === '/v1/documents') {
        if (failDocuments) return send(null, 500);
        return send(pageData(url.searchParams.get('transactionId') === '101' ? documents : []));
      }
      if (path === '/v1/documents/201/download') return route.fulfill({ status: 200, contentType: 'application/pdf', body: '%PDF-1.4\n% Sample only\n%%EOF' });
      if (path === '/v1/users/me/assets') return send(pageData([]));
      return send(null, 403);
    });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(`${baseUrl}/documents`);
    if (role === 'USER') {
      await page.waitForURL('**/my-assets');
      assert.equal(await page.locator('a[href="/documents"]').count(), 0);
      assert.equal(calls.filter(call => call.role === role && /documents|transactions/.test(call.path)).length, 0);
      await context.close();
      continue;
    }
    await page.getByRole('button').filter({ hasText: 'IMPORT-DEMO-001' }).click();
    await page.getByText(sampleName, { exact: true }).waitFor();
    assert.equal(await page.locator('input[type="file"]').count(), 0);
    assert.equal(await page.locator('.document-detail .badge').textContent(), language === 'en' ? 'Read-only' : 'Chỉ đọc');
    const downloadEvent = page.waitForEvent('download');
    await page.locator('.document-download').click();
    const download = await downloadEvent;
    assert.equal(download.suggestedFilename(), sampleName);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false);
    await page.screenshot({ path: fileURLToPath(new URL(`documents-${role}-${width}.png`, output)), fullPage: true });
    await page.getByRole('button').filter({ hasText: 'IMPORT-DEMO-002' }).click();
    await page.locator('.document-detail .empty-state').waitFor();
    failDocuments = true;
    await page.getByRole('button').filter({ hasText: 'IMPORT-DEMO-001' }).click();
    await page.getByRole('alert').waitFor();
    failDocuments = false;
    await page.locator('.document-error button').click();
    await page.getByText(sampleName, { exact: true }).waitFor();
    await page.locator('#document-keyword').fill('NOT-FOUND');
    await page.locator('.document-transaction-list .empty-state').waitFor();
    if (role === 'PUR_STAFF') {
      assert.equal(calls.filter(call => call.role === role && /\/assets|\/users/.test(call.path)).length, 0);
    }
    await context.close();
  }
  assert.deepEqual(errors, []);
  console.log('Document UI checks passed: desktop/mobile, en/vi, download, empty/error/retry, read-only guards and USER/PUR routing.');
} finally {
  await browser.close();
}
