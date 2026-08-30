import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Relationship, RelationshipKind } from '../model/relationship';

/**
 * The caller's own advisor/reviewer links. Accept and decline are the
 * counterpart's actions and live on their dashboard, not here - a user can only
 * request a link and end an active one.
 */
@Injectable({ providedIn: 'root' })
export class RelationshipService {

  private readonly apiUrl = '/api/relationships';
  private readonly http = inject(HttpClient);

  mine(): Observable<Relationship[]> {
    return this.http.get<Relationship[]>(`${this.apiUrl}/mine`);
  }

  request(counterpartId: string, kind: RelationshipKind): Observable<Relationship> {
    return this.http.post<Relationship>(this.apiUrl, { counterpartId, kind });
  }

  end(id: string): Observable<Relationship> {
    return this.http.post<Relationship>(`${this.apiUrl}/${id}/end`, {});
  }
}
