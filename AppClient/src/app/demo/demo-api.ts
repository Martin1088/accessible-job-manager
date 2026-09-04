import { Application } from '../model/application';
import { Company } from '../model/company';
import { Document, DocumentType } from '../model/document';
import { HtmlLetterTemplate } from '../model/cover-letter';
import { Relationship } from '../model/relationship';
import { UserPreferences } from '../model/user-preferences';
import { UserProfile } from '../model/user-profile';

import { DemoDb } from './demo-db';
import { BLOCK_SUGGESTIONS } from './seed/cover-letters';
import { DIRECTORY_ADVISORS, DIRECTORY_REVIEWERS, PEOPLE } from './seed/people';
import { MY_USERS, SuggestionDto } from './seed/advisor';
import { POSTING_FULL_CHAIN, POSTING_OVERVIEW } from './seed/extraction';

/** A file that really exists in `public/` - the backend fetches it as an asset. */
export class DemoAsset {
  constructor(readonly path: string) {}
}

/**
 * A real endpoint that the demo deliberately does not answer. It surfaces as a
 * 501 with a readable reason, so a visitor who clicks it is told, rather than
 * left with a spinner. Not the same as an unmapped route, which is a bug.
 */
export const OUT_OF_SCOPE = Symbol('out of scope in the demo');

/**
 * Nobody has entered the demo yet. Surfaces as a 401, which is exactly what the
 * real backend answers an unauthenticated API request with - so the auth guard
 * redirects to `/login` and the demo starts on the login letter.
 */
export const UNAUTHENTICATED = Symbol('no role picked yet');

export interface DemoRequest {
  readonly method: string;
  /** Path only - the query string is parsed into `query`. */
  readonly path: string;
  /** The `:id` segments of the matched pattern, in order. */
  readonly params: readonly string[];
  readonly query: URLSearchParams;
  readonly body: unknown;
}

type Handler = (req: DemoRequest, db: DemoDb) => unknown;

interface Route {
  readonly method: string;
  readonly pattern: RegExp;
  readonly handle: Handler;
}

const routes: Route[] = [];

/** `route('PUT', '/api/companies/:id', …)` - `:name` captures one path segment. */
function route(method: string, path: string, handle: Handler): void {
  const source = '^' + path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/:\w+/g, '([^/]+)') + '$';
  routes.push({ method, pattern: new RegExp(source), handle });
}

/** The handler for a request, or undefined when nothing matches. */
export function resolve(method: string, path: string): { handle: Handler; params: string[] } | undefined {
  for (const candidate of routes) {
    if (candidate.method !== method) continue;
    const match = candidate.pattern.exec(path);
    if (match) return { handle: candidate.handle, params: match.slice(1) };
  }
  return undefined;
}

const SAMPLE_PDF = new DemoAsset('demo/anschreiben-muster.pdf');

// ---------------------------------------------------------------------------
// Identity. There is no real sign-in in the demo - the login letter and the
// role switcher set `db.role`, and every guard and every nav block in the
// shell follows from this response.
// ---------------------------------------------------------------------------

route('GET', '/api/me', (_req, db) => {
  const role = db.role();
  if (role === null) return UNAUTHENTICATED;
  const profile = db.profile();
  return {
    sub: profile.userId,
    name: profile.name,
    email: profile.email,
    roles: PEOPLE[role].roles,
  };
});

// Reloading the page is what resets the demo, so logout needs no special case:
// it sends the browser back to the same document.
route('POST', '/api/logout', () => ({ redirectUrl: window.location.pathname }));

route('GET', '/api/profile', (_req, db) => db.profile());
route('PUT', '/api/profile', (req, db) => db.patchProfile(req.body as Partial<UserProfile>));
route('PATCH', '/api/profile/preferences', (req, db) => db.patchPreferences(req.body as UserPreferences));

// ---------------------------------------------------------------------------
// Companies and applications
// ---------------------------------------------------------------------------

route('GET', '/api/companies', (_req, db) => db.companies());

route('POST', '/api/companies', (req, db) => {
  const body = req.body as Company;
  // Positions get ids too, not just the company: the advisor's import page
  // reads `positions[0].id` back out of this response to file the snapshot and
  // the suggestion against, so a position without one would dead-end there.
  const nextPositionId = Math.max(
    0, ...db.companies().flatMap(c => c.positions ?? []).map(p => p.id ?? 0)) + 1;
  const created: Company = {
    ...body,
    id: db.nextNumericId(db.companies()),
    positions: (body.positions ?? []).map((p, i) => ({ ...p, id: p.id ?? nextPositionId + i })),
  };
  db.companies.update(all => [...all, created]);
  return created;
});

