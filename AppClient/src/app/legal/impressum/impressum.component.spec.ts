import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { provideRouter } from '@angular/router';

import { ImpressumComponent } from './impressum.component';

describe('ImpressumComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ImpressumComponent],
      providers: [provideTranslateService({ fallbackLang: 'en' }), provideRouter([])]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ImpressumComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
