export type StorageUnit = 'GB' | 'TB';
export type StorageType = '' | 'HDD' | 'SSD';

const numeric = /^\d+(?:[.,]\d+)?$/;

export function normalizeRam(value: string): string {
  const trimmed = value.trim();
  return numeric.test(trimmed) ? `${trimmed} GB` : trimmed;
}

export function normalizeStorage(value: string, unit: StorageUnit, type: StorageType): string {
  let trimmed = value.trim();
  if (!trimmed) return '';
  if (numeric.test(trimmed)) trimmed = `${trimmed} ${unit}`;
  if (type) {
    const existingType = /\b(?:HDD|SSD)\b/gi;
    if (existingType.test(trimmed)) {
      let first = true;
      trimmed = trimmed.replace(existingType, () => {
        if (!first) return '';
        first = false;
        return type;
      }).replace(/\s{2,}/g, ' ').trim();
    } else {
      trimmed = `${trimmed} ${type}`;
    }
  }
  return trimmed;
}

export function changeStorageUnit(value: string, previous: StorageUnit, next: StorageUnit): string {
  return value.replace(new RegExp(`^(\\d+(?:[.,]\\d+)?) ${previous}(?= (?:HDD|SSD)$|$)`, 'i'), `$1 ${next}`);
}
