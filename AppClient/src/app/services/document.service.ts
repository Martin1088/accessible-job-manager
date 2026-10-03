import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Document, DocumentLanguage, DocumentType } from '../model/document';

/** The editable metadata of a stored document; every field is optional. */
export interface UpdateDocumentRequest {
  label?: string;
  language?: DocumentLanguage;
  type?: DocumentType;
}

/** A document a reviewer shared back - not owned by the caller, so it is not a Document. */
export interface SharedWithMeDocument {
  id: string;
  label: string;
  filename: string;
  type: DocumentType;
  sharedByName: string;
  grantedAt: string;
}

/**
 * The caller's stored documents - cover letter templates (.docx) and the PDFs they
 * keep on file. Mirrors the backend's `DocumentController`; sharing one with a
 * reviewer is a separate concern and does not live here.
 */
@Injectable({ providedIn: 'root' })
export class DocumentService {

  private readonly apiUrl = '/api/documents';
  private readonly http = inject(HttpClient);

  /** All of the caller's documents, or only those of one type. */
  getAll(type?: DocumentType): Observable<Document[]> {
    return this.http.get<Document[]>(this.apiUrl, {
      params: type ? new HttpParams().set('type', type) : undefined,
    });
  }

  /**
   * The server rejects a file whose media type the document type does not allow, so
   * the type chosen here decides what may be uploaded.
   */
  upload(file: File, label: string, type: DocumentType, language: DocumentLanguage): Observable<Document> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('label', label);
    formData.append('type', type);
    formData.append('language', language);
    return this.http.post<Document>(`${this.apiUrl}/upload`, formData);
  }

  update(id: string, request: UpdateDocumentRequest): Observable<Document> {
    return this.http.patch<Document>(`${this.apiUrl}/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /**
   * Fetched as a blob rather than linked to directly, so the request carries the
   * session cookie and a failure surfaces as an error instead of a broken tab.
   */
  download(id: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/download`, { responseType: 'blob' });
  }

  /** Documents reviewers have shared back, keyed by their own id rather than the caller's. */
  getSharedWithMe(): Observable<SharedWithMeDocument[]> {
    return this.http.get<SharedWithMeDocument[]>(`${this.apiUrl}/shared-with-me`);
  }

  downloadShared(id: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/shared-with-me/${id}/download`, { responseType: 'blob' });
  }
}
