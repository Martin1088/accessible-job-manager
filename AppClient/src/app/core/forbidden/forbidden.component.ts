import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  template: `
    <main id="main-content" class="forbidden">
      <div class="card" role="alert" aria-live="assertive">
        <span class="icon" aria-hidden="true">⛔</span>
        <h1>Access denied</h1>
        <p>You don't have the required role to view this page.</p>
        <button type="button" (click)="goHome()">Back to Home</button>
      </div>
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [`
    .forbidden {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 60vh;
      padding: 2rem;
    }
    .card {
      text-align: center;
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 2.5rem 3rem;
      max-width: 400px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06);
    }
    .icon { font-size: 3rem; display: block; margin-bottom: 1rem; }
    h1 { margin: 0 0 0.5rem; font-size: 1.5rem; color: #1e293b; }
    p  { color: #64748b; margin: 0 0 1.5rem; }
    button {
      padding: 0.55rem 1.4rem;
      background: #005fcc;
      color: #fff;
      border: none;
      border-radius: 6px;
      font-size: 0.95rem;
      font-family: inherit;
      cursor: pointer;
      &:hover { background: #004aaa; }
      &:focus-visible { outline: 3px solid #005fcc; outline-offset: 2px; }
    }
  `],
})
export class ForbiddenComponent {
  constructor(private router: Router) {}
  goHome(): void { this.router.navigate(['/']); }
}
