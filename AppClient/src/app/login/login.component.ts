
import { ActivatedRoute } from '@angular/router';
import { Component, OnInit, ChangeDetectionStrategy, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { DEMO_CONTROLS, DEMO_MODE, DemoRole } from '../demo/demo-mode';

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
   * The login page is the front door in every build. In the `demo` build the
   * three role buttons hand over to the demo controls instead of the OAuth
   * endpoints (which do not exist on GitHub Pages), and they carry the name of
   * the seeded person you would sit down as. In every other build both tokens
   * are inert and the buttons redirect to `/api/login/as/{role}`.
   */
  readonly demoMode = inject(DEMO_MODE);
  private readonly demoControls = inject(DEMO_CONTROLS);

  /** The three seeded people, in the order [primary, other, other]. */
  readonly personas = (['USER', 'ADVISOR', 'REVIEWER'] as const).map(role => ({
    role,
    name: this.demoControls?.people[role].name ?? '',
  }));

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
