import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { provideTranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';

import { MailApplication, SendMailComponent } from './send-mail.component';
import { ApplicationMailService, PreparedMail } from './application-mail.service';
import { ApplicationService } from '../../../services/application.service';
import { Application } from '../../../model/application';
import { expectNoAxeViolations } from '../../../../testing/a11y';

const APPLICATION: MailApplication = {
  id: 7,
  companyName: 'Acme GmbH',
  positionTitle: 'Developer',
  companyPositionId: 3,
  status: 'DRAFT',
  notes: 'Referral',
};

const MAIL: PreparedMail = {
  to: 'jobs@acme.example',
  subject: 'Bewerbung als Developer',
  body: 'Sehr geehrte Damen und Herren, …',
  shortBody: 'Kurz',
  attachments: [
    { file: new File(['%PDF'], 'Anschreiben_Acme.pdf', { type: 'application/pdf' }), bytes: new Uint8Array(4) },
    { file: new File(['%PDF'], 'Lebenslauf.pdf', { type: 'application/pdf' }), bytes: new Uint8Array(4) },
  ],
  totalBytes: 1_900_000,
};

describe('SendMailComponent', () => {
  let mails: jasmine.SpyObj<ApplicationMailService>;
  let applications: jasmine.SpyObj<ApplicationService>;
  let announcer: jasmine.SpyObj<LiveAnnouncer>;
  let closed: boolean[];

  function create(canShare: boolean, prepared = of(MAIL) as any): ComponentFixture<SendMailComponent> {
    mails.prepare.and.returnValue(prepared);
    mails.canShareFiles.and.returnValue(canShare);
    const fixture = TestBed.createComponent(SendMailComponent);
    fixture.componentRef.setInput('application', APPLICATION);
    fixture.componentRef.setInput('template', { id: 'tpl-1', label: 'DIN 5008', provider: 'HTML' });
    fixture.componentRef.setInput('enclosures', []);
    fixture.componentInstance.closed.subscribe(sent => closed.push(sent));
    fixture.detectChanges();
    return fixture;
  }

  function routeIds(fixture: ComponentFixture<SendMailComponent>): string[] {
    return Array.from<HTMLInputElement>(fixture.nativeElement.querySelectorAll('input[type="radio"]')).map(r => r.id);
  }

  function button(fixture: ComponentFixture<SendMailComponent>, key: string): HTMLButtonElement {
    const found = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
      .find(b => b.textContent!.includes(key));
    if (!found) throw new Error(`no button labelled ${key}`);
    return found;
  }

  beforeEach(async () => {
    mails = jasmine.createSpyObj('ApplicationMailService', ['prepare', 'canShareFiles', 'share', 'eml', 'mailto']);
    mails.mailto.and.returnValue({ url: 'mailto:jobs@acme.example?subject=x&body=y', short: false });
    mails.eml.and.returnValue(new Blob(['eml']));
    applications = jasmine.createSpyObj('ApplicationService', ['update']);
    applications.update.and.returnValue(of({} as Application));
    announcer = jasmine.createSpyObj('LiveAnnouncer', ['announce']);
    closed = [];

    await TestBed.configureTestingModule({
      imports: [SendMailComponent],
      providers: [
        { provide: ApplicationMailService, useValue: mails },
        { provide: ApplicationService, useValue: applications },
        { provide: LiveAnnouncer, useValue: announcer },
        provideTranslateService({ fallbackLang: 'en' }),
      ],
    }).compileComponents();
  });

  it('prepares the attachments as soon as it opens and says how many are ready', () => {
    const fixture = create(false);

    expect(mails.prepare).toHaveBeenCalledWith(7, jasmine.objectContaining({ id: 'tpl-1' }), []);
    expect(fixture.componentInstance.step()).toBe('READY');
    expect(announcer.announce).toHaveBeenCalledWith('APPLICATIONS.MAIL_READY', 'polite');
    expect(fixture.nativeElement.querySelectorAll('.attachments li').length).toBe(2);
  });

  it('shows a live loading status while the files arrive', () => {
    const fixture = create(false, new Subject<PreparedMail>());

    expect(fixture.nativeElement.querySelector('[role="status"]')).not.toBeNull();
  });

  it('recommends sharing first where the device can share files', () => {
    const fixture = create(true);

    expect(routeIds(fixture)).toEqual(['send-mail-route-SHARE', 'send-mail-route-EML', 'send-mail-route-MAILTO']);
    expect(fixture.componentInstance.route()).toBe('SHARE');
  });

  it('recommends the .eml draft where sharing files is unavailable, and does not offer sharing', () => {
    const fixture = create(false);

    expect(routeIds(fixture)).toEqual(['send-mail-route-EML', 'send-mail-route-MAILTO']);
    expect(fixture.componentInstance.route()).toBe('EML');
  });

  it('calls share() synchronously in the click, keeping the user activation', () => {
    mails.share.and.returnValue(new Promise(() => {}));
    const fixture = create(true);

    button(fixture, 'APPLICATIONS.MAIL_SHARE').click();

    expect(mails.share).toHaveBeenCalledWith(MAIL);
  });

  it('stays quiet when the share sheet is cancelled', async () => {
    mails.share.and.returnValue(Promise.reject(new DOMException('cancelled', 'AbortError')));
    const fixture = create(true);

    fixture.componentInstance.share();
    await fixture.whenStable();

    expect(fixture.componentInstance.shareFailed()).toBeFalse();
    expect(fixture.componentInstance.step()).toBe('READY');
  });

  it('falls back to the .eml draft when sharing fails for another reason', async () => {
    mails.share.and.returnValue(Promise.reject(new DOMException('denied', 'NotAllowedError')));
    const fixture = create(true);

    fixture.componentInstance.share();
    await fixture.whenStable();

    expect(fixture.componentInstance.shareFailed()).toBeTrue();
    expect(fixture.componentInstance.routes()).toEqual(['EML', 'MAILTO']);
    expect(fixture.componentInstance.route()).toBe('EML');
  });

  it('asks whether the mail went out once the .eml is downloaded', async () => {
    spyOn(URL, 'createObjectURL').and.returnValue('blob:stub');
    spyOn(URL, 'revokeObjectURL');
    const saved: string[] = [];
    spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
      saved.push(this.download);
    });
    const fixture = create(false);

    button(fixture, 'APPLICATIONS.MAIL_EML_DOWNLOAD').click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(mails.eml).toHaveBeenCalledWith(MAIL, undefined);
    expect(saved).toEqual(['APPLICATIONS.MAIL_EML_FILENAME']);
    expect(fixture.componentInstance.step()).toBe('CONFIRM');
    expect(document.activeElement?.id).toBe('send-mail-confirm');
    await expectNoAxeViolations(fixture);
  });

  it('marks a draft as sent today on "yes", keeping the notes', () => {
    const fixture = create(false);
    fixture.componentInstance.handedOver();
    fixture.detectChanges();

    button(fixture, 'APPLICATIONS.MAIL_CONFIRM_YES').click();

    const today = new Date();
    const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    expect(applications.update).toHaveBeenCalledWith(7, {
      companyPositionId: 3, status: 'SENT', appliedDate: iso, notes: 'Referral',
    });
    expect(closed).toEqual([true]);
  });

  it('does not set a later status back to SENT', () => {
    const fixture = create(false);
    fixture.componentRef.setInput('application', { ...APPLICATION, status: 'INTERVIEW_SCHEDULED' });
    fixture.detectChanges();

    fixture.componentInstance.markSent();

    expect(applications.update.calls.mostRecent().args[1].status).toBe('INTERVIEW_SCHEDULED');
  });

  it('changes nothing on "not yet"', () => {
    const fixture = create(false);
    fixture.componentInstance.handedOver();
    fixture.detectChanges();

    button(fixture, 'APPLICATIONS.MAIL_CONFIRM_NO').click();

    expect(applications.update).not.toHaveBeenCalled();
    expect(closed).toEqual([false]);
  });

  it('reports a failed save and keeps the question open', () => {
    applications.update.and.returnValue(throwError(() => new Error('500')));
    const fixture = create(false);
    fixture.componentInstance.handedOver();
    fixture.detectChanges();

    button(fixture, 'APPLICATIONS.MAIL_CONFIRM_YES').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(closed).toEqual([]);
  });

  it('offers a retry when the attachments cannot be loaded', () => {
    const fixture = create(false, throwError(() => new Error('502')));

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    mails.prepare.and.returnValue(of(MAIL));
    button(fixture, 'APPLICATIONS.MAIL_RETRY').click();

    expect(fixture.componentInstance.step()).toBe('READY');
  });

  it('lists every attachment for download on the mailto route', async () => {
    const fixture = create(false);
    fixture.componentInstance.route.set('MAILTO');
    fixture.detectChanges();

    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a[href^="mailto:"]');
    expect(link).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.downloads button').length).toBe(2);
    await expectNoAxeViolations(fixture);
  });

  it('starts over when the open panel is pointed at another application', () => {
    const fixture = create(false);
    mails.prepare.calls.reset();

    fixture.componentRef.setInput('application', { ...APPLICATION, id: 9, companyName: 'Beta AG' });
    fixture.detectChanges();

    expect(mails.prepare).toHaveBeenCalledOnceWith(9, jasmine.anything(), []);
  });

  for (const canShare of [true, false]) {
    it(`has no axe-detectable violations with the routes listed (share ${canShare})`, async () => {
      const fixture = create(canShare);
      await expectNoAxeViolations(fixture);
    });
  }
});
