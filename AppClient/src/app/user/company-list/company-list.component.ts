import { Component, OnInit } from '@angular/core';
import { Company } from '../../model/company';
import { CompanyService } from '../../services/company.service';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { DataTableComponent, TableColumn, TableAction } from '../../shared/data-table/data-table.component';

@Component({
  selector: 'app-company-list',
  imports: [CommonModule, DataTableComponent],
  templateUrl: './company-list.component.html',
  styleUrl: './company-list.component.scss'
})
export class CompanyListComponent implements OnInit {

  companies: Company[] = [];
  rows: any[] = [];
  errorMessage = '';

  columns: TableColumn[] = [
    { label: 'Company',  field: 'name',          sortable: true },
    { label: 'City',     field: 'city',           sortable: true },
    { label: 'Position', field: 'positionTitle',  sortable: true },
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
        this.rows = this.toRows(data);
      },
      error: () => this.errorMessage = 'Failed to load companies.'
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
        this.rows = this.toRows(this.companies);
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
      }))
    );
  }
}
