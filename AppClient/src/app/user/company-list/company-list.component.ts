import {Component, OnInit} from '@angular/core';
import {Company} from '../../model/company';
import {CompanyService} from '../../services/company.service';
import {Router} from '@angular/router';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-company-list',
  imports: [CommonModule],
  templateUrl: './company-list.component.html',
  styleUrl: './company-list.component.scss'
})
export class CompanyListComponent implements OnInit {

  companies: Company[] = [];
  errorMessage = '';

  constructor(private companyService: CompanyService, private router: Router) {}

  ngOnInit(): void {
    this.companyService.getAll().subscribe({
      next: (data) => this.companies = data,
      error: () => this.errorMessage = 'Failed to load companies.'
    });
  }

  delete(id: number): void {
    if (!confirm('Delete this company?')) return;
    this.companyService.delete(id).subscribe({
      next: () => this.companies = this.companies.filter(c => c.id !== id),
      error: () => this.errorMessage = 'Failed to delete company.'
    });
  }

  edit(id: number): void {
    this.router.navigate(['/companies/edit', id]);
  }

  create(): void {
    this.router.navigate(['/companies/new']);
  }
}
