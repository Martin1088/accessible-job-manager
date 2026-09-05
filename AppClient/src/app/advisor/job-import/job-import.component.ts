import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { ImportedPosting, JobPostingImportComponent } from '../../shared/job-posting-import/job-posting-import.component';

/**
 * The advisor's way of getting a posting they found into their own company
 * catalogue. Extraction only, using the same importer the user's home page
 * uses - the result is handed straight to the full company form
 * (/advisor/companies/new) to review and save, exactly the way the user
 * side's `onImported` sends an extracted posting to /companies/new.
 *
 * Suggesting the saved position to one of their users is a separate step
 * that now happens afterwards, from the "Suggest to a user" action on
 * /advisor/companies (CompanyListComponent) - not here. A posting worth
 * saving is not always worth sending immediately, and folding both into one
 * page forced an advisor to pick a recipient before they had even seen the
 * extracted company laid out in full, with every field editable.
 */
@Component({
  selector: 'app-advisor-job-import',
  standalone: true,
  imports: [TranslatePipe, JobPostingImportComponent],
  templateUrl: './job-import.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './job-import.component.scss'
})
export class JobImportComponent {

  constructor(private router: Router) {}

  onImported({ company, sourceJobUrl }: ImportedPosting): void {
    this.router.navigate(['/advisor/companies/new'], { state: { company, sourceJobUrl } });
  }
}
