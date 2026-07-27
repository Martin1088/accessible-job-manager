import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DataTableComponent, TableColumn } from '../../shared/data-table/data-table.component';
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
  imports: [FormsModule, DataTableComponent, TranslatePipe],
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

  constructor(private http: HttpClient, private translate: TranslateService) {
    this.translate.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => {
      this.suggestionRows = this.toSuggestionRows(this.suggestions);
    });
  }

  ngOnInit(): void {
    this.loadMyUsers();
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
