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
    { label: 'Name',      field: 'name',      sortable: true },
    { label: 'Cities',    field: 'cities',    sortable: true },
    { label: 'Positions', field: 'positions', sortable: true },
  ];

  actions: TableAction[] = [
    {
      label: 'Edit',
      ariaLabel: (row) => `Edit ${row.name}`,
      handler: (row) => this.router.navigate(['/companies/edit', row.id]),
    },
    {
      label: 'Delete',
      ariaLabel: (row) => `Delete ${row.name}`,
      handler: (row) => this.deleteCompany(row.id, row.name),
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
    return companies.map(c => ({
      id:        c.id,
      name:      c.name,
      cities:    c.locations.map(l => l.city).join(', '),
      positions: c.positions.map(p => p.title).join(', '),
    }));
  }
}
