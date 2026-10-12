/**
 * The fixed result of the CV matching.
 *
 * The demo runs no model, so this comes back for any posting and either CV. It
 * belongs to the posting the demo's import always extracts (`extraction.ts`)
 * and to Sabine Vogt's CV, so the showcase tells one consistent story. The
 * component labels it as an example where it is displayed, as the extraction
 * result is.
 *
 * Written by hand, not captured: the backend endpoint does not exist yet. The
 * shape is the contract in `model/cv-match.ts`.
 *
 * Published in German and English; any other interface language gets the
 * English one, and the result's `language` says so - which is what makes the
 * component put `lang="en"` on it.
 */
import { CvMatchResult } from '../../model/cv-match';
import { POSTING_OVERVIEW } from './extraction';

type SeedResult = Omit<CvMatchResult, 'cvDocumentId'>;

const DE: SeedResult = {
  level: 'PARTIAL',
  summary: 'Der Lebenslauf deckt die Kernanforderungen an Barrierefreiheit und Angular gut ab. '
    + 'Es fehlen React, Erfahrung mit barrierefreien PDFs und Kenntnisse aus dem Finanzumfeld.',
  postingTitle: POSTING_OVERVIEW.title,
  company: POSTING_OVERVIEW.company,
  language: 'de',
  postingLanguage: 'de',
  cvLanguage: 'de',
  generatedAt: '2026-10-12T09:30:00',
  skills: [
    {
      skill: 'React',
      category: 'TECHNICAL',
      importance: 'REQUIRED',
      status: 'MISSING',
      postingQuote: 'Sehr gute Kenntnisse in React oder einem vergleichbaren komponentenbasierten Framework',
      cvEvidence: null,
      reasoning: 'Der Lebenslauf nennt Angular, aber kein React. Ob Angular als „vergleichbar“ gilt, '
        + 'entscheidet das Unternehmen; ausdrücklich verlangt ist React.',
      suggestion: 'Im Anschreiben die Angular-Erfahrung als komponentenbasiert einordnen und ein kleines '
        + 'React-Projekt nennen, falls vorhanden.',
    },
    {
      skill: 'Barrierefreie PDFs (PDF/UA)',
      category: 'TECHNICAL',
      importance: 'REQUIRED',
      status: 'MISSING',
      postingQuote: 'Erfahrung mit der Erstellung barrierefreier Dokumente (PDF/UA)',
      cvEvidence: null,
      reasoning: 'Im Lebenslauf findet sich keine Arbeit mit getaggten PDFs oder PDF/UA.',
      suggestion: 'Eine Einführung in PDF/UA und veraPDF durcharbeiten; das lässt sich im Gespräch gut belegen.',
    },
    {
      skill: 'Erfahrung im Finanzumfeld',
      category: 'EXPERIENCE',
      importance: 'NICE_TO_HAVE',
      status: 'MISSING',
      postingQuote: 'Idealerweise erste Erfahrungen im Banken- oder Fintech-Umfeld',
      cvEvidence: null,
      reasoning: 'Die bisherigen Stationen liegen in Logistik und öffentlicher Verwaltung.',
      suggestion: 'Nur wünschenswert - kein Ausschlussgrund. Erfahrung mit regulierten Umgebungen '
        + '(öffentliche Verwaltung, BITV) kann hier als Brücke dienen.',
    },
    {
      skill: 'Screenreader-Tests',
      category: 'TECHNICAL',
      importance: 'REQUIRED',
      status: 'PARTIAL',
      postingQuote: 'Manuelle Tests mit NVDA, JAWS und VoiceOver',
      cvEvidence: 'Regelmäßige Tests der Oberflächen mit NVDA',
      reasoning: 'NVDA ist belegt, JAWS und VoiceOver werden nicht erwähnt.',
      suggestion: 'VoiceOver (macOS/iOS) ergänzen - ohne zusätzliche Lizenz testbar.',
    },
    {
      skill: 'Design-Systeme',
      category: 'TECHNICAL',
      importance: 'NICE_TO_HAVE',
      status: 'PARTIAL',
      postingQuote: 'Mitarbeit an unserem Design-System',
      cvEvidence: 'Pflege einer gemeinsamen Komponentenbibliothek',
      reasoning: 'Eine Komponentenbibliothek ist nah dran, ein Design-System mit Tokens und Dokumentation wird nicht genannt.',
      suggestion: 'Beschreiben, ob die Bibliothek Design-Tokens oder eine Dokumentation hatte.',
    },
    {
      skill: 'Angular',
      category: 'TECHNICAL',
      importance: 'REQUIRED',
      status: 'MET',
      postingQuote: 'Erfahrung mit Angular',
      cvEvidence: 'Vier Jahre Entwicklung von Angular-Anwendungen bei der Nordlicht Logistik GmbH',
      reasoning: 'Mehrjährige Berufserfahrung ist belegt.',
      suggestion: null,
    },
    {
      skill: 'TypeScript',
      category: 'TECHNICAL',
      importance: 'REQUIRED',
      status: 'MET',
      postingQuote: 'Sicherer Umgang mit TypeScript',
      cvEvidence: 'TypeScript, RxJS, Signals',
      reasoning: 'Im Kenntnisprofil und in den Projekten genannt.',
      suggestion: null,
    },
    {
      skill: 'WCAG 2.2 / BITV 2.0',
      category: 'TECHNICAL',
      importance: 'REQUIRED',
      status: 'MET',
      postingQuote: 'Fundierte Kenntnisse der WCAG 2.2',
      cvEvidence: 'Umsetzung der BITV 2.0 für ein Bürgerportal',
      reasoning: 'Die BITV 2.0 verweist auf die EN 301 549 und damit auf die WCAG; praktische Umsetzung ist belegt.',
      suggestion: null,
    },
    {
      skill: 'Deutsch (C1)',
      category: 'LANGUAGE',
      importance: 'REQUIRED',
      status: 'MET',
      postingQuote: 'Verhandlungssichere Deutschkenntnisse',
      cvEvidence: 'Deutsch: Muttersprache',
      reasoning: 'Übertrifft die Anforderung.',
      suggestion: null,
    },
  ],
};

