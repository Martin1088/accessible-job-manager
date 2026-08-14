import { Component, OnInit, ChangeDetectionStrategy, ElementRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth.service';
import { UserProfileService } from '../../services/user-profile.service';
import { UserProfile } from '../../model/user-profile';

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
  readonly auth = inject(AuthService);

  profile: UserProfile | null = null;
  loadError = false;
  saveError = false;
  saving = false;
  submitted = false;

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
