/**
 * Seed data for the advisor role.
 *
 * `MY_USERS` holds exactly one entry although `RELATIONSHIPS` names two advisor
 * links, because the second one is ENDED. An advisor who still saw Sabine after
 * the link ended would make the demo lie about the thing it exists to show.
 *
 * Captured against the API of `feature/advisor` @ 72c91ad, 2026-08-30.
 */
export interface AdvisorUserDto {
  userId: string;
  name: string;
  email: string;
}

export interface SuggestionDto {
  id: number;
  targetUserName: string;
  companyName: string;
  positionTitle: string;
  message: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
  createdAt: string;
}

export const MY_USERS: AdvisorUserDto[] = [
  { userId: 'demo-applicant', name: 'Sabine Vogt', email: 'sabine.vogt@example.org' },
];

export const SUGGESTIONS: SuggestionDto[] = [
  {
    id: 1, targetUserName: 'Sabine Vogt',
    companyName: 'Aurum Fintech SE', positionTitle: 'Produktmanagerin Zahlungsverkehr',
    message: 'Passt zu Ihrer Erfahrung im Zahlungsumfeld - die Stelle ist bis 30.09. ausgeschrieben.',
    status: 'ACCEPTED', createdAt: '2026-08-27T10:05:00',
  },
  {
    id: 2, targetUserName: 'Sabine Vogt',
    companyName: 'Perlmutt Analytics B.V.', positionTitle: 'Accessibility Engineer',
    message: 'Die Ausschreibung nennt WCAG 2.2 AA ausdrücklich - schauen Sie sich das an.',
    status: 'PENDING', createdAt: '2026-08-29T09:40:00',
  },
  {
    id: 3, targetUserName: 'Sabine Vogt',
    companyName: 'Talwind Energie AG', positionTitle: 'Technische Redakteurin',
    message: 'Eher ein Ausweichvorschlag, falls die Frankfurter Stelle nicht klappt.',
    status: 'REJECTED', createdAt: '2026-08-14T13:25:00',
  },
];
