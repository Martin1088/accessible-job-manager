/** How well a CV fits a posting overall. Decided by the server, never computed here. */
export type MatchLevel = 'STRONG' | 'PARTIAL' | 'WEAK';

/** Whether the CV covers one requirement of the posting. */
export type SkillStatus = 'MET' | 'PARTIAL' | 'MISSING';

export type SkillImportance = 'REQUIRED' | 'NICE_TO_HAVE';

export type SkillCategory =
  | 'TECHNICAL'
  | 'LANGUAGE'
  | 'EXPERIENCE'
  | 'EDUCATION'
  | 'CERTIFICATE'
  | 'SOFT_SKILL';

/** One requirement of the posting, held against the CV. */
export interface SkillAssessment {
  skill: string;
  category: SkillCategory;
  importance: SkillImportance;
  status: SkillStatus;
  /** The requirement as the posting words it. */
  postingQuote: string | null;
  /** The passage of the CV that covers it, when one does. */
  cvEvidence: string | null;
  reasoning: string;
  /** How to close the gap - only for MISSING and PARTIAL. */
  suggestion: string | null;
}

export interface CvMatchResult {
  level: MatchLevel;
  summary: string;
  cvDocumentId: string;
  postingTitle: string | null;
  company: string | null;
  /**
   * The language the model actually answered in. Put on the result's `lang`
   * attribute when it differs from the interface (WCAG 3.1.2), the same way the
   * legal documents do.
   */
  language: string;
  /**
   * The languages the quotes are in. `postingQuote` and `cvEvidence` are quoted
   * as written, so a German posting stays German inside an English result and
   * needs its own `lang` to be read out by the right voice.
   */
  postingLanguage: string | null;
  cvLanguage: string | null;
  generatedAt: string;
  skills: SkillAssessment[];
}

/**
 * Where the posting comes from. The server reads the posting itself - the
 * extraction keeps no requirements text, and a requirement list assembled here
 * would be a second implementation of the parse.
 */
export type PostingSource =
  | { kind: 'url'; url: string }
  | { kind: 'text'; text: string };
