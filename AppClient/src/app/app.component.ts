import { RouterOutlet, RouterLink, RouterLinkActive, NavigationEnd, Router } from '@angular/router';
import { AsyncPipe } from '@angular/common';
import { Component, HostListener, Inject, OnInit, DOCUMENT, ChangeDetectionStrategy, inject } from '@angular/core';
import { CdkMenuModule } from '@angular/cdk/menu';
import { TranslatePipe } from '@ngx-translate/core';
import { filter } from 'rxjs/operators';
import { AuthService } from './core/auth.service';
import { LanguageService } from './core/language.service';
import { DEMO_MODE } from './demo/demo-mode';
import { DemoBarComponent } from './demo/demo-bar/demo-bar.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AsyncPipe, CdkMenuModule, TranslatePipe, DemoBarComponent],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {

  /** False in every build but `demo`, where app.config.demo.ts overrides the token. */
  readonly demoMode = inject(DEMO_MODE);

  constructor(
    public auth: AuthService,
    public language: LanguageService,
    private router: Router,
    @Inject(DOCUMENT) private document: Document,
  ) {}

  private readonly userShortcuts: Record<string, string> = {
    'o': '/',
    'a': '/applications',
    'd': '/documents',
    'c': '/companies',
    'q': '/queue',
    't': '/cover-letter-template',
    'u': '/support',
    'h': '/guide',
    'p': '/profile',
    'e': '/preferences',
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

    // Platform-independent alternate binding for the Guide/Help page, alongside the
    // platform-gated bare-letter scheme below.
    if (e.ctrlKey && e.altKey && !e.shiftKey && !e.metaKey && e.key.toLowerCase() === 'h') {
      e.preventDefault();
      this.router.navigate(['/guide']);
      return;
    }

    // Cover letter template answers to Alt+Shift+T on every platform, not just
    // Windows, so the combo keeps working where the bare 't' below also does.
    if (e.altKey && e.shiftKey && !e.ctrlKey && !e.metaKey && e.key.toLowerCase() === 't') {
      e.preventDefault();
      this.router.navigate(['/cover-letter-template']);
      return;
    }

    // Support likewise answers to Alt+Shift+U everywhere, alongside the bare
    // 'u' below - 's' was already taken by the home page's posting search.
    if (e.altKey && e.shiftKey && !e.ctrlKey && !e.metaKey && e.key.toLowerCase() === 'u') {
      e.preventDefault();
      this.router.navigate(['/support']);
      return;
    }

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
