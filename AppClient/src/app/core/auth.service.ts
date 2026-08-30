import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, of } from 'rxjs';
import { catchError, map, shareReplay, switchMap } from 'rxjs/operators';

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

  private readonly reload$ = new BehaviorSubject<void>(undefined);

  readonly me$ = this.reload$.pipe(
    // catchError sits inside the switchMap so a failed request yields `null`
    // for that attempt instead of completing the stream - otherwise the first
    // 401 would make every later refresh a no-op.
    switchMap(() => this.http.get<UserMe>('/api/me').pipe(catchError(() => of(null)))),
    shareReplay(1)
  );

  readonly isUser$     = this.hasRole('USER');
  readonly isAdvisor$  = this.hasRole('ADVISOR');
  readonly isReviewer$ = this.hasRole('REVIEWER');

  /**
   * Drops the cached identity and re-reads /api/me. `me$` is shared and replayed,
   * so without this nothing can change who the app thinks is logged in for the
   * lifetime of the page - which is what the demo's role switcher needs to do.
   */
  refresh(): void {
    this.reload$.next();
  }

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
