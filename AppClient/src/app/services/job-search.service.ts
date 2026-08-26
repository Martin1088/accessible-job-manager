import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface JobSearchStatus {
  configured: boolean;
  source: string;
  country: string;
  attribution: string;
}

export interface JobSearchCategory {
  tag: string;
  label: string;
}

export interface JobSearchHit {
  id: string;
  title: string;
  company: string | null;
  location: string | null;
  url: string;
  summary: string | null;
  created: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  salaryPredicted: boolean;
  contractType: string | null;
  contractTime: string | null;
  category: string | null;
  source: string;
}

export interface JobSearchResults {
  source: string;
  totalCount: number;
  page: number;
  resultsPerPage: number;
  hits: JobSearchHit[];
  attribution: string;
}

export type JobSearchSort = 'RELEVANCE' | 'DATE' | 'SALARY' | 'HYBRID';

export interface JobSearchQuery {
  what?: string;
  whatExclude?: string;
  where?: string;
  distanceKm?: number;
  page?: number;
  resultsPerPage?: number;
  maxDaysOld?: number;
  salaryMin?: number;
  fullTime?: boolean;
  permanent?: boolean;
  category?: string;
  sortBy?: JobSearchSort;
  country?: string;
}

@Injectable({
  providedIn: 'root'
})
export class JobSearchService {

  private readonly baseUrl = '/api/advisor/job-search';

  constructor(private http: HttpClient) {}

  status(): Observable<JobSearchStatus> {
    return this.http.get<JobSearchStatus>(`${this.baseUrl}/status`);
  }

  categories(country?: string): Observable<JobSearchCategory[]> {
    let params = new HttpParams();
    if (country) params = params.set('country', country);
    return this.http.get<JobSearchCategory[]>(`${this.baseUrl}/categories`, { params });
  }

  search(query: JobSearchQuery): Observable<JobSearchResults> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<JobSearchResults>(this.baseUrl, { params });
  }
}
