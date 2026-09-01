import { Component, OnInit, ChangeDetectionStrategy, computed, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Company, CompanyLocation, CompanyPosition } from '../../model/company';
import { CompanyService } from '../../services/company.service';
import { SuggestionService } from '../../services/suggestion.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Observable, finalize } from 'rxjs';

import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { JobPostingImportStore } from '../../services/job-posting-import.store';
import { containsName, isSameName, nameKeys } from './company-name-match';

/** One already-saved company offered to the user, with its sites named. */
export interface CompanyMatch {
  readonly company: Company;
  readonly cities: string;
}

/** The cities a company is on record at, blanks dropped. */
function citiesOf(company: Company): string[] {
  return (company.locations ?? []).map(l => l.city).filter((c): c is string => !!c?.trim());
}

/**
 * Precomputed for the template: the lists are rebuilt only when the companies
 * or the typed name change, so the cities are joined once rather than on every
 * change detection pass.
 */
function toMatch(company: Company): CompanyMatch {
  return { company, cities: citiesOf(company).join(', ') };
}

@Component({
  selector: 'app-company-form',
  imports: [FormsModule, TranslatePipe, NgTemplateOutlet, RouterLink],
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

  /**
   * The companies this user already has. Loaded in both modes: edit mode picks
   * the one being edited out of it, and both modes match the name being typed
   * against it, so the company the user already has is offered before a second
   * copy of it is written.
   */
  private readonly existingCompanies = signal<Company[]>([]);

  /**
   * Mirrors `company.name`, which is a plain field driven by ngModel and so
   * cannot be read reactively. Everything that writes the name goes through
   * `onNameChange` or `setCompany` to keep this in step - the alternative,
   * matching in a getter, re-scans the list on every change detection pass.
   */
  private readonly typedName = signal('');

  /**
   * The cities entered in this form, kept in step the same way and for the
   * same reason as the name. A company is identified by its name and where it
   * sits: two firms called "Müller GmbH" in two cities are two companies, and
   * the same name at the same city is one. Positions are deliberately not part
   * of this - a new posting brings a new position every time, so a position
   * can never say whether the company is one the user already has.
   */
  private readonly typedCities = signal<string[]>([]);

  /** A company is never its own duplicate, so the edited one is left out. */
  private readonly otherCompanies = computed(() =>
    this.existingCompanies().filter(c => c.id !== this.companyId));

  /** Companies already saved under the name being typed, at any site. */
  private readonly sameNameCompanies = computed(() => {
    const typed = this.typedName();
    return nameKeys(typed).length
      ? this.otherCompanies().filter(c => isSameName(c.name, typed))
      : [];
  });

  /** The company this form would duplicate: the same name, at the same site. */
  readonly duplicateOf = computed(() => {
    const found = this.sameNameCompanies().find(c => this.couldBeSameSite(c));
    return found ? toMatch(found) : undefined;
  });

  /**
   * The same name at a site the user has not entered here. Not a duplicate - a
   * second branch is a legitimate second record - but still worth showing,
   * because a mistyped city looks exactly like one.
   */
  readonly sameNameElsewhere = computed(() =>
    this.duplicateOf() ? [] : this.sameNameCompanies().map(toMatch).slice(0, 5));

  /**
   * Companies whose name merely contains what has been typed so far, shown
   * while the name is still being written - by the time it is finished one of
   * the two lists above has taken over. Capped at five: it sits under the
   * input to be glanced at, not read.
   */
  readonly similarCompanies = computed(() => {
    const typed = this.typedName();
    const keys = nameKeys(typed);
    if (!keys.length || keys[0].length < 2) return [];
    if (this.duplicateOf() || this.sameNameElsewhere().length) return [];
    return this.otherCompanies()
      .filter(c => containsName(c.name, typed))
      .map(toMatch)
      .slice(0, 5);
  });

  /**
   * Whether an already-saved company could be sitting where this form says.
   * Only a city on both sides can rule it out: with nothing typed yet, or
   * nothing stored against the saved company, the name is all there is to go
   * on and the two are still taken to be the same company.
   */
  private couldBeSameSite(company: Company): boolean {
    const typed = this.typedCities();
    if (!typed.length) return true;
    const saved = citiesOf(company);
    if (!saved.length) return true;
    return typed.some(city => saved.some(known => isSameName(known, city)));
  }

  // The posting the suggestions are read from. Prefilled when the form was
  // opened from the dashboard, but editable so a company started by hand can
  // be filled from a posting too.
  suggestionUrl = '';
  // Which suggestion request is in flight, so only the button that was pressed
  // shows a busy state. Positions and locations are keyed by row index.
  pendingSuggestion: string | null = null;
  suggestionError = '';
  suggestionStatus = '';
  // Which trigger the current message belongs to. Only one suggestion runs at
  // a time, so a single message plus its owner is enough to render it next to
  // the button that was pressed - a position's buttons sit several screens
  // below the suggestions section, where a message there is never seen.
  feedbackKey: string | null = null;
  // Counts what the running suggestion actually filled in, so a run that
  // changed nothing says so instead of reporting success over an unchanged
  // form - which is indistinguishable from the button doing nothing at all.
  private filledCount = 0;

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
    private importStore: JobPostingImportStore,
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode = true;
      this.companyId = +id;
    }

    // Fetched in create mode too, where it feeds the "you already have this
    // one" hint under the name field rather than the form's own values.
    this.companyService.getAll().subscribe({
      next: (companies) => {
        this.existingCompanies.set(companies);
        if (!this.companyId) return;
        const found = companies.find(c => c.id === this.companyId);
        if (found) this.setCompany({ ...found, locations: [...found.locations], positions: [...found.positions] });
      }
    });

    if (this.isEditMode) return;

    // Prefill from a parsed job posting, passed via router navigation state
    // (e.g. the "Use for new company" action on the dashboard).
    const prefill = history.state?.company as Company | undefined;
    if (prefill) {
      this.setCompany({
        name: prefill.name ?? '',
        locations: prefill.locations?.length ? [...prefill.locations] : [],
        positions: prefill.positions?.length ? [...prefill.positions] : [],
      });
    }
    this.sourceJobUrl = (history.state?.sourceJobUrl as string | undefined)?.trim() || undefined;
    this.suggestionUrl = this.sourceJobUrl ?? '';
  }

  /** Replaces the whole form value, keeping what is matched on in step. */
  private setCompany(company: Company): void {
    this.company = company;
    this.typedName.set(company.name ?? '');
    this.syncCities();
  }

  /** The name field writes through here rather than binding straight to the field. */
  onNameChange(name: string): void {
    this.company.name = name;
    this.typedName.set(name);
  }

  /** Likewise for a city: it decides which site of a company this form is about. */
  onCityChange(index: number, city: string): void {
    this.company.locations[index].city = city;
    this.syncCities();
  }

  private syncCities(): void {
    this.typedCities.set(citiesOf(this.company));
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
      this.onNameChange(this.fill(this.company.name, s.name) ?? '');
    });
  }

  suggestLocation(index: number): void {
    const location = this.company.locations[index];
    this.runSuggestion(`location-${index}`, this.suggestionService.location(this.suggestionUrl), s => {
      location.street = this.fill(location.street, s.street) ?? '';
      location.city = this.fill(location.city, s.city) ?? '';
      location.postcode = this.fill(location.postcode, s.postcode);
      location.country = this.fill(location.country, s.country);
      this.syncCities();
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
   *
   * <p>Like every other suggestion this only fills blanks: a method the user
   * picked themselves outranks a guess read off the posting. The target field
   * is still offered, so a hand-picked EMAIL can pick up a detected address.
   */
  suggestApplicationMethod(index: number): void {
    const position = this.company.positions[index];
    this.runSuggestion(`apply-${index}`, this.suggestionService.applicationMethod(this.suggestionUrl), s => {
      position.applicationMethod = this.fill(position.applicationMethod, s.method);
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

  /** The message shown next to one trigger, empty for every other trigger. */
  suggestionStatusFor(key: string): string {
    return this.feedbackKey === key ? this.suggestionStatus : '';
  }

  suggestionErrorFor(key: string): string {
    return this.feedbackKey === key ? this.suggestionError : '';
  }

  private runSuggestion<T>(key: string, request: Observable<T>, apply: (result: T) => void): void {
    this.feedbackKey = key;
    if (!this.suggestionUrl.trim()) {
      this.suggestionStatus = '';
      this.suggestionError = this.translate.instant('COMPANIES.SUGGEST_URL_REQUIRED');
      return;
    }
    this.suggestionError = '';
    this.suggestionStatus = this.translate.instant('COMPANIES.SUGGEST_RUNNING');
    this.pendingSuggestion = key;
    request.pipe(finalize(() => this.pendingSuggestion = null)).subscribe({
      next: (result) => {
        this.filledCount = 0;
        apply(result);
        this.suggestionStatus = this.translate.instant(
          this.filledCount > 0 ? 'COMPANIES.SUGGEST_DONE' : 'COMPANIES.SUGGEST_NOTHING');
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
    this.filledCount++;
    return suggested;
  }

  addLocation(): void {
    this.company.locations.push({ street: '', city: '' });
  }

  removeLocation(index: number): void {
    this.company.locations.splice(index, 1);
    this.syncCities();
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
          // An uploaded PDF is reason enough on its own: the import screen's PDF
          // path has no source URL at all, so gating only on `sourceJobUrl`
          // would silently drop the snapshot in exactly the case it is the only
          // copy that can be filed.
          if (positionId && (this.sourceJobUrl || this.importStore.hasPending)) {
            this.createSnapshot(positionId, this.sourceJobUrl ?? '');
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
  //
  // A PDF the user uploaded on the import screen wins over rendering the URL:
  // it only exists when the posting came in that way, which is precisely when
  // the render would fail too — Gotenberg's Chromium fetches the URL from this
  // server, so a board that answers 403 to the parser answers 403 to it as well.
  private createSnapshot(companyPositionId: number, url: string): void {
    const uploaded = this.importStore.take();
    if (uploaded) {
      const form = new FormData();
      form.append('file', uploaded);
      const params = new HttpParams().set('companyPositionId', companyPositionId);
      this.http.post('/api/posting/snapshot/upload', form, { params }).subscribe({ error: () => {} });
      return;
    }
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
        this.setCompany({
          name: parsed.name ?? '',
          locations: Array.isArray(parsed.locations) ? parsed.locations : [],
          positions: Array.isArray(parsed.positions) ? parsed.positions : [],
        });
        this.importMode = false;
      } catch {
        this.jsonError = this.translate.instant('COMPANIES.JSON_INVALID');
      }
    };
    reader.readAsText(file);
  }
}
