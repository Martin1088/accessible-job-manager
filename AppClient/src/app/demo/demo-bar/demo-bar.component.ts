import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { DEMO_CONTROLS, DemoRole } from '../demo-mode';

/**
 * The permanently visible demo notice, and the role switcher that replaces the
 * login.
 *
 * Showing the role separation is the point of the demo, and three perspectives
 * one click apart show it better than three sign-ins would. The switch is
 * announced because a screen reader user would otherwise only find out that the
 * role changed by noticing the navigation is different.
 *
 * The component knows nothing about the seed data: it talks to DEMO_CONTROLS,
 * which is null everywhere except the demo build. That is what lets the bar sit
 * in the shared shell template without dragging fixtures into production.
 */
@Component({
  selector: 'app-demo-bar',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './demo-bar.component.html',
  styleUrl: './demo-bar.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class DemoBarComponent {

  private readonly controls = inject(DEMO_CONTROLS);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly translate = inject(TranslateService);

  readonly roles: DemoRole[] = ['USER', 'ADVISOR', 'REVIEWER'];

  role(): DemoRole {
    return this.controls?.role() ?? 'USER';
  }

  onRoleChange(event: Event): void {
    const role = (event.target as HTMLSelectElement).value as DemoRole;
    this.controls?.switchTo(role);
    this.announce('DEMO.ROLE_CHANGED', { role: this.translate.instant(`DEMO.ROLE_${role}`) });
  }

  reset(): void {
    this.controls?.reset();
    this.announce('DEMO.RESET_DONE');
  }

  private announce(key: string, params?: Record<string, unknown>): void {
    void this.announcer.announce(this.translate.instant(key, params), 'assertive');
  }
}
