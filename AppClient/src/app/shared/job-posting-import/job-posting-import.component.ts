import { Component, ChangeDetectionStrategy, ElementRef, EventEmitter, HostListener, Input, OnDestroy, Output, ViewChild, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { HttpFailure, describeHttpFailure } from '../../core/http-error';
import { Company, Gender } from '../../model/company';
import { JobPostingImportStore } from '../../services/job-posting-import.store';
import { ErrorTextComponent } from '../error-text/error-text.component';
import { normalizeJobUrl } from './job-url';

interface JobPostingExtraction {
  title: string | null;
  company: string | null;
  location: string | null;
  employmentType: string | null;
}

interface CompanyPositionExtraction {
  title: string | null;
  contactGender: 'MALE' | 'FEMALE' | 'TEAM' | null;
  contactTitle: string | null;
  contactLastName: string | null;
  email: string | null;
  website: string | null;
  notes: string | null;
}

interface CompanyLocationExtraction {
  street: string | null;
  city: string | null;
}

interface CompanyExtraction {
  name: string | null;
  locations: CompanyLocationExtraction[];
  positions: CompanyPositionExtraction[];
}

interface JobPostingFullChain {
  company: CompanyExtraction | null;
  sourceJobId: string | null;
  postedAt: string | null;
  deadline: string | null;
  employmentType: string | null;
}

/** What a finished import hands to whichever page hosted it. */
export interface ImportedPosting {
  company: Company;
  /** The URL the posting came from, or '' on the paste-text and PDF paths. */
  sourceJobUrl: string;
}

/**
 * Getting a job posting into the system: a URL, or - where the URL cannot be
 * fetched - a printed PDF or pasted text, through to the extracted fields the
 * caller turns into a company.
 *
 * Hosted by two pages that end differently. The user's home page sends the
 * result to the prefilled company form so the person can correct it before
 * saving; the advisor's import page saves it and suggests it in one step. Only
 * that ending differs, which is why it is an output rather than two copies of
 * the import: the fetch paths, the fallbacks and the extraction display drifted
 * apart the moment they were duplicated.
 *
 * The PDF, when there was one, stays in `JobPostingImportStore` for whoever
 * files it against the position that does not exist yet at this point.
 */
@Component({
  selector: 'app-job-posting-import',
  standalone: true,
  imports: [FormsModule, TranslatePipe, ErrorTextComponent],
  templateUrl: './job-posting-import.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './job-posting-import.component.scss'
})
export class JobPostingImportComponent implements OnDestroy {
  @ViewChild('jobUrlInput') jobUrlInput?: ElementRef<HTMLInputElement>;

  /** Translation key for the button that finishes the import. */
  @Input() submitLabelKey = 'HOME.USE_FOR_NEW_COMPANY';

  /** Set while the host is saving, to keep the finish button inert. */
  @Input() submitting = false;

  @Output() readonly imported = new EventEmitter<ImportedPosting>();

  // Same platform gating as AppComponent's global shortcuts: Windows screen
  // readers reserve bare letters for their own browse-mode navigation.
  readonly isWindows = /Win/i.test(navigator.userAgent);

  jobUrl = '';

  /** Set when a paste was rewritten, so the field can say so rather than silently changing. */
  urlCleaned = false;

  /**
   * Set when a paste held no link at all - the moment the user needs to be told
   * the text path exists, since they have the posting text on the clipboard
   * right then.
   */
  pastedTextWithoutUrl = false;

  postingText = '';
  searchingText = false;

  postingPdfName = '';
  searchingPdf = false;

  searchingJobPosting = false;
  jobPosting: JobPostingExtraction | null = null;

  searchingFullChain = false;
  fullChainResult: JobPostingFullChain | null = null;

  validatingSnapshot = false;

  // How each request failed, or null while it has not. The server's own reason
  // is only one of the possibilities: a site that refuses automated access, a
  // URL with a typo, an expired session and a backend that is not running all
  // arrive here, and only the classification tells them apart - a single
  // per-endpoint sentence would send the user off to re-check a URL that is
  // perfectly correct. See `describeHttpFailure`.
  textSearchFailure: HttpFailure | null = null;
  pdfSearchFailure: HttpFailure | null = null;
  jobSearchFailure: HttpFailure | null = null;
  fullChainFailure: HttpFailure | null = null;
  snapshotValidateFailure: HttpFailure | null = null;
  private previewObjectUrl?: string;

  // Which extraction source wins for a given field when the two disagree.
  // Defaults to the full-chain result (richer parse); the user can switch a
  // field to the quick-overview value instead when they conflict.
  selectedSource: { name: 'fullChain' | 'overview'; title: 'fullChain' | 'overview'; location: 'fullChain' | 'overview' } = {
    name: 'fullChain', title: 'fullChain', location: 'fullChain',
  };

  private readonly announcer = inject(LiveAnnouncer);
  private readonly translate = inject(TranslateService);
  private readonly importStore = inject(JobPostingImportStore);
  private readonly http = inject(HttpClient);

  /**
   * Extraction from a PDF the user printed from the posting page.
   *
   * On success the file is held for whoever files it as the position's
   * snapshot once the position exists. That replaces the URL path's Gotenberg
   * re-fetch, which cannot work here for the same reason the extraction could
   * not: a board that refuses this server refuses it whether the request comes
   * from the parser or from Chromium.
   */
  onPostingPdfSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // Cleared so picking the same file twice still raises `change` - a retry
    // after a failed extraction is otherwise silently ignored.
    input.value = '';
    if (!file) return;

    this.searchingPdf = true;
    this.pdfSearchFailure = null;
    this.postingPdfName = file.name;
    this.jobPosting = null;
    this.jobSearchFailure = null;
    this.textSearchFailure = null;
    this.resetFullChainForTextPath();

    const form = new FormData();
    form.append('file', file);

    this.http.post<JobPostingExtraction>('/api/posting/overview-pdf', form).subscribe({
      next: (result) => {
        this.jobPosting = result;
        this.importStore.hold(file);
        this.searchingPdf = false;
        this.announcer.announce(this.translate.instant('HOME.PDF_EXTRACTED_ANNOUNCE'), 'polite');
      },
      error: (err: HttpErrorResponse) => {
        this.pdfSearchFailure = describeHttpFailure(err);
        this.importStore.clear();
        this.searchingPdf = false;
      },
    });
  }

  /**
   * Neither text nor PDF can fill the full-chain column: that extraction
   * follows links out of the fetched page, and there is no page here.
   */
  private resetFullChainForTextPath(): void {
    this.fullChainResult = null;
    this.fullChainFailure = null;
    this.selectedSource = { name: 'overview', title: 'overview', location: 'overview' };
  }

  /**
   * Rewrites a pasted share-sheet blob down to the link inside it.
   *
   * A phone share button produces a title line and the same URL twice; the
   * whole thing reaches the backend as one string and fails URL parsing on the
   * first space, which surfaces as "Malformed URL" and blames the user for a
   * link that was fine.
   *
   * The default paste is allowed to stand whenever there is nothing to change,
   * so undo history survives every ordinary paste. When the text holds no link
   * at all, it is left alone too - that is posting text, and the flag steers
   * the user to the path that takes it.
   */
  onJobUrlPaste(event: ClipboardEvent): void {
    const pasted = event.clipboardData?.getData('text') ?? '';
    if (!pasted.trim()) return;

    this.urlCleaned = false;
    this.pastedTextWithoutUrl = false;

    const normalized = normalizeJobUrl(pasted);
    if (normalized === null) {
      this.pastedTextWithoutUrl = true;
      return;
    }
    if (normalized === pasted) return;

    event.preventDefault();
    this.jobUrl = normalized;
    this.urlCleaned = true;
    this.announcer.announce(this.translate.instant('HOME.URL_CLEANED'), 'polite');
  }

  onJobUrlInput(): void {
    this.urlCleaned = false;
    this.pastedTextWithoutUrl = false;
  }

  /**
   * Extraction from text the user pasted, for a posting the server cannot
   * fetch. Only the overview column can be filled: the full-chain extraction
   * follows links out of the page, so it has nothing to work from here.
   */
  searchFromText(): void {
    const text = this.postingText.trim();
    if (!text) return;

    this.searchingText = true;
    this.textSearchFailure = null;
    this.jobPosting = null;
    this.jobSearchFailure = null;
    this.pdfSearchFailure = null;
    // The text path files no snapshot, so any PDF held from an earlier attempt
    // must not be filed against the company this extraction goes on to create.
    this.importStore.clear();
    this.postingPdfName = '';
    this.resetFullChainForTextPath();

    this.http.post<JobPostingExtraction>('/api/posting/overview-text', { text }).subscribe({
      next: (result) => {
        this.jobPosting = result;
        this.searchingText = false;
      },
      error: (err: HttpErrorResponse) => {
        this.textSearchFailure = describeHttpFailure(err);
        this.searchingText = false;
      },
    });
  }

  ngOnDestroy(): void {
    this.releasePreviewObjectUrl();
  }

  // Page-scoped shortcut: jump focus into the job-posting URL field.
  // Distinct from AppComponent's route-navigation shortcuts.
  @HostListener('document:keydown', ['$event'])
  onKeydown(e: KeyboardEvent): void {
    const tag = (e.target as HTMLElement).tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
    if (!this.jobUrlInput) return;

    if (this.isWindows) {
      if (!e.altKey || !e.shiftKey || e.ctrlKey || e.metaKey) return;
    } else {
      if (e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return;
    }

    if (e.key.toLowerCase() === 'i') {
      e.preventDefault();
      this.jobUrlInput.nativeElement.focus();
    }
  }

  searchJobPosting(): void {
    if (!this.jobUrl.trim()) return;
    const url = this.jobUrl.trim();
    const params = new HttpParams().set('url', url);

    // Both extractions are kicked off in parallel and shown separately, since the
    // full-chain parse can differ from the quick overview parse and the user may
    // want to compare the two before merging one into the "new company" form.
    this.selectedSource = { name: 'fullChain', title: 'fullChain', location: 'fullChain' };
    this.snapshotValidateFailure = null;
    // A URL search means the snapshot will be rendered from that URL, so a PDF
    // held from an earlier attempt must not also be filed against the company.
    this.importStore.clear();
    this.postingPdfName = '';
    this.pdfSearchFailure = null;

    this.searchingJobPosting = true;
    this.jobSearchFailure = null;
    this.jobPosting = null;
    this.http.post<JobPostingExtraction>('/api/posting/overview', null, { params }).subscribe({
      next: (result) => {
        this.jobPosting = result;
        this.searchingJobPosting = false;
      },
      error: (err: HttpErrorResponse) => {
        this.jobSearchFailure = describeHttpFailure(err);
        this.searchingJobPosting = false;
      },
    });

    this.searchingFullChain = true;
    this.fullChainFailure = null;
    this.fullChainResult = null;
    this.http.post<JobPostingFullChain>('/api/posting/full-chain', null, { params }).subscribe({
      next: (result) => {
        this.fullChainResult = result;
        this.searchingFullChain = false;
      },
      error: (err: HttpErrorResponse) => {
        this.fullChainFailure = describeHttpFailure(err);
        this.searchingFullChain = false;
      },
    });
  }

  // Stateless PDF preview via /api/posting/snapshot-validate: renders the URL
  // through Gotenberg and streams the PDF back without persisting anything,
  // so the user can confirm the page converts cleanly before creating a company.
  // Opened in a new tab (rather than an embedded iframe) since the rendered
  // page is full-size and a shrunk-down inline preview isn't readable.
  previewSnapshot(): void {
    if (!this.jobUrl.trim()) return;
    const params = new HttpParams().set('url', this.jobUrl.trim());

    this.releasePreviewObjectUrl();
    this.validatingSnapshot = true;
    this.snapshotValidateFailure = null;
    this.http.post('/api/posting/snapshot-validate', null, { params, responseType: 'blob' }).subscribe({
      next: (blob) => {
        this.previewObjectUrl = URL.createObjectURL(blob);
        window.open(this.previewObjectUrl, '_blank', 'noopener');
        this.validatingSnapshot = false;
      },
      error: (err: HttpErrorResponse) => {
        this.validatingSnapshot = false;
        // This request asks for a blob, so its error body arrives as a Blob
        // rather than parsed JSON - the server's reason has to be read out of
        // it before the failure can be classified.
        this.readBlobMessage(err.error).then(message => {
          this.snapshotValidateFailure = describeHttpFailure(err, message);
        });
      },
    });
  }

  private async readBlobMessage(body: unknown): Promise<string> {
    if (!(body instanceof Blob)) return '';
    try {
      return JSON.parse(await body.text())?.message ?? '';
    } catch {
      return '';
    }
  }

  private releasePreviewObjectUrl(): void {
    if (this.previewObjectUrl) {
      URL.revokeObjectURL(this.previewObjectUrl);
      this.previewObjectUrl = undefined;
    }
  }

  get nameConflict(): boolean {
    return this.hasConflict(this.jobPosting?.company, this.fullChainResult?.company?.name);
  }

  get titleConflict(): boolean {
    return this.hasConflict(this.jobPosting?.title, this.fullChainResult?.company?.positions?.[0]?.title);
  }

  get locationConflict(): boolean {
    return this.hasConflict(this.jobPosting?.location, this.fullChainResult?.company?.locations?.[0]?.city);
  }

  get hasAnyResult(): boolean {
    return !!this.jobPosting || !!this.fullChainResult;
  }

  private hasConflict(overviewValue: string | null | undefined, fullChainValue: string | null | undefined): boolean {
    const a = overviewValue?.trim();
    const b = fullChainValue?.trim();
    return !!a && !!b && a.toLowerCase() !== b.toLowerCase();
  }

  submit(): void {
    const company = this.buildCompanyPrefill();
    if (!company) return;
    this.imported.emit({ company, sourceJobUrl: this.jobUrl.trim() });
  }

  private buildCompanyPrefill(): Company | null {
    if (!this.fullChainResult && !this.jobPosting) return null;

    const fcCompany = this.fullChainResult?.company;
    const fcLocation = fcCompany?.locations?.[0];
    const fcPosition = fcCompany?.positions?.[0];

    const name = this.pick(this.selectedSource.name, this.jobPosting?.company, fcCompany?.name);
    const title = this.pick(this.selectedSource.title, this.jobPosting?.title, fcPosition?.title);
    const city = this.pick(this.selectedSource.location, this.jobPosting?.location, fcLocation?.city);

    return {
      name: name ?? '',
      locations: [{
        street: fcLocation?.street ?? '',
        city: city ?? '',
      }],
      positions: [{
        title: title ?? '',
        contactGender: this.mapGender(fcPosition?.contactGender),
        contactTitle: fcPosition?.contactTitle ?? undefined,
        contactLastName: fcPosition?.contactLastName ?? undefined,
        email: fcPosition?.email ?? undefined,
        // If neither extraction found a company website, fall back to the job
        // posting URL the user searched for rather than leaving it blank. `||`,
        // not `??`: on the paste-the-text path there is no URL at all, and `??`
        // would put an empty string in the field instead of leaving it unset.
        website: (fcPosition?.website || this.jobUrl.trim()) || undefined,
        notes: fcPosition?.notes ?? undefined,
      }],
    };
  }

  private pick(source: 'fullChain' | 'overview', overviewValue: string | null | undefined, fullChainValue: string | null | undefined): string | null | undefined {
    return source === 'overview'
      ? (overviewValue ?? fullChainValue)
      : (fullChainValue ?? overviewValue);
  }

  private mapGender(backendGender: 'MALE' | 'FEMALE' | 'TEAM' | null | undefined): Gender | undefined {
    switch (backendGender) {
      case 'MALE': return 'MALE';
      case 'FEMALE': return 'FEMALE';
      case 'TEAM': return 'DIVERSE';
      default: return undefined;
    }
  }
}