const EN: SeedResult = {
  level: 'PARTIAL',
  summary: 'The CV covers the core accessibility and Angular requirements well. '
    + 'React, experience with accessible PDFs and a finance background are missing.',
  postingTitle: POSTING_OVERVIEW.title,
  company: POSTING_OVERVIEW.company,
  language: 'en',
  postingLanguage: 'de',
  cvLanguage: 'de',
  generatedAt: '2026-10-12T09:30:00',
  skills: [
    {
      skill: 'React',
      category: 'TECHNICAL',
      importance: 'REQUIRED',
      status: 'MISSING',
      postingQuote: 'Sehr gute Kenntnisse in React oder einem vergleichbaren komponentenbasierten Framework',
      cvEvidence: null,
      reasoning: 'The CV names Angular but not React. Whether Angular counts as "comparable" is the '
        + 'company\'s call; React is what the posting asks for.',
      suggestion: 'In the cover letter, frame the Angular experience as component-based work and name a '
        + 'small React project if there is one.',
    },
    {
      skill: 'Accessible PDFs (PDF/UA)',
      category: 'TECHNICAL',
      importance: 'REQUIRED',
      status: 'MISSING',
      postingQuote: 'Erfahrung mit der Erstellung barrierefreier Dokumente (PDF/UA)',
      cvEvidence: null,
      reasoning: 'The CV shows no work with tagged PDFs or PDF/UA.',
      suggestion: 'Work through an introduction to PDF/UA and veraPDF; that is easy to show in an interview.',
    },
    {
      skill: 'Finance background',
      category: 'EXPERIENCE',
      importance: 'NICE_TO_HAVE',
      status: 'MISSING',
      postingQuote: 'Idealerweise erste Erfahrungen im Banken- oder Fintech-Umfeld',
      cvEvidence: null,
      reasoning: 'Previous positions were in logistics and public administration.',
      suggestion: 'Only nice to have, not a reason to be turned down. Experience with regulated settings '
        + '(public administration, BITV) can serve as a bridge.',
    },
    {
      skill: 'Screen reader testing',
      category: 'TECHNICAL',
      importance: 'REQUIRED',
      status: 'PARTIAL',
      postingQuote: 'Manuelle Tests mit NVDA, JAWS und VoiceOver',
      cvEvidence: 'Regelmäßige Tests der Oberflächen mit NVDA',
      reasoning: 'NVDA is shown; JAWS and VoiceOver are not mentioned.',
      suggestion: 'Add VoiceOver (macOS/iOS) - it can be tested without an extra licence.',
    },
    {
      skill: 'Design systems',
      category: 'TECHNICAL',
      importance: 'NICE_TO_HAVE',
      status: 'PARTIAL',
      postingQuote: 'Mitarbeit an unserem Design-System',
      cvEvidence: 'Pflege einer gemeinsamen Komponentenbibliothek',
      reasoning: 'A component library comes close; a design system with tokens and documentation is not mentioned.',
      suggestion: 'Describe whether the library had design tokens or documentation.',
    },
    {
      skill: 'Angular',
      category: 'TECHNICAL',
      importance: 'REQUIRED',
      status: 'MET',
      postingQuote: 'Erfahrung mit Angular',
      cvEvidence: 'Vier Jahre Entwicklung von Angular-Anwendungen bei der Nordlicht Logistik GmbH',
      reasoning: 'Several years of professional experience are shown.',
      suggestion: null,
    },
    {
      skill: 'TypeScript',
      category: 'TECHNICAL',
      importance: 'REQUIRED',
      status: 'MET',
      postingQuote: 'Sicherer Umgang mit TypeScript',
      cvEvidence: 'TypeScript, RxJS, Signals',
      reasoning: 'Named in the skills profile and in the projects.',
      suggestion: null,
    },
    {
      skill: 'WCAG 2.2 / BITV 2.0',
      category: 'TECHNICAL',
      importance: 'REQUIRED',
      status: 'MET',
      postingQuote: 'Fundierte Kenntnisse der WCAG 2.2',
      cvEvidence: 'Umsetzung der BITV 2.0 für ein Bürgerportal',
      reasoning: 'BITV 2.0 refers to EN 301 549 and so to WCAG; hands-on implementation is shown.',
      suggestion: null,
    },
    {
      skill: 'German (C1)',
      category: 'LANGUAGE',
      importance: 'REQUIRED',
      status: 'MET',
      postingQuote: 'Verhandlungssichere Deutschkenntnisse',
      cvEvidence: 'Deutsch: Muttersprache',
      reasoning: 'Exceeds the requirement.',
      suggestion: null,
    },
  ],
};

export const DEMO_MATCHING: Readonly<Record<string, SeedResult>> = { de: DE, en: EN };
