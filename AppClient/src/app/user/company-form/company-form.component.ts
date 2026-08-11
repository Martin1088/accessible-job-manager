import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Company, CompanyLocation, CompanyPosition } from '../../model/company';
import { CompanyService } from '../../services/company.service';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';

import { TranslatePipe, TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-company-form',
  imports: [FormsModule, TranslatePipe],
  templateUrl: './company-form.component.html',
  styleUrl: './company-form.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager,
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
  importMode = false;
  jsonError = '';

  // Set when this form was opened from the dashboard's "Use for new company"
  // action, so we know to generate a PDF snapshot of the source posting once
  // the company (and its first position) has actually been saved. The
  // snapshot is created in the background; it's viewable later from the
  // company list's "View job posting" action rather than shown here.
  private sourceJobUrl?: string;

  // Values are translation keys, translated via the `translate` pipe in the
  // template so the options stay in sync when the language is switched.
  readonly genderOptions: { value: string; label: string }[] = [
    { value: 'FEMALE', label: 'COMPANIES.GENDER_FEMALE' },
    { value: 'MALE', label: 'COMPANIES.GENDER_MALE' },
    { value: 'DIVERSE', label: 'COMPANIES.GENDER_DIVERSE' },
  ];

  constructor(
    private companyService: CompanyService,
    private route: ActivatedRoute,
    private router: Router,
    private translate: TranslateService,
    private http: HttpClient,
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
      return;
    }

    // Prefill from a parsed job posting, passed via router navigation state
    // (e.g. the "Use for new company" action on the dashboard).
    const prefill = history.state?.company as Company | undefined;
    if (prefill) {
      this.company = {
        name: prefill.name ?? '',
        locations: prefill.locations?.length ? [...prefill.locations] : [],
        positions: prefill.positions?.length ? [...prefill.positions] : [],
      };
    }
    this.sourceJobUrl = (history.state?.sourceJobUrl as string | undefined)?.trim() || undefined;
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
        error: (err: HttpErrorResponse) => {
          this.errorMessage = err.status === 409
            ? (err.error ?? this.translate.instant('COMPANIES.ERROR_UPDATE_CONFLICT'))
            : this.translate.instant('COMPANIES.ERROR_UPDATE');
        }
      });
    } else {
      this.companyService.create(this.company).subscribe({
        next: (created) => {
          const positionId = created.positions?.[0]?.id;
          if (this.sourceJobUrl && positionId) {
            this.createSnapshot(positionId, this.sourceJobUrl);
          }
          this.router.navigate(['/companies']);
        },
        error: () => this.errorMessage = this.translate.instant('COMPANIES.ERROR_CREATE')
      });
    }
  }

  cancel(): void {
    this.router.navigate(['/companies']);
  }

  // Best-effort: the company is already saved and the user has moved on by
  // the time this resolves, so failures here aren't surfaced — they'd just
  // mean no snapshot shows up under "View job posting" in the company list.
  private createSnapshot(companyPositionId: number, url: string): void {
    const params = new HttpParams().set('url', url).set('companyPositionId', companyPositionId);
    this.http.post('/api/posting/snapshot', null, { params }).subscribe({ error: () => {} });
  }

  onJsonFileSelected(event: Event): void {
    this.jsonError = '';
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsed = JSON.parse(reader.result as string);
        this.company = {
          name: parsed.name ?? '',
          locations: Array.isArray(parsed.locations) ? parsed.locations : [],
          positions: Array.isArray(parsed.positions) ? parsed.positions : [],
        };
        this.importMode = false;
      } catch {
        this.jsonError = this.translate.instant('COMPANIES.JSON_INVALID');
      }
    };
    reader.readAsText(file);
  }
}
