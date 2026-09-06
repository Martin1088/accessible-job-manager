import { Component, OnInit, ChangeDetectionStrategy, ElementRef, ViewChild } from '@angular/core';
import { Company, CompanyPosition } from '../../model/company';
import { CompanyService } from '../../services/company.service';
import { ActivatedRoute, Router } from '@angular/router';

import { HttpClient, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DataTableComponent, TableColumn, TableAction } from '../../shared/data-table/data-table.component';

interface JobPostingSnapshot {
  id: string;
}

interface AdvisorUser {
  userId: string;
  name: string;
  email: string;
}

type SnapshotState = 'loading' | 'ready' | 'none' | 'error';
type ViewMode = 'structure' | 'original';

function yearOf(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const y = parseInt(iso.substring(0, 4), 10);
  return isNaN(y) ? null : y;
}

function monthOf(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const m = parseInt(iso.substring(5, 7), 10);
  return isNaN(m) ? null : m;
}

@Component({
  selector: 'app-company-list',
  imports: [FormsModule, DataTableComponent, TranslatePipe],
  templateUrl: './company-list.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './company-list.component.scss'
})
export class CompanyListComponent implements OnInit {
  @ViewChild('jobPostingViewHeading') jobPostingViewHeading?: ElementRef<HTMLElement>;
  @ViewChild('suggestViewHeading') suggestViewHeading?: ElementRef<HTMLElement>;

  companies: Company[] = [];
  allRows: any[] = [];
  errorMessage = '';

  viewingRow: any = null;
  viewMode: ViewMode = 'structure';
  snapshotState: SnapshotState = 'loading';
  snapshotUrl: string | null = null;
  snapshotSrc: SafeResourceUrl | null = null;
  private lastFocusedElement: HTMLElement | null = null;

  // --- Suggest to a user (advisor catalogue only) -----------------------
  suggestingRow: any = null;
  assignedUsers: AdvisorUser[] = [];
  usersError = '';
  suggestTargetUserId = '';
  suggestMessage = '';
  suggestSubmitting = false;
  suggestError = '';
  suggestSuccess = false;
  private lastFocusedSuggestElement: HTMLElement | null = null;

  filterYear: number | '' = '';
  filterMonth: number | '' = '';

  searchField = 'all';
  searchTerm = '';

  readonly searchFields = [
    { value: 'all',           label: 'COMPANIES.SEARCH_ALL' },
    { value: 'name',          label: 'COMPANIES.COL_NAME' },
    { value: 'city',          label: 'COMPANIES.COL_CITY' },
    { value: 'positionTitle', label: 'COMPANIES.COL_POSITION' },
  ];

  readonly monthIndexes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

  columns: TableColumn[] = [
    { label: 'COMPANIES.COL_NAME',     field: 'name',          sortable: true },
    { label: 'COMPANIES.COL_CITY',     field: 'city',          sortable: true },
    { label: 'COMPANIES.COL_POSITION', field: 'positionTitle', sortable: true },
    { label: 'COMPANIES.COL_ADDED',    field: 'positionDate',  sortable: true },
  ];

  /**
   * Where "add" and "edit" navigate to. Defaults to the user's own /companies
   * tree; the advisor route supplies 'advisor/companies' via route data so
   * the same component manages the advisor's own catalogue instead - see
   * app.routes.ts.
   */
  private readonly basePath: string;

  /** Whether this is the advisor's own catalogue rather than a user's. */
  readonly isAdvisorContext: boolean;

  actions: TableAction[];

