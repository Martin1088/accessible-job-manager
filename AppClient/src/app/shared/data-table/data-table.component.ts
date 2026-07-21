import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

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
  imports: [CommonModule, TranslatePipe],
  templateUrl: './data-table.component.html',
  styleUrl: './data-table.component.scss'
})
export class DataTableComponent implements OnChanges {
  // caption / columns[].label / emptyMessage / actions[].label are translation
  // keys, not display text — this component translates them itself so callers
  // stay reactive to language switches without any extra plumbing.
  @Input() caption = '';
  @Input() columns: TableColumn[] = [];
  @Input() rows: any[] = [];
  @Input() actions: TableAction[] = [];
  @Input() emptyMessage = 'No entries found.';

  sortedRows: any[] = [];
  sortField: string | null = null;
  sortDirection: SortDirection = null;

  constructor(private translate: TranslateService) {}

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
    const column = this.translate.instant(col.label);
    if (this.sortField === col.field) {
      if (this.sortDirection === 'asc') return this.translate.instant('TABLE.SORTED_ASC', { column });
      if (this.sortDirection === 'desc') return this.translate.instant('TABLE.SORTED_DESC', { column });
    }
    return this.translate.instant('TABLE.SORT_BY', { column });
  }
}
