import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DataTableComponent, TableColumn, TableAction } from '../../shared/data-table/data-table.component';
import { Document, DocumentLanguage } from '../../model/document';

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
  allRows: any[] = [];
  errorMessage = '';
  uploading = false;

  pendingFile: File | null = null;
  pendingLabel = '';
  pendingLanguage: DocumentLanguage = 'ENGLISH';
  showUploadForm = false;
  editingId: string | null = null;

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
    { value: 'filename',      label: 'DOCUMENTS.COL_FILENAME' },
    { value: 'languageLabel', label: 'DOCUMENTS.COL_LANGUAGE' },
  ];

  readonly monthIndexes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

  columns: TableColumn[] = [
    { label: 'DOCUMENTS.COL_LABEL',    field: 'label',        sortable: true  },
    { label: 'DOCUMENTS.COL_FILENAME', field: 'filename',     sortable: true  },
    { label: 'DOCUMENTS.COL_LANGUAGE', field: 'languageLabel', sortable: true },
    { label: 'DOCUMENTS.COL_UPLOADED', field: 'createdAt',    sortable: true  },
  ];

  actions: TableAction[] = [
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

  constructor(private http: HttpClient, private translate: TranslateService) {
    this.translate.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => {
      this.allRows = this.toRows(this.documents);
    });
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
      ? ['label', 'filename', 'languageLabel']
      : [this.searchField];
    return fields.some(f => (row[f] ?? '').toString().toLowerCase().includes(term));
  }

  get filteredRows(): any[] {
    let source = this.allRows;

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

  ngOnInit(): void {
    this.http.get<Document[]>('/api/documents', {
      params: { type: 'COVER_LETTER_TEMPLATE' },
    }).subscribe({
      next: (docs) => {
        this.documents = docs;
        this.allRows = this.toRows(docs);
      },
      error: () => this.errorMessage = this.translate.instant('DOCUMENTS.ERROR_LOAD'),
    });
  }

  private toRows(docs: Document[]): any[] {
    return docs.map(d => ({
      id:            d.id,
      label:         d.label,
      filename:      d.filename,
      language:      d.language,
      languageLabel: this.translate.instant(LANGUAGE_KEY[d.language]),
      createdAt:     d.createdAt ? d.createdAt.substring(0, 10) : '—',
      rawCreatedAt:  d.createdAt ?? null,
    }));
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.editingId = null;
    this.pendingFile = file;
    this.pendingLabel = file.name.replace(/\.docx$/i, '');
    this.pendingLanguage = 'ENGLISH';
    this.showUploadForm = true;
    input.value = '';
  }

  private startEdit(row: any): void {
    this.editingId = row.id;
    this.pendingFile = null;
    this.pendingLabel = row.label;
    this.pendingLanguage = row.language;
    this.showUploadForm = true;
  }

  saveEdit(): void {
    if (!this.editingId || !this.pendingLabel.trim()) return;
    this.uploading = true;
    this.errorMessage = '';

    this.http.patch<Document>(`/api/documents/${this.editingId}`, {
      label: this.pendingLabel.trim(),
      language: this.pendingLanguage,
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
    this.http.delete(`/api/documents/${row.id}`).subscribe({
      next: () => {
        this.documents = this.documents.filter(d => d.id !== row.id);
        this.allRows = this.toRows(this.documents);
      },
      error: () => this.errorMessage = this.translate.instant('DOCUMENTS.ERROR_DELETE'),
    });
  }

  confirmUpload(): void {
    if (!this.pendingFile || !this.pendingLabel.trim()) return;
    const formData = new FormData();
    formData.append('file', this.pendingFile);
    formData.append('label', this.pendingLabel.trim());
    formData.append('type', 'COVER_LETTER_TEMPLATE');
    formData.append('language', this.pendingLanguage);

    this.uploading = true;
    this.errorMessage = '';

    this.http.post<Document>('/api/documents/upload', formData).subscribe({
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
  }
}
