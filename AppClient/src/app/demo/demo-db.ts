import { Injectable, signal } from '@angular/core';

import { Application } from '../model/application';
import { Company } from '../model/company';
import { Document } from '../model/document';
import { HtmlLetterTemplate } from '../model/cover-letter';
import { Relationship } from '../model/relationship';
import { UserProfile } from '../model/user-profile';
import { UserPreferences } from '../model/user-preferences';

import { APPLICATIONS } from './seed/applications';
import { COMPANIES } from './seed/companies';
import { DOCUMENTS, DemoShare, SHARES } from './seed/documents';
import { LETTER_TEMPLATES } from './seed/cover-letters';
import { RELATIONSHIPS, PEOPLE } from './seed/people';
import { DemoRole } from './demo-mode';
import { SUGGESTIONS, SuggestionDto } from './seed/advisor';

/** A seed constant must never be mutated - every signal starts from a copy of it. */
function copy<T>(value: T): T {
  return structuredClone(value) as T;
}

/**
 * The whole mutable state of the demo, in memory, for one session.
 *
 * There is deliberately no persistence: a reload resets the demo, which is what
 * you want when three people share one link. (`localStorage` still holds the
 * chosen language - that is a preference of the visitor, not demo data, and the
 * language switch is one of the things being demonstrated.)
 *
 * This class stores and hands out data. It does not decide anything. No
 * validation, no permission checks, no derived business rules - the moment those
 * appear here there are two implementations of them and they start to drift.
 */
@Injectable({ providedIn: 'root' })
export class DemoDb {

  /**
   * Which of the three people is looking. `null` until the visitor picks one
   * on the login letter - while null, `/api/me` answers 401 and the auth guard
   * sends every route to `/login`, which is what makes the letter the front
   * door of the demo rather than a page nobody ever sees.
   */
  readonly role = signal<DemoRole | null>(null);

  readonly companies = signal<Company[]>(copy(COMPANIES));
  readonly applications = signal<Application[]>(copy(APPLICATIONS));
  readonly documents = signal<Document[]>(copy(DOCUMENTS));
  readonly shares = signal<DemoShare[]>(copy(SHARES));
  readonly templates = signal<HtmlLetterTemplate[]>(copy(LETTER_TEMPLATES));
  readonly relationships = signal<Relationship[]>(copy(RELATIONSHIPS));
  readonly suggestions = signal<SuggestionDto[]>(copy(SUGGESTIONS));
  readonly profiles = signal<Record<DemoRole, UserProfile>>(copy({
    USER: PEOPLE.USER.profile,
    ADVISOR: PEOPLE.ADVISOR.profile,
    REVIEWER: PEOPLE.REVIEWER.profile,
  }));

  /**
   * The profile of whoever is currently looking. Handlers that reach this only
   * run behind the guards, i.e. after a role was picked - the fallback covers
   * a direct API poke before entering, not a real flow.
   */
  profile(): UserProfile {
    return this.profiles()[this.role() ?? 'USER'];
  }

  patchProfile(change: Partial<UserProfile>): UserProfile {
    const role = this.role() ?? 'USER';
    const updated = { ...this.profiles()[role], ...change };
    this.profiles.update(all => ({ ...all, [role]: updated }));
    return updated;
  }

  patchPreferences(preferences: UserPreferences): UserProfile {
    return this.patchProfile({ preferences });
  }

  /** Highest id in use plus one - enough for a session that nobody reloads. */
  nextNumericId(taken: readonly { id?: number }[]): number {
    return taken.reduce((max, item) => Math.max(max, item.id ?? 0), 0) + 1;
  }

  nextStringId(prefix: string): string {
    return `${prefix}-${Date.now().toString(36)}`;
  }

  /** Back to the seeds. Wired to the "reset demo" menu item, and to a reload. */
  reset(): void {
    this.role.set('USER');
    this.companies.set(copy(COMPANIES));
    this.applications.set(copy(APPLICATIONS));
    this.documents.set(copy(DOCUMENTS));
    this.shares.set(copy(SHARES));
    this.templates.set(copy(LETTER_TEMPLATES));
    this.relationships.set(copy(RELATIONSHIPS));
    this.suggestions.set(copy(SUGGESTIONS));
    this.profiles.set(copy({
      USER: PEOPLE.USER.profile,
      ADVISOR: PEOPLE.ADVISOR.profile,
      REVIEWER: PEOPLE.REVIEWER.profile,
    }));
  }
}
