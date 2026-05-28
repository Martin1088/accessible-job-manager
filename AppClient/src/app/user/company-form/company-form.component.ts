import { Component, OnInit } from '@angular/core';
import { Company, CompanyLocation, CompanyPosition } from '../../model/company';
import { CompanyService } from '../../services/company.service';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

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
    locations: [],
    positions: []
  };
  isEditMode = false;
  companyId?: number;
  errorMessage = '';

  readonly genderOptions: { value: string; label: string }[] = [
    { value: 'FEMALE', label: 'Female' },
    { value: 'MALE', label: 'Male' },
    { value: 'DIVERSE', label: 'Diverse' },
  ];

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
          if (found) this.company = { ...found, locations: [...found.locations], positions: [...found.positions] };
        }
      });
    }
  }

  addLocation(): void {
    this.company.locations.push({ street: '', city: '' });
  }

  removeLocation(index: number): void {
    this.company.locations.splice(index, 1);
  }

  addPosition(): void {
    this.company.positions.push({ title: '' });
  }

  removePosition(index: number): void {
    this.company.positions.splice(index, 1);
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
