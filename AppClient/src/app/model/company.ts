export type Gender = 'MALE' | 'FEMALE' | 'DIVERSE';

export interface CompanyLocation {
  id?: number;
  street: string;
  city: string;
  postcode?: string;
  country?: string;
}

export interface CompanyPosition {
  id?: number;
  title: string;
  contactGender?: Gender;
  contactTitle?: string;
  contactLastName?: string;
  email?: string;
  website?: string;
  notes?: string;
  createdAt?: string;
}

export interface Company {
  id?: number;
  name: string;
  locations: CompanyLocation[];
  positions: CompanyPosition[];
}
