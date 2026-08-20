import { Component, OnInit, OnDestroy, ChangeDetectionStrategy, ElementRef, HostListener, ViewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService, UserMe } from '../../core/auth.service';
import { Company, Gender } from '../../model/company';

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

@Component({
  selector: 'app-home',
  imports: [RouterLink, FormsModule, TranslatePipe],
  templateUrl: './home.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit, OnDestroy {
  @ViewChild('jobUrlInput') jobUrlInput?: ElementRef<HTMLInputElement>;

  // Same platform gating as AppComponent's global shortcuts: Windows screen readers
  // reserve bare letters for their own browse-mode navigation.
  readonly isWindows = /Win/i.test(navigator.userAgent);

  profile: UserMe | null = null;
  profileError = false;

  jobUrl = '';
  searchingJobPosting = false;
  jobSearchError = false;
  jobPosting: JobPostingExtraction | null = null;

  searchingFullChain = false;
  fullChainError = false;
  fullChainResult: JobPostingFullChain | null = null;

  validatingSnapshot = false;
  snapshotValidateError = false;

  // The server's own reason, when it gave one. A site that refuses automated
  // access and a URL with a typo both fail here, and only the server can tell
  // them apart - the generic text below would send the user off to re-check a
  // URL that is perfectly correct.
  jobSearchErrorMessage = '';
  fullChainErrorMessage = '';
  snapshotValidateErrorMessage = '';
  private previewObjectUrl?: string;

  // Which extraction source wins for a given field when the two disagree.
  // Defaults to the full-chain result (richer parse); the user can switch a
  // field to the quick-overview value instead when they conflict.
  selectedSource: { name: 'fullChain' | 'overview'; title: 'fullChain' | 'overview'; location: 'fullChain' | 'overview' } = {
    name: 'fullChain', title: 'fullChain', location: 'fullChain',
  };

  constructor(private auth: AuthService, private router: Router, private http: HttpClient) {}

  ngOnDestroy(): void {
    this.releasePreviewObjectUrl();
  }

  // Page-scoped shortcut: jump focus into the job-posting search field.
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

    if (e.key.toLowerCase() === 's') {
      e.preventDefault();
      this.jobUrlInput.nativeElement.focus();
    }
  }

  ngOnInit(): void {
    this.auth.me$.subscribe({
      next: (me) => {
        if (!me) { this.profileError = true; return; }
        if (me.groups.includes('ADVISOR'))  { this.router.navigate(['/advisor']);   return; }
        if (me.groups.includes('REVIEWER')) { this.router.navigate(['/reviewer']);  return; }
        this.profile = me;
      },
      error: () => this.profileError = true,
    });
  }

  searchJobPosting(): void {
    if (!this.jobUrl.trim()) return;
    const url = this.jobUrl.trim();
    const params = new HttpParams().set('url', url);

    // Both extractions are kicked off in parallel and shown separately, since the
    // full-chain parse can differ from the quick overview parse and the user may
    // want to compare the two before merging one into the "new company" form.
    this.selectedSource = { name: 'fullChain', title: 'fullChain', location: 'fullChain' };
    this.snapshotValidateError = false;
    this.snapshotValidateErrorMessage = '';

    this.searchingJobPosting = true;
    this.jobSearchError = false;
    this.jobSearchErrorMessage = '';
    this.jobPosting = null;
    this.http.post<JobPostingExtraction>('/api/posting/overview', null, { params }).subscribe({
      next: (result) => {
        this.jobPosting = result;
        this.searchingJobPosting = false;
      },
      error: (err: HttpErrorResponse) => {
        this.jobSearchError = true;
        this.jobSearchErrorMessage = err.error?.message ?? '';
        this.searchingJobPosting = false;
      },
    });

    this.searchingFullChain = true;
    this.fullChainError = false;
    this.fullChainErrorMessage = '';
    this.fullChainResult = null;
    this.http.post<JobPostingFullChain>('/api/posting/full-chain', null, { params }).subscribe({
      next: (result) => {
        this.fullChainResult = result;
        this.searchingFullChain = false;
      },
      error: (err: HttpErrorResponse) => {
        this.fullChainError = true;
        this.fullChainErrorMessage = err.error?.message ?? '';
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
    this.snapshotValidateError = false;
    this.snapshotValidateErrorMessage = '';
    this.http.post('/api/posting/snapshot-validate', null, { params, responseType: 'blob' }).subscribe({
      next: (blob) => {
        this.previewObjectUrl = URL.createObjectURL(blob);
        window.open(this.previewObjectUrl, '_blank', 'noopener');
        this.validatingSnapshot = false;
      },
      error: (err: HttpErrorResponse) => {
        this.snapshotValidateError = true;
        this.validatingSnapshot = false;
        // This request asks for a blob, so its error body arrives as a Blob
        // rather than parsed JSON - the server's reason has to be read out of it.
        this.readBlobMessage(err.error).then(message => this.snapshotValidateErrorMessage = message);
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

  submitAsCompany(): void {
    const company = this.buildCompanyPrefill();
    if (!company) return;
    this.router.navigate(['/companies/new'], { state: { company, sourceJobUrl: this.jobUrl.trim() } });
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
        // posting URL the user searched for rather than leaving it blank.
        website: fcPosition?.website ?? this.jobUrl.trim() ?? undefined,
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
