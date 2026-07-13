import { Component, OnInit } from '@angular/core';
import { Company } from '../../model/company';
import { CompanyService } from '../../services/company.service';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
  imports: [CommonModule, FormsModule, DataTableComponent],
  templateUrl: './company-list.component.html',
  styleUrl: './company-list.component.scss'
})
export class CompanyListComponent implements OnInit {

  companies: Company[] = [];
  allRows: any[] = [];
  errorMessage = '';

  filterYear: number | '' = '';
  filterMonth: number | '' = '';

  readonly months = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
  ];

  columns: TableColumn[] = [
    { label: 'Company',  field: 'name',          sortable: true },
    { label: 'City',     field: 'city',           sortable: true },
    { label: 'Position', field: 'positionTitle',  sortable: true },
    { label: 'Added',    field: 'positionDate',   sortable: true },
  ];

  actions: TableAction[] = [
    {
      label: 'Apply',
      ariaLabel: (row) => `Apply for ${row.positionTitle} at ${row.name}`,
      handler: (row) => this.router.navigate(['/applications'], {
        queryParams: { positionId: row.positionId, companyName: row.name, positionTitle: row.positionTitle }
      }),
    },
    {
      label: 'Edit',
      ariaLabel: (row) => `Edit ${row.name}`,
      handler: (row) => this.router.navigate(['/companies/edit', row.companyId]),
    },
    {
      label: 'Delete',
      ariaLabel: (row) => `Delete ${row.name}`,
      handler: (row) => this.deleteCompany(row.companyId, row.name),
    },
  ];

  constructor(private companyService: CompanyService, private router: Router) {}

  ngOnInit(): void {
    this.companyService.getAll().subscribe({
      next: (data) => {
        this.companies = data;
        this.allRows = this.toRows(data);
      },
      error: () => this.errorMessage = 'Failed to load companies.'
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
      if (!iso) return true; // include rows with unknown date
      if (this.filterYear !== '' && yearOf(iso) !== Number(this.filterYear)) return false;
      if (this.filterMonth !== '' && monthOf(iso) !== Number(this.filterMonth)) return false;
      return true;
    });
  }

  create(): void {
    this.router.navigate(['/companies/new']);
  }

  private deleteCompany(id: number, name: string): void {
    if (!confirm(`Delete ${name}?`)) return;
    this.companyService.delete(id).subscribe({
      next: () => {
        this.companies = this.companies.filter(c => c.id !== id);
        this.allRows = this.toRows(this.companies);
      },
      error: () => this.errorMessage = 'Failed to delete company.'
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
