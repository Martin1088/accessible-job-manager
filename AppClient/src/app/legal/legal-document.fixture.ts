import { LegalDocument } from './legal-document.model';

/**
 * One document using all three block types, shared by the renderer spec and both route
 * specs so they assert against the same shape.
 */
export const LEGAL_FIXTURE: LegalDocument = {
  slug: 'datenschutz',
  requestedLanguage: 'en',
  language: 'en',
  title: 'Privacy policy',
  intro: ['This page explains what is processed.'],
  sections: [
    {
      heading: 'Controller',
      blocks: [{
        type: 'definitions',
        items: [
          { term: 'Name', value: 'Acme GmbH' },
          { term: 'Email', value: 'privacy@acme.test', href: 'mailto:privacy@acme.test' }
        ]
      }]
    },
    {
      heading: 'Your rights',
      blocks: [
        { type: 'paragraph', text: 'Under the GDPR, you have the right to:' },
        { type: 'list', items: ['Access your data.', 'Have it corrected.'] },
        { type: 'paragraph', text: 'Contact us to exercise any of these.' }
      ]
    }
  ]
};
