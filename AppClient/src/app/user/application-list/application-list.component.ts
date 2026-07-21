import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Application, ApplicationRequest, ApplicationStatus } from '../../model/application';
import { ApplicationService } from '../../services/application.service';
import { Document } from '../../model/document';

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

function matchesFilter(iso: string | null | undefined, filterYear: number | '', filterMonth: number | ''): boolean {
  if (!iso) return false;
  if (filterYear !== '' && yearOf(iso) !== Number(filterYear)) return false;
  if (filterMonth !== '' && monthOf(iso) !== Number(filterMonth)) return false;
  return true;
}

@Component({
  selector: 'app-application-list',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './application-list.component.html',
  styleUrl: './application-list.component.scss',
})
export class ApplicationListComponent implements OnInit {

  private applications: Application[] = [];
  templates: Document[] = [];
  selectedTemplate: Record<number, string> = {};
  downloading: Record<number, boolean> = {};
  statusDraft: Record<number, ApplicationStatus> = {};
  updatingStatus: Record<number, boolean> = {};
  errorMessage = '';
  submitting = false;

  // pre-filled create form (shown when navigating from company list)
  newForm: { positionId: number | null; companyName: string; positionTitle: string; status: ApplicationStatus; appliedDate: string; notes: string } = {
    positionId:    null,
    companyName:   '',
    positionTitle: '',
    status:        'DRAFT',
    appliedDate:   '',
    notes:         '',
  };
  showForm = false;
  editingId: number | null = null;

  sortField: string | null = null;
  sortDir: 'asc' | 'desc' | null = null;

  filterYear: number | '' = '';
  filterMonth: number | '' = '';

  searchField = 'all';
  searchTerm = '';

  readonly searchFields = [
    { value: 'all',           label: 'APPLICATIONS.SEARCH_ALL' },
    { value: 'companyName',   label: 'APPLICATIONS.COL_COMPANY' },
    { value: 'positionTitle', label: 'APPLICATIONS.COL_POSITION' },
    { value: 'statusLabel',   label: 'APPLICATIONS.COL_STATUS' },
    { value: 'notes',         label: 'APPLICATIONS.COL_NOTES' },
  ];

  readonly monthIndexes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

  readonly statusOptions: ApplicationStatus[] = [
    'DRAFT', 'SENT', 'INTERVIEW_SCHEDULED', 'INTERVIEW_DONE',
    'OFFER_RECEIVED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN',
  ];

  readonly columns = [
    { label: 'APPLICATIONS.COL_COMPANY',  field: 'companyName'  },
    { label: 'APPLICATIONS.COL_POSITION', field: 'positionTitle' },
    { label: 'APPLICATIONS.COL_STATUS',   field: 'statusLabel'  },
    { label: 'APPLICATIONS.COL_APPLIED',  field: 'appliedDate'  },
    { label: 'APPLICATIONS.COL_NOTES',    field: 'notes'        },
  ];

  // A stable field, not a getter: *ngFor identity-diffs sortedRows, so rows
  // must only get a new array/objects when the underlying data actually
  // changes, not on every change-detection pass.
  rows: any[] = [];

