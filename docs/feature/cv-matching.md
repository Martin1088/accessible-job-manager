# Plan: matching a CV to a job posting with an AI model (frontend and demo only)

Status: first draft on `feature/cv-matching`, demo only. It deviates from the plan
below in one deliberate way: to leave the existing code untouched, the matching is a
standalone page `/matching` (linked from the home page) with its own URL/text input,
instead of a section under the importer. The `postingReady` output on
`app-job-posting-import`, the PDF path and the backend are still open.

Original status: plan, not implemented. Scope of this phase: Angular frontend and the demo
build. The backend is sketched in section 7 only so the API contract fits it later.

Roles: **USER only**. The advisor's import page (`/advisor/job-import`) shares the
importer component but does not get the matching.

## 1. What the user sees

On `/` the person imports a job posting as they do today (URL, PDF or pasted text).
Once the posting has been read, a new **"Check fit with my CV"** section appears under
the extraction, next to "Use for new company":

1. **Choose a CV:** a `<fieldset>` with a `<legend>` and radio buttons, one per
   document of type `CV` (the demo has "Lebenslauf 2026" and "CV (English)"). The
   newest CV is preselected. If the person has no CV, they see a short message linking
   to `/documents`.
2. **Notice before sending:** a short text saying the CV is sent to the AI model this
   server is configured with, and that the result is AI-generated and may be wrong.
   This is about transparency (AI Act Art. 50) and privacy (GDPR), since a CV is
   personal data.
3. **"Check fit" button:** while it runs, the section has `aria-busy` and a
   `role="status"` line ("Analysing… this can take up to a minute"), plus a Cancel
   button. A real model run takes a long time.
4. **Result:**
   - **Overall fit:** shown as a level in words (*Good / Partial / Low fit*) with a
     glyph hidden from screen readers. There is no percentage and the level is never
     shown by colour alone. A short summary sentence follows.
   - **Counts:** a `<dl>` with how many requirements are met, partly met and missing.
   - **"Missing skills" comes first** because it is what the person is looking for. It
     is an `<h3>` followed by `<ul role="list">`. Each item shows:
     - the skill and whether it is "Required" or "Nice to have";
     - a `<details>` element whose `<summary>` reads "Details: React". Inside are the
       quote from the posting (`<blockquote>`), why the model rated it missing, and a
       suggestion (for example a course, or what to write in the cover letter).
   - **"Partly met" and "Met"** use the same structure. For met skills the details show
     the supporting passage from the CV, and these items are collapsed by default.
   - **Focus:** after loading, focus moves to the result heading (`tabindex="-1"`) and
     `LiveAnnouncer` reads a summary such as "Good fit: 6 of 9 requirements met, 2
     missing".
   - **Errors:** shown with `role="alert"` through the existing `app-error-text`
     component.

Every result group has its own heading, so a screen reader user can jump between
groups with the VoiceOver headings rotor.

## 2. API contract (the demo implements it now, the backend later)

The three endpoints mirror the existing import paths: `overview`, `overview-text` and
`overview-pdf`.

```
POST /api/matching?url=…&cvDocumentId=…&lang=de
POST /api/matching/text   { text, cvDocumentId, lang }
POST /api/matching/pdf    multipart: file, cvDocumentId, lang
```

The frontend only sends where the posting came from, never extracted requirements. The
backend later reads the posting itself, for two reasons:

- The current extraction doesn't keep the requirements text.
- It follows the rule "one logic in the backend, nothing re-implemented in TypeScript".

New file `model/cv-match.ts`:

```ts
export type MatchLevel = 'STRONG' | 'PARTIAL' | 'WEAK';
export type SkillStatus = 'MET' | 'PARTIAL' | 'MISSING';
export type SkillImportance = 'REQUIRED' | 'NICE_TO_HAVE';
export type SkillCategory = 'TECHNICAL' | 'LANGUAGE' | 'EXPERIENCE' | 'EDUCATION' | 'CERTIFICATE' | 'SOFT_SKILL';

export interface SkillAssessment {
  skill: string;
  category: SkillCategory;
  importance: SkillImportance;
  status: SkillStatus;
  postingQuote: string | null;   // the requirement as worded in the posting
  cvEvidence: string | null;     // supporting passage from the CV
  reasoning: string;
  suggestion: string | null;     // how to close the gap (MISSING/PARTIAL only)
}

export interface CvMatchResult {
  level: MatchLevel;             // decided by the server, never computed in TS
  summary: string;
  cvDocumentId: string;
  postingTitle: string | null;
  company: string | null;
  language: string;              // language actually served → lang attribute (WCAG 3.1.2)
  generatedAt: string;
  skills: SkillAssessment[];
}
```

