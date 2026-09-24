import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';

import { LegalDocumentComponent } from './legal-document.component';
import { LEGAL_FIXTURE } from '../legal-document.fixture';
import { LegalDocument } from '../legal-document.model';
import { expectNoAxeViolations } from '../../../testing/a11y';

describe('LegalDocumentComponent', () => {
  let fixture: ComponentFixture<LegalDocumentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LegalDocumentComponent],
      providers: [provideTranslateService({ fallbackLang: 'en' })]
    }).compileComponents();

    TestBed.inject(TranslateService).use('en');

    fixture = TestBed.createComponent(LegalDocumentComponent);
    fixture.componentRef.setInput('titleKey', 'DATA_PROTECTION.TITLE');
  });

  function render(document?: LegalDocument, state: { loading?: boolean; error?: unknown } = {}): HTMLElement {
    fixture.componentRef.setInput('document', document);
    fixture.componentRef.setInput('loading', state.loading ?? false);
    fixture.componentRef.setInput('error', state.error);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the document title as the only h1', () => {
    const el = render(LEGAL_FIXTURE);

    expect(el.querySelectorAll('h1').length).toBe(1);
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Privacy policy');
  });

  it('associates every section with its heading', () => {
    const el = render(LEGAL_FIXTURE);
    const sections = Array.from(el.querySelectorAll('section'));

    expect(sections.length).toBe(2);
    sections.forEach(section => {
      const id = section.getAttribute('aria-labelledby');
      expect(id).toBeTruthy();
      expect(section.querySelector('h2')?.id).toBe(id!);
    });
  });

  it('renders a definitions block as a description list with a real link', () => {
    const el = render(LEGAL_FIXTURE);
    const list = el.querySelector('dl');

    expect(Array.from(list!.querySelectorAll('dt')).map(dt => dt.textContent?.trim()))
      .toEqual(['Name', 'Email']);
    expect(list!.querySelector('a')?.getAttribute('href')).toBe('mailto:privacy@acme.test');
  });

  it('renders a section that mixes paragraphs and a list in the authored order', () => {
    const el = render(LEGAL_FIXTURE);
    const rights = el.querySelectorAll('section')[1];
    const tags = Array.from(rights.children).map(child => child.tagName.toLowerCase());

    // The rights section is a lead-in, a list, then a closing paragraph - the case a
    // one-block-per-section model could not express.
    expect(tags).toEqual(['h2', 'p', 'ul', 'p']);
    expect(rights.querySelectorAll('li').length).toBe(2);
  });

  it('neuters a hostile href even though the backend already rejects one', () => {
    const hostile: LegalDocument = {
      ...LEGAL_FIXTURE,
      sections: [{
        heading: 'Controller',
        blocks: [{ type: 'definitions', items: [{ term: 'Name', value: 'Click', href: 'javascript:alert(1)' }] }]
      }]
    };

    const href = render(hostile).querySelector('a')?.getAttribute('href');

    expect(href?.startsWith('javascript:')).toBeFalse();
  });

  it('marks the language only when the document is not in the interface language', () => {
    expect(render(LEGAL_FIXTURE).querySelector('article')?.getAttribute('lang')).toBeNull();

    const german = render({ ...LEGAL_FIXTURE, language: 'de', requestedLanguage: 'en' });

    expect(german.querySelector('article')?.getAttribute('lang')).toBe('de');
    expect(german.querySelector('.language-notice')).not.toBeNull();
  });

  it('renders as a page by default: title is the h1, sections are h2', () => {
    const el = render(LEGAL_FIXTURE);

    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Privacy policy');
    expect(Array.from(el.querySelectorAll('h2')).map(h => h.textContent?.trim()))
      .toEqual(['Controller', 'Your rights']);
    expect(el.querySelector('h3')).toBeNull();
  });

  it('shifts one level down when embedded, without skipping a level', () => {
    fixture.componentRef.setInput('headingLevel', 2);
    const el = render(LEGAL_FIXTURE);

    // The login page already owns the h1, so the document title becomes the h2 and its
    // sections h3. A document that kept its h1 there would give the page two.
    expect(el.querySelector('h1')).toBeNull();
    expect(el.querySelector('h2')?.textContent?.trim()).toBe('Privacy policy');
    expect(Array.from(el.querySelectorAll('h3')).map(h => h.textContent?.trim()))
      .toEqual(['Controller', 'Your rights']);
  });

  it('puts titleId on the title so an embedding landmark can be named after it', () => {
    fixture.componentRef.setInput('headingLevel', 2);
    fixture.componentRef.setInput('titleId', 'demo-notice-title');
    const el = render(LEGAL_FIXTURE);

    expect(el.querySelector('h2')?.id).toBe('demo-notice-title');
  });

  it('announces a loading state', () => {
    expect(render(undefined, { loading: true }).querySelector('[role="status"]')).not.toBeNull();
  });

  it('announces a failure as an alert', () => {
    const el = render(undefined, { error: new Error('offline') });

    expect(el.querySelector('[role="alert"]')).not.toBeNull();
    expect(el.querySelector('h1')).not.toBeNull();
  });

  it('has no axe-detectable accessibility violations when loaded', async () => {
    render(LEGAL_FIXTURE);
    await expectNoAxeViolations(fixture);
  });

  it('has no axe-detectable accessibility violations while loading', async () => {
    render(undefined, { loading: true });
    await expectNoAxeViolations(fixture);
  });

  it('has no axe-detectable accessibility violations in the error state', async () => {
    render(undefined, { error: new Error('offline') });
    await expectNoAxeViolations(fixture);
  });
});
