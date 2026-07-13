import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DataTableComponent, TableColumn } from '../../shared/data-table/data-table.component';
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

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [CommonModule, FormsModule, DataTableComponent],
  templateUrl: './documents.component.html',
  styleUrl: './documents.component.scss',
})
export class DocumentsComponent implements OnInit {

  allRows: any[] = [];
  errorMessage = '';
  uploading = false;

  pendingFile: File | null = null;
  pendingLabel = '';
  showUploadForm = false;

  filterYear: number | '' = '';
  filterMonth: number | '' = '';

  readonly months = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
  ];

  columns: TableColumn[] = [
    { label: 'Label',    field: 'label',     sortable: true  },
    { label: 'Filename', field: 'filename',  sortable: true  },
    { label: 'Uploaded', field: 'createdAt', sortable: true  },
  ];

  constructor(private http: HttpClient) {}

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
    return this.filterYear !== '' || this.filterMonth !== '';
  }

  clearFilter(): void {
    this.filterYear = '';
    this.filterMonth = '';
  }

  get filteredRows(): any[] {
    if (!this.filterActive) return this.allRows;
    return this.allRows.filter(r => {
      const iso = r.rawCreatedAt;
      if (!iso) return false;
      if (this.filterYear !== '' && yearOf(iso) !== Number(this.filterYear)) return false;
      if (this.filterMonth !== '' && monthOf(iso) !== Number(this.filterMonth)) return false;
      return true;
    });
  }

  ngOnInit(): void {
    this.http.get<Document[]>('/api/documents', {
      params: { type: 'COVER_LETTER_TEMPLATE' },
    }).subscribe({
      next: (docs) => this.allRows = docs.map(d => ({
        label:        d.label,
        filename:     d.filename,
        createdAt:    d.createdAt ? d.createdAt.substring(0, 10) : '—',
        rawCreatedAt: d.createdAt ?? null,
      })),
      error: () => this.errorMessage = 'Failed to load templates.',
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.pendingFile = file;
    this.pendingLabel = file.name.replace(/\.docx$/i, '');
    this.showUploadForm = true;
    input.value = '';
  }

  confirmUpload(): void {
    if (!this.pendingFile || !this.pendingLabel.trim()) return;
    const formData = new FormData();
    formData.append('file', this.pendingFile);
    formData.append('label', this.pendingLabel.trim());
    formData.append('type', 'COVER_LETTER_TEMPLATE');

    this.uploading = true;
    this.errorMessage = '';

    this.http.post<Document>('/api/documents/upload', formData).subscribe({
      next: () => {
        this.uploading = false;
        this.showUploadForm = false;
        this.pendingFile = null;
        this.pendingLabel = '';
        this.ngOnInit();
      },
      error: () => {
        this.uploading = false;
        this.errorMessage = 'Upload failed. Only .docx files are accepted.';
      },
    });
  }

  cancelUpload(): void {
    this.showUploadForm = false;
    this.pendingFile = null;
    this.pendingLabel = '';
  }
}
