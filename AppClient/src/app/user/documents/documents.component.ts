import { Component, OnInit, ChangeDetectionStrategy, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { catchError, forkJoin, of } from 'rxjs';
import { DataTableComponent, TableColumn, TableAction } from '../../shared/data-table/data-table.component';
import { CoverLetterService } from '../../services/cover-letter.service';
import { DocumentService } from '../../services/document.service';
import { Document, DocumentLanguage, DocumentType } from '../../model/document';
import { HtmlLetterTemplate, LayoutLetterKey } from '../../model/cover-letter';

/** Which provider a row came from. The two are stored and edited in different places. */
type TemplateKind = 'WORD' | 'HTML';

/**
 * The two upload paths. Cover letter templates are .docx and feed the Word provider;
 * everything else is a PDF the user keeps on file and shares with reviewers.
 */
type UploadMode = 'TEMPLATE' | 'DOCUMENT';

/** PDF types a user uploads themselves. JOB_POSTING_SNAPSHOT is created by the importer. */
const PDF_TYPES: DocumentType[] = ['CV', 'CERTIFICATE', 'OTHER'];

const TYPE_KEY: Record<DocumentType, string> = {
  CV:                    'DOCUMENTS.TYPE_CV',
  CERTIFICATE:           'DOCUMENTS.TYPE_CERTIFICATE',
  OTHER:                 'DOCUMENTS.TYPE_OTHER',
  COVER_LETTER_TEMPLATE: 'DOCUMENTS.KIND_WORD',
};

const LAYOUT_KEY: Record<LayoutLetterKey, string> = {
  DIN5008_COVER_LETTER_A: 'DOCUMENTS.LAYOUT_DIN5008_A',
  DIN5008_COVER_LETTER_B: 'DOCUMENTS.LAYOUT_DIN5008_B',
  DIN5008_CV:             'DOCUMENTS.LAYOUT_DIN5008_CV',
};

/** Columns an HTML template has no equivalent for; it is edited, never uploaded. */
const NOT_APPLICABLE = '—';

// Reuses the LANGUAGE.* UI-language keys (EN/DE/NL) since they name the same
// three human languages the document itself can be written in.
const LANGUAGE_KEY: Record<DocumentLanguage, string> = {
  ENGLISH: 'LANGUAGE.EN',
  GERMAN:  'LANGUAGE.DE',
  DUTCH:   'LANGUAGE.NL',
};

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
  selector: 'app-documents',
  standalone: true,
  imports: [FormsModule, DataTableComponent, TranslatePipe],
  templateUrl: './documents.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './documents.component.scss',
})
export class DocumentsComponent implements OnInit {

  private documents: Document[] = [];
  private htmlTemplates: HtmlLetterTemplate[] = [];
  allRows: any[] = [];
  errorMessage = '';
  uploading = false;

  pendingFile: File | null = null;
  pendingLabel = '';
  pendingLanguage: DocumentLanguage = 'ENGLISH';
  pendingType: DocumentType = 'CV';
  uploadMode: UploadMode = 'TEMPLATE';
  showUploadForm = false;
  editingId: string | null = null;

  /** Rows of the second table: the user's uploaded PDFs. */
  documentRows: any[] = [];

  readonly pdfTypeOptions = PDF_TYPES.map(value => ({ value, label: TYPE_KEY[value] }));

  documentColumns: TableColumn[] = [
    { label: 'DOCUMENTS.COL_LABEL',    field: 'label',        sortable: true },
    { label: 'DOCUMENTS.COL_TYPE',     field: 'typeLabel',    sortable: true },
    { label: 'DOCUMENTS.COL_FILENAME', field: 'filename',     sortable: true },
    { label: 'DOCUMENTS.COL_LANGUAGE', field: 'languageLabel', sortable: true },
    { label: 'DOCUMENTS.COL_UPLOADED', field: 'createdAt',    sortable: true },
  ];

