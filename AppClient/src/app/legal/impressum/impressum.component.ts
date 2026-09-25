import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { LegalDocumentComponent } from '../legal-document/legal-document.component';
import { LegalDocumentService } from '../legal-document.service';

/**
 * The legal notice route. § 5 DDG requires the operator's own details, which is exactly
 * why this page cannot ship its text in the image - see LegalDocumentRegistry.
 */
@Component({
  selector: 'app-impressum',
  standalone: true,
  imports: [TranslatePipe, RouterLink, LegalDocumentComponent],
  templateUrl: './impressum.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../legal-page.scss'
})
export class ImpressumComponent {
  readonly doc = inject(LegalDocumentService).resourceFor('impressum');
}
