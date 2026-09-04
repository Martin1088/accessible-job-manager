import { Component, OnInit, ChangeDetectionStrategy, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { HttpFailure, describeHttpFailure } from '../../core/http-error';
import { Company } from '../../model/company';
import { JobPostingImportStore } from '../../services/job-posting-import.store';
import { ErrorTextComponent } from '../../shared/error-text/error-text.component';
import { ImportedPosting, JobPostingImportComponent } from '../../shared/job-posting-import/job-posting-import.component';

interface AdvisorUser {
  userId: string;
  name: string;
  email: string;
}

/**
 * The advisor's way of getting a posting they found into the system and in
 * front of one of their users.
 *
 * Until this page existed an advisor could only suggest a position that was
 * already in their own catalogue, and the only way to put one there was to
 * type it into the company form by hand - while the user's home page had the
 * importer all along. This is that importer with the advisor's ending.
 *
 * "Their own catalogue" is literal: `/api/companies` is filtered by the
 * caller's subject, so a company saved here belongs to the advisor, and
 * `advisor/home` already builds its suggestion dropdown from exactly that
 * list. Nothing about the ownership model changes; this only fills it faster.
 */
@Component({
  selector: 'app-advisor-job-import',
  standalone: true,
  imports: [FormsModule, RouterLink, TranslatePipe, ErrorTextComponent, JobPostingImportComponent],
  templateUrl: './job-import.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './job-import.component.scss'
})
export class JobImportComponent implements OnInit {

  assignedUsers: AdvisorUser[] = [];
  usersFailure: HttpFailure | null = null;

  /** The extracted posting, held until it is saved. */
  pending: ImportedPosting | null = null;

  targetUserId = '';
  message = '';

  saving = false;
  saveFailure: HttpFailure | null = null;
  savedPositionTitle = '';
  savedUserName = '';

  private readonly http = inject(HttpClient);
  private readonly translate = inject(TranslateService);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly importStore = inject(JobPostingImportStore);

  ngOnInit(): void {
    this.http.get<AdvisorUser[]>('/api/advisor/my-users').subscribe({
      next: (users) => this.assignedUsers = users,
      error: (err: HttpErrorResponse) => this.usersFailure = describeHttpFailure(err),
    });
  }

  /**
   * The extraction is finished. It is not saved yet: the advisor still has to
   * say who it is for, and a posting saved without a recipient would leave an
   * orphan company in their catalogue every time they changed their mind.
   */
  onImported(posting: ImportedPosting): void {
    this.pending = posting;
    this.savedPositionTitle = '';
    this.saveFailure = null;
    this.announcer.announce(this.translate.instant('ADVISOR_IMPORT.EXTRACTED_ANNOUNCE'), 'polite');
  }

  get canSend(): boolean {
    return !!this.pending && !!this.targetUserId && !this.saving;
  }

  /**
   * Three calls in sequence, because each needs the id the one before it
   * returns: the company (and with it the position), then the snapshot filed
   * against that position, then the suggestion pointing at it.
   *
   * The snapshot is deliberately not allowed to fail the whole thing. It is
   * the archived copy of the posting, useful but not the point - and a
   * Gotenberg that is down would otherwise mean the advisor cannot suggest
   * anything at all.
   */
  saveAndSuggest(): void {
    if (!this.pending || !this.targetUserId) return;

    this.saving = true;
    this.saveFailure = null;

    this.http.post<Company>('/api/companies', this.pending.company).subscribe({
      next: (created) => {
        const positionId = created.positions?.[0]?.id;
        if (positionId == null) {
          this.saving = false;
          this.saveFailure = { kind: 'server', message: '', status: 0 };
          return;
        }
        this.fileSnapshot(positionId);
        this.sendSuggestion(positionId, created.positions[0].title);
      },
      error: (err: HttpErrorResponse) => {
        this.saveFailure = describeHttpFailure(err);
        this.saving = false;
      },
    });
  }

  /** Best-effort: a missing snapshot never blocks the suggestion. */
  private fileSnapshot(companyPositionId: number): void {
    const held = this.importStore.take();
    if (held) {
      const form = new FormData();
      form.append('file', held);
      form.append('companyPositionId', String(companyPositionId));
      this.http.post('/api/posting/snapshot/upload', form).subscribe({ error: () => {} });
      return;
    }
    const url = this.pending?.sourceJobUrl;
    if (url) {
      const params = new HttpParams().set('url', url).set('companyPositionId', companyPositionId);
      this.http.post('/api/posting/snapshot', null, { params }).subscribe({ error: () => {} });
    }
  }

  private sendSuggestion(companyPositionId: number, positionTitle: string): void {
    this.http.post('/api/advisor/suggestions', {
      targetUserId: this.targetUserId,
      companyPositionId,
      message: this.message,
    }).subscribe({
      next: () => {
        this.savedPositionTitle = positionTitle;
        this.savedUserName = this.assignedUsers.find(u => u.userId === this.targetUserId)?.name ?? '';
        this.pending = null;
        this.targetUserId = '';
        this.message = '';
        this.saving = false;
        this.announcer.announce(
          this.translate.instant('ADVISOR_IMPORT.SENT_ANNOUNCE', { name: this.savedUserName }), 'polite');
      },
      error: (err: HttpErrorResponse) => {
        this.saveFailure = describeHttpFailure(err);
        this.saving = false;
      },
    });
  }
}
