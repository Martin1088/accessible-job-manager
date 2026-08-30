import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import {
  JobSearchService,
  JobSearchStatus,
  JobSearchCategory,
  JobSearchHit,
  JobSearchResults,
  JobSearchSort,
} from '../../services/job-search.service';

/**
 * The number fields are not typed `string`: Angular's number value accessor
 * writes a `number` into them once the field is touched, and `null` when it is
 * cleared again. Reading them as strings threw before the request was ever
 * sent, so both they and the text fields are read through the helpers below
 * rather than by calling String methods on them directly.
 */
interface SearchForm {
  what: string;
  whatExclude: string;
  where: string;
  distanceKm: number | string | null;
  maxDaysOld: number | string | null;
  salaryMin: number | string | null;
  fullTime: 'any' | 'true' | 'false';
  permanent: 'any' | 'true' | 'false';
  category: string;
  sortBy: JobSearchSort;
}

const EMPTY_FORM: SearchForm = {
  what: '',
  whatExclude: '',
  where: '',
  distanceKm: '',
  maxDaysOld: '',
  salaryMin: '',
  fullTime: 'any',
  permanent: 'any',
  category: '',
  sortBy: 'RELEVANCE',
};

@Component({
  selector: 'app-job-search',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './job-search.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './job-search.component.scss'
})
export class JobSearchComponent implements OnInit {

  status: JobSearchStatus | null = null;
  statusLoading = true;
  statusError = false;

  categories: JobSearchCategory[] = [];

  form: SearchForm = { ...EMPTY_FORM };

  results: JobSearchResults | null = null;
  searching = false;
  hasSearched = false;
  searchError = '';

  private readonly resultsPerPage = 20;

  constructor(private jobSearchService: JobSearchService, private translate: TranslateService) {}

  ngOnInit(): void {
    this.jobSearchService.status().subscribe({
      next: (status) => {
        this.status = status;
        this.statusLoading = false;
        if (status.configured) {
          this.jobSearchService.categories(status.country).subscribe({
            next: (categories) => this.categories = categories,
          });
        }
      },
      error: () => {
        this.statusError = true;
        this.statusLoading = false;
      },
    });
  }

  get totalPages(): number {
    if (!this.results) return 1;
    return Math.max(1, Math.ceil(this.results.totalCount / this.results.resultsPerPage));
  }

  search(page = 1): void {
    this.searchError = '';

    const what = this.text(this.form.what);
    const where = this.text(this.form.where);
    const category = this.text(this.form.category);
    if (!what && !where && !category) {
      this.searchError = this.translate.instant('JOB_SEARCH.ERROR_CRITERIA_MISSING');
      return;
    }

    this.searching = true;
    this.jobSearchService.search({
      what: what || undefined,
      whatExclude: this.text(this.form.whatExclude) || undefined,
      where: where || undefined,
      distanceKm: this.toNumber(this.form.distanceKm),
      page,
      resultsPerPage: this.resultsPerPage,
      maxDaysOld: this.toNumber(this.form.maxDaysOld),
      salaryMin: this.toNumber(this.form.salaryMin),
      fullTime: this.toBoolean(this.form.fullTime),
      permanent: this.toBoolean(this.form.permanent),
      category: category || undefined,
      sortBy: this.form.sortBy,
      country: this.status?.country,
    }).subscribe({
      next: (results) => {
        this.results = results;
        this.hasSearched = true;
        this.searching = false;
      },
      error: (err: HttpErrorResponse) => {
        this.searchError = err.error?.message ?? this.translate.instant('JOB_SEARCH.ERROR_SEARCH');
        this.searching = false;
      },
    });
  }

  nextPage(): void {
    if (this.results && this.results.page < this.totalPages) {
      this.search(this.results.page + 1);
    }
  }

  previousPage(): void {
    if (this.results && this.results.page > 1) {
      this.search(this.results.page - 1);
    }
  }

  formatDate(iso: string | null): string {
    return iso ? iso.substring(0, 10) : '—';
  }

  formatSalary(hit: JobSearchHit): string | null {
    if (hit.salaryMin == null && hit.salaryMax == null) return null;
    const format = (n: number) => Math.round(n).toLocaleString(this.translate.currentLang() || 'en');
    const range = hit.salaryMin != null && hit.salaryMax != null && hit.salaryMin !== hit.salaryMax
      ? `${format(hit.salaryMin)}–${format(hit.salaryMax)}`
      : format(hit.salaryMin ?? hit.salaryMax!);
    return hit.salaryPredicted
      ? this.translate.instant('JOB_SEARCH.SALARY_ESTIMATED', { range })
      : range;
  }

  formatContract(hit: JobSearchHit): string | null {
    const parts = [hit.contractType, hit.contractTime].filter(Boolean).map(v => this.humanize(v!));
    return parts.length ? parts.join(' · ') : null;
  }

  private humanize(value: string): string {
    const spaced = value.replace(/_/g, ' ');
    return spaced.charAt(0).toUpperCase() + spaced.slice(1);
  }

  /** Trims whatever the value accessor left behind, including a cleared field's null. */
  private text(value: string | null | undefined): string {
    return value == null ? '' : String(value).trim();
  }

  private toNumber(value: number | string | null | undefined): number | undefined {
    const trimmed = this.text(value as string | null | undefined);
    if (!trimmed) return undefined;
    const n = Number(trimmed);
    return Number.isFinite(n) ? n : undefined;
  }

  private toBoolean(value: 'any' | 'true' | 'false'): boolean | undefined {
    return value === 'any' ? undefined : value === 'true';
  }
}
