import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { provideRouter } from '@angular/router';

import { DataProtectionComponent } from './data-protection.component';
import { expectNoAxeViolations } from '../../../testing/a11y';

describe('DataProtectionComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DataProtectionComponent],
      providers: [provideTranslateService({ fallbackLang: 'en' }), provideRouter([])]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(DataProtectionComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('has no axe-detectable accessibility violations', async () => {
    const fixture = TestBed.createComponent(DataProtectionComponent);
    fixture.detectChanges();
    await expectNoAxeViolations(fixture);
  });
});
