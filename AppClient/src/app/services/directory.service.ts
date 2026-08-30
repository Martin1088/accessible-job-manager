import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DirectoryPerson } from '../model/relationship';

/** The advisors and reviewers a user can ask to be linked with. */
@Injectable({ providedIn: 'root' })
export class DirectoryService {

  private readonly apiUrl = '/api/directory';
  private readonly http = inject(HttpClient);

  advisors(): Observable<DirectoryPerson[]> {
    return this.http.get<DirectoryPerson[]>(`${this.apiUrl}/advisors`);
  }

  reviewers(): Observable<DirectoryPerson[]> {
    return this.http.get<DirectoryPerson[]>(`${this.apiUrl}/reviewers`);
  }
}