  constructor(
    private applicationService: ApplicationService,
    private http: HttpClient,
    private route: ActivatedRoute,
    private router: Router,
    private translate: TranslateService,
  ) {
    this.translate.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => {
      this.rows = this.toRows(this.applications);
    });
  }

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      if (params['positionId']) {
        this.newForm.positionId    = +params['positionId'];
        this.newForm.companyName   = params['companyName'] ?? '';
        this.newForm.positionTitle = params['positionTitle'] ?? '';
        this.newForm.notes         = `${this.newForm.companyName} – ${this.newForm.positionTitle}`;
        this.showForm = true;
      }
    });
    this.loadApplications();
    this.loadTemplates();
  }

  submit(): void {
    if (!this.newForm.positionId) return;
    const req: ApplicationRequest = {
      companyPositionId: this.newForm.positionId,
      status:      this.newForm.status,
      appliedDate: this.newForm.appliedDate || null,
      notes:       this.newForm.notes || null,
    };
    this.submitting = true;
    this.applicationService.create(req).subscribe({
      next: () => {
        this.submitting = false;
        this.showForm = false;
        this.router.navigate([], { queryParams: {} });
        this.loadApplications();
      },
      error: () => {
        this.submitting = false;
        this.errorMessage = this.translate.instant('APPLICATIONS.ERROR_CREATE');
      },
    });
  }

  cancel(): void {
    this.showForm = false;
    this.editingId = null;
    this.router.navigate([], { queryParams: {} });
  }

  startEdit(row: any): void {
    this.editingId = row.id;
    this.newForm.positionId = row.companyPositionId;
    this.newForm.companyName = row.companyName;
    this.newForm.positionTitle = row.positionTitle;
    this.newForm.status = row.status;
    this.newForm.appliedDate = row.appliedDateRaw;
    this.newForm.notes = row.notes;
    this.showForm = true;
  }

  saveEdit(): void {
    if (!this.editingId) return;
    const req: ApplicationRequest = {
      companyPositionId: this.newForm.positionId!,
      status:      this.newForm.status,
      appliedDate: this.newForm.appliedDate || null,
      notes:       this.newForm.notes || null,
    };
    this.submitting = true;
    this.applicationService.update(this.editingId, req).subscribe({
      next: () => {
        this.submitting = false;
        this.showForm = false;
        this.editingId = null;
        this.loadApplications();
      },
      error: () => {
        this.submitting = false;
        this.errorMessage = this.translate.instant('APPLICATIONS.ERROR_UPDATE');
      },
    });
  }

  applyStatusChange(row: any): void {
    const newStatus = this.statusDraft[row.id];
    if (!newStatus || newStatus === row.status) return;
    const req: ApplicationRequest = {
      companyPositionId: row.companyPositionId,
      status:      newStatus,
      appliedDate: row.appliedDateRaw || null,
      notes:       row.notes || null,
    };
    this.updatingStatus[row.id] = true;
    this.applicationService.update(row.id, req).subscribe({
      next: () => {
        this.updatingStatus[row.id] = false;
        this.loadApplications();
      },
      error: () => {
        this.updatingStatus[row.id] = false;
        this.errorMessage = this.translate.instant('APPLICATIONS.ERROR_UPDATE');
      },
    });
  }

  deleteApplication(row: any): void {
    if (!confirm(this.translate.instant('APPLICATIONS.CONFIRM_DELETE', { company: row.companyName, position: row.positionTitle }))) return;
    this.applicationService.delete(row.id).subscribe({
      next: () => {
        this.applications = this.applications.filter(a => a.id !== row.id);
        this.rows = this.toRows(this.applications);
      },
      error: () => this.errorMessage = this.translate.instant('APPLICATIONS.ERROR_DELETE'),
    });
  }

  get availableYears(): number[] {
    const years = new Set<number>();
    const current = new Date().getFullYear();
    years.add(current - 1);
    years.add(current);
    years.add(current + 1);
    this.rows.forEach(r => {
      const y = yearOf(r.createdAt);
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
      ? ['companyName', 'positionTitle', 'statusLabel', 'notes']
      : [this.searchField];
    return fields.some(f => (row[f] ?? '').toString().toLowerCase().includes(term));
  }

  get sortedRows(): any[] {
    let source = this.rows;

    if (this.filterYear !== '' || this.filterMonth !== '') {
      source = source.filter(r => matchesFilter(r.createdAt, this.filterYear, this.filterMonth));
    }

    const term = this.searchTerm.trim().toLowerCase();
    if (term) {
      source = source.filter(r => this.matchesSearch(r, term));
    }

    if (!this.sortField || !this.sortDir) return source;
    const field = this.sortField;
    const dir = this.sortDir === 'asc' ? 1 : -1;
    return [...source].sort((a, b) => {
      const av = (a[field] ?? '').toString().toLowerCase();
      const bv = (b[field] ?? '').toString().toLowerCase();
      return av < bv ? -dir : av > bv ? dir : 0;
    });
  }

  sortBy(field: string): void {
    if (this.sortField === field) {
      if (this.sortDir === 'asc') this.sortDir = 'desc';
      else if (this.sortDir === 'desc') { this.sortDir = null; this.sortField = null; }
      else this.sortDir = 'asc';
    } else {
      this.sortField = field;
      this.sortDir = 'asc';
    }
  }

  sortIcon(field: string): string {
    if (this.sortField !== field) return '↕';
    return this.sortDir === 'asc' ? '↑' : '↓';
  }

  ariaSort(field: string): string {
    if (this.sortField !== field) return 'none';
    return this.sortDir === 'asc' ? 'ascending' : 'descending';
  }

  downloadCoverLetter(appId: number): void {
    this.fetchCoverLetter(appId, '', 'pdf');
  }

  downloadCoverLetterWord(appId: number): void {
    this.fetchCoverLetter(appId, '/word', 'docx');
  }

  private fetchCoverLetter(appId: number, urlSuffix: string, extension: string): void {
    const docId = this.selectedTemplate[appId];
    if (!docId) return;
    this.downloading[appId] = true;
    this.http.post(`/api/cover-letter/${appId}/fill/${docId}${urlSuffix}`, null, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const row = this.rows.find(r => r.id === appId);
        const name = `Anschreiben_${(row?.companyName ?? 'cover_letter').replace(/\s+/g, '_')}.${extension}`;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = name; a.click();
        URL.revokeObjectURL(url);
        this.downloading[appId] = false;
        if (row) this.markAppliedToday(row);
      },
      error: () => {
        this.errorMessage = this.translate.instant('APPLICATIONS.ERROR_COVER_LETTER');
        this.downloading[appId] = false;
      },
    });
  }

  // A downloaded cover letter means the application just got (re-)sent, so
  // default its applied date to today. Runs silently: the download already
  // succeeded, so a failure here shouldn't be reported as if it hadn't.
  private markAppliedToday(row: any): void {
    const today = new Date().toISOString().substring(0, 10);
    if (row.appliedDateRaw === today) return;
    const req: ApplicationRequest = {
      companyPositionId: row.companyPositionId,
      status:      row.status,
      appliedDate: today,
      notes:       row.notes || null,
    };
    this.applicationService.update(row.id, req).subscribe({
      next: () => this.loadApplications(),
      error: () => {},
    });
  }

  statusLabel(s: string): string {
    return this.translate.instant('STATUS.' + s);
  }

  private loadTemplates(): void {
    this.http.get<Document[]>('/api/documents', { params: { type: 'COVER_LETTER_TEMPLATE' } }).subscribe({
      next: (docs) => this.templates = docs,
    });
  }

  private loadApplications(): void {
    this.applicationService.getAll().subscribe({
      next: (data) => {
        this.applications = data;
        this.rows = this.toRows(data);
        this.statusDraft = {};
        data.forEach(a => { if (a.id != null) this.statusDraft[a.id] = a.status; });
      },
      error: () => this.errorMessage = this.translate.instant('APPLICATIONS.ERROR_LOAD'),
    });
  }

  private toRows(applications: Application[]): any[] {
    return applications.map(a => ({
      id:                a.id,
      companyPositionId: a.companyPositionId,
      companyName:       a.companyName,
      positionTitle:     a.positionTitle,
      status:            a.status,
      statusLabel:       this.statusLabel(a.status),
      appliedDate:       a.appliedDate ?? '—',
      appliedDateRaw:    a.appliedDate ?? '',
      notes:             a.notes ?? '',
      createdAt:         a.createdAt ?? null,
    }));
  }
}
