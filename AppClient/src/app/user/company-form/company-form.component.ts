import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Company, CompanyLocation, CompanyPosition } from '../../model/company';
import { CompanyService } from '../../services/company.service';
import { SuggestionService } from '../../services/suggestion.service';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Observable, finalize } from 'rxjs';

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

  // The posting the suggestions are read from. Prefilled when the form was
  // opened from the dashboard, but editable so a company started by hand can
  // be filled from a posting too.
  suggestionUrl = '';
  // Which suggestion request is in flight, so only the button that was pressed
  // shows a busy state. Positions and locations are keyed by row index.
  pendingSuggestion: string | null = null;
  suggestionError = '';
  suggestionStatus = '';

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

  readonly languageOptions: { value: string; label: string }[] = [
    { value: 'GERMAN', label: 'COMPANIES.LANGUAGE_GERMAN' },
    { value: 'ENGLISH', label: 'COMPANIES.LANGUAGE_ENGLISH' },
    { value: 'DUTCH', label: 'COMPANIES.LANGUAGE_DUTCH' },
  ];

  readonly applicationMethodOptions: { value: string; label: string }[] = [
    { value: 'EMAIL', label: 'COMPANIES.APPLY_METHOD_EMAIL' },
    { value: 'WEB_FORM', label: 'COMPANIES.APPLY_METHOD_WEB_FORM' },
    { value: 'UNKNOWN', label: 'COMPANIES.APPLY_METHOD_UNKNOWN' },
  ];

  constructor(
    private companyService: CompanyService,
    private suggestionService: SuggestionService,
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
    this.suggestionUrl = this.sourceJobUrl ?? '';
  }

  // --- On-demand suggestions -------------------------------------------------
  //
  // Each section of the form asks for its own suggestion, so the user waits
  // only for the part they are filling in - the default provider is a local
  // model and a full-form call would block on all of it at once. A suggestion
  // never overwrites something the user already typed: it fills blanks and
  // leaves the rest, because the user's own value is the more reliable one.

  suggestCompany(): void {
    this.runSuggestion('company', this.suggestionService.company(this.suggestionUrl), s => {
      this.company.name = this.fill(this.company.name, s.name) ?? '';
    });
  }

  suggestLocation(index: number): void {
    const location = this.company.locations[index];
    this.runSuggestion(`location-${index}`, this.suggestionService.location(this.suggestionUrl), s => {
      location.street = this.fill(location.street, s.street) ?? '';
      location.city = this.fill(location.city, s.city) ?? '';
      location.postcode = this.fill(location.postcode, s.postcode);
      location.country = this.fill(location.country, s.country);
    });
  }

  suggestPosition(index: number): void {
    const position = this.company.positions[index];
    this.runSuggestion(`position-${index}`, this.suggestionService.position(this.suggestionUrl), s => {
      position.title = this.fill(position.title, s.title) ?? '';
      position.contactGender = this.fill(position.contactGender, s.contactGender);
      position.contactTitle = this.fill(position.contactTitle, s.contactTitle);
      position.contactLastName = this.fill(position.contactLastName, s.contactLastName);
      position.email = this.fill(position.email, s.email);
    });
  }

  /**
   * Answers "which way do I go to apply?". The application link goes into the
   * position's existing website field rather than a field of its own.
   */
  suggestApplicationMethod(index: number): void {
    const position = this.company.positions[index];
    this.runSuggestion(`apply-${index}`, this.suggestionService.applicationMethod(this.suggestionUrl), s => {
      position.applicationMethod = s.method;
      if (s.method === 'EMAIL') {
        position.email = this.fill(position.email, s.email);
      } else if (s.method === 'WEB_FORM') {
        position.website = this.fill(position.website, s.applicationUrl);
      }
    });
  }

  isSuggesting(key: string): boolean {
    return this.pendingSuggestion === key;
  }

  private runSuggestion<T>(key: string, request: Observable<T>, apply: (result: T) => void): void {
    if (!this.suggestionUrl.trim()) {
      this.suggestionError = this.translate.instant('COMPANIES.SUGGEST_URL_REQUIRED');
      return;
    }
    this.suggestionError = '';
    this.suggestionStatus = this.translate.instant('COMPANIES.SUGGEST_RUNNING');
    this.pendingSuggestion = key;
    request.pipe(finalize(() => this.pendingSuggestion = null)).subscribe({
      next: (result) => {
        apply(result);
        this.suggestionStatus = this.translate.instant('COMPANIES.SUGGEST_DONE');
      },
      error: (err: HttpErrorResponse) => {
        this.suggestionStatus = '';
        this.suggestionError = err.error?.message ?? this.translate.instant('COMPANIES.SUGGEST_FAILED');
      }
    });
  }

  /** Keeps what the user typed; only blanks are filled from a suggestion. */
  private fill<T>(current: T | undefined, suggested: T | undefined | null): T | undefined {
    const isBlank = current === undefined || current === null
      || (typeof current === 'string' && current.trim() === '');
    if (!isBlank) return current;
    if (suggested === undefined || suggested === null) return current;
    if (typeof suggested === 'string' && suggested.trim() === '') return current;
    return suggested;
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
        error: (err: HttpErrorResponse) => {
          this.errorMessage = err.error?.message ?? this.translate.instant('COMPANIES.ERROR_CREATE');
        }
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
