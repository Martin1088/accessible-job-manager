import { Document } from './document';

export type ApplicationStatus =
  | 'DRAFT'
  | 'SENT'
  | 'INTERVIEW_SCHEDULED'
  | 'INTERVIEW_DONE'
  | 'OFFER_RECEIVED'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'WITHDRAWN';

export interface Application {
  id?: number;
  companyPositionId: number;
  companyName: string;
  positionTitle: string;
  status: ApplicationStatus;
  appliedDate?: string;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ApplicationRequest {
  companyPositionId: number;
  status: ApplicationStatus;
  appliedDate?: string | null;
  notes?: string | null;
}

/** Mirrors `ApplicationDocumentsDto`: the documents enclosed with one application, in reading order. */
export interface ApplicationDocuments {
  applicationId: number;
  documents: Document[];
}
