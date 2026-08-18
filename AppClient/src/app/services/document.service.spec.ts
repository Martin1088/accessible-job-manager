import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { DocumentService } from './document.service';
import { Document } from '../model/document';

describe('DocumentService', () => {
  let service: DocumentService;
  let httpMock: HttpTestingController;

  const cv: Document = {
    id: 'doc-1',
    label: 'My CV',
    filename: 'cv.pdf',
    mimeType: 'application/pdf',
    type: 'CV',
    language: 'ENGLISH',
    createdAt: '2026-08-17T10:00:00',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(DocumentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  // Fails the test if any call went out that no expectOne() claimed.
  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getAll', () => {
    it('requests every document when no type is given', () => {
      let received: Document[] | undefined;
      service.getAll().subscribe(docs => received = docs);

      const req = httpMock.expectOne('/api/documents');
      expect(req.request.method).toBe('GET');
      // No type param at all, rather than an empty one the backend would try to parse.
      expect(req.request.params.has('type')).toBeFalse();

      req.flush([cv]);
      expect(received).toEqual([cv]);
    });

    it('narrows to one type when given one', () => {
      service.getAll('COVER_LETTER_TEMPLATE').subscribe();

      const req = httpMock.expectOne(r => r.url === '/api/documents');
      expect(req.request.params.get('type')).toBe('COVER_LETTER_TEMPLATE');
      req.flush([]);
    });
  });

  describe('upload', () => {
    it('posts the file and its metadata as multipart form data', () => {
      const file = new File(['pdf bytes'], 'cv.pdf', { type: 'application/pdf' });
      service.upload(file, 'My CV', 'CV', 'ENGLISH').subscribe();

      const req = httpMock.expectOne('/api/documents/upload');
      expect(req.request.method).toBe('POST');

      const body = req.request.body as FormData;
      expect(body instanceof FormData).toBeTrue();
      expect(body.get('file')).toBe(file);
      expect(body.get('label')).toBe('My CV');
      expect(body.get('type')).toBe('CV');
      expect(body.get('language')).toBe('ENGLISH');

      // Left to the browser: setting it by hand omits the multipart boundary.
      expect(req.request.headers.has('Content-Type')).toBeFalse();

      req.flush(cv);
    });
  });

  describe('update', () => {
    it('patches only the fields it is given', () => {
      service.update('doc-1', { label: 'Renamed' }).subscribe();

      const req = httpMock.expectOne('/api/documents/doc-1');
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ label: 'Renamed' });
      req.flush({ ...cv, label: 'Renamed' });
    });

    it('can reclassify a document', () => {
      service.update('doc-1', { type: 'CERTIFICATE' }).subscribe();

      const req = httpMock.expectOne('/api/documents/doc-1');
      expect(req.request.body).toEqual({ type: 'CERTIFICATE' });
      req.flush({ ...cv, type: 'CERTIFICATE' });
    });

    it('surfaces the 415 the server returns for an incompatible type', () => {
      let status: number | undefined;
      service.update('doc-1', { type: 'COVER_LETTER_TEMPLATE' })
        .subscribe({ error: (e) => status = e.status });

      httpMock.expectOne('/api/documents/doc-1').flush(
        { message: 'COVER_LETTER_TEMPLATE requires ...' },
        { status: 415, statusText: 'Unsupported Media Type' });

      expect(status).toBe(415);
    });
  });

  describe('delete', () => {
    it('deletes by id', () => {
      let completed = false;
      service.delete('doc-1').subscribe(() => completed = true);

      const req = httpMock.expectOne('/api/documents/doc-1');
      expect(req.request.method).toBe('DELETE');

      req.flush(null);
      expect(completed).toBeTrue();
    });
  });

  describe('download', () => {
    it('asks for a blob so the response is not parsed as JSON', () => {
      let received: Blob | undefined;
      service.download('doc-1').subscribe(blob => received = blob);

      const req = httpMock.expectOne('/api/documents/doc-1/download');
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');

      const blob = new Blob(['pdf bytes'], { type: 'application/pdf' });
      req.flush(blob);
      expect(received).toBe(blob);
    });
  });
});
