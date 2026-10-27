import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ApplicationService } from './application.service';
import { ApplicationDocuments } from '../model/application';

describe('ApplicationService', () => {
  let service: ApplicationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withXhr()), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApplicationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  // Fails the test if any call went out that no expectOne() claimed.
  afterEach(() => httpMock.verify());

  describe('enclosed documents', () => {
    const enclosed: ApplicationDocuments = { applicationId: 7, documents: [] };

    it('reads every application\'s documents in one request', () => {
      let received: ApplicationDocuments[] | undefined;
      service.getDocuments().subscribe(r => received = r);

      const req = httpMock.expectOne('/api/applications/documents');
      expect(req.request.method).toBe('GET');
      req.flush([enclosed]);
      expect(received).toEqual([enclosed]);
    });

    it('replaces one application\'s set with a PUT of the ids', () => {
      service.setDocuments(7, ['cv', 'cert']).subscribe();

      const req = httpMock.expectOne('/api/applications/7/documents');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ documentIds: ['cv', 'cert'] });
      req.flush(enclosed);
    });
  });
});
