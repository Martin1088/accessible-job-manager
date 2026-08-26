import { Component, DestroyRef, ElementRef, OnInit, ViewChild, inject, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LiveAnnouncer } from '@angular/cdk/a11y';

import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Application, ApplicationRequest, ApplicationStatus } from '../../model/application';
import { ApplicationService } from '../../services/application.service';
import { CoverLetterService } from '../../services/cover-letter.service';
import { CoverLetterEmail, CoverLetterRenderRequest } from '../../model/cover-letter';
import { Document } from '../../model/document';

/**
 * One entry of the template picker. Both cover letter providers write a letter for
 * an application, so the picker lists them side by side and the provider decides
 * which endpoint answers - the user picks a template, not a technology.
 */
export interface TemplateOption {
  id: string;
  label: string;
  provider: 'WORD' | 'HTML';
}

// The list has no attachment editor; attachments are chosen in the template editor.
const NO_ATTACHMENTS: CoverLetterRenderRequest = { attachments: [] };

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
  imports: [FormsModule, TranslatePipe],
  templateUrl: './application-list.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './application-list.component.scss',
})
export class ApplicationListComponent implements OnInit {

  private applications: Application[] = [];
  wordTemplates: TemplateOption[] = [];
  htmlTemplates: TemplateOption[] = [];
  selectedTemplate: Record<number, TemplateOption | undefined> = {};
  downloading: Record<number, boolean> = {};
  previewing: Record<number, boolean> = {};
  preview: { company: string; template: string; text: string } | null = null;
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

  @ViewChild('previewPanel') private previewPanel?: ElementRef<HTMLElement>;

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

  private readonly announcer = inject(LiveAnnouncer);

  constructor(
    private applicationService: ApplicationService,
    private coverLetterService: CoverLetterService,
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
    this.announceSort(field);
  }

  /**
   * aria-sort alone is not reliably announced on change by VoiceOver - this
   * confirms the sort event itself, while aria-sort covers state on re-read.
   */
  private announceSort(field: string): void {
    const col = this.columns.find(c => c.field === field);
    const column = col ? this.translate.instant(col.label) : field;
    const key = this.sortField === field
      ? (this.sortDir === 'asc' ? 'TABLE.SORT_ANNOUNCE_ASC' : 'TABLE.SORT_ANNOUNCE_DESC')
      : 'TABLE.SORT_ANNOUNCE_NONE';
    this.announcer.announce(this.translate.instant(key, { column }), 'polite');
  }

  sortIcon(field: string): string {
    if (this.sortField !== field) return '↕';
    return this.sortDir === 'asc' ? '↑' : '↓';
  }

  ariaSort(field: string): string {
    if (this.sortField !== field) return 'none';
    return this.sortDir === 'asc' ? 'ascending' : 'descending';
  }

  get hasTemplates(): boolean {
    return this.wordTemplates.length > 0 || this.htmlTemplates.length > 0;
  }

  /**
   * .docx is a mail-merge output. An HTML template is printed by a browser engine,
   * which has no Word document to hand back, so that one action stays unavailable
   * while such a template is picked.
   */
  supportsWord(appId: number): boolean {
    return this.selectedTemplate[appId]?.provider === 'WORD';
  }

  /** Both providers print a PDF; the picked template decides which one is asked. */
  downloadCoverLetter(appId: number): void {
    const template = this.selectedTemplate[appId];
    if (!template) return;
    this.saveCoverLetter(appId, 'pdf', template.provider === 'HTML'
      ? this.coverLetterService.renderPdf(appId, template.id, NO_ATTACHMENTS).pipe(map(res => res.body!))
      : this.http.post(`/api/word/cover-letter/${appId}/fill/${template.id}`, null, { responseType: 'blob' }));
  }

  downloadCoverLetterWord(appId: number): void {
    const template = this.selectedTemplate[appId];
    if (!template || template.provider !== 'WORD') return;
    this.saveCoverLetter(appId, 'docx',
      this.http.post(`/api/word/cover-letter/${appId}/fill/${template.id}/word`, null, { responseType: 'blob' }));
  }

  emailCoverLetter(appId: number): void {
    const template = this.selectedTemplate[appId];
    if (!template) return;
    const draft = template.provider === 'HTML'
      ? this.coverLetterService.renderEmail(appId, template.id, NO_ATTACHMENTS)
      : this.http.post<CoverLetterEmail>(`/api/word/cover-letter/${appId}/fill/${template.id}/email`, null);

    this.downloading[appId] = true;
    draft.subscribe({
      next: (res) => {
        const to = res.to ?? '';
        const mailto = `mailto:${to}?subject=${encodeURIComponent(res.subject)}&body=${encodeURIComponent(res.body)}`;
        window.location.href = mailto;
        this.downloading[appId] = false;
        const row = this.rows.find(r => r.id === appId);
        if (row) this.markAppliedToday(row);
      },
      error: () => {
        this.errorMessage = this.translate.instant('APPLICATIONS.ERROR_COVER_LETTER');
        this.downloading[appId] = false;
      },
    });
  }

  /**
   * The letter read front to back before it is printed or sent. Both providers
   * linearize the same letter they render, so the preview cannot describe a
   * document other than the one that would come out.
   */
  previewCoverLetter(appId: number): void {
    const template = this.selectedTemplate[appId];
    if (!template) return;
    const text = template.provider === 'HTML'
      ? this.coverLetterService.renderText(appId, template.id, NO_ATTACHMENTS)
      : this.http.post(`/api/word/cover-letter/${appId}/fill/${template.id}/text`, null, { responseType: 'text' });

    this.previewing[appId] = true;
    text.subscribe({
      next: (rendered) => {
        this.previewing[appId] = false;
        this.preview = {
          company: this.rows.find(r => r.id === appId)?.companyName ?? '',
          template: template.label,
          text: rendered,
        };
        // The panel opens at the top of the page while the button that opened it
        // can be rows below, so move focus there rather than leaving the reader
        // to find it - which also scrolls it into view.
        setTimeout(() => this.previewPanel?.nativeElement.focus());
      },
      error: () => {
        this.previewing[appId] = false;
        this.errorMessage = this.translate.instant('APPLICATIONS.ERROR_COVER_LETTER');
      },
    });
  }

  closePreview(): void {
    this.preview = null;
  }

  private saveCoverLetter(appId: number, extension: string, request: Observable<Blob>): void {
    this.downloading[appId] = true;
    request.subscribe({
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

  // Both providers are asked separately: they are independent, and a letter can be
  // written with either, so one being unavailable must not empty the whole picker.
  private loadTemplates(): void {
    this.http.get<Document[]>('/api/documents', { params: { type: 'COVER_LETTER_TEMPLATE' } }).subscribe({
      next: (docs) => this.wordTemplates = docs.map(
        doc => ({ id: doc.id, label: doc.label, provider: 'WORD' })),
    });
    this.coverLetterService.listTemplates().subscribe({
      next: (letters) => this.htmlTemplates = letters.map(
        letter => ({ id: letter.id, label: letter.name, provider: 'HTML' })),
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
