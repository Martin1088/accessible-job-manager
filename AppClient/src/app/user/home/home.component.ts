import { Component, Injector, OnInit, ChangeDetectionStrategy, afterNextRender, inject, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService, UserMe } from '../../core/auth.service';
import { PostingSource } from '../../model/cv-match';
import { CvMatchComponent } from '../cv-match/cv-match.component';
import { ImportedPosting, JobPostingImportComponent } from '../../shared/job-posting-import/job-posting-import.component';

@Component({
  selector: 'app-home',
  imports: [TranslatePipe, JobPostingImportComponent, CvMatchComponent],
  templateUrl: './home.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  profile: UserMe | null = null;
  profileError = false;

  /**
   * The CV matching, opened from the import's "check against my CV" button with
   * the posting the import already has. Loaded only once it is asked for.
   */
  matchOpen = false;
  matchPosting: PostingSource | null = null;

  private readonly importer = viewChild(JobPostingImportComponent);
  // By template reference, not by class: naming CvMatchComponent here would pull it
  // into the initial bundle and defeat the @defer around it.
  private readonly matcher = viewChild<{ focusHeading(): void }>('matcher');
  private readonly injector = inject(Injector);

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

  onMatchRequested(posting: PostingSource | null): void {
    // Opening it the first time, the sheet focuses its own heading (`focusOnOpen`).
    // Already open, it is only re-targeted - so move focus there from here.
    const alreadyOpen = this.matchOpen;
    this.matchPosting = posting;
    this.matchOpen = true;
    if (alreadyOpen) {
      afterNextRender(() => this.matcher()?.focusHeading(), { injector: this.injector });
    }
  }

  /**
   * The import's own finish, offered again under the matching so the result does
   * not have to be scrolled back past - same prefill, same way to the form.
   */
  createCompany(): void {
    this.importer()?.submit();
  }
}
