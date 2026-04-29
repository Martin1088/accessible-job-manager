import { Component, OnInit } from '@angular/core';
import {Company} from '../../model/company';
import {CompanyService} from '../../services/company.service';
import {ActivatedRoute, Router} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-company-form',
  imports: [FormsModule, CommonModule],
  templateUrl: './company-form.component.html',
  styleUrl: './company-form.component.scss',
  standalone: true,
})
export class CompanyFormComponent implements OnInit {

  company: Company = {
    name: '',
    street: '',
    city: '',
    position: '',
    contact: '',
    website: '',
    notes: ''
  };
  isEditMode = false;
  companyId?: number;
  errorMessage = '';

  constructor(
    private companyService: CompanyService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode = true;
      this.companyId = +id;
      this.companyService.getAll().subscribe({
        next: (companies) => {
          const found = companies.find(c => c.id === this.companyId);
          if (found) this.company = { ...found };
        }
      });
    }
  }

  save(): void {
    if (this.isEditMode && this.companyId) {
      this.companyService.update(this.companyId, this.company).subscribe({
        next: () => this.router.navigate(['/companies']),
        error: () => this.errorMessage = 'Failed to update company.'
      });
    } else {
      this.companyService.create(this.company).subscribe({
        next: () => this.router.navigate(['/companies']),
        error: () => this.errorMessage = 'Failed to create company.'
      });
    }
  }

  cancel(): void {
    this.router.navigate(['/companies']);
  }
}
