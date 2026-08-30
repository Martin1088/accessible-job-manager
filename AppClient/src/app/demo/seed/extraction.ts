/**
 * The fixed result of the paste flow and the job import.
 *
 * The demo has no parser, so this comes back for any URL that is entered. The
 * components that show it label it as an example at the point of display, not
 * only in the banner - somebody who notices afterwards that nothing was
 * extracted is worse off than somebody who knew from the start.
 *
 * Captured from `POST /api/posting/overview` and `/full-chain` on
 * `feature/advisor` @ 72c91ad, 2026-08-30.
 */
export const POSTING_OVERVIEW = {
  title: 'Frontend Engineer Accessibility (m/w/d)',
  company: 'Aurum Fintech SE',
  location: 'Frankfurt am Main',
  employmentType: 'Vollzeit',
};

export const POSTING_FULL_CHAIN = {
  company: {
    name: 'Aurum Fintech SE',
    locations: [{ street: 'Börsenallee 45', city: 'Frankfurt am Main' }],
    positions: [
      {
        title: 'Frontend Engineer Accessibility (m/w/d)',
        contactGender: 'TEAM' as const,
        contactTitle: null,
        contactLastName: null,
        email: null,
        website: 'https://aurum.example/jobs/frontend-a11y',
        notes: 'Bewerbung ausschließlich über das Webformular.',
      },
    ],
  },
  sourceJobId: 'aurum-2026-0814',
  postedAt: '2026-08-14',
  deadline: '2026-09-30',
  employmentType: 'Vollzeit',
};
