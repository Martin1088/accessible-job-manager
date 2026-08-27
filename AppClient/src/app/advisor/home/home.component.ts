import { Component, OnInit, ChangeDetectionStrategy, inject } from '@angular/core';

import { AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DataTableComponent, TableColumn, TableAction } from '../../shared/data-table/data-table.component';
import { AuthService } from '../../core/auth.service';
import { RelationshipService } from '../../services/relationship.service';
import { Relationship } from '../../model/relationship';
import { Company } from '../../model/company';

interface AdvisorUser {
  userId: string;
  name: string;
  email: string;
}

interface SuggestionDto {
  id: number;
  targetUserName: string;
  companyName: string;
  positionTitle: string;
  message: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
  createdAt: string;
}

interface PositionOption {
  id: number;
  label: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [FormsModule, DataTableComponent, TranslatePipe, AsyncPipe],
  templateUrl: './home.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {

  // My Users
  userRows: any[] = [];
  userColumns: TableColumn[] = [
    { label: 'ADVISOR.COL_NAME',  field: 'name',  sortable: true },
    { label: 'ADVISOR.COL_EMAIL', field: 'email', sortable: true },
  ];

  // Incoming assignment requests: users who asked this advisor to advise them
  // (from the directory on their profile page) and are waiting for an answer.
  private incoming: Relationship[] = [];
  requestRows: any[] = [];
  requestError = '';
  requestBusy = false;
  requestColumns: TableColumn[] = [
    { label: 'ADVISOR.COL_USER',           field: 'user',      sortable: true },
    { label: 'ADVISOR.REQ_COL_REQUESTED',  field: 'requested', sortable: true },
  ];
  requestActions: TableAction[] = [
    {
      label: 'ADVISOR.REQ_ACCEPT',
      ariaLabel: (row) => this.translate.instant('ADVISOR.REQ_ACCEPT_ARIA', { name: row.user }),
      handler: (row) => this.answerRequest(row, 'accept'),
    },
    {
      label: 'ADVISOR.REQ_DECLINE',
      ariaLabel: (row) => this.translate.instant('ADVISOR.REQ_DECLINE_ARIA', { name: row.user }),
      handler: (row) => this.answerRequest(row, 'decline'),
    },
  ];

  // Suggestion form
  assignedUsers: AdvisorUser[] = [];
  positionOptions: PositionOption[] = [];
  form = { targetUserId: '', companyPositionId: '' as unknown as number, message: '' };
  formError = '';
  formSuccess = false;
  submitting = false;

  // Suggestions overview
  private suggestions: SuggestionDto[] = [];
  suggestionRows: any[] = [];
  suggestionColumns: TableColumn[] = [
    { label: 'ADVISOR.COL_USER',     field: 'user',     sortable: true },
    { label: 'ADVISOR.COL_COMPANY',  field: 'company',  sortable: true },
    { label: 'ADVISOR.COL_POSITION', field: 'position', sortable: true },
    { label: 'ADVISOR.COL_STATUS',   field: 'status',   sortable: true },
    { label: 'ADVISOR.COL_DATE',     field: 'date',     sortable: true },
  ];

  errorMessage = '';

  private readonly announcer = inject(LiveAnnouncer);

  constructor(
    private http: HttpClient,
    private translate: TranslateService,
    private relationships: RelationshipService,
    protected auth: AuthService,
  ) {
    this.translate.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => {
      this.suggestionRows = this.toSuggestionRows(this.suggestions);
      this.requestRows = this.toRequestRows(this.incoming);
    });
  }

  /** Reference-line figures: caseload size, suggestions still awaiting a reply, and the date the page was drawn. */
  get userCount(): number {
    return this.assignedUsers.length;
  }

  get openSuggestionCount(): number {
    return this.suggestions.filter(s => s.status === 'PENDING').length;
  }

  get asOfDate(): string {
    return new Date().toISOString().slice(0, 10);
  }

  ngOnInit(): void {
    this.loadMyUsers();
    this.loadRequests();
    this.loadPositions();
    this.loadSuggestions();
  }

  private loadMyUsers(): void {
    this.http.get<AdvisorUser[]>('/api/advisor/my-users').subscribe({
      next: (users) => {
        this.assignedUsers = users;
        this.userRows = users.map(u => ({ name: u.name, email: u.email }));
      },
      error: () => this.errorMessage = this.translate.instant('ADVISOR.ERROR_LOAD_USERS'),
    });
  }

  private loadRequests(): void {
    this.relationships.incoming().subscribe({
      next: (list) => {
        // Only advisor-kind links still awaiting an answer are actionable here.
        this.incoming = list.filter(r => r.kind === 'ADVISOR' && r.status === 'REQUESTED');
        this.requestRows = this.toRequestRows(this.incoming);
      },
      error: () => this.requestError = this.translate.instant('ADVISOR.ERROR_LOAD_REQUESTS'),
    });
  }

  private toRequestRows(list: Relationship[]): any[] {
    return list.map(r => ({
      user:      r.applicantName,
      requested: r.createdAt ? r.createdAt.substring(0, 10) : '—',
      id:        r.id,
    }));
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
            outcome === 'accept' ? 'ADVISOR.REQ_ACCEPTED' : 'ADVISOR.REQ_DECLINED',
            { name: row.user }),
          'polite');
        this.requestBusy = false;
        this.loadRequests();
        // An accepted user now belongs in My Users.
        if (outcome === 'accept') {
          this.loadMyUsers();
        }
      },
      error: () => {
        this.requestError = this.translate.instant('ADVISOR.ERROR_REQUEST_ACTION');
        this.requestBusy = false;
      },
    });
  }

  private loadPositions(): void {
    this.http.get<Company[]>('/api/companies').subscribe({
      next: (companies) => {
        this.positionOptions = companies.flatMap(c =>
          c.positions.map(p => ({
            id: p.id!,
            label: `${c.name} — ${p.title}`,
          }))
        );
      },
    });
  }

  private loadSuggestions(): void {
    this.http.get<SuggestionDto[]>('/api/advisor/suggestions').subscribe({
      next: (list) => {
        this.suggestions = list;
        this.suggestionRows = this.toSuggestionRows(list);
      },
      error: () => this.errorMessage = this.translate.instant('ADVISOR.ERROR_LOAD_SUGGESTIONS'),
    });
  }

  private toSuggestionRows(list: SuggestionDto[]): any[] {
    return list.map(s => ({
      user:     s.targetUserName,
      company:  s.companyName,
      position: s.positionTitle,
      status:   this.translate.instant('ADVISOR.STATUS_' + s.status),
      date:     s.createdAt ? s.createdAt.substring(0, 10) : '—',
    }));
  }

  submitSuggestion(): void {
    this.formError = '';
    this.formSuccess = false;

    if (!this.form.targetUserId || !this.form.companyPositionId) {
      this.formError = this.translate.instant('ADVISOR.ERROR_SELECT_REQUIRED');
      return;
    }

    this.submitting = true;
    this.http.post('/api/advisor/suggestions', {
      targetUserId:      this.form.targetUserId,
      companyPositionId: this.form.companyPositionId,
      message:           this.form.message,
    }).subscribe({
      next: () => {
        this.formSuccess = true;
        this.form = { targetUserId: '', companyPositionId: '' as unknown as number, message: '' };
        this.submitting = false;
        this.loadSuggestions();
      },
      error: () => {
        this.formError = this.translate.instant('ADVISOR.ERROR_CREATE_SUGGESTION');
        this.submitting = false;
      },
    });
  }
}
