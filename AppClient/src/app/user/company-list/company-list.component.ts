import { Component, OnInit } from '@angular/core';
import { Company } from '../../model/company';
import { CompanyService } from '../../services/company.service';
import { Router } from '@angular/router';

import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DataTableComponent, TableColumn, TableAction } from '../../shared/data-table/data-table.component';

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
  selector: 'app-company-list',
  imports: [FormsModule, DataTableComponent, TranslatePipe],
  templateUrl: './company-list.component.html',
  styleUrl: './company-list.component.scss'
})
export class CompanyListComponent implements OnInit {

  companies: Company[] = [];
  allRows: any[] = [];
  errorMessage = '';

  filterYear: number | '' = '';
  filterMonth: number | '' = '';

  searchField = 'all';
  searchTerm = '';

  readonly searchFields = [
    { value: 'all',           label: 'COMPANIES.SEARCH_ALL' },
    { value: 'name',          label: 'COMPANIES.COL_NAME' },
    { value: 'city',          label: 'COMPANIES.COL_CITY' },
    { value: 'positionTitle', label: 'COMPANIES.COL_POSITION' },
  ];

  readonly monthIndexes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

  columns: TableColumn[] = [
    { label: 'COMPANIES.COL_NAME',     field: 'name',          sortable: true },
    { label: 'COMPANIES.COL_CITY',     field: 'city',          sortable: true },
    { label: 'COMPANIES.COL_POSITION', field: 'positionTitle', sortable: true },
    { label: 'COMPANIES.COL_ADDED',    field: 'positionDate',  sortable: true },
  ];

  actions: TableAction[] = [
    {
      label: 'COMPANIES.ACTION_APPLY',
      ariaLabel: (row) => this.translate.instant('COMPANIES.ACTION_APPLY_ARIA', { position: row.positionTitle, company: row.name }),
      handler: (row) => this.router.navigate(['/applications'], {
        queryParams: { positionId: row.positionId, companyName: row.name, positionTitle: row.positionTitle }
      }),
    },
    {
      label: 'COMPANIES.ACTION_EDIT',
      ariaLabel: (row) => this.translate.instant('COMPANIES.ACTION_EDIT_ARIA', { company: row.name }),
      handler: (row) => this.router.navigate(['/companies/edit', row.companyId]),
    },
    {
      label: 'COMPANIES.ACTION_DELETE',
      ariaLabel: (row) => this.translate.instant('COMPANIES.ACTION_DELETE_ARIA', { company: row.name }),
      handler: (row) => this.deleteCompany(row.companyId, row.name),
    },
  ];

  constructor(private companyService: CompanyService, private router: Router, private translate: TranslateService) {}

  ngOnInit(): void {
    this.companyService.getAll().subscribe({
      next: (data) => {
        this.companies = data;
        this.allRows = this.toRows(data);
      },
      error: () => this.errorMessage = this.translate.instant('COMPANIES.ERROR_LOAD')
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
      ? ['name', 'city', 'positionTitle']
      : [this.searchField];
    return fields.some(f => (row[f] ?? '').toString().toLowerCase().includes(term));
  }

  get filteredRows(): any[] {
    let source = this.allRows;

    if (this.filterYear !== '' || this.filterMonth !== '') {
      source = source.filter(r => {
        const iso = r.rawCreatedAt;
        if (!iso) return true; // include rows with unknown date
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

  create(): void {
    this.router.navigate(['/companies/new']);
  }

  private deleteCompany(id: number, name: string): void {
    if (!confirm(this.translate.instant('COMPANIES.CONFIRM_DELETE', { name }))) return;
    this.companyService.delete(id).subscribe({
      next: () => {
        this.companies = this.companies.filter(c => c.id !== id);
        this.allRows = this.toRows(this.companies);
      },
      error: () => this.errorMessage = this.translate.instant('COMPANIES.ERROR_DELETE')
    });
  }

  private toRows(companies: Company[]): any[] {
    return companies.flatMap(c =>
      c.positions.map(p => ({
        companyId:     c.id,
        positionId:    p.id,
        name:          c.name,
        city:          c.locations.map(l => l.city).filter(Boolean).join(', ') || '—',
        positionTitle: p.title,
        positionDate:  p.createdAt ? p.createdAt.substring(0, 10) : '—',
        rawCreatedAt:  p.createdAt ?? null,
      }))
    );
  }
}
