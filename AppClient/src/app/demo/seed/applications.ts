/**
 * Seed data - fifteen applications spread across every status the model has, so
 * the status filter and the sort on the applications table have something to do.
 *
 * Captured against the API of `feature/advisor` @ 72c91ad, 2026-08-30.
 */
import { Application } from '../../model/application';

export const APPLICATIONS: Application[] = [
  { id: 1,  companyPositionId: 11, companyName: 'Nordlicht Datentechnik GmbH', positionTitle: 'Softwareentwicklerin Backend',    status: 'INTERVIEW_SCHEDULED', appliedDate: '2026-07-14', notes: 'Zweitgespräch am 12.09., Videocall.', createdAt: '2026-07-14T09:00:00' },
  { id: 2,  companyPositionId: 12, companyName: 'Nordlicht Datentechnik GmbH', positionTitle: 'Werkstudentin Qualitätssicherung', status: 'REJECTED',            appliedDate: '2026-05-30', notes: 'Absage: Stelle intern besetzt.',   createdAt: '2026-05-30T10:20:00' },
  { id: 3,  companyPositionId: 21, companyName: 'Kranich Verlag KG',           positionTitle: 'Lektorin Sachbuch',                status: 'SENT',                appliedDate: '2026-08-19',                                            createdAt: '2026-08-19T08:40:00' },
  { id: 4,  companyPositionId: 22, companyName: 'Kranich Verlag KG',           positionTitle: 'Redakteurin Digitalformate',       status: 'DRAFT',                                          notes: 'Anschreiben fehlt noch.',        createdAt: '2026-08-25T18:05:00' },
  { id: 5,  companyPositionId: 31, companyName: 'Perlmutt Analytics B.V.',     positionTitle: 'Data Analyst',                     status: 'WITHDRAWN',           appliedDate: '2026-04-20', notes: 'Umzug nach Amsterdam verworfen.', createdAt: '2026-04-20T13:00:00' },
  { id: 6,  companyPositionId: 32, companyName: 'Perlmutt Analytics B.V.',     positionTitle: 'Accessibility Engineer',           status: 'INTERVIEW_DONE',      appliedDate: '2026-06-25', notes: 'Gespräch lief gut, Rückmeldung bis KW 36.', createdAt: '2026-06-25T16:50:00' },
  { id: 7,  companyPositionId: 41, companyName: 'Talwind Energie AG',          positionTitle: 'Projektingenieurin Netzanschluss', status: 'OFFER_RECEIVED',      appliedDate: '2026-03-09', notes: 'Angebot liegt vor, Frist 15.09.', createdAt: '2026-03-09T07:30:00' },
  { id: 8,  companyPositionId: 42, companyName: 'Talwind Energie AG',          positionTitle: 'Sachbearbeiterin Genehmigungen',   status: 'REJECTED',            appliedDate: '2026-03-09',                                            createdAt: '2026-03-09T07:35:00' },
  { id: 9,  companyPositionId: 43, companyName: 'Talwind Energie AG',          positionTitle: 'Technische Redakteurin',           status: 'DRAFT',                                          notes: 'Kontaktperson unklar.',          createdAt: '2026-07-30T12:10:00' },
  { id: 10, companyPositionId: 51, companyName: 'Steinbach Sozialwerk e. V.',  positionTitle: 'Verwaltungsfachkraft Teilzeit',    status: 'ACCEPTED',            appliedDate: '2026-02-17', notes: 'Zugesagt, Start 01.10.',         createdAt: '2026-02-17T09:45:00' },
  { id: 11, companyPositionId: 52, companyName: 'Steinbach Sozialwerk e. V.',  positionTitle: 'Koordinatorin Ehrenamt',           status: 'SENT',                appliedDate: '2026-07-09',                                            createdAt: '2026-07-09T10:30:00' },
  { id: 12, companyPositionId: 61, companyName: 'Aurum Fintech SE',            positionTitle: 'Frontend Engineer Accessibility',  status: 'INTERVIEW_SCHEDULED', appliedDate: '2026-08-04', notes: 'Fachgespräch 08.09., vor Ort.',  createdAt: '2026-08-04T08:30:00' },
  { id: 13, companyPositionId: 62, companyName: 'Aurum Fintech SE',            positionTitle: 'IT-Sicherheitsanalystin',          status: 'SENT',                appliedDate: '2026-08-22',                                            createdAt: '2026-08-22T15:20:00' },
  { id: 14, companyPositionId: 63, companyName: 'Aurum Fintech SE',            positionTitle: 'Produktmanagerin Zahlungsverkehr', status: 'DRAFT',                                          notes: 'Auf Vorschlag von Jonas Reinhardt.', createdAt: '2026-08-28T11:15:00' },
  { id: 15, companyPositionId: 11, companyName: 'Nordlicht Datentechnik GmbH', positionTitle: 'Softwareentwicklerin Backend',     status: 'WITHDRAWN',           appliedDate: '2026-01-12', notes: 'Erste Bewerbungsrunde, zurückgezogen.', createdAt: '2026-01-12T09:00:00' },
];
