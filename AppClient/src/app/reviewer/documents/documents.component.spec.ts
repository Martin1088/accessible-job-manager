import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { ReviewerDocumentsComponent } from './documents.component';
import { expectNoAxeViolations } from '../../../testing/a11y';

const SHARED = [
  {
    userId: 'u1',
    name: 'Anna Berg',
    email: 'anna.berg@example.org',
    documents: [
      { id: 'd1', label: 'CV', filename: 'cv.pdf', type: 'CV', grantedAt: '2026-09-01T10:05:00' },
    ],
  },
];

describe('ReviewerDocumentsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReviewerDocumentsComponent],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'en' }),
      ],
    }).compileComponents();
  });

  function render() {
    const fixture = TestBed.createComponent(ReviewerDocumentsComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/reviewer/users').flush(SHARED);
    fixture.detectChanges();
    return { fixture, http, el: fixture.nativeElement as HTMLElement };
  }

  const bodyRows = (table: Element) => Array.from(table.querySelectorAll('tbody tr'));

  it('should create', () => {
    const fixture = TestBed.createComponent(ReviewerDocumentsComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('has no axe-detectable accessibility violations', async () => {
    const fixture = TestBed.createComponent(ReviewerDocumentsComponent);
    fixture.detectChanges();
    await expectNoAxeViolations(fixture);
  });

  it('lists the documents shared with the reviewer', async () => {
    const { fixture, http, el } = render();

    expect(el.querySelector('.sender__monogram')?.textContent?.trim()).toBe('A');
    expect(bodyRows(el.querySelector('.doc-table')!).length).toBe(1);

    await expectNoAxeViolations(fixture);
    http.verify();
  });

  it('uploads a review and shares it back, then reloads the list', () => {
    const { fixture, http } = render();
    const component = fixture.componentInstance;

    const file = new File(['comments'], 'review.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    });
    component.beginReview(SHARED[0].documents[0] as any);
    component.onReviewFileSelected({ target: { files: [file], value: '' } } as unknown as Event);
    fixture.detectChanges();

    expect(component.showReviewForm).toBe(true);
    // No translation loader is configured in this test setup, so instant() returns the
    // raw key - matching how other specs in this app assert on untranslated button text.
    expect(component.pendingReviewLabel).toBe('REVIEWER.REVIEW_LABEL_SUGGESTED');

    component.confirmReviewUpload();

    const call = http.expectOne('/api/reviewer/documents/d1/review');
    expect(call.request.method).toBe('POST');
    expect(call.request.body instanceof FormData).toBe(true);
    call.flush({ id: 'new-doc', label: component.pendingReviewLabel });

    expect(component.showReviewForm).toBe(false);
    http.expectOne('/api/reviewer/users').flush(SHARED);
    http.verify();
  });
});
