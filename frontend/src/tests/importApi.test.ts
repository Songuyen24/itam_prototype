import { describe, it, expect, vi, beforeEach } from 'vitest';
import { importApi } from '@/features/assets/api/importApi';

describe('Import API & Excel Import Workflow', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('importApi.preview sends multipart/form-data with file to preview endpoint', async () => {
    const mockPreviewResponse = {
      success: true,
      message: 'Kiểm tra dữ liệu file Excel hoàn tất',
      data: {
        fileName: 'test_assets.xlsx',
        totalRows: 3,
        validRows: 2,
        invalidRows: 1,
        duplicateRows: 0,
        rows: [
          {
            rowNumber: 2,
            validationStatus: 'VALID',
            errors: [],
            rawData: { assetTag: 'AST-001', name: 'Laptop Dell 5420' },
          },
          {
            rowNumber: 3,
            validationStatus: 'VALID',
            errors: [],
            rawData: { assetTag: 'AST-002', name: 'PC HP 400' },
          },
          {
            rowNumber: 4,
            validationStatus: 'INVALID',
            errorMessage: 'Mã tài sản là bắt buộc',
            errors: [
              {
                rowNumber: 4,
                column: 'assetTag',
                code: 'REQUIRED',
                message: 'Mã tài sản (Asset Tag) là bắt buộc',
              },
            ],
            rawData: { assetTag: null, name: 'Monitor Dell' },
          },
        ],
      },
    };

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockPreviewResponse,
    } as unknown as Response);

    vi.stubGlobal('fetch', mockFetch);

    const testFile = new File(['mock content'], 'test_assets.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });

    const result = await importApi.preview(testFile);

    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/asset-imports/preview'),
      expect.objectContaining({
        method: 'POST',
      })
    );
    expect(result.data.totalRows).toBe(3);
    expect(result.data.validRows).toBe(2);
    expect(result.data.invalidRows).toBe(1);
  });

  it('importApi.confirm sends POST request with confirmed valid rows', async () => {
    const mockConfirmResponse = {
      success: true,
      message: 'Nhập tài sản từ Excel thành công',
      data: {
        importBatchId: 10,
        fileName: 'test_assets.xlsx',
        status: 'COMPLETED',
        totalRows: 2,
        validRows: 2,
        invalidRows: 0,
        duplicateRows: 0,
        importedRows: 2,
        uploadedByFullName: 'Admin User',
      },
    };

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockConfirmResponse,
    } as unknown as Response);

    vi.stubGlobal('fetch', mockFetch);

    const payload = {
      fileName: 'test_assets.xlsx',
      validRows: [
        { assetTag: 'AST-001', name: 'Laptop Dell 5420', type: 'LAPTOP' },
        { assetTag: 'AST-002', name: 'PC HP 400', type: 'DESKTOP' },
      ],
    };

    const result = await importApi.confirm(payload);

    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/asset-imports/confirm'),
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(payload),
      })
    );
    expect(result.data.importBatchId).toBe(10);
    expect(result.data.importedRows).toBe(2);
    expect(result.data.status).toBe('COMPLETED');
  });

  it('importApi.getBatches calls endpoint with pagination', async () => {
    const mockResponse = {
      success: true,
      data: {
        content: [
          {
            importBatchId: 1,
            fileName: 'batch-01.xlsx',
            status: 'COMPLETED',
            totalRows: 5,
            importedRows: 5,
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

    const result = await importApi.getBatches(0, 20);

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/asset-imports?page=0&size=20&sort=createdAt,desc'),
      expect.anything()
    );
    expect(result.data.content[0].importBatchId).toBe(1);
  });

  it('importApi.getBatchById calls endpoint with batch ID', async () => {
    const mockResponse = {
      success: true,
      data: {
        importBatchId: 5,
        fileName: 'batch-05.xlsx',
        status: 'COMPLETED',
        rows: [],
      },
    };

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    } as unknown as Response);

    vi.stubGlobal('fetch', mockFetch);

    const result = await importApi.getBatchById(5);

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/asset-imports/5'),
      expect.anything()
    );
    expect(result.data.importBatchId).toBe(5);
  });

  it('importApi.getBatchErrors calls error list endpoint', async () => {
    const mockResponse = {
      success: true,
      data: [
        {
          rowNumber: 3,
          validationStatus: 'INVALID',
          errorMessage: 'Serial trùng lặp',
        },
      ],
    };

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    } as unknown as Response);

    vi.stubGlobal('fetch', mockFetch);

    const result = await importApi.getBatchErrors(5);

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/asset-imports/5/errors'),
      expect.anything()
    );
    expect(result.data.length).toBe(1);
    expect(result.data[0].validationStatus).toBe('INVALID');
  });
});
