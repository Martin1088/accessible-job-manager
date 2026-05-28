import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface TableColumn {
  label: string;
  field: string;
  sortable?: boolean;
}

export interface TableAction {
  label: string;
  ariaLabel: (row: any) => string;
  handler: (row: any) => void;
}

type SortDirection = 'asc' | 'desc' | null;

@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './data-table.component.html',
  styleUrl: './data-table.component.scss'
})
export class DataTableComponent implements OnChanges {
  @Input() caption = '';
  @Input() columns: TableColumn[] = [];
  @Input() rows: any[] = [];
  @Input() actions: TableAction[] = [];
  @Input() emptyMessage = 'No entries found.';

  sortedRows: any[] = [];
  sortField: string | null = null;
  sortDirection: SortDirection = null;

  ngOnChanges(): void {
    this.sortedRows = [...this.rows];
    this.applySort();
  }

  sortBy(field: string): void {
    if (this.sortField === field) {
      if (this.sortDirection === 'asc') this.sortDirection = 'desc';
      else if (this.sortDirection === 'desc') { this.sortDirection = null; this.sortField = null; }
      else this.sortDirection = 'asc';
    } else {
      this.sortField = field;
      this.sortDirection = 'asc';
    }
    this.applySort();
  }

  private applySort(): void {
    if (!this.sortField || !this.sortDirection) {
      this.sortedRows = [...this.rows];
      return;
    }
    const field = this.sortField;
    const dir = this.sortDirection === 'asc' ? 1 : -1;
    this.sortedRows = [...this.rows].sort((a, b) => {
      const aVal = (a[field] ?? '').toString().toLowerCase();
      const bVal = (b[field] ?? '').toString().toLowerCase();
      return aVal < bVal ? -dir : aVal > bVal ? dir : 0;
    });
  }

  sortIcon(field: string): string {
    if (this.sortField !== field) return '↕';
    return this.sortDirection === 'asc' ? '↑' : '↓';
  }

  sortLabel(col: TableColumn): string {
    if (this.sortField !== col.field) return `Sort by ${col.label}`;
    if (this.sortDirection === 'asc') return `${col.label} sorted ascending, click for descending`;
    if (this.sortDirection === 'desc') return `${col.label} sorted descending, click to clear`;
    return `Sort by ${col.label}`;
  }
}
