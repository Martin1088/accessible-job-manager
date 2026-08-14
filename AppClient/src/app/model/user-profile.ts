/** Mirrors the backend `UserProfileDto` / `UserProfileUpdateRequest`. */

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