  documentActions: TableAction[] = [
    {
      label: 'DOCUMENTS.ACTION_DOWNLOAD',
      ariaLabel: (row) => this.translate.instant('DOCUMENTS.ACTION_DOWNLOAD_ARIA', { label: row.label }),
      handler: (row) => this.downloadDocument(row),
    },
    {
      label: 'DOCUMENTS.ACTION_EDIT',
      ariaLabel: (row) => this.translate.instant('DOCUMENTS.ACTION_EDIT_ARIA', { label: row.label }),
      handler: (row) => this.startEdit(row),
    },
    {
      label: 'DOCUMENTS.ACTION_DELETE',
      ariaLabel: (row) => this.translate.instant('DOCUMENTS.ACTION_DELETE_ARIA', { label: row.label }),
      handler: (row) => this.deleteDocument(row),
    },
  ];

  filterYear: number | '' = '';
  filterMonth: number | '' = '';

  searchField = 'all';
  searchTerm = '';

  readonly languageOptions: { value: DocumentLanguage; label: string }[] = [
    { value: 'ENGLISH', label: LANGUAGE_KEY.ENGLISH },
    { value: 'GERMAN',  label: LANGUAGE_KEY.GERMAN },
    { value: 'DUTCH',   label: LANGUAGE_KEY.DUTCH },
  ];

  readonly searchFields = [
    { value: 'all',           label: 'DOCUMENTS.SEARCH_ALL' },
    { value: 'label',         label: 'DOCUMENTS.COL_LABEL' },
    { value: 'kindLabel',     label: 'DOCUMENTS.COL_KIND' },
    { value: 'typeLabel',     label: 'DOCUMENTS.COL_TYPE' },
    { value: 'filename',      label: 'DOCUMENTS.COL_FILENAME' },
    { value: 'languageLabel', label: 'DOCUMENTS.COL_LANGUAGE' },
  ];

  readonly monthIndexes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

  columns: TableColumn[] = [
    { label: 'DOCUMENTS.COL_LABEL',    field: 'label',        sortable: true  },
    { label: 'DOCUMENTS.COL_KIND',     field: 'kindLabel',    sortable: true  },
    { label: 'DOCUMENTS.COL_FILENAME', field: 'filename',     sortable: true  },
    { label: 'DOCUMENTS.COL_LANGUAGE', field: 'languageLabel', sortable: true },
    { label: 'DOCUMENTS.COL_UPLOADED', field: 'createdAt',    sortable: true  },
  ];

  // Edit and delete act on the uploaded file, which only a Word row has; an HTML
  // template is edited on the cover letter page, so its single action goes there.
  actions: TableAction[] = [
    {
      label: 'DOCUMENTS.ACTION_EDIT',
      ariaLabel: (row) => this.translate.instant('DOCUMENTS.ACTION_EDIT_ARIA', { label: row.label }),
      handler: (row) => this.startEdit(row),
      visible: (row) => row.kind === 'WORD',
    },
    {
      label: 'DOCUMENTS.ACTION_DELETE',
      ariaLabel: (row) => this.translate.instant('DOCUMENTS.ACTION_DELETE_ARIA', { label: row.label }),
      handler: (row) => this.deleteDocument(row),
      visible: (row) => row.kind === 'WORD',
    },
    {
      label: 'DOCUMENTS.ACTION_OPEN',
      ariaLabel: (row) => this.translate.instant('DOCUMENTS.ACTION_OPEN_ARIA', { label: row.label }),
      handler: () => this.router.navigate(['/cover-letter-template']),
      visible: (row) => row.kind === 'HTML',
    },
  ];

  private readonly router = inject(Router);
  private readonly coverLetters = inject(CoverLetterService);
  private readonly documentService = inject(DocumentService);

