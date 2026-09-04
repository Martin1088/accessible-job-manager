import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { ErrorTextComponent } from './error-text.component';
import { HttpFailure } from '../../core/http-error';
import { expectNoAxeViolations } from '../../../testing/a11y';

describe('ErrorTextComponent', () => {
  let fixture: ComponentFixture<ErrorTextComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorTextComponent],
      providers: [provideTranslateService({ fallbackLang: 'en' })],
    }).compileComponents();

    fixture = TestBed.createComponent(ErrorTextComponent);
    fixture.componentInstance.fallbackKey = 'HOME.JOB_SEARCH_ERROR';
  });

  function render(failure: HttpFailure | null): string {
    fixture.componentInstance.failure = failure;
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent?.trim() ?? '';
  }

  it('renders nothing until a request has failed', () => {
    expect(render(null)).toBe('');
  });

  it('shows the server\'s own reason verbatim, not the caller\'s fallback', () => {
    const text = render({ kind: 'reported', message: 'This site does not allow automated access (403).', status: 502 });

    expect(text).toBe('This site does not allow automated access (403).');
    expect(text).not.toContain('HOME.JOB_SEARCH_ERROR');
  });

  /**
   * The regression this guards: an unreachable backend used to render the
   * caller's "check the URL and try again" wording, sending the user to fix
   * something that was never wrong.
   */
  it('does not blame the URL when the server was unreachable', () => {
    const text = render({ kind: 'offline', message: '', status: 0 });

    expect(text).toContain('ERROR.OFFLINE');
    expect(text).not.toContain('HOME.JOB_SEARCH_ERROR');
  });

  it('names the session rather than the endpoint when the login expired', () => {
    const text = render({ kind: 'unauthenticated', message: '', status: 401 });

    expect(text).toContain('ERROR.SESSION_EXPIRED');
    expect(text).not.toContain('HOME.JOB_SEARCH_ERROR');
  });

  it('falls back to the caller\'s wording and keeps the status when the server gave no reason', () => {
    const text = render({ kind: 'server', message: '', status: 500 });

    expect(text).toContain('HOME.JOB_SEARCH_ERROR');
    expect(text).toContain('ERROR.STATUS');
  });

  it('has no axe-detectable accessibility violations', async () => {
    render({ kind: 'offline', message: '', status: 0 });
    await expectNoAxeViolations(fixture);
  });
});
