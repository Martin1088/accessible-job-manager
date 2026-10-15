import {
  ChangeDetectionStrategy, Component, ElementRef, Injector, OnDestroy, OnInit,
  afterNextRender, booleanAttribute, computed, effect, inject, input, signal, untracked, viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { HttpFailure, describeHttpFailure } from '../../core/http-error';
import { DEMO_MODE } from '../../demo/demo-mode';
import { CvMatchResult, PostingSource, SkillAssessment, SkillStatus } from '../../model/cv-match';
import { DOCUMENT_LANGUAGE_KEY, DefaultDocument, DocumentLanguage, uiToLetterLanguage } from '../../model/document';
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

  /**
   * The posting, when the page already has it - the home page's import passes its
   * link or text, so it is not asked for twice. Without it the form asks itself.
   */
  readonly presetPosting = input<PostingSource | null>(null);

  /** Moves focus to the heading on open: set when a button just revealed the sheet. */
  readonly focusOnOpen = input(false, { transform: booleanAttribute });

  private readonly sheetHeading = viewChild<ElementRef<HTMLElement>>('sheetHeading');

  private readonly resultHeading = viewChild<ElementRef<HTMLElement>>('resultHeading');
  private running?: Subscription;

  // --- Input --------------------------------------------------------
  readonly sourceKind = signal<PostingSource['kind']>('url');
  readonly postingUrl = signal('');
  readonly postingText = signal('');

  /** The user's default documents; null while loading. */
  readonly defaults = signal<DefaultDocument[] | null>(null);
  readonly cvsError = signal(false);

  /** The interface language, kept as a signal so the CV follows a language switch. */
  private readonly uiLang = signal(this.translate.currentLang());

  /** The language a CV has to be in to be compared: the interface's. */
  readonly cvLanguage = computed<DocumentLanguage>(() => uiToLetterLanguage(this.uiLang()));

  /** Translation key of that language's name, for the sentences that mention it. */
  readonly cvLanguageKey = computed(() => DOCUMENT_LANGUAGE_KEY[this.cvLanguage()]);

  /**
   * The CV that is compared: the user's default CV in the interface language, and
   * no other. No fallback to a default in another language or to the newest upload -
   * which CV is compared is set once, on the Documents page, not guessed here.
   */
  readonly cv = computed(() =>
    this.defaults()?.find(d => d.type === 'CV' && d.language === this.cvLanguage())?.document ?? null);

  // --- Run ----------------------------------------------------------
  readonly busy = signal(false);
  readonly failure = signal<HttpFailure | null>(null);
  readonly result = signal<CvMatchResult | null>(null);

  readonly posting = computed<PostingSource | null>(() => {
    const preset = this.presetPosting();
    if (preset) return preset;
    if (this.sourceKind() === 'url') {
      const url = this.postingUrl().trim();
      return url ? { kind: 'url', url } : null;
    }
    const text = this.postingText().trim();
    return text ? { kind: 'text', text } : null;
  });

  readonly canRun = computed(() => !this.busy() && !!this.cv() && !!this.posting());

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

  constructor() {
    this.translate.onLangChange.pipe(takeUntilDestroyed()).subscribe(({ lang }) => this.uiLang.set(lang));
    // Another posting handed in: a result for the previous one must not stay on screen.
    effect(() => {
      this.presetPosting();
      untracked(() => this.reset());
    });
  }

  ngOnInit(): void {
    if (this.focusOnOpen()) {
      afterNextRender(() => this.focusHeading(), { injector: this.injector });
    }
    this.documents.getDefaults().subscribe({
      next: defaults => this.defaults.set(defaults),
      error: () => this.cvsError.set(true),
    });
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
    const cvId = this.cv()?.id;
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

  /** Moves focus to the sheet's heading, for the page that has just revealed or re-targeted it. */
  focusHeading(): void {
    this.sheetHeading()?.nativeElement.focus();
  }

  private reset(): void {
    this.running?.unsubscribe();
    this.busy.set(false);
    this.failure.set(null);
    this.result.set(null);
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
