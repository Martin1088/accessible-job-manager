import { Component, OnInit, ChangeDetectionStrategy, inject } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

interface SharedDocument {
  id: string;
  label: string;
  filename: string;
  type: string;
  grantedAt: string;
}

interface ReviewerUser {
  userId: string;
  name: string;
  email: string;
  documents: SharedDocument[];
}

/**
 * The reviewer's working page: every document they can review, with a download and,
 * per document, an upload of their own .docx feedback - shared back to that document's
 * owner as a new, separate document (`POST /api/reviewer/documents/{id}/review`).
 * The dashboard-style overview (`reviewer/home`) keeps its own read-only copy of this
 * list; this page is where the reviewing actually happens.
 */
@Component({
  selector: 'app-reviewer-documents',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './documents.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './documents.component.scss',
})
export class ReviewerDocumentsComponent implements OnInit {
  users: ReviewerUser[] = [];
  downloading: Record<string, boolean> = {};
  errorMessage = '';

  reviewTarget: SharedDocument | null = null;
  pendingReviewFile: File | null = null;
  pendingReviewLabel = '';
  showReviewForm = false;
  reviewUploading = false;
  reviewError = '';

  private readonly announcer = inject(LiveAnnouncer);

  constructor(private http: HttpClient, private translate: TranslateService) {}

  get documentCount(): number {
    return this.users.reduce((total, user) => total + user.documents.length, 0);
  }

  ngOnInit(): void {
    this.loadSharedDocuments();
  }

  private loadSharedDocuments(): void {
    this.http.get<ReviewerUser[]>('/api/reviewer/users').subscribe({
      next: (users) => this.users = users,
      error: () => this.errorMessage = this.translate.instant('REVIEWER.ERROR_LOAD_USERS'),
    });
  }

  download(doc: SharedDocument): void {
    this.downloading[doc.id] = true;
    this.http.get(`/api/reviewer/documents/${doc.id}/download`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = doc.filename; a.click();
        URL.revokeObjectURL(url);
        this.downloading[doc.id] = false;
      },
      error: () => {
        this.errorMessage = this.translate.instant('REVIEWER.ERROR_DOWNLOAD');
        this.downloading[doc.id] = false;
      },
    });
  }

  /** Remembers which document the shared hidden file input's next selection is for. */
  beginReview(doc: SharedDocument): void {
    this.reviewTarget = doc;
  }

  onReviewFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this.reviewTarget) return;

    this.pendingReviewFile = file;
    this.pendingReviewLabel = this.translate.instant('REVIEWER.REVIEW_LABEL_SUGGESTED', { label: this.reviewTarget.label });
    this.reviewError = '';
    this.showReviewForm = true;
  }

  confirmReviewUpload(): void {
    if (!this.reviewTarget || !this.pendingReviewFile || !this.pendingReviewLabel.trim()) return;

    const target = this.reviewTarget;
    const ownerName = this.ownerNameFor(target);
    this.reviewUploading = true;
    this.reviewError = '';

    const formData = new FormData();
    formData.append('file', this.pendingReviewFile);
    formData.append('label', this.pendingReviewLabel.trim());

    this.http.post(`/api/reviewer/documents/${target.id}/review`, formData).subscribe({
      next: () => {
        this.announcer.announce(
          this.translate.instant('REVIEWER.REVIEW_SHARED', { name: ownerName }), 'polite');
        this.reviewUploading = false;
        this.cancelReviewUpload();
        this.loadSharedDocuments();
      },
      error: () => {
        this.reviewUploading = false;
        this.reviewError = this.translate.instant('REVIEWER.ERROR_REVIEW_UPLOAD');
      },
    });
  }

  cancelReviewUpload(): void {
    this.reviewTarget = null;
    this.pendingReviewFile = null;
    this.pendingReviewLabel = '';
    this.showReviewForm = false;
    this.reviewError = '';
  }

  private ownerNameFor(doc: SharedDocument): string {
    return this.users.find(u => u.documents.some(d => d.id === doc.id))?.name ?? '';
  }
}
