import { UserPreferences } from './user-preferences';

export interface UserProfileContact {
  userId: string;
  name: string | null;
  email: string | null;
}

export interface UserProfile {
  userId: string;
  name: string | null;
  email: string | null;
  street: string | null;
  postalCode: string | null;
  city: string | null;
  phone: string | null;
  role: string | null;
  advisors: UserProfileContact[];
  reviewers: UserProfileContact[];
  preferences: UserPreferences;
}

/**
 * The editable half of a profile. These six fields are the sender block of every
 * letter, which is why no cover letter form asks for them again.
 */
export interface UserProfileUpdateRequest {
  name: string | null;
  email: string | null;
  street: string | null;
  postalCode: string | null;
  city: string | null;
  phone: string | null;
}
