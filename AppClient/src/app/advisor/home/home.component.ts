import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
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

const STATUS_LABELS: Record<string, string> = {
  PENDING:  'Pending',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
};

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule, DataTableComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {

  // My Users
  userRows: any[] = [];
  userColumns: TableColumn[] = [
    { label: 'Name',  field: 'name',  sortable: true },
    { label: 'Email', field: 'email', sortable: true },
  ];

  // Suggestion form
  assignedUsers: AdvisorUser[] = [];
  positionOptions: PositionOption[] = [];
  form = { targetUserId: '', companyPositionId: '' as unknown as number, message: '' };
  formError = '';
  formSuccess = false;
  submitting = false;

  // Suggestions overview
  suggestionRows: any[] = [];
  suggestionColumns: TableColumn[] = [
    { label: 'User',     field: 'user',     sortable: true },
    { label: 'Company',  field: 'company',  sortable: true },
    { label: 'Position', field: 'position', sortable: true },
    { label: 'Status',   field: 'status',   sortable: true },
    { label: 'Date',     field: 'date',     sortable: true },
  ];

  errorMessage = '';

  constructor(private http: HttpClient) {}

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
      error: () => this.errorMessage = 'Failed to load users.',
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
        this.suggestionRows = list.map(s => ({
          user:     s.targetUserName,
          company:  s.companyName,
          position: s.positionTitle,
          status:   STATUS_LABELS[s.status] ?? s.status,
          date:     s.createdAt ? s.createdAt.substring(0, 10) : '—',
        }));
      },
      error: () => this.errorMessage = 'Failed to load suggestions.',
    });
  }

  submitSuggestion(): void {
    this.formError = '';
    this.formSuccess = false;

    if (!this.form.targetUserId || !this.form.companyPositionId) {
      this.formError = 'Please select a user and a position.';
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
        this.formError = 'Failed to create suggestion. Please try again.';
        this.submitting = false;
      },
    });
  }
}
