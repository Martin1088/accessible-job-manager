import { Component, OnInit, HostListener, ChangeDetectionStrategy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { PreferencesService } from '../../services/preferences.service';
import { ContrastMode, PreferredFontFamily, UserPreferences } from '../../model/user-preferences';

type TriState = 'SYSTEM' | 'ON' | 'OFF';

function toTriState(value: boolean | null): TriState {
  if (value === true) return 'ON';
  if (value === false) return 'OFF';
  return 'SYSTEM';
}

function fromTriState(value: TriState): boolean | null {
  if (value === 'ON') return true;
  if (value === 'OFF') return false;
  return null;
}

const DEFAULT_FONT_SCALE = 1;
const DEFAULT_LINE_HEIGHT = 1.5;

/**
 * Accessibility/display preferences, split out from the profile page so it has its
 * own place in the account menu rather than sharing a screen with the sender form.
 */
@Component({
  standalone: true,
  selector: 'app-preferences',
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe],
  templateUrl: './preferences.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './preferences.component.scss'
})
export class PreferencesComponent implements OnInit {

  private readonly fb = inject(FormBuilder);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly translate = inject(TranslateService);
  private readonly preferences = inject(PreferencesService);

  saveError = false;
  saving = false;

  readonly form = this.fb.nonNullable.group({
    contrastMode: this.fb.nonNullable.control<ContrastMode>('SYSTEM'),
    reduceMotion: this.fb.nonNullable.control<TriState>('SYSTEM'),
    hideImages: this.fb.nonNullable.control<TriState>('SYSTEM'),
    fontFamily: this.fb.nonNullable.control<PreferredFontFamily>('SYSTEM'),
    fontScale: this.fb.nonNullable.control<number>(DEFAULT_FONT_SCALE, [Validators.min(0.8), Validators.max(2)]),
    lineHeight: this.fb.nonNullable.control<number>(DEFAULT_LINE_HEIGHT, [Validators.min(1), Validators.max(3)]),
  });

  // A modified but unsaved form should still answer to Ctrl+S, wherever focus is on this page.
  @HostListener('document:keydown', ['$event'])
  onKeydown(e: KeyboardEvent): void {
    if (e.ctrlKey && !e.altKey && !e.metaKey && !e.shiftKey && e.key.toLowerCase() === 's') {
      e.preventDefault();
      if (this.form.dirty) {
        this.save();
      }
    }
  }

  ngOnInit(): void {
    // AccessibilityService's app initializer always loads this before any route
    // renders, so this reads the already-warmed cache rather than issuing its own GET.
    this.preferences.preferences$.subscribe(preferences => this.apply(preferences));
  }

  private apply(preferences: UserPreferences): void {
    this.form.reset({
      contrastMode: preferences.contrastMode ?? 'SYSTEM',
      reduceMotion: toTriState(preferences.reduceMotion),
      hideImages: toTriState(preferences.hideImages),
      fontFamily: preferences.fontFamily ?? 'SYSTEM',
      fontScale: preferences.fontScale ?? DEFAULT_FONT_SCALE,
      lineHeight: preferences.lineHeight ?? DEFAULT_LINE_HEIGHT,
    });
  }

  save(): void {
    this.saveError = false;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const request: UserPreferences = {
      contrastMode: raw.contrastMode,
      reduceMotion: fromTriState(raw.reduceMotion),
      hideImages: fromTriState(raw.hideImages),
      fontFamily: raw.fontFamily,
      fontScale: raw.fontScale,
      lineHeight: raw.lineHeight,
    };

    this.saving = true;
    this.preferences.update(request).subscribe({
      // The preferences$ subscription in ngOnInit already reset the form to the
      // saved values by the time this runs - update() pushes into that stream first.
      next: () => {
        this.saving = false;
        this.announce('PREFERENCES.SAVED');
      },
      error: () => {
        this.saveError = true;
        this.saving = false;
      },
    });
  }

  /** An explicit escape hatch: clears every override, not just the form's unsaved edits. */
  reset(): void {
    this.form.reset({
      contrastMode: 'SYSTEM',
      reduceMotion: 'SYSTEM',
      hideImages: 'SYSTEM',
      fontFamily: 'SYSTEM',
      fontScale: DEFAULT_FONT_SCALE,
      lineHeight: DEFAULT_LINE_HEIGHT,
    });
    this.save();
  }

  invalid(path: string): boolean {
    const control = this.form.get(path);
    return !!control && control.invalid && control.touched;
  }

  private announce(key: string): void {
    this.announcer.announce(this.translate.instant(key), 'polite');
  }
}
