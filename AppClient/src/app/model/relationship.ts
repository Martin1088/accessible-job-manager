/**
 * A user's link to an advisor or reviewer. The user asks for one from the
 * directory on the profile page; the counterpart accepts or declines it from
 * their own dashboard. Only `ADVISOR` and `REVIEWER` links exist.
 */
export type RelationshipKind = 'ADVISOR' | 'REVIEWER';

export type RelationshipStatus = 'REQUESTED' | 'ACTIVE' | 'DECLINED' | 'ENDED';

export interface Relationship {
  id: string;
  applicantId: string;
  applicantName: string;
  counterpartId: string;
  counterpartName: string;
  kind: RelationshipKind;
  status: RelationshipStatus;
  createdAt: string;
}

/** One advisor or reviewer as listed in the directory (`/api/directory/*`). */
export interface DirectoryPerson {
  userId: string;
  name: string;
  email: string;
}
