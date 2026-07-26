import { RouterOutlet, RouterLink, RouterLinkActive, NavigationEnd, Router } from '@angular/router';
import { AsyncPipe } from '@angular/common';
import { Component, HostListener, Inject, OnInit, DOCUMENT, ChangeDetectionStrategy } from '@angular/core';
import { CdkMenuModule } from '@angular/cdk/menu';
import { TranslatePipe } from '@ngx-translate/core';
import { filter } from 'rxjs/operators';
import { AuthService } from './core/auth.service';
import { LanguageService } from './core/language.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AsyncPipe, CdkMenuModule, TranslatePipe],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  constructor(
    public auth: AuthService,
    public language: LanguageService,
    private router: Router,
    @Inject(DOCUMENT) private document: Document,
  ) {}

  private readonly userShortcuts: Record<string, string> = {
    'h': '/',
    'a': '/applications',
    'd': '/documents',
    'c': '/companies',
  };

  // Windows screen readers (JAWS/NVDA) reserve bare letter keys for their own
  // browse-mode quick navigation, so they never reach this handler there.
  // macOS/VoiceOver doesn't intercept plain letters the same way, so bare
  // keys stay usable on that platform.
  readonly isWindows = /Win/i.test(navigator.userAgent);

  @HostListener('document:keydown', ['$event'])
  onKeydown(e: KeyboardEvent): void {
    const tag = (e.target as HTMLElement).tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;

    if (this.isWindows) {
      if (!e.altKey || !e.shiftKey || e.ctrlKey || e.metaKey) return;
    } else {
      if (e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return;
    }

    const route = this.userShortcuts[e.key.toLowerCase()];
    if (route) {
      e.preventDefault();
      this.router.navigate([route]);
    }
  }

  ngOnInit(): void {
    this.language.init();

    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe(() => {
        setTimeout(() => {
          const h1 = this.document.querySelector('#main-content h1') as HTMLElement | null;
          if (h1) {
            h1.setAttribute('tabindex', '-1');
            h1.focus();
          }
        });
      });
  }

  logout(): void {
    this.auth.logout();
  }

  onLanguageChange(e: Event): void {
    this.language.use((e.target as HTMLSelectElement).value);
  }
}