  constructor(
    private companyService: CompanyService,
    private router: Router,
    private translate: TranslateService,
    private http: HttpClient,
    private sanitizer: DomSanitizer,
    route: ActivatedRoute,
  ) {
    this.basePath = route.snapshot.data?.['companyBasePath'] ?? '/companies';
    this.isAdvisorContext = this.basePath !== '/companies';

    // "Apply for this position" only makes sense against the applicant's own
    // list - /applications is a userGuard route an advisor cannot reach, so
    // the action is left out of their catalogue entirely rather than
    // pointing somewhere it would be forbidden. "Suggest to a user" is the
    // advisor's counterpart to it: only meaningful once a position sits in
    // their own catalogue, and it is what turns a saved position into
    // something one of their users actually sees.
    const applyAction: TableAction[] = !this.isAdvisorContext ? [{
      label: 'COMPANIES.ACTION_APPLY',
      ariaLabel: (row) => this.translate.instant('COMPANIES.ACTION_APPLY_ARIA', { position: row.positionTitle, company: row.name }),
      handler: (row) => this.router.navigate(['/applications'], {
        queryParams: { positionId: row.positionId, companyName: row.name, positionTitle: row.positionTitle }
      }),
    }] : [];

    const suggestAction: TableAction[] = this.isAdvisorContext ? [{
      label: 'COMPANIES.ACTION_SUGGEST',
      ariaLabel: (row) => this.translate.instant('COMPANIES.ACTION_SUGGEST_ARIA', { position: row.positionTitle, company: row.name }),
      handler: (row) => this.openSuggest(row),
    }] : [];

    this.actions = [
      ...applyAction,
      {
        label: 'COMPANIES.ACTION_VIEW_JOB_POSTING',
        ariaLabel: (row) => this.translate.instant('COMPANIES.ACTION_VIEW_JOB_POSTING_ARIA', { position: row.positionTitle, company: row.name }),
        handler: (row) => this.viewJobPosting(row),
      },
      ...suggestAction,
      {
        label: 'COMPANIES.ACTION_EDIT',
        ariaLabel: (row) => this.translate.instant('COMPANIES.ACTION_EDIT_ARIA', { company: row.name }),
        handler: (row) => this.router.navigate([this.basePath, 'edit', row.companyId]),
      },
      {
        label: 'COMPANIES.ACTION_DELETE',
        ariaLabel: (row) => this.translate.instant('COMPANIES.ACTION_DELETE_ARIA', { company: row.name }),
        handler: (row) => this.deleteCompany(row.companyId, row.name),
      },
    ];
  }

  ngOnInit(): void {
    this.companyService.getAll().subscribe({
      next: (data) => {
        this.companies = data;
        this.allRows = this.toRows(data);
      },
      error: () => this.errorMessage = this.translate.instant('COMPANIES.ERROR_LOAD')
    });

    if (this.isAdvisorContext) {
      this.http.get<AdvisorUser[]>('/api/advisor/my-users').subscribe({
        next: (users) => this.assignedUsers = users,
        error: () => this.usersError = this.translate.instant('ADVISOR.ERROR_LOAD_USERS'),
      });
    }
  }

  get availableYears(): number[] {
    const years = new Set<number>();
    const current = new Date().getFullYear();
    years.add(current - 1);
    years.add(current);
    years.add(current + 1);
    this.allRows.forEach(r => {
      const y = yearOf(r.rawCreatedAt);
      if (y) years.add(y);
    });
    return [...years].sort((a, b) => a - b);
  }

  get filterActive(): boolean {
    return this.filterYear !== '' || this.filterMonth !== '' || this.searchTerm.trim() !== '';
  }

  clearFilter(): void {
    this.filterYear = '';
    this.filterMonth = '';
    this.searchField = 'all';
    this.searchTerm = '';
  }

  private matchesSearch(row: any, term: string): boolean {
    const fields = this.searchField === 'all'
      ? ['name', 'city', 'positionTitle']
      : [this.searchField];
    return fields.some(f => (row[f] ?? '').toString().toLowerCase().includes(term));
  }

  get filteredRows(): any[] {
    let source = this.allRows;

    if (this.filterYear !== '' || this.filterMonth !== '') {
      source = source.filter(r => {
        const iso = r.rawCreatedAt;
        if (!iso) return true; // include rows with unknown date
        if (this.filterYear !== '' && yearOf(iso) !== Number(this.filterYear)) return false;
        if (this.filterMonth !== '' && monthOf(iso) !== Number(this.filterMonth)) return false;
        return true;
      });
    }

    const term = this.searchTerm.trim().toLowerCase();
    if (term) {
      source = source.filter(r => this.matchesSearch(r, term));
    }

    return source;
  }

  create(): void {
    this.router.navigate([this.basePath, 'new']);
  }

  private deleteCompany(id: number, name: string): void {
    if (!confirm(this.translate.instant('COMPANIES.CONFIRM_DELETE', { name }))) return;
    this.companyService.delete(id).subscribe({
      next: () => {
        this.companies = this.companies.filter(c => c.id !== id);
        this.allRows = this.toRows(this.companies);
      },
      error: () => this.errorMessage = this.translate.instant('COMPANIES.ERROR_DELETE')
    });
  }

