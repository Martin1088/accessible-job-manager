
import { ActivatedRoute } from '@angular/router';
import { Component, OnInit, ChangeDetectionStrategy, computed, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { DEMO_CONTROLS, DEMO_MODE, DemoRole } from '../demo/demo-mode';
import { LanguageService } from '../core/language.service';

/** `toLocaleDateString` wants a full locale, the language switch stores a code. */
const DATE_LOCALE: Record<string, string> = { en: 'en-GB', de: 'de-DE', nl: 'nl-NL' };

@Component({
  standalone: true,
  selector: 'app-login',
  imports: [TranslatePipe],
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './login.component.scss'
})
export class LoginComponent implements OnInit {
  // The raw code from the query param (e.g. "wrong_role") or null. Resolved
  // to a message in the template via the translate pipe, keyed by code, so
  // the text stays reactive to a language switch like everywhere else.
  error: string | null = null;

  /**
   * In the demo build this page is the front door: the sign-in card becomes a
   * DIN 5008 letter inviting the visitor in, and the three role buttons hand
   * over to the demo controls instead of the OAuth endpoints (which do not
   * exist on GitHub Pages). In every other build both tokens are inert and the
   * page renders and behaves exactly as before.
   */
  readonly demoMode = inject(DEMO_MODE);
  private readonly demoControls = inject(DEMO_CONTROLS);
  private readonly language = inject(LanguageService);

  /** The three seeded people, in the order the letter offers them. */
  readonly personas = (['USER', 'ADVISOR', 'REVIEWER'] as const).map(role => ({
    role,
    name: this.demoControls?.people[role].name ?? '',
  }));

  /**
   * The letter's dateline, in the active language. DIN 5008 dates a letter;
   * an undated one reads as a form, and the demo letter is a real letter.
   */
  readonly demoDate = computed(() => {
    const lang = this.language.current() ?? 'en';
    return new Date().toLocaleDateString(DATE_LOCALE[lang] ?? lang, {
      day: 'numeric', month: 'long', year: 'numeric',
    });
  });

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.error = this.route.snapshot.queryParamMap.get('error');
  }

  enterDemo(role: DemoRole): void {
    this.demoControls?.switchTo(role);
  }

  loginAsUser(): void {
    window.location.href = '/api/login/as/user';
  }

  loginAsAdvisor(): void {
    window.location.href = '/api/login/as/advisor';
  }

  loginAsReviewer(): void {
    window.location.href = '/api/login/as/reviewer';
  }
}
