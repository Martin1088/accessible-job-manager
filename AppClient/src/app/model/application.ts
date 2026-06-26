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
