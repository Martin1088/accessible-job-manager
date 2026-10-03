import { Component, OnInit, ChangeDetectionStrategy, inject } from '@angular/core';

import { HttpClient } from '@angular/common/http';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DataTableComponent, TableAction, TableColumn } from '../../shared/data-table/data-table.component';
import { RelationshipService } from '../../services/relationship.service';
import { Relationship } from '../../model/relationship';

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

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [DataTableComponent, TranslatePipe],
  templateUrl: './home.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  // Shared documents, grouped by the user who shared them
  users: ReviewerUser[] = [];
  downloading: Record<string, boolean> = {};
  errorMessage = '';

  // Reviewer-kind links: active ones are My Users, requested ones await an answer.
  private links: Relationship[] = [];

  myUserRows: any[] = [];
  myUserColumns: TableColumn[] = [
    { label: 'REVIEWER.COL_USER',      field: 'user',      sortable: true },
    { label: 'REVIEWER.REF_DOCUMENTS', field: 'documents' },
  ];

  requestRows: any[] = [];
  requestError = '';
  requestBusy = false;
  requestColumns: TableColumn[] = [
    { label: 'REVIEWER.COL_USER',          field: 'user',      sortable: true },
    { label: 'REVIEWER.REQ_COL_REQUESTED', field: 'requested', sortable: true },
  ];
  requestActions: TableAction[] = [
    {
      label: 'REVIEWER.REQ_ACCEPT',
      ariaLabel: (row) => this.translate.instant('REVIEWER.REQ_ACCEPT_ARIA', { name: row.user }),
      handler: (row) => this.answerRequest(row, 'accept'),
    },
    {
      label: 'REVIEWER.REQ_DECLINE',
      ariaLabel: (row) => this.translate.instant('REVIEWER.REQ_DECLINE_ARIA', { name: row.user }),
      handler: (row) => this.answerRequest(row, 'decline'),
    },
  ];

  private readonly announcer = inject(LiveAnnouncer);
  private readonly relationships = inject(RelationshipService);

  constructor(private http: HttpClient, private translate: TranslateService) {}

  get userCount(): number {
    return this.myUserRows.length;
  }

  get openRequestCount(): number {
    return this.requestRows.length;
  }

  get documentCount(): number {
    return this.users.reduce((total, user) => total + user.documents.length, 0);
  }

  get asOfDate(): string {
    return new Date().toISOString().slice(0, 10);
  }

  ngOnInit(): void {
    this.loadSharedDocuments();
    this.loadLinks();
  }

  private loadSharedDocuments(): void {
    this.http.get<ReviewerUser[]>('/api/reviewer/users').subscribe({
      next: (users) => {
        this.users = users;
        this.refreshLinkRows();
      },
      error: () => this.errorMessage = this.translate.instant('REVIEWER.ERROR_LOAD_USERS'),
    });
  }

  private loadLinks(): void {
    this.relationships.incoming().subscribe({
      next: (list) => {
        this.links = list.filter(r => r.kind === 'REVIEWER');
        this.refreshLinkRows();
      },
      error: () => this.requestError = this.translate.instant('REVIEWER.ERROR_LOAD_REQUESTS'),
    });
  }

  private refreshLinkRows(): void {
    const sharedCount = new Map(this.users.map(u => [u.userId, u.documents.length]));

    this.myUserRows = this.links
      .filter(r => r.status === 'ACTIVE')
      .map(r => ({ id: r.id, user: r.applicantName, documents: sharedCount.get(r.applicantId) ?? 0 }));

    this.requestRows = this.links
      .filter(r => r.status === 'REQUESTED')
      .map(r => ({ id: r.id, user: r.applicantName, requested: r.createdAt ? r.createdAt.substring(0, 10) : '—' }));
  }

  private answerRequest(row: { id: string; user: string }, outcome: 'accept' | 'decline'): void {
    if (this.requestBusy) {
      return;
    }
    this.requestBusy = true;
    this.requestError = '';

    const call = outcome === 'accept'
      ? this.relationships.accept(row.id)
      : this.relationships.decline(row.id);

    call.subscribe({
      next: () => {
        this.announcer.announce(
          this.translate.instant(
            outcome === 'accept' ? 'REVIEWER.REQ_ACCEPTED' : 'REVIEWER.REQ_DECLINED',
            { name: row.user }),
          'polite');
        this.requestBusy = false;
        this.loadLinks();
      },
      error: () => {
        this.requestError = this.translate.instant('REVIEWER.ERROR_REQUEST_ACTION');
        this.requestBusy = false;
      },
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
}