- `lang` asks the model to answer in the interface language. If `language` in the
  response differs, the result region gets its own `lang` attribute. This is the same
  pattern the legal documents already use.
- The frontend groups skills by `status`, which is only presentation. It never computes
  a level or a score.

## 3. Files

| File | Change |
|---|---|
| `shared/job-posting-import/job-posting-import.component.ts` | New output `postingReady`, emitted after a successful extraction with `{kind:'url',url} \| {kind:'text',text} \| {kind:'pdf',file}`. The advisor page simply doesn't bind it. The PDF goes with the event; it is **not** taken through `JobPostingImportStore.take()`, which would hide it from the company form. |
| `user/home/home.component.{ts,html}` | Keeps the latest `postingReady` value as a signal and renders `<app-cv-match [posting]="…">` inside the import section. The value is cleared on a new import. |
| `user/cv-match/cv-match.component.{ts,html,scss,spec.ts}` | **New.** Standalone, OnPush, signals. Holds the CV choice, the run (cancelled by unsubscribing), the grouped result and the focus/announcement logic. |
| `services/cv-match.service.ts` | **New.** Picks one of the three endpoints depending on `posting.kind`. |
| `model/cv-match.ts` | **New.** The types above. |
| `demo/seed/matching.ts` | **New.** A fixed result per language (`de`, `en`; `nl` falls back the same way the legal seed does). It belongs to the existing demo posting "Frontend Engineer Accessibility – Aurum Fintech SE" and the demo CV, for example: Angular ✓, TypeScript ✓, WCAG 2.2 ✓, German C1 ✓, screen-reader testing ◐, design systems ◐, React ✗ (required), fintech domain ✗ (nice to have), PDF/UA ✗. |
| `demo/demo-api.ts` | Three `route('POST', '/api/matching…')` entries returning the seed. Like the extraction, the component labels the result **as an example at the point where it is shown**, not only in the demo banner. |
| `public/i18n/{en,de,nl}.json` | New namespace `MATCHING.*` in all three files: headings, level names, status and importance labels, notices, the announcement with counts. Check German strings for overflow. Text the model writes is content and is not translated. |
| `CHANGELOG.md` | Entry under `## [Unreleased]` in the same commit. |

## 4. Accessibility checklist for this feature

- Status is always given as **text** plus a glyph hidden from screen readers. Colours
  come only from tokens and are checked in the default, high-contrast and dark themes.
- Native `<details>/<summary>` for the details, so keyboard and screen-reader support
  comes for free. Each summary names its skill so it is distinct in the rotor.
- Quotes use `<blockquote>`, the counts use `<dl>`, and the lists keep their semantics
  with `role="list"`, which a `list-style: none` style would otherwise remove in Safari.
- Buttons are at least 44×44 px, and the layout uses only `bp.mobile` with no
  horizontal scroll at 375 px.
- Focus moves only once, after the result arrives; the LiveAnnouncer confirms. On an
  error, focus stays where it is and the `role="alert"` message announces itself.

## 5. Tests

`cv-match.component.spec.ts`:

- skills are grouped correctly, with "Missing" first;
- the "no CV" state;
- focus lands on the result heading and the announcement is made;
- an error gives `role="alert"`;
- cancel stops the run;
- the `lang` attribute is set when the response language differs;
- `expectNoAxeViolations` for the selection, loading and result states.

The importer spec gets one case: `postingReady` fires for each of the three sources.

Before merging: `npm run lint:a11y`, `npx ng test --watch=false`, the demo build in the
browser in de/en/nl, and the VoiceOver checks from `AppClient/CLAUDE.md`.

## 6. Order of work

1. Model, service and demo seed/routes, so the contract can be tried in the demo.
2. The `postingReady` output on the importer and wiring it into the home page.
3. The `cv-match` component, i18n and styles.
4. Tests, the a11y pass and the CHANGELOG entry.

## 7. Later backend phase (not part of this plan)

- A new package `de.ajm.manager.matching`: a `MatchingController` and a
  `CvMatchService` that reuse the existing Ollama client.
- CV text read through `DocumentService` with an ownership check, using PDFBox for the
  text.
- A fixed JSON schema for the model's output.
- **Supporting passages from the CV are checked against the real CV text, and any
  passage the model invented is dropped.**
- `level` is derived on the server from the requirement statuses.
- Error texts go through `messages.properties`.

Open points for that phase: how long the model takes (whether streaming or a job
pattern is needed), and whether the result is stored on the position or computed fresh
each time.