  // Opens an inline panel toggling between the position's own structured data
  // (always available, since it's the row itself) and the PDF snapshot of the
  // original posting, if one was generated when the company was created from a
  // parsed job posting. Structure is the default view: it's the one every
  // screen reader can actually read, unlike an embedded PDF.
  viewJobPosting(row: any): void {
    this.lastFocusedElement = document.activeElement as HTMLElement | null;
    this.viewingRow = row;
    this.viewMode = 'structure';
    this.snapshotState = 'loading';
    this.snapshotUrl = null;
    this.snapshotSrc = null;

    setTimeout(() => this.jobPostingViewHeading?.nativeElement.focus());

    const params = new HttpParams().set('companyPositionId', row.positionId);
    this.http.get<JobPostingSnapshot[]>('/api/posting/snapshot', { params }).subscribe({
      next: (docs) => {
        if (!docs.length) {
          this.snapshotState = 'none';
          return;
        }
        this.snapshotUrl = `/api/posting/snapshot/${docs[0].id}`;
        this.snapshotSrc = this.sanitizer.bypassSecurityTrustResourceUrl(this.snapshotUrl);
        this.snapshotState = 'ready';
      },
      error: () => this.snapshotState = 'error',
    });
  }

  setViewMode(mode: ViewMode): void {
    this.viewMode = mode;
  }

  closeJobPostingView(): void {
    this.viewingRow = null;
    this.lastFocusedElement?.focus();
  }

  // Advisor-only: send one position from this catalogue to one of their
  // users as a suggestion. companyPositionId comes straight from the row -
  // the same id the "View job posting" and "Edit" actions already use - so
  // there is nothing to look up, only who it's for and an optional note.
  openSuggest(row: any): void {
    this.lastFocusedSuggestElement = document.activeElement as HTMLElement | null;
    this.suggestingRow = row;
    this.suggestTargetUserId = '';
    this.suggestMessage = '';
    this.suggestError = '';
    this.suggestSuccess = false;

    setTimeout(() => this.suggestViewHeading?.nativeElement.focus());
  }

  closeSuggest(): void {
    this.suggestingRow = null;
    this.lastFocusedSuggestElement?.focus();
  }

  submitSuggestion(): void {
    if (!this.suggestingRow) return;

    if (!this.suggestTargetUserId) {
      this.suggestError = this.translate.instant('COMPANIES.SUGGEST_SELECT_USER_REQUIRED');
      return;
    }

    this.suggestError = '';
    this.suggestSuccess = false;
    this.suggestSubmitting = true;

    this.http.post('/api/advisor/suggestions', {
      targetUserId: this.suggestTargetUserId,
      companyPositionId: this.suggestingRow.positionId,
      message: this.suggestMessage,
    }).subscribe({
      next: () => {
        this.suggestSuccess = true;
        this.suggestTargetUserId = '';
        this.suggestMessage = '';
        this.suggestSubmitting = false;
      },
      error: () => {
        this.suggestError = this.translate.instant('ADVISOR.ERROR_CREATE_SUGGESTION');
        this.suggestSubmitting = false;
      },
    });
  }

  get viewingCompany(): Company | undefined {
    return this.viewingRow ? this.companies.find(c => c.id === this.viewingRow.companyId) : undefined;
  }

  get viewingPosition(): CompanyPosition | undefined {
    return this.viewingCompany?.positions.find(p => p.id === this.viewingRow.positionId);
  }

  formatLocation(loc: { street: string; postcode?: string; city: string; country?: string }): string {
    return [loc.street, loc.postcode, loc.city, loc.country].filter(v => v).join(', ');
  }

  formatContact(position?: CompanyPosition): string {
    if (!position) return '';
    return [position.contactTitle, position.contactLastName].filter(v => v).join(' ');
  }

  private toRows(companies: Company[]): any[] {
    return companies.flatMap(c =>
      c.positions.map(p => ({
        companyId:     c.id,
        positionId:    p.id,
        name:          c.name,
        city:          c.locations.map(l => l.city).filter(Boolean).join(', ') || '—',
        positionTitle: p.title,
        positionDate:  p.createdAt ? p.createdAt.substring(0, 10) : '—',
        rawCreatedAt:  p.createdAt ?? null,
      }))
    );
  }
}
