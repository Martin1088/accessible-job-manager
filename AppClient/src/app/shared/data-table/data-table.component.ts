import { Component, Input, OnChanges, ChangeDetectionStrategy, inject } from '@angular/core';
import { LiveAnnouncer } from '@angular/cdk/a11y';

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
  /** Omitted means the action shows on every row; used where rows are of mixed kinds. */
  visible?: (row: any) => boolean;
  /**
   * Rendered as `data-action`, so a caller whose action removes the row can
   * find the same button in the next one and move focus there first. Without a
   * handle like this the only way to address it is by counting buttons, which
   * breaks the moment a row shows a different set of them.
   */
  name?: string;
}

type SortDirection = 'asc' | 'desc' | null;

@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './data-table.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
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

  private rowActions = new WeakMap<any, TableAction[]>();
  private readonly announcer = inject(LiveAnnouncer);

  constructor(private translate: TranslateService) {}

  ngOnChanges(): void {
    // The per-row action lists are keyed by row identity, so they have to go whenever
    // the rows or the actions themselves are replaced.
    this.rowActions = new WeakMap();
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
      ? (this.sortDirection === 'asc' ? 'TABLE.SORT_ANNOUNCE_ASC' : 'TABLE.SORT_ANNOUNCE_DESC')
      : 'TABLE.SORT_ANNOUNCE_NONE';
    this.announcer.announce(this.translate.instant(key, { column }), 'polite');
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

  /**
   * The actions that apply to one row, so a table can mix rows of different kinds.
   * Called from the template on every change detection pass, so the filtered list is
   * cached per row rather than rebuilt each time.
   */
  actionsFor(row: any): TableAction[] {
    let actions = this.rowActions.get(row);
    if (!actions) {
      actions = this.actions.filter(action => !action.visible || action.visible(row));
      this.rowActions.set(row, actions);
    }
    return actions;
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