  constructor(private translate: TranslateService) {
    this.translate.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => this.rebuildRows());
  }

  /**
   * Recomputed when the rows change rather than read as a getter: the template iterates
   * it, and a fresh array on every change detection pass would rebuild the option list
   * each time.
   */
  availableYears: number[] = [];

  private computeAvailableYears(): number[] {
    const years = new Set<number>();
    const current = new Date().getFullYear();
    years.add(current - 1);
    years.add(current);
    years.add(current + 1);
    [...this.allRows, ...this.documentRows].forEach(r => {
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
      ? ['label', 'kindLabel', 'typeLabel', 'filename', 'languageLabel']
      : [this.searchField];
    return fields.some(f => (row[f] ?? '').toString().toLowerCase().includes(term));
  }

  /**
   * Both tables read their rows through a one-entry memo. Without it each read returns
   * a new array, which changes the data table's `rows` input identity and re-runs its
   * sort on every change detection pass.
   */
  private filterCache: Record<string, { key: string; rows: any[] }> = {};

  get filteredRows(): any[] {
    return this.filtered('templates', this.allRows);
  }

  get filteredDocumentRows(): any[] {
    return this.filtered('documents', this.documentRows);
  }

  private filtered(name: string, rows: any[]): any[] {
    const key = `${this.filterYear}|${this.filterMonth}|${this.searchField}|${this.searchTerm.trim().toLowerCase()}`;
    const cached = this.filterCache[name];
    if (cached && cached.key === key) return cached.rows;

    const result = this.applyFilters(rows);
    this.filterCache[name] = { key, rows: result };
    return result;
  }

  /** The filter bar above the page drives both tables. */
  private applyFilters(rows: any[]): any[] {
    let source = rows;

    if (this.filterYear !== '' || this.filterMonth !== '') {
      source = source.filter(r => {
        const iso = r.rawCreatedAt;
        if (!iso) return false;
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

  /**
   * Both providers' templates in one table. The two calls are independent, so a
   * failing one only costs its own rows rather than blanking the table.
   */
  ngOnInit(): void {
    forkJoin({
      documents: this.documentService.getAll().pipe(catchError(() => of(null))),
      templates: this.coverLetters.listTemplates().pipe(catchError(() => of(null))),
    }).subscribe(({ documents, templates }) => {
      this.errorMessage = documents && templates
        ? ''
        : this.translate.instant('DOCUMENTS.ERROR_LOAD');
      this.documents = documents ?? [];
      this.htmlTemplates = templates ?? [];
      this.rebuildRows();
    });
  }

  /**
   * One request, split by type: cover letter templates drive the first table, the
   * uploaded PDFs the second. Job posting snapshots are left out of both - the importer
   * creates them per application and they are managed there.
   */
  private rebuildRows(): void {
    const templates = this.documents.filter(d => d.type === 'COVER_LETTER_TEMPLATE');
    const pdfs = this.documents.filter(d => PDF_TYPES.includes(d.type));

    this.allRows = [...this.wordRows(templates), ...this.htmlRows(this.htmlTemplates)];
    this.documentRows = pdfs.map(d => ({
      ...this.documentRow(d),
      typeLabel: this.translate.instant(TYPE_KEY[d.type]),
      type:      d.type,
    }));

    // The memo is keyed on the filter inputs only, so a new set of rows has to drop it.
    this.filterCache = {};
    this.availableYears = this.computeAvailableYears();
  }

  private wordRows(docs: Document[]): any[] {
    return docs.map(d => ({
      ...this.documentRow(d),
      kind:      'WORD' as TemplateKind,
      kindLabel: this.translate.instant('DOCUMENTS.KIND_WORD'),
    }));
  }

  /** The columns every stored document has, whichever table it lands in. */
  private documentRow(d: Document): any {
    return {
      id:            d.id,
      label:         d.label,
      filename:      d.filename,
      language:      d.language,
      languageLabel: this.translate.instant(LANGUAGE_KEY[d.language]),
      createdAt:     d.createdAt ? d.createdAt.substring(0, 10) : NOT_APPLICABLE,
      rawCreatedAt:  d.createdAt ?? null,
    };
  }

  /**
   * An HTML template has no filename - it is edited in place rather than uploaded - so
   * that one column reads as not applicable. Its name is the label, and the date column
   * shows when it was last edited, which is the closest counterpart to an upload date.
   */
  private htmlRows(templates: HtmlLetterTemplate[]): any[] {
    return templates.map(t => {
      const changedAt = t.updatedAt ?? t.createdAt;
      return {
        kind:          'HTML' as TemplateKind,
        id:            t.id,
        label:         this.htmlLabel(t),
        kindLabel:     this.translate.instant('DOCUMENTS.KIND_HTML'),
        filename:      NOT_APPLICABLE,
        language:      t.language,
        languageLabel: t.language ? this.translate.instant(LANGUAGE_KEY[t.language]) : NOT_APPLICABLE,
        createdAt:     changedAt ? changedAt.substring(0, 10) : NOT_APPLICABLE,
        rawCreatedAt:  changedAt ?? null,
      };
    });
  }

  /** Falls back to the layout only for templates stored before names existed. */
  private htmlLabel(template: HtmlLetterTemplate): string {
    return template.name?.trim()
      || this.translate.instant(LAYOUT_KEY[template.layoutLetter] ?? 'DOCUMENTS.KIND_HTML');
  }

  onFileSelected(event: Event, mode: UploadMode): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.editingId = null;
    this.uploadMode = mode;
    this.pendingFile = file;
    this.pendingLabel = file.name.replace(/\.(docx|pdf)$/i, '');
    this.pendingLanguage = 'ENGLISH';
    if (mode === 'DOCUMENT') this.pendingType = 'CV';
    this.showUploadForm = true;
    input.value = '';
  }

  private startEdit(row: any): void {
    this.editingId = row.id;
    this.uploadMode = row.type && row.type !== 'COVER_LETTER_TEMPLATE' ? 'DOCUMENT' : 'TEMPLATE';
    this.pendingFile = null;
    this.pendingLabel = row.label;
    this.pendingLanguage = row.language;
    if (row.type) this.pendingType = row.type;
    this.showUploadForm = true;
  }

  /**
   * Fetched as a blob rather than linked directly, so the request carries the session
   * cookie and a failure surfaces as an error instead of a broken tab.
   */
  private downloadDocument(row: any): void {
    this.documentService.download(row.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = row.filename || row.label; a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.errorMessage = this.translate.instant('DOCUMENTS.ERROR_DOWNLOAD'),
    });
  }

  saveEdit(): void {
    if (!this.editingId || !this.pendingLabel.trim()) return;
    this.uploading = true;
    this.errorMessage = '';

    this.documentService.update(this.editingId, {
      label: this.pendingLabel.trim(),
      language: this.pendingLanguage,
      ...(this.uploadMode === 'DOCUMENT' ? { type: this.pendingType } : {}),
    }).subscribe({
      next: () => {
        this.uploading = false;
        this.showUploadForm = false;
        this.editingId = null;
        this.pendingLabel = '';
        this.pendingLanguage = 'ENGLISH';
        this.ngOnInit();
      },
      error: () => {
        this.uploading = false;
        this.errorMessage = this.translate.instant('DOCUMENTS.ERROR_UPDATE');
      },
    });
  }

  private deleteDocument(row: any): void {
    if (!confirm(this.translate.instant('DOCUMENTS.CONFIRM_DELETE', { label: row.label }))) return;
    this.documentService.delete(row.id).subscribe({
      next: () => {
        this.documents = this.documents.filter(d => d.id !== row.id);
        this.rebuildRows();
      },
      error: () => this.errorMessage = this.translate.instant('DOCUMENTS.ERROR_DELETE'),
    });
  }

  confirmUpload(): void {
    if (!this.pendingFile || !this.pendingLabel.trim()) return;
    this.uploading = true;
    this.errorMessage = '';

    this.documentService.upload(
      this.pendingFile,
      this.pendingLabel.trim(),
      this.uploadMode === 'DOCUMENT' ? this.pendingType : 'COVER_LETTER_TEMPLATE',
      this.pendingLanguage,
    ).subscribe({
      next: () => {
        this.uploading = false;
        this.showUploadForm = false;
        this.pendingFile = null;
        this.pendingLabel = '';
        this.pendingLanguage = 'ENGLISH';
        this.ngOnInit();
      },
      error: () => {
        this.uploading = false;
        this.errorMessage = this.translate.instant('DOCUMENTS.ERROR_UPLOAD');
      },
    });
  }

  cancelUpload(): void {
    this.showUploadForm = false;
    this.editingId = null;
    this.pendingFile = null;
    this.pendingLabel = '';
    this.pendingLanguage = 'ENGLISH';
    this.uploadMode = 'TEMPLATE';
  }
}
