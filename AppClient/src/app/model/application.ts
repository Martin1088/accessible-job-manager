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
  companyName: string;
  positionTitle: string;
  status: ApplicationStatus;
  appliedDate?: string;
  notes?: string;
}
