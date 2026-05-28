import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';

const ERROR_MESSAGES: Record<string, string> = {
  wrong_role:    'Your account does not have the required role. Please try a different login option.',
  access_denied: 'Access was denied. Please try again.',
};

@Component({
  standalone: true,
  selector: 'app-login',
  imports: [CommonModule, RouterModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent implements OnInit {
  error = '';

  constructor(private route: ActivatedRoute, private http: HttpClient) {}

  ngOnInit(): void {
    const code = this.route.snapshot.queryParamMap.get('error');
    if (code) {
      this.error = ERROR_MESSAGES[code] ?? `Login failed (${code}). Please try again.`;
    }
  }

  login(): void {
    window.location.href = '/oauth2/authorization/authentik';
  }

  loginAsAdvisor(): void {
    window.location.href = '/api/login/as/advisor';
  }

  loginAsReviewer(): void {
    window.location.href = '/api/login/as/reviewer';
  }

  logout(): void {
    fetch('/logout', { method: 'POST', credentials: 'include' })
      .then(() => window.location.href = '/');
  }
}