route('PUT', '/api/companies/:id', (req, db) => {
  const id = Number(req.params[0]);
  const updated: Company = { ...(req.body as Company), id };
  db.companies.update(all => all.map(c => (c.id === id ? updated : c)));
  return updated;
});

route('DELETE', '/api/companies/:id', (req, db) => {
  const id = Number(req.params[0]);
  db.companies.update(all => all.filter(c => c.id !== id));
  return null;
});

route('GET', '/api/applications', (_req, db) => db.applications());

route('POST', '/api/applications', (req, db) => {
  const request = req.body as Partial<Application>;
  const position = positionLabel(db, request.companyPositionId);
  const created: Application = {
    ...(request as Application),
    id: db.nextNumericId(db.applications()),
    companyName: position.companyName,
    positionTitle: position.positionTitle,
    createdAt: new Date().toISOString(),
  };
  db.applications.update(all => [...all, created]);
  return created;
});

route('PUT', '/api/applications/:id', (req, db) => {
  const id = Number(req.params[0]);
  const request = req.body as Partial<Application>;
  const existing = db.applications().find(a => a.id === id);
  const position = positionLabel(db, request.companyPositionId);
  const updated: Application = {
    ...(existing as Application),
    ...(request as Application),
    id,
    companyName: position.companyName,
    positionTitle: position.positionTitle,
    updatedAt: new Date().toISOString(),
  };
  db.applications.update(all => all.map(a => (a.id === id ? updated : a)));
  return updated;
});

route('DELETE', '/api/applications/:id', (req, db) => {
  const id = Number(req.params[0]);
  db.applications.update(all => all.filter(a => a.id !== id));
  return null;
});

/** The two denormalised labels the applications list shows, looked up by position id. */
function positionLabel(db: DemoDb, companyPositionId: number | undefined): { companyName: string; positionTitle: string } {
  for (const company of db.companies()) {
    const position = company.positions.find(p => p.id === companyPositionId);
    if (position) return { companyName: company.name, positionTitle: position.title };
  }
  return { companyName: '', positionTitle: '' };
}

// ---------------------------------------------------------------------------
// Documents
// ---------------------------------------------------------------------------

route('GET', '/api/documents', (req, db) => {
  const type = req.query.get('type');
  const all = db.documents();
  return type ? all.filter(d => d.type === type) : all;
});

route('POST', '/api/documents/upload', (req, db) => {
  // The real endpoint takes multipart; the demo reads the same field names back
  // off the FormData so that an upload in the demo produces a visible row.
  const form = req.body as FormData;
  const file = form.get('file');
  const filename = file instanceof File ? file.name : 'beispiel.pdf';
  const created: Document = {
    id: db.nextStringId('doc'),
    label: String(form.get('label') ?? filename),
    filename,
    mimeType: file instanceof File ? file.type : 'application/pdf',
    type: (form.get('type') as DocumentType | null) ?? 'OTHER',
    language: (form.get('language') as Document['language'] | null) ?? 'GERMAN',
    createdAt: new Date().toISOString(),
  };
  db.documents.update(all => [...all, created]);
  return created;
});

route('PATCH', '/api/documents/:id', (req, db) => {
  const id = req.params[0];
  const change = req.body as Partial<Document>;
  let updated: Document | undefined;
  db.documents.update(all => all.map(d => (d.id === id ? (updated = { ...d, ...change }) : d)));
  return updated;
});

route('DELETE', '/api/documents/:id', (req, db) => {
  const id = req.params[0];
  db.documents.update(all => all.filter(d => d.id !== id));
  db.shares.update(all => all.filter(s => s.documentId !== id));
  return null;
});

route('GET', '/api/documents/:id/download', () => SAMPLE_PDF);

// ---------------------------------------------------------------------------
// Relationships and directory
// ---------------------------------------------------------------------------

route('GET', '/api/relationships/mine', (_req, db) => db.relationships());

