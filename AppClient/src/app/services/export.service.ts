import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Language } from '../model/company';

/** The two spreadsheet formats `ExportController` can write. */
export type ExportFormat = 'CSV' | 'XLSX';

export const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

const ACCEPT: Record<ExportFormat, string> = {
  CSV: 'text/csv',
  XLSX: XLSX_MIME,
};

/**
 * The caller's own data as a spreadsheet - every company, its positions and the
 * applications made for them, one row each.
 *
 * The format is chosen by content negotiation rather than a query parameter, because
 * that is the contract `ExportController` exposes: it reads Accept and falls back to
 * .xlsx for anything that is not CSV. The Accept header is therefore not incidental
 * here, and `*​/*` would silently produce a workbook.
 */
@Injectable({ providedIn: 'root' })
export class ExportService {

  private readonly apiUrl = '/api/export';
  private readonly http = inject(HttpClient);

  /**
   * Observed as a full response: the server names the file (`companies-export-<date>`)
   * in Content-Disposition, so the download keeps that name instead of inventing one.
   */
  exportCompanies(format: ExportFormat, language: Language): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.apiUrl}/companies`, {
      headers: new HttpHeaders({ Accept: ACCEPT[format] }),
      params: new HttpParams().set('language', language),
      responseType: 'blob',
      observe: 'response',
    });
  }
}
