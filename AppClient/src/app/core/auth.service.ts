import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { catchError, map, shareReplay } from 'rxjs/operators';

export type AppRole = 'USER' | 'ADVISOR' | 'REVIEWER';

export interface UserMe {
  sub: string;
  name: string;
  email: string;
  roles: AppRole[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly http = inject(HttpClient);

  readonly me$ = this.http.get<UserMe>('/api/me').pipe(
    shareReplay(1),
    catchError(() => of(null))
  );

  readonly isUser$     = this.hasRole('USER');
  readonly isAdvisor$  = this.hasRole('ADVISOR');
  readonly isReviewer$ = this.hasRole('REVIEWER');

  hasRole(role: AppRole) {
    return this.me$.pipe(map(me => !!me?.roles?.includes(role)));
  }

  logout(): void {
    this.http.post<{ redirectUrl: string }>('/api/logout', {}).subscribe({
      next: res => this.redirectTo(res.redirectUrl || '/'),
      error: () => this.redirectTo('/')
    });
  }

  redirectTo(url: string): void {
    window.location.href = url;
  }
}
