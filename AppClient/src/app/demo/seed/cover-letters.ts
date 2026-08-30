/**
 * Seed data - one editable letter template plus the block suggestions the editor
 * offers. The blocks carry real prose, not lorem ipsum: the template editor is
 * what the demo is for, and placeholder text would hide whether the DIN 5008
 * layout actually holds a paragraph of that length.
 *
 * Captured against the API of `feature/advisor` @ 72c91ad, 2026-08-30.
 */
import { HtmlLetterTemplate, LetterBlock } from '../../model/cover-letter';

export const LETTER_TEMPLATES: HtmlLetterTemplate[] = [
  {
    id: 'tpl-de-standard',
    name: 'Anschreiben Standard (DE)',
    language: 'GERMAN',
    layoutLetter: 'DIN5008_COVER_LETTER_B',
    version: 3,
    createdAt: '2026-02-01T11:00:00',
    updatedAt: '2026-08-21T16:30:00',
    blocks: [
      { id: 'b1', key: 'SUBJECT', content: 'Bewerbung als {{position}}', items: [] },
      { id: 'b2', key: 'SALUTATION', content: '', items: [] },
      {
        id: 'b3', key: 'PARAGRAPH',
        content: 'mit großem Interesse habe ich Ihre Ausschreibung für die Stelle als '
          + '<b>{{position}}</b> bei {{company}} gelesen. Die Verbindung aus barrierefreier '
          + 'Softwareentwicklung und einem Produkt, das täglich benutzt wird, ist genau das '
          + 'Umfeld, in dem ich arbeiten möchte.',
        items: [],
      },
      {
        id: 'b4', key: 'PARAGRAPH',
        content: 'In den vergangenen vier Jahren habe ich Weboberflächen entwickelt und '
          + 'dabei gelernt, Barrierefreiheit nicht als Prüfschritt am Ende zu behandeln:',
        items: [],
      },
      {
        id: 'b5', key: 'BULLET_LIST', content: '',
        items: [
          'WCAG 2.2 AA als Abnahmekriterium in der Definition of Done verankert',
          'Testläufe mit NVDA und VoiceOver fest im Entwicklungsprozess',
          'Komponentenbibliothek auf Tastaturbedienbarkeit umgestellt',
        ],
      },
      {
        id: 'b6', key: 'PARAGRAPH',
        content: 'Über eine Einladung zum Gespräch freue ich mich sehr.',
        items: [],
      },
      { id: 'b7', key: 'REGARDS', content: '', items: [] },
    ],
  },
  {
    id: 'tpl-en-standard',
    name: 'Cover letter (EN)',
    language: 'ENGLISH',
    layoutLetter: 'DIN5008_COVER_LETTER_B',
    version: 1,
    createdAt: '2026-06-19T09:30:00',
    updatedAt: '2026-06-19T09:30:00',
    blocks: [
      { id: 'c1', key: 'SUBJECT', content: 'Application for {{position}}', items: [] },
      { id: 'c2', key: 'SALUTATION', content: '', items: [] },
      {
        id: 'c3', key: 'PARAGRAPH',
        content: 'I am writing to apply for the position of <b>{{position}}</b> at '
          + '{{company}}. Accessibility has been the centre of my work for the past four '
          + 'years, and your posting is the first I have read that names WCAG 2.2 AA '
          + 'as a requirement rather than an aspiration.',
        items: [],
      },
      { id: 'c4', key: 'REGARDS', content: '', items: [] },
    ],
  },
];

/** What `/api/html/cover-letter/template/suggestions` offers when adding a block. */
export const BLOCK_SUGGESTIONS: LetterBlock[] = [
  { id: null, key: 'PARAGRAPH', content: 'Zu meinen fachlichen Schwerpunkten zählen …', items: [] },
  { id: null, key: 'PARAGRAPH', content: 'Meine Kündigungsfrist beträgt drei Monate zum Quartalsende.', items: [] },
  { id: null, key: 'BULLET_LIST', content: '', items: ['Erste Station', 'Zweite Station'] },
  { id: null, key: 'HEADING', content: 'Warum ich zu Ihnen passe', items: [] },
];
