import { describe, expect, it } from 'vitest';
import i18n, { resources } from '@/shared/i18n';

function translationKeys(value: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(value).flatMap(([key, entry]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return typeof entry === 'object' && entry !== null ? translationKeys(entry as Record<string, unknown>, path) : [path];
  });
}

describe('Document translations', () => {
  it('provides both languages for every document label and message', () => {
    const keys = translationKeys(resources.vi.documents);
    expect(keys.sort()).toEqual(translationKeys(resources.en.documents).sort());
    for (const lng of ['vi', 'en']) {
      for (const key of keys) {
        expect(i18n.exists(`documents:${key}`, { lng })).toBe(true);
        expect(i18n.t(`documents:${key}`, { lng })).not.toBe(key);
      }
    }
    expect(i18n.t('documents:title', { lng: 'vi' })).toBe('Chứng từ');
    expect(i18n.t('documents:title', { lng: 'en' })).toBe('Documents');
  });
});
