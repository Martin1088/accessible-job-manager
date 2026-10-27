import {
  ChangeDetectionStrategy, Component, DestroyRef, ElementRef, Injector, OnInit,
  afterNextRender, computed, inject, input, output, signal, viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { ApplicationService } from '../../../services/application.service';
import { ApplicationRequest, ApplicationStatus } from '../../../model/application';
import { Document } from '../../../model/document';
import { saveBlob } from '../../../core/file-download';
import { ApplicationMailService, MailAttachment, PreparedMail } from './application-mail.service';
import type { TemplateOption } from '../application-list.component';

/** The three routes into the user's mail app; see `ApplicationMailService`. */
export type MailRoute = 'SHARE' | 'EML' | 'MAILTO';

/** The part of an applications-table row this panel reads. */
export interface MailApplication {
  id: number;
  companyName: string;
  positionTitle: string;
  companyPositionId: number;
  status: ApplicationStatus;
  notes?: string | null;
}

/** Receiving servers commonly refuse somewhere between 20 and 25 MB. */
export const SIZE_WARNING_BYTES = 20 * 1024 * 1024;

type Step = 'LOADING' | 'FAILED' | 'READY' | 'CONFIRM';

/**
 * "Send application by email": gathers the letter and its enclosures in the browser,
 * offers the routes this device supports with the best one first, and asks
 * afterwards whether the mail went out - AJM cannot see that, so it never assumes it.
 *
 * Every step moves focus to its own heading and says what changed, so the flow is
 * the same with VoiceOver or a braille line as with a mouse.
 */
@Component({
  selector: 'app-send-mail',
  imports: [TranslatePipe],
  templateUrl: './send-mail.component.html',
  styleUrl: './send-mail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SendMailComponent implements OnInit {
  readonly application = input.required<MailApplication>();
  readonly template = input.required<TemplateOption>();
  readonly enclosures = input<Document[]>([]);
  /** The letter's language as a BCP 47 tag, for the HTML part's `lang`. */
  readonly letterLang = input<string | undefined>(undefined);
  /** `true` once the application was marked as sent, so the list reloads it. */
  readonly closed = output<boolean>();

  private readonly mails = inject(ApplicationMailService);
  private readonly applications = inject(ApplicationService);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly translate = inject(TranslateService);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private readonly confirmHeading = viewChild<ElementRef<HTMLElement>>('confirmHeading');

  readonly step = signal<Step>('LOADING');
  readonly mail = signal<PreparedMail | null>(null);
  readonly route = signal<MailRoute>('EML');
  readonly routes = signal<MailRoute[]>(['EML', 'MAILTO']);
  readonly shareFailed = signal(false);
  readonly saving = signal(false);
  readonly saveFailed = signal(false);

  readonly tooLarge = computed(() => (this.mail()?.totalBytes ?? 0) >= SIZE_WARNING_BYTES);
  readonly size = computed(() => formatMegabytes(this.mail()?.totalBytes ?? 0, this.translate.currentLang()));
  readonly mailto = computed(() => {
    const mail = this.mail();
    return mail ? this.mails.mailto(mail) : null;
  });

  ngOnInit(): void {
    this.prepare();
    afterNextRender(() => this.heading()?.nativeElement.focus(), { injector: this.injector });
  }

  prepare(): void {
    this.step.set('LOADING');
    this.mails.prepare(this.application().id, this.template(), this.enclosures())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: mail => {
          this.mail.set(mail);
          const routes: MailRoute[] = this.mails.canShareFiles(mail) ? ['SHARE', 'EML', 'MAILTO'] : ['EML', 'MAILTO'];
          this.routes.set(routes);
          this.route.set(routes[0]);
          this.step.set('READY');
          this.announce('APPLICATIONS.MAIL_READY', { count: mail.attachments.length, size: this.size() });
        },
        // The FAILED step's role="alert" says it; announcing too would say it twice.
        error: () => this.step.set('FAILED'),
      });
  }

  /**
   * `share()` runs before anything else in the handler - the share sheet needs this
   * click's user activation, which an earlier `await` can use up.
   */
  share(): void {
    const mail = this.mail();
    if (!mail) return;
    this.mails.share(mail).then(
      () => this.handedOver(),
      (error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        this.shareFailed.set(true);
        this.routes.set(['EML', 'MAILTO']);
        this.route.set('EML');
      });
  }

  downloadEml(): void {
    const mail = this.mail();
    if (!mail) return;
    const company = this.application().companyName.trim().replace(/[\\/:*?"<>|\s]+/g, '_');
    saveBlob(this.mails.eml(mail, this.letterLang()),
      this.translate.instant('APPLICATIONS.MAIL_EML_FILENAME', { company }));
    this.announce('APPLICATIONS.MAIL_EML_SAVED');
    this.handedOver();
  }

  downloadAttachment(attachment: MailAttachment): void {
    saveBlob(attachment.file, attachment.file.name);
  }

  copy(value: string, announcement: string): void {
    navigator.clipboard.writeText(value).then(
      () => this.announce(announcement),
      () => this.announce('APPLICATIONS.MAIL_COPY_FAILED'));
  }

  /** After the hand-over, ask - whatever route was taken, the mail app has the last word. */
  handedOver(): void {
    this.step.set('CONFIRM');
    afterNextRender(() => this.confirmHeading()?.nativeElement.focus(), { injector: this.injector });
  }

  /**
   * Sets the applied date to today and moves a draft to SENT; a later status
   * (an interview already scheduled) is not set back.
   */
  markSent(): void {
    const app = this.application();
    const request: ApplicationRequest = {
      companyPositionId: app.companyPositionId,
      status: app.status === 'DRAFT' ? 'SENT' : app.status,
      appliedDate: today(),
      notes: app.notes || null,
    };
    this.saving.set(true);
    this.saveFailed.set(false);
    this.applications.update(app.id, request).subscribe({
      next: () => {
        this.saving.set(false);
        this.announce('APPLICATIONS.MAIL_MARKED_SENT', { company: app.companyName });
        this.close(true);
      },
      error: () => {
        this.saving.set(false);
        this.saveFailed.set(true);
      },
    });
  }

  /** Drops the attachments - they live only in memory and only for this panel. */
  close(sent = false): void {
    this.mail.set(null);
    this.closed.emit(sent);
  }

  routeHelpId(route: MailRoute): string {
    return `send-mail-route-${route.toLowerCase()}-help`;
  }

  private announce(key: string, params?: Record<string, unknown>): void {
    this.announcer.announce(this.translate.instant(key, params), 'polite');
  }
}

function today(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function formatMegabytes(bytes: number, lang: string | null | undefined): string {
  const megabytes = bytes / (1024 * 1024);
  return new Intl.NumberFormat(lang || 'en', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(megabytes) + ' MB';
}
