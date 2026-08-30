import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, map, of, tap } from 'rxjs';
import { UserProfile } from '../model/user-profile';
import { DEFAULT_PREFERENCES, UserPreferences } from '../model/user-preferences';
import { UserProfileService } from './user-profile.service';

/**
 * Caches the caller's accessibility preferences so AccessibilityService can
 * re-apply them on every change without a network round trip. Reads piggyback
 * on UserProfileService.get() rather than issuing a second GET /api/profile.
 */
@Injectable({ providedIn: 'root' })
export class PreferencesService {

  private readonly apiUrl = '/api/profile/preferences';
  private readonly http = inject(HttpClient);
  private readonly profiles = inject(UserProfileService);

  private readonly state$ = new BehaviorSubject<UserPreferences>(DEFAULT_PREFERENCES);
  readonly preferences$: Observable<UserPreferences> = this.state$.asObservable();

  /** Fetches the stored preferences once and seeds the cached stream; call at app bootstrap. */
  load(): Observable<UserPreferences> {
    return this.profiles.get().pipe(
      map(profile => profile.preferences),
      catchError(() => of(DEFAULT_PREFERENCES)),
      tap(preferences => this.state$.next(preferences))
    );
  }

  update(request: UserPreferences): Observable<UserPreferences> {
    return this.http.patch<UserProfile>(this.apiUrl, request).pipe(
      map(profile => profile.preferences),
      tap(preferences => this.state$.next(preferences))
    );
  }

  /** Feeds a UserProfile response another caller already fetched into the cache, instead of a redundant GET. */
  seed(preferences: UserPreferences): void {
    this.state$.next(preferences);
  }
}
