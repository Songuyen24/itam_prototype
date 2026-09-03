import { describe, it, expect, vi, beforeEach } from 'vitest';
import { assetApi } from '@/features/assets/api/assetApi';

describe('Asset API & Hardware Asset Management', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('assetApi.getAssets calls endpoint with filter query parameters', async () => {
    const mockResponse = {
      success: true,
      data: {
        content: [
          {
            assetId: 1,
            assetTag: 'AST-001',
            name: 'Laptop Lenovo T14',
            serialNumber: 'SN-12345',
            effectiveCpu: 'Intel Core i5',
            statusCode: 'IN_STOCK',
            statusName: 'In Stock',
          },
        ],
        pageNumber: 0,
        pageSize: 20,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
        empty: false,
      },
    };

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    } as unknown as Response);

    vi.stubGlobal('fetch', mockFetch);

    const result = await assetApi.getAssets({
      keyword: 'Lenovo',
      statusId: 1,
      page: 0,
      size: 20,
    });

    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/assets?page=0&size=20&keyword=Lenovo&statusId=1'),
      expect.anything()
    );
    expect(result.data.content[0].assetTag).toBe('AST-001');
  });

  it('assetApi.createAsset sends POST request with payload', async () => {
    const payload = {
      assetTag: 'AST-NEW-01',
      name: 'Dell Latitude 5440',
      typeId: 1,
      modelId: 5,
      serialNumber: 'SN-DELL-99',
      actualRam: '32GB',
    };

    const mockResponse = {
      success: true,
      data: {
        assetId: 10,
        ...payload,
        effectiveRam: '32GB',
        hardwareConfig: {
          defaultRam: '16GB',
          actualRam: '32GB',
          effectiveRam: '32GB',
        },
      },
    };

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    } as unknown as Response);

    vi.stubGlobal('fetch', mockFetch);

    const result = await assetApi.createAsset(payload);

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/assets'),
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(payload),
      })
    );
    expect(result.data.assetId).toBe(10);
    expect(result.data.effectiveRam).toBe('32GB');
  });

  it('assetApi.validateUniqueness posts tag and serial and parses result', async () => {
    const mockResponse = {
      success: true,
      data: {
        assetTagAvailable: true,
        assetTagMessage: 'Mã tài sản hợp lệ',
        serialNumberAvailable: false,
        serialNumberMessage: 'Số serial number đã tồn tại',
      },
    };

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    } as unknown as Response);

    vi.stubGlobal('fetch', mockFetch);

    const result = await assetApi.validateUniqueness({
      assetTag: 'TAG-VALID',
      serialNumber: 'SN-DUPLICATE',
    });

    expect(result.data.assetTagAvailable).toBe(true);
    expect(result.data.serialNumberAvailable).toBe(false);
  });
});
