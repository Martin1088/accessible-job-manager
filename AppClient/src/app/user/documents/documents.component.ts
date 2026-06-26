import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { DataTableComponent, TableColumn } from '../../shared/data-table/data-table.component';
import { Document } from '../../model/document';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [CommonModule, DataTableComponent],
  templateUrl: './documents.component.html',
  styleUrl: './documents.component.scss',
})
export class DocumentsComponent implements OnInit {

  rows: any[] = [];
  errorMessage = '';
  uploading = false;

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

    const formData = new FormData();
    formData.append('file', file);
    formData.append('label', file.name.replace(/\.docx$/i, ''));
    formData.append('type', 'COVER_LETTER_TEMPLATE');

    this.uploading = true;
    this.errorMessage = '';

    this.http.post<Document>('/api/documents/upload', formData).subscribe({
      next: () => {
        this.uploading = false;
        input.value = '';
        this.ngOnInit();
      },
      error: () => {
        this.uploading = false;
        input.value = '';
        this.errorMessage = 'Upload failed. Only .docx files are accepted.';
      },
    });
  }
}
