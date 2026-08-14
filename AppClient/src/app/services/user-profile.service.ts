import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserProfile, UserProfileUpdateRequest } from '../model/user-profile';

/** The caller's own profile, including the sender block used by every letter. */
@Injectable({ providedIn: 'root' })
export class UserProfileService {

  private readonly apiUrl = '/api/profile';
  private readonly http = inject(HttpClient);

  get(): Observable<UserProfile> {
    return this.http.get<UserProfile>(this.apiUrl);
  }

  update(request: UserProfileUpdateRequest): Observable<UserProfile> {
    return this.http.put<UserProfile>(this.apiUrl, request);
  }
}
