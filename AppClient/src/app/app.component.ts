import { RouterOutlet, RouterLink, RouterLinkActive, NavigationEnd, Router } from '@angular/router';
import { AsyncPipe, NgIf, DOCUMENT } from '@angular/common';
import { Component, HostListener, Inject, OnInit } from '@angular/core';
import { filter } from 'rxjs/operators';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AsyncPipe, NgIf],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  constructor(
    public auth: AuthService,
    private router: Router,
    @Inject(DOCUMENT) private document: Document,
  ) {}

  private readonly userShortcuts: Record<string, string> = {
    'h': '/',
    'a': '/applications',
    'd': '/documents',
    'c': '/companies',
  };

  @HostListener('document:keydown', ['$event'])
  onKeydown(e: KeyboardEvent): void {
    if (e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return;
    const tag = (e.target as HTMLElement).tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
    const route = this.userShortcuts[e.key];
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
}
