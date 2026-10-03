import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { LegalDocumentComponent } from '../legal-document/legal-document.component';
import { LegalDocumentService } from '../legal-document.service';

/**
 * The privacy policy route. It holds the page frame and the fetch; the text itself is
 * whatever this deployment publishes, so none of it lives in the bundle any more.
 */
@Component({
  selector: 'app-data-protection',
  standalone: true,
  imports: [TranslatePipe, RouterLink, LegalDocumentComponent],
  templateUrl: './data-protection.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: '../legal-page.scss'
})
export class DataProtectionComponent {
  readonly doc = inject(LegalDocumentService).resourceFor('datenschutz');
}
