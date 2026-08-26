import { Component, OnInit, ChangeDetectionStrategy, ElementRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth.service';
import { UserProfileService } from '../../services/user-profile.service';
import { UserProfile } from '../../model/user-profile';
import { PreferencesService } from '../../services/preferences.service';
import { ExportFormat, ExportService } from '../../services/export.service';
import { LanguageService } from '../../core/language.service';
import { uiToLetterLanguage } from '../../model/document';
import { saveBlobResponse } from '../../core/file-download';

/**
 * The profile form. Its six fields are the sender block of every letter, maintained
 * once here so no cover letter form has to ask for a postal address again.
 */
@Component({
  standalone: true,
  selector: 'app-profile',
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe],
  templateUrl: './profile.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './profile.component.scss'
})
export class ProfileComponent implements OnInit {
  @ViewChild('errorSummary') errorSummary?: ElementRef<HTMLElement>;

  private readonly fb = inject(FormBuilder);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly translate = inject(TranslateService);
  private readonly profiles = inject(UserProfileService);
  private readonly preferences = inject(PreferencesService);
  private readonly exports = inject(ExportService);
  private readonly language = inject(LanguageService);
  readonly auth = inject(AuthService);

  profile: UserProfile | null = null;
  loadError = false;
  saveError = false;
  saving = false;
  submitted = false;

  /** Which export is in flight, so only the button pressed shows its busy label. */
  exporting: ExportFormat | null = null;
  exportError = false;

  readonly form = this.fb.group({
    name: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    street: ['', Validators.required],
    postalCode: ['', Validators.required],
    city: ['', Validators.required],
    phone: [''],
  });

  ngOnInit(): void {
    this.profiles.get().subscribe({
      next: (profile) => this.applyProfile(profile),
      error: () => this.loadError = true,
    });
  }

  private applyProfile(profile: UserProfile): void {
    this.profile = profile;
    // Seeds the shared preferences cache from the response this page already fetched,
    // rather than depending on AccessibilityService's bootstrap-time load having run.
    this.preferences.seed(profile.preferences);
    this.form.patchValue({
      name: profile.name ?? '',
      email: profile.email ?? '',
      street: profile.street ?? '',
      postalCode: profile.postalCode ?? '',
      city: profile.city ?? '',
      phone: profile.phone ?? '',
    });
  }

  save(): void {
    this.submitted = true;
    this.saveError = false;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorSummary?.nativeElement.focus();
      return;
    }

    this.saving = true;
    this.profiles.update(this.form.getRawValue()).subscribe({
      next: (profile) => {
        this.applyProfile(profile);
        this.saving = false;
        this.submitted = false;
        this.announce('PROFILE.SAVED');
      },
      error: () => {
        this.saveError = true;
        this.saving = false;
      },
    });
  }

  /**
   * Downloads the caller's companies, positions and applications as one spreadsheet.
   * The column headers are written in the language the UI is being read in - the page
   * offers no separate picker for it, since a second language choice here would only
   * ever be answered with the one already made in the header.
   */
  exportData(format: ExportFormat): void {
    if (this.exporting) {
      return;
    }
    this.exportError = false;
    this.exporting = format;

    this.exports.exportCompanies(format, uiToLetterLanguage(this.language.current())).subscribe({
      next: (response) => {
        saveBlobResponse(response, format === 'CSV' ? 'companies-export.csv' : 'companies-export.xlsx');
        this.exporting = null;
        this.announce('PROFILE.EXPORT_STARTED');
      },
      error: () => {
        this.exportError = true;
        this.exporting = null;
      },
    });
  }

  invalid(path: string): boolean {
    const control = this.form.get(path);
    return !!control && control.invalid && (control.touched || this.submitted);
  }

  /** Never names an element that is absent: a dangling aria-describedby is dropped. */
  describedBy(path: string, ...ids: string[]): string | null {
    const present = ids.filter(id => id.endsWith('-error') ? this.invalid(path) : true);
    return present.length ? present.join(' ') : null;
  }

  get invalidFields(): { path: string; id: string; label: string }[] {
    const candidates = [
      { path: 'name', id: 'profile-name', label: 'PROFILE.NAME' },
      { path: 'email', id: 'profile-email', label: 'PROFILE.EMAIL' },
      { path: 'street', id: 'profile-street', label: 'PROFILE.STREET' },
      { path: 'postalCode', id: 'profile-postal-code', label: 'PROFILE.POSTAL_CODE' },
      { path: 'city', id: 'profile-city', label: 'PROFILE.CITY' },
    ];
    return candidates.filter(field => this.invalid(field.path));
  }

  private announce(key: string): void {
    this.announcer.announce(this.translate.instant(key), 'polite');
  }
}
