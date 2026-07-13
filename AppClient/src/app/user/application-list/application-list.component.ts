import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
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

const STATUS_LABELS: Record<string, string> = {
  DRAFT:                'Draft',
  SENT:                 'Sent',
  INTERVIEW_SCHEDULED:  'Interview scheduled',
  INTERVIEW_DONE:       'Interview done',
  OFFER_RECEIVED:       'Offer received',
  ACCEPTED:             'Accepted',
  REJECTED:             'Rejected',
  WITHDRAWN:            'Withdrawn',
};

@Component({
  selector: 'app-application-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './application-list.component.html',
  styleUrl: './application-list.component.scss',
})
export class ApplicationListComponent implements OnInit {

  rows: any[] = [];
  templates: Document[] = [];
  selectedTemplate: Record<number, string> = {};
  downloading: Record<number, boolean> = {};
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

  sortField: string | null = null;
  sortDir: 'asc' | 'desc' | null = null;

  filterYear: number | '' = '';
  filterMonth: number | '' = '';

  readonly months = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
  ];

  readonly statusOptions: ApplicationStatus[] = [
    'DRAFT', 'SENT', 'INTERVIEW_SCHEDULED', 'INTERVIEW_DONE',
    'OFFER_RECEIVED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN',
  ];

  readonly columns = [
    { label: 'Company',  field: 'companyName'  },
    { label: 'Position', field: 'positionTitle' },
    { label: 'Status',   field: 'statusLabel'  },
    { label: 'Applied',  field: 'appliedDate'  },
    { label: 'Notes',    field: 'notes'        },
  ];

  constructor(
    private applicationService: ApplicationService,
    private http: HttpClient,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

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
        this.errorMessage = 'Failed to create application.';
      },
    });
  }

  cancel(): void {
    this.showForm = false;
    this.router.navigate([], { queryParams: {} });
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
    return this.filterYear !== '' || this.filterMonth !== '';
  }

  clearFilter(): void {
    this.filterYear = '';
    this.filterMonth = '';
  }

  get sortedRows(): any[] {
    let source = this.rows;

    if (this.filterActive) {
      source = source.filter(r => matchesFilter(r.createdAt, this.filterYear, this.filterMonth));
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
    const docId = this.selectedTemplate[appId];
    if (!docId) return;
    this.downloading[appId] = true;
    this.http.post(`/api/cover-letter/${appId}/fill/${docId}`, null, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const row = this.rows.find(r => r.id === appId);
        const name = `Anschreiben_${(row?.companyName ?? 'cover_letter').replace(/\s+/g, '_')}.pdf`;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = name; a.click();
        URL.revokeObjectURL(url);
        this.downloading[appId] = false;
      },
      error: () => {
        this.errorMessage = 'Failed to generate cover letter.';
        this.downloading[appId] = false;
      },
    });
  }

  statusLabel(s: string): string {
    return STATUS_LABELS[s] ?? s;
  }

  private loadTemplates(): void {
    this.http.get<Document[]>('/api/documents', { params: { type: 'COVER_LETTER_TEMPLATE' } }).subscribe({
      next: (docs) => this.templates = docs,
    });
  }

  private loadApplications(): void {
    this.applicationService.getAll().subscribe({
      next: (data) => this.rows = this.toRows(data),
      error: () => this.errorMessage = 'Failed to load applications.',
    });
  }

  private toRows(applications: Application[]): any[] {
    return applications.map(a => ({
      id:            a.id,
      companyName:   a.companyName,
      positionTitle: a.positionTitle,
      statusLabel:   STATUS_LABELS[a.status] ?? a.status,
      appliedDate:   a.appliedDate ?? '—',
      notes:         a.notes ?? '',
      createdAt:     a.createdAt ?? null,
    }));
  }
}
