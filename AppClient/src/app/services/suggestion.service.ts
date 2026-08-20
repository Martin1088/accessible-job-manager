import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApplicationMethod, Gender } from '../model/company';

export interface CompanySuggestion {
  name?: string;
  website?: string;
}

export interface LocationSuggestion {
  street?: string;
  city?: string;
  postcode?: string;
  country?: string;
}

export interface PositionSuggestion {
  title?: string;
  employmentType?: string;
  contactGender?: Gender;
  contactTitle?: string;
  contactLastName?: string;
  email?: string;
}

export interface ApplicationMethodSuggestion {
  method: ApplicationMethod;
  email?: string;
  /** Belongs in the position's `website` field; there is no separate column. */
  applicationUrl?: string;
}

/**
 * Asks the backend to read a job posting and propose values for one section
 * of the company form. Each call is a separate request the user triggers, so
 * they only wait for the section they are filling in.
 */
@Injectable({
  providedIn: 'root'
})
export class SuggestionService {

  private readonly apiUrl = '/api/suggestions';

  constructor(private http: HttpClient) {}

  company(url: string): Observable<CompanySuggestion> {
    return this.http.post<CompanySuggestion>(`${this.apiUrl}/company`, null, { params: this.urlParam(url) });
  }

  location(url: string): Observable<LocationSuggestion> {
    return this.http.post<LocationSuggestion>(`${this.apiUrl}/location`, null, { params: this.urlParam(url) });
  }

  position(url: string): Observable<PositionSuggestion> {
    return this.http.post<PositionSuggestion>(`${this.apiUrl}/position`, null, { params: this.urlParam(url) });
  }

  applicationMethod(url: string): Observable<ApplicationMethodSuggestion> {
    return this.http.post<ApplicationMethodSuggestion>(
      `${this.apiUrl}/application-method`, null, { params: this.urlParam(url) });
  }

  private urlParam(url: string): HttpParams {
    return new HttpParams().set('url', url);
  }
}
