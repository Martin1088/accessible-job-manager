import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { catchError, map, shareReplay } from 'rxjs/operators';

export interface UserMe {
  sub: string;
  name: string;
  email: string;
  groups: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly http = inject(HttpClient);

  readonly me$ = this.http.get<UserMe>('/api/me').pipe(
    shareReplay(1),
    catchError(() => of(null))
  );

  readonly isAdvisor$  = this.me$.pipe(map(me => !!me?.groups?.includes('ADVISOR')));
  readonly isReviewer$ = this.me$.pipe(map(me => !!me?.groups?.includes('REVIEWER')));
  readonly isUser$     = this.me$.pipe(
    map(me => !!me && !me?.groups?.includes('ADVISOR') && !me?.groups?.includes('REVIEWER'))
  );

  logout(): void {
    fetch('/logout', { method: 'POST', credentials: 'include' })
      .then(() => window.location.href = '/');
  }
}
