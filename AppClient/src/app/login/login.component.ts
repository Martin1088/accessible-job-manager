
import { ActivatedRoute } from '@angular/router';
import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

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

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.error = this.route.snapshot.queryParamMap.get('error');
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
