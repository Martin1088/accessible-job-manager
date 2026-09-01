import { Injectable } from '@angular/core';

/**
 * Carries the PDF of a job posting from the import screen to the company form.
 *
 * The posting snapshot is filed against a CompanyPosition, which does not exist
 * yet while the user is still reviewing the extracted fields - so the file
 * cannot be stored when it is uploaded. It is held here instead and filed once
 * the position has been created, which is the same point at which the URL path
 * calls `POST /api/posting/snapshot`.
 *
 * Held in memory rather than uploaded as an unlinked Document on purpose:
 * `documents.component` deliberately leaves JOB_POSTING_SNAPSHOT out of both of
 * its tables, so a snapshot with no position would be a row the user can
 * neither see nor delete. Losing the file on a page reload is the better
 * failure - the extracted company prefill travels in `history.state` and does
 * not survive a reload either, so the whole flow restarts together.
 */
@Injectable({ providedIn: 'root' })
export class JobPostingImportStore {

  private pending?: File;

  hold(file: File): void {
    this.pending = file;
  }

  /** Returns the held file and forgets it, so a later save cannot re-file it. */
  take(): File | undefined {
    const file = this.pending;
    this.pending = undefined;
    return file;
  }

  clear(): void {
    this.pending = undefined;
  }

  get hasPending(): boolean {
    return !!this.pending;
  }
}
