import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

import { QueueComponent, QueuedPosition } from './queue.component';
import { expectNoAxeViolations } from '../../../testing/a11y';

describe('QueueComponent', () => {
  let fixture: ComponentFixture<QueueComponent>;
  let component: QueueComponent;
  let http: HttpTestingController;
  let announcer: jasmine.SpyObj<LiveAnnouncer>;

  const QUEUE: QueuedPosition[] = [
    { id: 1, title: 'Accessibility Engineer', companyId: 10, companyName: 'Perlmutt', city: 'Utrecht', createdAt: '2026-08-20T09:00:00' },
    { id: 2, title: 'Technische Redakteurin', companyId: 11, companyName: 'Talwind', city: 'Bonn', createdAt: '2026-08-21T09:00:00' },
    { id: 3, title: 'Produktmanagerin', companyId: 12, companyName: 'Aurum', city: 'Frankfurt', createdAt: '2026-08-22T09:00:00' },
  ];

  /** The buttons of one action, in the order the table renders them. */
  function buttons(action: 'accept' | 'dismiss'): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll(`tbody [data-action="${action}"]`));
  }

  function flushQueue(rows: QueuedPosition[] = QUEUE): void {
    http.expectOne('/api/positions/queue').flush(rows);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    announcer = jasmine.createSpyObj('LiveAnnouncer', ['announce']);

    await TestBed.configureTestingModule({
      imports: [QueueComponent],
      providers: [
        { provide: LiveAnnouncer, useValue: announcer },
        provideTranslateService({ fallbackLang: 'en' }),
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    // Without a real string the announcement assertion could only check the
    // key, which is exactly the part that cannot be wrong - the count reaching
    // the sentence is what matters.
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {
      QUEUE: {
        ANNOUNCE_ACCEPTED: 'Accepted. {{remaining}} positions remaining.',
        ANNOUNCE_DISMISSED: 'Dismissed. {{remaining}} positions remaining.',
      },
    }, true);
    translate.use('en');

    fixture = TestBed.createComponent(QueueComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('should create', () => {
    flushQueue();
    expect(component).toBeTruthy();
  });

  it('lists what is waiting', () => {
    flushQueue();
    expect(buttons('accept').length).toBe(3);
  });

  /**
   * The point of the whole component. Focus has to be on the next row's button
   * once the acted-on row is gone - not on document.body, which is where the
   * browser puts it when the focused element is removed, and from where the
   * reader has to find the table again.
   */
  it('leaves focus on the next row after accepting', () => {
    flushQueue();
    const first = buttons('accept')[0];
    const second = buttons('accept')[1];

    first.focus();
    first.click();
    http.expectOne('/api/positions/1/accept').flush({ remaining: 2 });
    fixture.detectChanges();

    expect(document.activeElement).toBe(second);
    expect(buttons('accept').length).toBe(2);
  });

  it('leaves focus on the next row after dismissing', () => {
    flushQueue();
    const first = buttons('dismiss')[0];
    const second = buttons('dismiss')[1];

    first.focus();
    first.click();
    http.expectOne('/api/positions/1/dismiss').flush({ remaining: 2 });
    fixture.detectChanges();

    expect(document.activeElement).toBe(second);
  });

  it('falls back to the heading when the last row is acted on', () => {
    flushQueue([QUEUE[0]]);
    const only = buttons('accept')[0];

    only.focus();
    only.click();
    http.expectOne('/api/positions/1/accept').flush({ remaining: 0 });
    fixture.detectChanges();

    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('h1'));
  });

  it('announces the outcome and what is left', () => {
    flushQueue();
    buttons('accept')[0].click();
    http.expectOne('/api/positions/1/accept').flush({ remaining: 2 });
    fixture.detectChanges();

    expect(announcer.announce).toHaveBeenCalledWith('Accepted. 2 positions remaining.', 'polite');
  });

  /** A failed write must not look like a successful one. */
  it('keeps the row when the action fails', () => {
    flushQueue();
    buttons('accept')[0].click();
    http.expectOne('/api/positions/1/accept').flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(buttons('accept').length).toBe(3);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
  });

  it('shows the empty state rather than a bare table', () => {
    flushQueue([]);
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeTruthy();
  });

  it('has no axe violations', async () => {
    flushQueue();
    await expectNoAxeViolations(fixture);
  });
});
