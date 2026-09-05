/**
 * Seed data - fictional employers with DIN 5008-shaped addresses, because the
 * address block is what the cover letter renders and a placeholder would break
 * the one thing the letter is meant to demonstrate.
 *
 * Captured against the API of `feature/advisor` @ 72c91ad, 2026-08-30.
 */
import { Company } from '../../model/company';

export const COMPANIES: Company[] = [
  {
    id: 1,
    name: 'Nordlicht Datentechnik GmbH',
    locations: [
      { id: 1, street: 'Hafenstraße 12', postcode: '20457', city: 'Hamburg', country: 'Deutschland' },
    ],
    positions: [
      {
        id: 11, title: 'Softwareentwicklerin Backend',
        contactGender: 'FEMALE', contactTitle: 'Dr.', contactLastName: 'Brandt',
        applyLanguage: 'GERMAN', email: 'bewerbung@nordlicht.example',
        applicationMethod: 'EMAIL', createdAt: '2026-05-04T08:00:00',
      },
      {
        id: 12, title: 'Werkstudentin Qualitätssicherung',
        contactGender: 'MALE', contactLastName: 'Ohlsen',
        applyLanguage: 'GERMAN', email: 'jobs@nordlicht.example',
        applicationMethod: 'EMAIL', createdAt: '2026-05-04T08:05:00',
      },
    ],
  },
  {
    id: 2,
    name: 'Kranich Verlag KG',
    locations: [
      { id: 2, street: 'Buchbinderweg 3', postcode: '04109', city: 'Leipzig', country: 'Deutschland' },
    ],
    positions: [
      {
        id: 21, title: 'Lektorin Sachbuch',
        contactGender: 'FEMALE', contactLastName: 'Weiß',
        applyLanguage: 'GERMAN', email: 'personal@kranich-verlag.example',
        applicationMethod: 'EMAIL', createdAt: '2026-05-20T11:30:00',
      },
      {
        id: 22, title: 'Redakteurin Digitalformate',
        contactGender: 'DIVERSE', contactLastName: 'Sommer',
        applyLanguage: 'GERMAN', website: 'https://kranich-verlag.example/karriere',
        applicationMethod: 'WEB_FORM', createdAt: '2026-06-01T09:00:00',
      },
    ],
  },
  {
    id: 3,
    name: 'Perlmutt Analytics B.V.',
    locations: [
      { id: 3, street: 'Keizersgracht 210', postcode: '1016 DX', city: 'Amsterdam', country: 'Niederlande' },
    ],
    positions: [
      {
        id: 31, title: 'Data Analyst',
        contactGender: 'MALE', contactLastName: 'de Vries',
        applyLanguage: 'DUTCH', email: 'werk@perlmutt.example',
        applicationMethod: 'EMAIL', createdAt: '2026-04-14T13:20:00',
      },
      {
        id: 32, title: 'Accessibility Engineer',
        contactGender: 'FEMALE', contactLastName: 'Jansen',
        applyLanguage: 'ENGLISH', email: 'careers@perlmutt.example',
        applicationMethod: 'EMAIL',
        notes: 'Stellenanzeige nennt WCAG 2.2 AA ausdrücklich.',
        createdAt: '2026-06-22T16:45:00',
      },
    ],
  },
  {
    id: 4,
    name: 'Talwind Energie AG',
    locations: [
      { id: 4, street: 'Turbinenplatz 7', postcode: '70565', city: 'Stuttgart', country: 'Deutschland' },
      { id: 5, street: 'Windgasse 1', postcode: '24103', city: 'Kiel', country: 'Deutschland' },
    ],
    positions: [
      {
        id: 41, title: 'Projektingenieurin Netzanschluss',
        contactGender: 'MALE', contactTitle: 'Prof. Dr.', contactLastName: 'Hagedorn',
        applyLanguage: 'GERMAN', email: 'karriere@talwind.example',
        applicationMethod: 'EMAIL', createdAt: '2026-03-02T07:50:00',
      },
      {
        id: 42, title: 'Sachbearbeiterin Genehmigungen',
        contactGender: 'FEMALE', contactLastName: 'Kolb',
        applyLanguage: 'GERMAN', email: 'karriere@talwind.example',
        applicationMethod: 'EMAIL', createdAt: '2026-03-02T07:55:00',
      },
      {
        id: 43, title: 'Technische Redakteurin',
        applyLanguage: 'GERMAN', applicationMethod: 'UNKNOWN',
        notes: 'Kontaktperson noch nicht bekannt - vor dem Absenden klären.',
        createdAt: '2026-07-30T12:00:00',
      },
    ],
  },
  {
    id: 5,
    name: 'Steinbach Sozialwerk e. V.',
    locations: [
      { id: 6, street: 'Am Mühlbach 22', postcode: '79098', city: 'Freiburg', country: 'Deutschland' },
    ],
    positions: [
      {
        id: 51, title: 'Verwaltungsfachkraft Teilzeit',
        contactGender: 'FEMALE', contactLastName: 'Ritter',
        applyLanguage: 'GERMAN', email: 'bewerbung@steinbach-sozialwerk.example',
        applicationMethod: 'EMAIL', createdAt: '2026-02-10T09:30:00',
      },
      {
        id: 52, title: 'Koordinatorin Ehrenamt',
        contactGender: 'MALE', contactLastName: 'Baumgartner',
        applyLanguage: 'GERMAN', email: 'bewerbung@steinbach-sozialwerk.example',
        applicationMethod: 'EMAIL', createdAt: '2026-07-05T10:15:00',
      },
    ],
  },
  {
    id: 6,
    name: 'Aurum Fintech SE',
    locations: [
      { id: 7, street: 'Börsenallee 45', postcode: '60313', city: 'Frankfurt am Main', country: 'Deutschland' },
    ],
    positions: [
      {
        id: 61, title: 'Frontend Engineer Accessibility',
        contactGender: 'DIVERSE', contactLastName: 'Neumann',
        applyLanguage: 'ENGLISH', website: 'https://aurum.example/jobs/frontend-a11y',
        applicationMethod: 'WEB_FORM', createdAt: '2026-08-01T08:20:00',
      },
      {
        id: 62, title: 'IT-Sicherheitsanalystin',
        contactGender: 'MALE', contactTitle: 'Dr.', contactLastName: 'Falk',
        applyLanguage: 'GERMAN', email: 'security-jobs@aurum.example',
        applicationMethod: 'EMAIL', createdAt: '2026-08-12T15:10:00',
      },
      {
        id: 63, title: 'Produktmanagerin Zahlungsverkehr',
        contactGender: 'FEMALE', contactLastName: 'Arslan',
        applyLanguage: 'GERMAN', email: 'produkt@aurum.example',
        applicationMethod: 'EMAIL', createdAt: '2026-08-18T11:00:00',
      },
      {
        // Waiting in the review queue, so the same employer shows both states:
        // this one is absent from the company list until it is accepted.
        id: 64, title: 'Referentin Regulatorik',
        contactGender: 'FEMALE', contactLastName: 'Arslan',
        applyLanguage: 'GERMAN', email: 'produkt@aurum.example',
        applicationMethod: 'EMAIL', triageState: 'NEW', createdAt: '2026-08-28T09:15:00',
      },
    ],
  },
  {
    // An employer that exists only in the queue: what an import run leaves
    // behind before anybody has looked at it. Deliberately not one of the
    // employers above - a name appearing twice in the company list would read
    // as a duplicate rather than as a second position.
    id: 7,
    name: 'Halbmond Robotik GmbH',
    locations: [
      { id: 8, street: 'Werkstraße 5', postcode: '90402', city: 'Nürnberg', country: 'Deutschland' },
    ],
    positions: [
      {
        id: 71, title: 'Testingenieurin Automatisierung',
        contactGender: 'MALE', contactLastName: 'Sattler',
        applyLanguage: 'GERMAN', website: 'https://halbmond-robotik.example/stellen/test',
        applicationMethod: 'WEB_FORM', triageState: 'NEW', createdAt: '2026-08-29T07:40:00',
      },
      {
        id: 72, title: 'Technische Dokumentation Robotik',
        contactGender: 'FEMALE', contactLastName: 'Weiß',
        applyLanguage: 'GERMAN', email: 'stellen@halbmond-robotik.example',
        applicationMethod: 'EMAIL', triageState: 'NEW', createdAt: '2026-08-29T07:45:00',
      },
    ],
  },
];
