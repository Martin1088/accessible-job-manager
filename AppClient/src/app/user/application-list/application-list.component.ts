import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Application, ApplicationRequest, ApplicationStatus } from '../../model/application';
import { ApplicationService } from '../../services/application.service';
import { DataTableComponent, TableColumn } from '../../shared/data-table/data-table.component';

const STATUS_LABELS: Record<string, string> = {
  DRAFT:                'Draft',
  SENT:                 'Sent',
  INTERVIEW_SCHEDULED:  'Interview scheduled',
  INTERVIEW_DONE:       'Interview done',
  OFFER_RECEIVED:       'Offer received',
  ACCEPTED:             'Accepted',
  REJECTED:             'Rejected',
  WITHDRAWN:            'Withdrawn',
};

@Component({
  selector: 'app-application-list',
  standalone: true,
  imports: [CommonModule, FormsModule, DataTableComponent],
  templateUrl: './application-list.component.html',
  styleUrl: './application-list.component.scss',
})
export class ApplicationListComponent implements OnInit {

  rows: any[] = [];
  errorMessage = '';
  submitting = false;

  // pre-filled create form (shown when navigating from company list)
  newForm: { positionId: number | null; companyName: string; positionTitle: string; status: ApplicationStatus; appliedDate: string; notes: string } = {
    positionId:    null,
    companyName:   '',
    positionTitle: '',
    status:        'DRAFT',
    appliedDate:   '',
    notes:         '',
  };
  showForm = false;

  readonly statusOptions: ApplicationStatus[] = [
    'DRAFT', 'SENT', 'INTERVIEW_SCHEDULED', 'INTERVIEW_DONE',
    'OFFER_RECEIVED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN',
  ];

  columns: TableColumn[] = [
    { label: 'Company',   field: 'companyName',    sortable: true },
    { label: 'Position',  field: 'positionTitle',  sortable: true },
    { label: 'Status',    field: 'statusLabel',    sortable: true },
    { label: 'Applied',   field: 'appliedDate',    sortable: true },
    { label: 'Notes',     field: 'notes',          sortable: false },
  ];

  constructor(
    private applicationService: ApplicationService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      if (params['positionId']) {
        this.newForm.positionId    = +params['positionId'];
        this.newForm.companyName   = params['companyName'] ?? '';
        this.newForm.positionTitle = params['positionTitle'] ?? '';
        this.newForm.notes         = `${this.newForm.companyName} – ${this.newForm.positionTitle}`;
        this.showForm = true;
      }
    });
    this.loadApplications();
  }

  submit(): void {
    if (!this.newForm.positionId) return;
    const req: ApplicationRequest = {
      companyPositionId: this.newForm.positionId,
      status:      this.newForm.status,
      appliedDate: this.newForm.appliedDate || null,
      notes:       this.newForm.notes || null,
    };
    this.submitting = true;
    this.applicationService.create(req).subscribe({
      next: () => {
        this.submitting = false;
        this.showForm = false;
        this.router.navigate([], { queryParams: {} });
        this.loadApplications();
      },
      error: () => {
        this.submitting = false;
        this.errorMessage = 'Failed to create application.';
      },
    });
  }

  cancel(): void {
    this.showForm = false;
    this.router.navigate([], { queryParams: {} });
  }

  statusLabel(s: string): string {
    return STATUS_LABELS[s] ?? s;
  }

  private loadApplications(): void {
    this.applicationService.getAll().subscribe({
      next: (data) => this.rows = this.toRows(data),
      error: () => this.errorMessage = 'Failed to load applications.',
    });
  }

  private toRows(applications: Application[]): any[] {
    return applications.map(a => ({
      id:            a.id,
      companyName:   a.companyName,
      positionTitle: a.positionTitle,
      statusLabel:   STATUS_LABELS[a.status] ?? a.status,
      appliedDate:   a.appliedDate ?? '—',
      notes:         a.notes ?? '',
    }));
  }
}
