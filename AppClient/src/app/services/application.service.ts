import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Application, ApplicationDocuments, ApplicationRequest } from '../model/application';

@Injectable({ providedIn: 'root' })
export class ApplicationService {

  private readonly apiUrl = '/api/applications';

  constructor(private http: HttpClient) {}

  getAll(): Observable<Application[]> {
    return this.http.get<Application[]>(this.apiUrl);
  }

  create(req: ApplicationRequest): Observable<Application> {
    return this.http.post<Application>(this.apiUrl, req);
  }

  update(id: number, req: ApplicationRequest): Observable<Application> {
    return this.http.put<Application>(`${this.apiUrl}/${id}`, req);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Every application that has documents enclosed; the others are absent. */
  getDocuments(): Observable<ApplicationDocuments[]> {
    return this.http.get<ApplicationDocuments[]>(`${this.apiUrl}/documents`);
  }

  /** Replaces the documents enclosed with one application; the answer is the new set, in reading order. */
  setDocuments(id: number, documentIds: string[]): Observable<ApplicationDocuments> {
    return this.http.put<ApplicationDocuments>(`${this.apiUrl}/${id}/documents`, { documentIds });
  }
}
