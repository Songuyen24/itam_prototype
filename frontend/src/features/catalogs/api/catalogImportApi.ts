import { downloadFile, httpClient } from '@/shared/api/httpClient';
import type { ApiResponse } from '../types/catalog.types';

export const importableCatalogs = ['departments','locations','suppliers','types','models','software'] as const;
export type ImportableCatalog = typeof importableCatalogs[number];
export interface CatalogImportRow { row: number; values: string[]; status: 'VALID' | 'INVALID' | 'CREATED'; errors: string[] }
export interface CatalogImportResult { columns: string[]; rows: CatalogImportRow[]; valid: number; invalid: number; created: number }
const url = (kind: ImportableCatalog) => `/v1/catalog-imports/${kind}`;
const upload = (kind: ImportableCatalog, file: File, action: 'preview' | 'confirm') => { const body = new FormData(); body.append('file',file); return httpClient<ApiResponse<CatalogImportResult>>(`${url(kind)}/${action}`,{method:'POST',body}); };
export const catalogImportApi = {
  template: (kind: ImportableCatalog) => downloadFile(`${url(kind)}/template`,`catalog-${kind}.xlsx`),
  preview: (kind: ImportableCatalog,file: File) => upload(kind,file,'preview'),
  confirm: (kind: ImportableCatalog,file: File) => upload(kind,file,'confirm'),
};
