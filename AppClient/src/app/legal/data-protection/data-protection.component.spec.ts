import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { DataProtectionComponent } from './data-protection.component';

describe('DataProtectionComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DataProtectionComponent],
      providers: [provideTranslateService({ fallbackLang: 'en' })]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(DataProtectionComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
