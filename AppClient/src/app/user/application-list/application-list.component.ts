import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Application } from '../../model/application';
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
  imports: [CommonModule, DataTableComponent],
  templateUrl: './application-list.component.html',
  styleUrl: './application-list.component.scss',
})
export class ApplicationListComponent implements OnInit {

  rows: any[] = [];
  errorMessage = '';

  columns: TableColumn[] = [
    { label: 'Company',   field: 'companyName',    sortable: true },
    { label: 'Position',  field: 'positionTitle',  sortable: true },
    { label: 'Status',    field: 'statusLabel',    sortable: true },
    { label: 'Applied',   field: 'appliedDate',    sortable: true },
    { label: 'Notes',     field: 'notes',          sortable: false },
  ];

  constructor(private applicationService: ApplicationService) {}

  ngOnInit(): void {
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
