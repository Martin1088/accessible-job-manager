import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Application, ApplicationRequest } from '../model/application';

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
}
