import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';
import { buildRecoveryRequest, SmartCheckResponse } from '@/features/recovery/api/recoveryApi';
import { SmartCheckDialog } from '@/features/recovery/components/RecoveryForm';
import en from '@/shared/i18n/locales/en/recovery.json';
import vi from '@/shared/i18n/locales/vi/recovery.json';

const check: SmartCheckResponse = {
  requiredAssets: [
    { assetId: 10, assetTag: 'LAP-10', name: 'Laptop', category: 'DEVICE', reason: null },
    { assetId: 11, assetTag: 'OEM-11', name: 'OEM', category: 'LICENSE', reason: 'Bundled' },
  ],
  optionalAssets: [{ allocationId: 30, assetId: 20, assetTag: 'LIC-20', name: 'Per User', seats: 1, userId: 7, userName: 'Mai', deviceId: 10, deviceTag: 'LAP-10', selected: false }],
  blockedAssets: [],
  componentDecisions: [{ assetId: 12, assetTag: 'RAM-12', name: 'RAM', options: [], defaultAction: 'KEEP_ATTACHED' }],
  warnings: [],
  fingerprint: 'reviewed-bundle',
};

describe('T16 recovery completion payload', () => {
  it('keeps Vietnamese and English recovery UI keys in parity', () => {
    expect(Object.keys(vi).sort()).toEqual(Object.keys(en).sort());
  });

  it('A keeps the original selected device and reason while excluding bundled OEM from asset IDs', () => {
    expect(buildRecoveryRequest(check, [10], [], 'Replacement')).toMatchObject({
      reason: 'Replacement', assetIds: [10], allocationIds: [], expectedFingerprint: 'reviewed-bundle',
    });
  });

  it('B sends only the explicitly selected Per-User allocation', () => {
    expect(buildRecoveryRequest(check, [], [30], 'User departure')).toMatchObject({
      reason: 'User departure', assetIds: [], allocationIds: [30], perUserActions: { 30: true },
    });
  });

  it('C includes the chosen component and its detach decision', () => {
    expect(buildRecoveryRequest(check, [12], [], 'Upgrade', { 12: 'DETACH' })).toMatchObject({
      assetIds: [12], componentActions: { 12: { componentAction: 'DETACH', recoverPerUser: false } },
    });
  });

  it('D preserves the standalone OEM selection for server-side blocking', () => {
    expect(buildRecoveryRequest(check, [11], [], 'Replacement')).toMatchObject({ assetIds: [11] });
  });

  it('renders A bundled OEM, B locked allocation, C detach default, and D disabled completion controls', () => {
    const render = (value: SmartCheckResponse, assetIds: number[] = [], allocationIds: number[] = []) => renderToStaticMarkup(createElement(SmartCheckDialog, {
      check: value, selectedAssetIds: assetIds, selectedAllocationIds: allocationIds, reason: 'Replacement',
      onConfirm: () => {}, onCancel: () => {}, busy: false, t: (key: string) => key,
    }));
    expect(render(check, [10])).toContain('OEM-11');
    const perUser = { ...check, requiredAssets: [], componentDecisions: [], optionalAssets: [{ ...check.optionalAssets[0], selected: true }] };
    expect(render(perUser, [], [30])).toMatch(/type="checkbox" disabled="" checked=""/);
    const component = { ...check, requiredAssets: [], optionalAssets: [], componentDecisions: [{ ...check.componentDecisions[0], defaultAction: 'DETACH', options: [{ action: 'DETACH', label: 'Detach', description: 'Detach' }] }] };
    expect(render(component, [12])).toMatch(/type="radio"[^>]*checked=""[^>]*value="DETACH"/);
    const blocked = { ...check, requiredAssets: [], componentDecisions: [], blockedAssets: [{ assetId: 11, assetTag: 'OEM-11', name: 'OEM', category: 'LICENSE', reason: 'OEM blocked' }] };
    expect(render(blocked, [11])).toMatch(/button[^>]*disabled=""[^>]*>complete/);
  });

  it('uses a labelled native dialog instead of a declaratively open dialog', () => {
    const html = renderToStaticMarkup(createElement(SmartCheckDialog, {
      check, selectedAssetIds: [], selectedAllocationIds: [], reason: 'Replacement',
      onConfirm: () => {}, onCancel: () => {}, busy: false, t: (key: string) => key,
    }));
    expect(html).toContain('<dialog');
    expect(html).toContain('aria-labelledby="recovery-smart-check-title"');
    expect(html).not.toContain('<dialog open');
  });
});
