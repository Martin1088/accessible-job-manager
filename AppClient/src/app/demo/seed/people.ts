/**
 * Seed data - three fictional people, one per role.
 *
 * Captured against the API of `feature/advisor` @ 72c91ad, 2026-08-30. After an
 * API change these have to be pulled again; the type annotations below are what
 * turns a stale field into a build error rather than an empty column in the demo.
 *
 * The names, addresses and employers are invented. Nothing here refers to a real
 * person or a real employer, and nothing may - the file ships to whoever opens
 * the demo link.
 */
import { AppRole } from '../../core/auth.service';
import { DirectoryPerson, Relationship } from '../../model/relationship';
import { UserProfile } from '../../model/user-profile';
import { DemoRole } from '../demo-mode';
import { DEFAULT_PREFERENCES } from '../../model/user-preferences';

export interface DemoPerson {
  readonly profile: UserProfile;
  /** What `/api/me` answers while this person is the active role. */
  readonly roles: AppRole[];
}

export const APPLICANT_ID = 'demo-applicant';
export const ADVISOR_ID = 'demo-advisor';
export const REVIEWER_ID = 'demo-reviewer';

export const PEOPLE: Readonly<Record<DemoRole, DemoPerson>> = {
  USER: {
    roles: ['USER'],
    profile: {
      userId: APPLICANT_ID,
      name: 'Sabine Vogt',
      email: 'sabine.vogt@example.org',
      street: 'Lindenweg 14',
      postalCode: '79104',
      city: 'Freiburg',
      phone: '+49 761 1234567',
      roles: ['USER'],
      preferences: DEFAULT_PREFERENCES,
    },
  },
  ADVISOR: {
    roles: ['ADVISOR'],
    profile: {
      userId: ADVISOR_ID,
      name: 'Jonas Reinhardt',
      email: 'j.reinhardt@example.org',
      street: 'Bahnhofstraße 3',
      postalCode: '79098',
      city: 'Freiburg',
      phone: '+49 761 7654321',
      roles: ['ADVISOR'],
      preferences: DEFAULT_PREFERENCES,
    },
  },
  REVIEWER: {
    roles: ['REVIEWER'],
    profile: {
      userId: REVIEWER_ID,
      name: 'Amira Sayed',
      email: 'a.sayed@example.org',
      street: 'Kaiser-Joseph-Straße 88',
      postalCode: '79098',
      city: 'Freiburg',
      phone: '+49 761 2223334',
      roles: ['REVIEWER'],
      preferences: DEFAULT_PREFERENCES,
    },
  },
};

export const DIRECTORY_ADVISORS: DirectoryPerson[] = [
  { userId: ADVISOR_ID, name: 'Jonas Reinhardt', email: 'j.reinhardt@example.org' },
  { userId: 'demo-advisor-2', name: 'Petra Lohmann', email: 'p.lohmann@example.org' },
];

export const DIRECTORY_REVIEWERS: DirectoryPerson[] = [
  { userId: REVIEWER_ID, name: 'Amira Sayed', email: 'a.sayed@example.org' },
  { userId: 'demo-reviewer-2', name: 'Tobias Kern', email: 't.kern@example.org' },
];

/**
 * One active advisor link and one active reviewer link, plus an ended one. The
 * ended link is what makes the role separation visible rather than merely stated:
 * Petra Lohmann was an advisor and is not any more, and the advisor dashboard has
 * to stop showing Sabine to her.
 */
export const RELATIONSHIPS: Relationship[] = [
  {
    id: 'rel-advisor-active',
    applicantId: APPLICANT_ID,
    applicantName: 'Sabine Vogt',
    counterpartId: ADVISOR_ID,
    counterpartName: 'Jonas Reinhardt',
    kind: 'ADVISOR',
    status: 'ACTIVE',
    createdAt: '2026-06-02T09:15:00',
  },
  {
    id: 'rel-reviewer-active',
    applicantId: APPLICANT_ID,
    applicantName: 'Sabine Vogt',
    counterpartId: REVIEWER_ID,
    counterpartName: 'Amira Sayed',
    kind: 'REVIEWER',
    status: 'ACTIVE',
    createdAt: '2026-07-11T14:40:00',
  },
  {
    id: 'rel-advisor-ended',
    applicantId: APPLICANT_ID,
    applicantName: 'Sabine Vogt',
    counterpartId: 'demo-advisor-2',
    counterpartName: 'Petra Lohmann',
    kind: 'ADVISOR',
    status: 'ENDED',
    createdAt: '2026-03-18T10:00:00',
  },
];
