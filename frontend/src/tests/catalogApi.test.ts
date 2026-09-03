import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiError } from '@/shared/api/httpClient';
import { departmentApi, categoryApi } from '@/features/catalogs/api/catalogApi';

describe('Catalog API & Error handling', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('ApiError should correctly instantiate with message, status and code', () => {
    const error = new ApiError('Dữ liệu đang được sử dụng', 409, 'CATALOG_IN_USE');
    expect(error).toBeInstanceOf(Error);
    expect(error.name).toBe('ApiError');
    expect(error.status).toBe(409);
    expect(error.code).toBe('CATALOG_IN_USE');
    expect(error.message).toBe('Dữ liệu đang được sử dụng');
  });

  it('departmentApi.getAll calls fetch with expected URL and query params', async () => {
    const mockResponse = {
      success: true,
      data: {
        content: [{ departmentId: 1, code: 'IT', name: 'Information Technology', isActive: true }],
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

    const result = await departmentApi.getAll('IT', true, 0, 20);

    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/departments?page=0&size=20&search=IT&isActive=true'),
      expect.anything()
    );
    expect(result.data.content[0].code).toBe('IT');
  });

  it('categoryApi.create posts JSON payload and parses response', async () => {
    const mockCategory = {
      success: true,
      data: { categoryId: 1, code: 'DEVICE', name: 'Thiết bị', isActive: true },
    };

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockCategory,
    } as unknown as Response);

    vi.stubGlobal('fetch', mockFetch);

    const result = await categoryApi.create({ code: 'DEVICE', name: 'Thiết bị' });

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/asset-categories'),
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ code: 'DEVICE', name: 'Thiết bị' }),
      })
    );
    expect(result.data.code).toBe('DEVICE');
  });

  it('throws ApiError with CATALOG_IN_USE on 409 conflict', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({
        success: false,
        code: 'CATALOG_IN_USE',
        message: 'Không thể xóa: danh mục đang được sử dụng',
      }),
    } as unknown as Response);

    vi.stubGlobal('fetch', mockFetch);

    await expect(departmentApi.delete(1)).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
      code: 'CATALOG_IN_USE',
      message: 'Không thể xóa: danh mục đang được sử dụng',
    });
  });
});
