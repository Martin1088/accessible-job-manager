import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DataTableComponent, TableColumn } from '../../shared/data-table/data-table.component';
import { Document } from '../../model/document';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [CommonModule, FormsModule, DataTableComponent],
  templateUrl: './documents.component.html',
  styleUrl: './documents.component.scss',
})
export class DocumentsComponent implements OnInit {

  rows: any[] = [];
  errorMessage = '';
  uploading = false;

  pendingFile: File | null = null;
  pendingLabel = '';
  showUploadForm = false;

  columns: TableColumn[] = [
    { label: 'Label',    field: 'label',     sortable: true  },
    { label: 'Filename', field: 'filename',  sortable: true  },
    { label: 'Uploaded', field: 'createdAt', sortable: true  },
  ];

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get<Document[]>('/api/documents', {
      params: { type: 'COVER_LETTER_TEMPLATE' },
    }).subscribe({
      next: (docs) => this.rows = docs.map(d => ({
        label:     d.label,
        filename:  d.filename,
        createdAt: d.createdAt ? d.createdAt.substring(0, 10) : '—',
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
