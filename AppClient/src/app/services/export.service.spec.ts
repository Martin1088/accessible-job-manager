import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ExportService, XLSX_MIME } from './export.service';

describe('ExportService', () => {
  let service: ExportService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withXhr()), provideHttpClientTesting()],
    });
    service = TestBed.inject(ExportService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for CSV through the Accept header, not a query parameter', () => {
    service.exportCompanies('CSV', 'ENGLISH').subscribe();

    const req = http.expectOne(r => r.url === '/api/export/companies');
    expect(req.request.headers.get('Accept')).toBe('text/csv');
    expect(req.request.params.get('language')).toBe('ENGLISH');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['a,b']));
  });

  // The server treats anything that is not CSV as a workbook request, so the
  // spreadsheet MIME type has to be sent verbatim - */* would also yield xlsx,
  // but only by accident.
  it('asks for the spreadsheet media type when exporting xlsx', () => {
    service.exportCompanies('XLSX', 'GERMAN').subscribe();

    const req = http.expectOne(r => r.url === '/api/export/companies');
    expect(req.request.headers.get('Accept')).toBe(XLSX_MIME);
    expect(req.request.params.get('language')).toBe('GERMAN');
    req.flush(new Blob([]));
  });

  it('observes the full response so the server-named file can be kept', () => {
    const dispositions: (string | null)[] = [];
    service.exportCompanies('CSV', 'DUTCH')
      .subscribe(r => dispositions.push(r.headers.get('Content-Disposition')));

    http.expectOne(r => r.url === '/api/export/companies').flush(new Blob(['a,b']), {
      headers: { 'Content-Disposition': 'attachment; filename=companies-export-2026-08-23.csv' },
    });

    expect(dispositions).toEqual(['attachment; filename=companies-export-2026-08-23.csv']);
  });
});
