import { Component, OnInit, ChangeDetectionStrategy, inject } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService, UserMe } from '../../core/auth.service';
import { ImportedPosting, JobPostingImportComponent } from '../../shared/job-posting-import/job-posting-import.component';

@Component({
  selector: 'app-home',
  imports: [RouterLink, TranslatePipe, JobPostingImportComponent],
  templateUrl: './home.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  profile: UserMe | null = null;
  profileError = false;

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.auth.me$.subscribe({
      next: (me) => {
        if (!me) { this.profileError = true; return; }
        if (me.roles.includes('ADVISOR'))  { this.router.navigate(['/advisor']);   return; }
        if (me.roles.includes('REVIEWER')) { this.router.navigate(['/reviewer']);  return; }
        this.profile = me;
      },
      error: () => this.profileError = true,
    });
  }

  /**
   * The user's ending for an import: the extracted fields go to the company
   * form rather than straight to the database, so the person can correct a
   * value the parser guessed before anything is saved. The advisor's import
   * page ends differently - see `advisor/job-import`.
   *
   * The PDF, if the import came from one, stays in `JobPostingImportStore` for
   * the form to file once the position exists.
   */
  onImported({ company, sourceJobUrl }: ImportedPosting): void {
    this.router.navigate(['/companies/new'], { state: { company, sourceJobUrl } });
  }
}
