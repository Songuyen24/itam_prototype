import { describe, expect, it } from 'vitest';
import { changeStorageUnit, normalizeRam, normalizeStorage } from '@/features/assets/components/assetCapacity';

describe('asset capacity normalization', () => {
  it('adds GB only to numeric RAM', () => {
    expect(normalizeRam(' 16 ')).toBe('16 GB');
    expect(normalizeRam('16 GB DDR5')).toBe('16 GB DDR5');
    expect(normalizeRam('')).toBe('');
  });

  it('uses the chosen unit and optional drive type without duplicating existing specs', () => {
    expect(normalizeStorage(' 1 ', 'TB', '')).toBe('1 TB');
    expect(normalizeStorage('512', 'GB', 'SSD')).toBe('512 GB SSD');
    expect(normalizeStorage('1 TB NVMe', 'GB', 'SSD')).toBe('1 TB NVMe SSD');
    expect(normalizeStorage('1 TB HDD NVMe', 'GB', 'SSD')).toBe('1 TB SSD NVMe');
    expect(normalizeStorage('1 TB SSD', 'GB', 'SSD')).toBe('1 TB SSD');
    expect(normalizeStorage('HDD 1 TB', 'GB', 'SSD')).toBe('SSD 1 TB');
    expect(normalizeStorage('1 TB HDD SSD', 'GB', 'SSD')).toBe('1 TB SSD');
    expect(normalizeStorage(' ', 'GB', 'HDD')).toBe('');
  });

  it('switches the unit of a simple normalized value only', () => {
    expect(changeStorageUnit('512 GB', 'GB', 'TB')).toBe('512 TB');
    expect(changeStorageUnit('512 GB SSD', 'GB', 'TB')).toBe('512 TB SSD');
    expect(changeStorageUnit('1 TB NVMe', 'GB', 'TB')).toBe('1 TB NVMe');
  });
});