route('POST', '/api/relationships', (req, db) => {
  const request = req.body as { counterpartId: string; kind: Relationship['kind'] };
  const person = [...DIRECTORY_ADVISORS, ...DIRECTORY_REVIEWERS].find(p => p.userId === request.counterpartId);
  const profile = db.profiles().USER;
  const created: Relationship = {
    id: db.nextStringId('rel'),
    applicantId: profile.userId,
    applicantName: profile.name ?? '',
    counterpartId: request.counterpartId,
    counterpartName: person?.name ?? request.counterpartId,
    kind: request.kind,
    status: 'REQUESTED',
    createdAt: new Date().toISOString(),
  };
  db.relationships.update(all => [...all, created]);
  return created;
});

route('POST', '/api/relationships/:id/end', (req, db) => {
  const id = req.params[0];
  let ended: Relationship | undefined;
  db.relationships.update(all => all.map(r => (r.id === id ? (ended = { ...r, status: 'ENDED' }) : r)));
  return ended;
});

route('GET', '/api/directory/advisors', () => DIRECTORY_ADVISORS);
route('GET', '/api/directory/reviewers', () => DIRECTORY_REVIEWERS);

// ---------------------------------------------------------------------------
// Cover letter templates (HTML provider)
//
// `preview` and `render` return a fixed text and a fixed PDF. Deriving them from
// the edited blocks would mean writing placeholder and layout logic in
// TypeScript, which is exactly what the HTML provider exists to prevent - the
// preview and the PDF would then be two implementations that drift apart.
// ---------------------------------------------------------------------------

route('GET', '/api/html/cover-letter/template', (_req, db) => db.templates());
route('GET', '/api/html/cover-letter/template/suggestions', () => BLOCK_SUGGESTIONS);
route('GET', '/api/html/cover-letter/template/:id', (req, db) =>
  db.templates().find(t => t.id === req.params[0]));

