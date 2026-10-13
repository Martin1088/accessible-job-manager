import {
  ChangeDetectionStrategy, Component, ElementRef, Injector, OnDestroy, OnInit,
  afterNextRender, booleanAttribute, computed, inject, input, signal, viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Subscription, catchError, forkJoin, of } from 'rxjs';

import { HttpFailure, describeHttpFailure } from '../../core/http-error';
import { DEMO_MODE } from '../../demo/demo-mode';
import { CvMatchResult, PostingSource, SkillAssessment, SkillStatus } from '../../model/cv-match';
import { DefaultDocument, Document, defaultFor, uiToLetterLanguage } from '../../model/document';
import { CvMatchService } from '../../services/cv-match.service';
import { DocumentService } from '../../services/document.service';
import { ErrorTextComponent } from '../../shared/error-text/error-text.component';

export interface SkillGroup {
  status: SkillStatus;
  skills: SkillAssessment[];
}

/**
 * The order the groups are read in. What is missing comes first - it is the
 * question the person came here with - and what is already met comes last.
 */
const GROUP_ORDER: readonly SkillStatus[] = ['MISSING', 'PARTIAL', 'MET'];

/**
 * Holds one of the user's CVs against a job posting and shows, requirement by
 * requirement, what the CV covers and what is missing.
 *
 * Presentation only. The level, the statuses and every sentence come from the
 * server; this groups and orders them, and decides nothing about the fit -
 * a score computed here would be a second opinion that disagrees with the first.
 *
 * Built as the demo's showcase first: only `DemoBackend` answers
 * `/api/matching` so far.
 *
 * Two places show it. As its own page (`/matching`) it is the page's `<main>`
 * and `<h1>`. `embedded` puts it on the demo's user home instead, as one more
 * sheet under the posting import: no second `<main>`, and every heading one
 * level deeper, so the home page's outline stays a single tree.
 */
@Component({
  selector: 'app-cv-match',
  imports: [TranslatePipe, RouterLink, NgTemplateOutlet, ErrorTextComponent],
  templateUrl: './cv-match.component.html',
  styleUrl: './cv-match.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CvMatchComponent implements OnInit, OnDestroy {

  private readonly documents = inject(DocumentService);
  private readonly matching = inject(CvMatchService);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly translate = inject(TranslateService);
  private readonly injector = inject(Injector);
  readonly demoMode = inject(DEMO_MODE);

  /** On the home page rather than a page of its own - see the class comment. */
  readonly embedded = input(false, { transform: booleanAttribute });

  private readonly resultHeading = viewChild<ElementRef<HTMLElement>>('resultHeading');
  private running?: Subscription;

  // --- Input --------------------------------------------------------
  readonly sourceKind = signal<PostingSource['kind']>('url');
  readonly postingUrl = signal('');
  readonly postingText = signal('');

  readonly cvs = signal<Document[] | null>(null);
  readonly cvsError = signal(false);
  readonly cvId = signal<string | null>(null);

  // --- Run ----------------------------------------------------------
  readonly busy = signal(false);
  readonly failure = signal<HttpFailure | null>(null);
  readonly result = signal<CvMatchResult | null>(null);

  readonly posting = computed<PostingSource | null>(() => {
    if (this.sourceKind() === 'url') {
      const url = this.postingUrl().trim();
      return url ? { kind: 'url', url } : null;
    }
    const text = this.postingText().trim();
    return text ? { kind: 'text', text } : null;
  });

  readonly canRun = computed(() => !this.busy() && !!this.cvId() && !!this.posting());

  readonly groups = computed<SkillGroup[]>(() => {
    const skills = this.result()?.skills ?? [];
    return GROUP_ORDER
      .map(status => ({
        status,
        // Within a group the requirements come before the nice-to-haves.
        skills: skills
          .filter(s => s.status === status)
          .sort((a, b) => Number(a.importance !== 'REQUIRED') - Number(b.importance !== 'REQUIRED')),
      }))
      .filter(group => group.skills.length > 0);
  });

  readonly counts = computed(() => {
    const skills = this.result()?.skills ?? [];
    const count = (status: SkillStatus) => skills.filter(s => s.status === status).length;
    return { total: skills.length, met: count('MET'), partial: count('PARTIAL'), missing: count('MISSING') };
  });

  /** Set only when the model answered in a language other than the interface's. */
  readonly resultLang = computed(() => {
    const language = this.result()?.language;
    return language && language !== this.translate.currentLang() ? language : null;
  });

  readonly selectedCv = computed(() => this.cvs()?.find(cv => cv.id === this.cvId()) ?? null);

  ngOnInit(): void {
    forkJoin({
      cvs: this.documents.getAll('CV'),
      // Only a preselection: without the defaults the newest CV is chosen, as before.
      defaults: this.documents.getDefaults().pipe(catchError(() => of([] as DefaultDocument[]))),
    }).subscribe({
      next: ({ cvs, defaults }) => {
        // Newest first: the list reads from the one most likely current.
        const sorted = [...cvs].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
        this.cvs.set(sorted);
        this.cvId.set(this.preselect(sorted, defaults));
      },
      error: () => this.cvsError.set(true),
    });
  }

  /**
   * The user's default CV in the interface language, else any default CV, else
   * the newest. The interface language is the best guess there is before a
   * posting has been read - the posting's own language is only known afterwards.
   */
  private preselect(cvs: Document[], defaults: DefaultDocument[]): string | null {
    const present = (id: string | null) => (id && cvs.some(cv => cv.id === id) ? id : null);
    const inInterfaceLanguage = defaultFor(defaults, 'CV', uiToLetterLanguage(this.translate.currentLang()));
    const anyDefault = defaults.find(d => d.type === 'CV')?.document.id ?? null;
    return present(inInterfaceLanguage) ?? present(anyDefault) ?? cvs[0]?.id ?? null;
  }

  ngOnDestroy(): void {
    this.running?.unsubscribe();
  }

  /** A quote's own language, when it differs from the language around it. */
  quoteLang(language: string | null | undefined): string | null {
    const around = this.result()?.language ?? this.translate.currentLang();
    return language && language !== around ? language : null;
  }

  run(event?: Event): void {
    event?.preventDefault();
    const posting = this.posting();
    const cvId = this.cvId();
    if (!posting || !cvId || this.busy()) return;

    this.busy.set(true);
    this.failure.set(null);
    this.result.set(null);

    this.running = this.matching.match(posting, cvId, this.translate.currentLang() || 'en').subscribe({
      next: result => {
        this.result.set(result);
        this.busy.set(false);
        this.announceResult();
        // Once, after the result has rendered - so a screen reader starts reading it.
        afterNextRender(() => this.resultHeading()?.nativeElement.focus(), { injector: this.injector });
      },
      error: (err: HttpErrorResponse) => {
        this.failure.set(describeHttpFailure(err));
        this.busy.set(false);
      },
    });
  }

  cancel(): void {
    if (!this.busy()) return;
    this.running?.unsubscribe();
    this.busy.set(false);
    this.announcer.announce(this.translate.instant('MATCHING.CANCELLED'), 'polite');
  }

  private announceResult(): void {
    const result = this.result();
    if (!result) return;
    const counts = this.counts();
    this.announcer.announce(this.translate.instant('MATCHING.ANNOUNCE', {
      level: this.translate.instant('MATCHING.LEVEL_' + result.level),
      met: counts.met,
      partial: counts.partial,
      missing: counts.missing,
      total: counts.total,
    }), 'polite');
  }
}
