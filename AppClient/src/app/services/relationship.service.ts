import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Relationship, RelationshipKind } from '../model/relationship';

/**
 * Advisor/reviewer links from both ends. A user (the applicant) requests a link
 * and can end an active one; the advisor or reviewer (the counterpart) sees it
 * under `incoming()` and accepts or declines it.
 */
@Injectable({ providedIn: 'root' })
export class RelationshipService {

  private readonly apiUrl = '/api/relationships';
  private readonly http = inject(HttpClient);

  /** Links where the caller is the applicant - the profile page's directory. */
  mine(): Observable<Relationship[]> {
    return this.http.get<Relationship[]>(`${this.apiUrl}/mine`);
  }

  /** Links where the caller is the counterpart - the advisor/reviewer inbox. */
  incoming(): Observable<Relationship[]> {
    return this.http.get<Relationship[]>(`${this.apiUrl}/incoming`);
  }

  request(counterpartId: string, kind: RelationshipKind): Observable<Relationship> {
    return this.http.post<Relationship>(this.apiUrl, { counterpartId, kind });
  }

  accept(id: string): Observable<Relationship> {
    return this.http.post<Relationship>(`${this.apiUrl}/${id}/accept`, {});
  }

  decline(id: string): Observable<Relationship> {
    return this.http.post<Relationship>(`${this.apiUrl}/${id}/decline`, {});
  }

  end(id: string): Observable<Relationship> {
    return this.http.post<Relationship>(`${this.apiUrl}/${id}/end`, {});
  }
}