route('POST', '/api/html/cover-letter/template', (req, db) => {
  const request = req.body as Partial<HtmlLetterTemplate>;
  const created: HtmlLetterTemplate = {
    id: db.nextStringId('tpl'),
    name: request.name ?? 'Neues Anschreiben',
    language: request.language ?? 'GERMAN',
    layoutLetter: request.layoutLetter ?? 'DIN5008_COVER_LETTER_B',
    blocks: request.blocks ?? [],
    style: request.style,
    version: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
  db.templates.update(all => [...all, created]);
  return created;
});

route('PUT', '/api/html/cover-letter/template/:id', (req, db) => {
  const id = req.params[0];
  const request = req.body as Partial<HtmlLetterTemplate>;
  let updated: HtmlLetterTemplate | undefined;
  db.templates.update(all => all.map(t => (t.id === id
    ? (updated = { ...t, ...request, id, version: t.version + 1, updatedAt: new Date().toISOString() })
    : t)));
  return updated;
});

route('DELETE', '/api/html/cover-letter/template/:id', (req, db) => {
  const id = req.params[0];
  db.templates.update(all => all.filter(t => t.id !== id));
  return null;
});

route('POST', '/api/html/cover-letter/template/:id/preview', req =>
  req.query.get('format') === 'pdf' ? SAMPLE_PDF : SAMPLE_PREVIEW_TEXT);

route('POST', '/api/html/cover-letter/:applicationId/render/:templateId', req =>
  req.query.get('format') === 'text' ? SAMPLE_PREVIEW_TEXT : SAMPLE_PDF);

route('POST', '/api/html/cover-letter/:applicationId/email/:templateId', () => ({
  to: 'security-jobs@aurum.example',
  subject: 'Bewerbung als IT-Sicherheitsanalystin',
  body: SAMPLE_PREVIEW_TEXT,
}));

/** The linearized preview, as the backend renders it. Fixed - see the note above. */
const SAMPLE_PREVIEW_TEXT = [
  'Sabine Vogt',
  'Lindenweg 14',
  '79104 Freiburg',
  '',
  'Aurum Fintech SE',
  'Herrn Dr. Falk',
  'Börsenallee 45',
  '60313 Frankfurt am Main',
  '',
  'Freiburg, 30.08.2026',
  '',
  'Bewerbung als IT-Sicherheitsanalystin',
  '',
  'Sehr geehrter Herr Dr. Falk,',
  '',
  'mit großem Interesse habe ich Ihre Ausschreibung für die Stelle als',
  'IT-Sicherheitsanalystin bei Aurum Fintech SE gelesen. […]',
  '',
  'Mit freundlichen Grüßen',
  'Sabine Vogt',
].join('\n');

// ---------------------------------------------------------------------------
// Advisor
// ---------------------------------------------------------------------------

route('GET', '/api/advisor/my-users', () => MY_USERS);
route('GET', '/api/advisor/users', () => MY_USERS);
route('GET', '/api/advisor/suggestions', (_req, db) => db.suggestions());

route('POST', '/api/advisor/suggestions', (req, db) => {
  const request = req.body as { targetUserId: string; companyPositionId: number; message: string };
  const position = positionLabel(db, request.companyPositionId);
  const created: SuggestionDto = {
    id: db.nextNumericId(db.suggestions()),
    targetUserName: MY_USERS[0]?.name ?? '',
    companyName: position.companyName,
    positionTitle: position.positionTitle,
    message: request.message,
    status: 'PENDING',
    createdAt: new Date().toISOString(),
  };
  db.suggestions.update(all => [...all, created]);
  return created;
});

route('GET', '/api/advisor/job-search/status', () => OUT_OF_SCOPE);
route('GET', '/api/advisor/job-search/categories', () => OUT_OF_SCOPE);
route('GET', '/api/advisor/job-search', () => OUT_OF_SCOPE);

// ---------------------------------------------------------------------------
// Reviewer
//
// Built from the shares, not from the document list: a revoked share must
// disappear here while the document stays with its owner. That difference is
// the reviewer role, so the demo derives it rather than hardcoding the answer.
// ---------------------------------------------------------------------------

route('GET', '/api/reviewer/users', (_req, db) => {
  const documents = db.documents();
  const shared = db.shares()
    .filter(share => share.revokedAt === null && share.reviewerId === db.profile().userId)
    .flatMap(share => {
      const document = documents.find(d => d.id === share.documentId);
      return document
        ? [{ id: document.id, label: document.label, filename: document.filename, type: document.type, grantedAt: share.grantedAt }]
        : [];
    });

  if (!shared.length) return [];
  const owner = db.profiles().USER;
  return [{ userId: owner.userId, name: owner.name, email: owner.email, documents: shared }];
});

route('GET', '/api/reviewer/documents/:id/download', () => SAMPLE_PDF);

// ---------------------------------------------------------------------------
// Job posting extraction - a fixed result for any input. The components label
// it as an example where it is displayed, not only in the banner.
// ---------------------------------------------------------------------------

route('POST', '/api/posting/overview', () => POSTING_OVERVIEW);
route('POST', '/api/posting/full-chain', () => POSTING_FULL_CHAIN);
route('POST', '/api/posting/snapshot-validate', () => SAMPLE_PDF);
route('POST', '/api/posting/snapshot', () => ({ id: 'snap-demo' }));
route('GET', '/api/posting/snapshot', () => [{ id: 'snap-demo' }]);
route('GET', '/api/posting/snapshot/:id', () => SAMPLE_PDF);

// ---------------------------------------------------------------------------
// Endpoints the demo does not answer. Listed on purpose: a 501 here says "not
// part of the demo", an unmapped route says "somebody forgot one".
// ---------------------------------------------------------------------------

route('POST', '/api/word/cover-letter/:applicationId/fill/:documentId', () => SAMPLE_PDF);
route('POST', '/api/word/cover-letter/:applicationId/fill/:documentId/word', () => OUT_OF_SCOPE);
route('POST', '/api/word/cover-letter/:applicationId/fill/:documentId/text', () => SAMPLE_PREVIEW_TEXT);
route('POST', '/api/word/cover-letter/:applicationId/fill/:documentId/email', () => ({
  to: 'security-jobs@aurum.example',
  subject: 'Bewerbung als IT-Sicherheitsanalystin',
  body: SAMPLE_PREVIEW_TEXT,
}));
route('GET', '/api/word/cover-letter/personalize', () => OUT_OF_SCOPE);
route('GET', '/api/export/companies', () => OUT_OF_SCOPE);
route('POST', '/api/field-suggestions/company', () => OUT_OF_SCOPE);
route('POST', '/api/field-suggestions/location', () => OUT_OF_SCOPE);
route('POST', '/api/field-suggestions/position', () => OUT_OF_SCOPE);
route('POST', '/api/field-suggestions/application-method', () => OUT_OF_SCOPE);
