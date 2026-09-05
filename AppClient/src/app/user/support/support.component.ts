import { Component, OnInit, ChangeDetectionStrategy, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { forkJoin } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { DataTableComponent, TableColumn, TableAction } from '../../shared/data-table/data-table.component';
import { RelationshipService } from '../../services/relationship.service';
import { DirectoryService } from '../../services/directory.service';
import { DirectoryPerson, Relationship, RelationshipKind } from '../../model/relationship';

interface NetworkData {
  advisors: DirectoryPerson[];
  reviewers: DirectoryPerson[];
  relationships: Relationship[];
}

interface NetworkRow {
  name: string;
  email: string;
  role: string;
  status: string;
  // Carried for the row actions, not shown as columns.
  personId: string;
  kind: RelationshipKind;
  relationshipId: string | null;
  canRequest: boolean;
  canEnd: boolean;
}

const ROLE_KEY: Record<RelationshipKind, string> = {
  ADVISOR: 'PROFILE.ROLE_ADVISOR',
  REVIEWER: 'PROFILE.ROLE_REVIEWER',
};

type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

interface SuggestionDto {
  id: number;
  advisorName: string;
  companyName: string;
  positionTitle: string;
  message: string;
  status: SuggestionStatus;
  createdAt: string;
}

interface SuggestionRow {
  id: number;
  advisor: string;
  company: string;
  position: string;
  message: string;
  status: string;
  date: string;
  canAnswer: boolean;
}

/**
 * Where a user manages their links to advisors and reviewers, and sees the
 * postings an advisor has suggested for them - split out of the profile form
 * (which is the sender block for a letter) into a page of its own, since
 * neither of these is about the account's own data.
 */
@Component({
  standalone: true,
  selector: 'app-support',
  imports: [TranslatePipe, DataTableComponent],
  templateUrl: './support.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './support.component.scss'
})
export class SupportComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly translate = inject(TranslateService);
  private readonly relationships = inject(RelationshipService);
  private readonly directory = inject(DirectoryService);

  // --- Advisor/reviewer network -----------------------------------------
  networkLoading = false;
  networkError = false;
  networkActionError = false;
  networkBusy = false;
  networkRows: NetworkRow[] = [];
  private network: NetworkData | null = null;

  readonly networkColumns: TableColumn[] = [
    { label: 'PROFILE.REL_COL_NAME',   field: 'name',   sortable: true },
    { label: 'PROFILE.REL_COL_EMAIL',  field: 'email',  sortable: true },
    { label: 'PROFILE.REL_COL_ROLE',   field: 'role',   sortable: true },
    { label: 'PROFILE.REL_COL_STATUS', field: 'status', sortable: true },
  ];

  readonly networkActions: TableAction[] = [
    {
      label: 'PROFILE.REL_ACTION_REQUEST',
      ariaLabel: (row: NetworkRow) =>
        this.translate.instant('PROFILE.REL_ACTION_REQUEST_ARIA', { name: row.name, role: row.role }),
      handler: (row: NetworkRow) => this.requestLink(row),
      visible: (row: NetworkRow) => row.canRequest,
    },
    {
      label: 'PROFILE.REL_ACTION_END',
      ariaLabel: (row: NetworkRow) =>
        this.translate.instant('PROFILE.REL_ACTION_END_ARIA', { name: row.name }),
      handler: (row: NetworkRow) => this.endLink(row),
      visible: (row: NetworkRow) => row.canEnd,
    },
  ];

  // --- Suggested postings -------------------------------------------------
  suggestionsLoading = false;
  suggestionsError = false;
  suggestionsActionError = false;
  suggestionsBusy = false;
  suggestionRows: SuggestionRow[] = [];
  private suggestions: SuggestionDto[] = [];

  readonly suggestionColumns: TableColumn[] = [
    { label: 'SUPPORT.COL_ADVISOR', field: 'advisor', sortable: true },
    { label: 'ADVISOR.COL_COMPANY', field: 'company', sortable: true },
    { label: 'ADVISOR.COL_POSITION', field: 'position', sortable: true },
    { label: 'SUPPORT.COL_MESSAGE', field: 'message', sortable: false },
    { label: 'ADVISOR.COL_STATUS', field: 'status', sortable: true },
    { label: 'ADVISOR.COL_DATE', field: 'date', sortable: true },
  ];

  readonly suggestionActions: TableAction[] = [
    {
      label: 'SUPPORT.ACTION_ACCEPT',
      ariaLabel: (row: SuggestionRow) =>
        this.translate.instant('SUPPORT.ACTION_ACCEPT_ARIA', { position: row.position, company: row.company }),
      handler: (row: SuggestionRow) => this.answerSuggestion(row, 'ACCEPTED'),
      visible: (row: SuggestionRow) => row.canAnswer,
    },
    {
      label: 'SUPPORT.ACTION_DECLINE',
      ariaLabel: (row: SuggestionRow) =>
        this.translate.instant('SUPPORT.ACTION_DECLINE_ARIA', { position: row.position, company: row.company }),
      handler: (row: SuggestionRow) => this.answerSuggestion(row, 'REJECTED'),
      visible: (row: SuggestionRow) => row.canAnswer,
    },
  ];

  constructor() {
    // The status and role cells are translated up front, so a language switch
    // has to rebuild them - same reason the advisor dashboard rebuilds its rows.
    this.translate.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.network) {
        this.networkRows = this.toNetworkRows(this.network);
      }
      this.suggestionRows = this.toSuggestionRows(this.suggestions);
    });
  }

  ngOnInit(): void {
    this.loadNetwork();
    this.loadSuggestions();
  }

  // --- Network --------------------------------------------------------

  private loadNetwork(): void {
    this.networkLoading = true;
    this.networkError = false;
    forkJoin({
      advisors: this.directory.advisors(),
      reviewers: this.directory.reviewers(),
      relationships: this.relationships.mine(),
    }).subscribe({
      next: (data) => {
        this.network = data;
        this.networkRows = this.toNetworkRows(data);
        this.networkLoading = false;
      },
      error: () => {
        this.networkError = true;
        this.networkLoading = false;
      },
    });
  }

  private toNetworkRows(data: NetworkData): NetworkRow[] {
    const people: { person: DirectoryPerson; kind: RelationshipKind }[] = [
      ...data.advisors.map(person => ({ person, kind: 'ADVISOR' as const })),
      ...data.reviewers.map(person => ({ person, kind: 'REVIEWER' as const })),
    ];

    return people.map(({ person, kind }) => {
      const link = this.currentLink(data.relationships, person.userId, kind);
      return {
        name: person.name,
        email: person.email,
        role: this.translate.instant(ROLE_KEY[kind]),
        status: link
          ? this.translate.instant('PROFILE.REL_STATUS_' + link.status)
          : this.translate.instant('PROFILE.REL_STATUS_NONE'),
        personId: person.userId,
        kind,
        relationshipId: link?.id ?? null,
        // A declined or ended link can be asked for again; the backend only
        // blocks a second request while one is REQUESTED or ACTIVE.
        canRequest: !link || link.status === 'DECLINED' || link.status === 'ENDED',
        canEnd: link?.status === 'ACTIVE',
      };
    });
  }

  /** The link that decides this person's row: a live one if there is one, else the most recent. */
  private currentLink(links: Relationship[], personId: string, kind: RelationshipKind): Relationship | null {
    const forPerson = links
      .filter(link => link.counterpartId === personId && link.kind === kind)
      .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
    return forPerson.find(link => link.status === 'REQUESTED' || link.status === 'ACTIVE')
      ?? forPerson[0]
      ?? null;
  }

  requestLink(row: NetworkRow): void {
    if (this.networkBusy) {
      return;
    }
    this.networkBusy = true;
    this.networkActionError = false;
    this.relationships.request(row.personId, row.kind).subscribe({
      next: () => {
        this.announce('PROFILE.REL_REQUEST_SENT', { name: row.name });
        this.reloadLinks();
      },
      error: () => {
        this.networkActionError = true;
        this.networkBusy = false;
      },
    });
  }

  endLink(row: NetworkRow): void {
    if (this.networkBusy || !row.relationshipId) {
      return;
    }
    this.networkBusy = true;
    this.networkActionError = false;
    this.relationships.end(row.relationshipId).subscribe({
      next: () => {
        this.announce('PROFILE.REL_ENDED', { name: row.name });
        this.reloadLinks();
      },
      error: () => {
        this.networkActionError = true;
        this.networkBusy = false;
      },
    });
  }

  /** Re-reads just the links after an action; the directory itself has not changed. */
  private reloadLinks(): void {
    this.relationships.mine().subscribe({
      next: (relationships) => {
        if (this.network) {
          this.network = { ...this.network, relationships };
          this.networkRows = this.toNetworkRows(this.network);
        }
        this.networkBusy = false;
      },
      error: () => {
        this.networkActionError = true;
        this.networkBusy = false;
      },
    });
  }

  // --- Suggested postings ----------------------------------------------

  private loadSuggestions(): void {
    this.suggestionsLoading = true;
    this.suggestionsError = false;
    this.http.get<SuggestionDto[]>('/api/my/suggestions').subscribe({
      next: (list) => {
        this.suggestions = list;
        this.suggestionRows = this.toSuggestionRows(list);
        this.suggestionsLoading = false;
      },
      error: () => {
        this.suggestionsError = true;
        this.suggestionsLoading = false;
      },
    });
  }

  private toSuggestionRows(list: SuggestionDto[]): SuggestionRow[] {
    return list.map(s => ({
      id: s.id,
      advisor: s.advisorName,
      company: s.companyName,
      position: s.positionTitle,
      message: s.message || '—',
      status: this.translate.instant('ADVISOR.STATUS_' + s.status),
      date: s.createdAt ? s.createdAt.substring(0, 10) : '—',
      canAnswer: s.status === 'PENDING',
    }));
  }

  answerSuggestion(row: SuggestionRow, status: 'ACCEPTED' | 'REJECTED'): void {
    if (this.suggestionsBusy) {
      return;
    }
    this.suggestionsBusy = true;
    this.suggestionsActionError = false;
    this.http.patch(`/api/my/suggestions/${row.id}`, { status }).subscribe({
      next: () => {
        this.announce(
          status === 'ACCEPTED' ? 'SUPPORT.SUGGESTION_ACCEPTED' : 'SUPPORT.SUGGESTION_DECLINED',
          { position: row.position, company: row.company });
        this.suggestionsBusy = false;
        this.loadSuggestions();
      },
      error: () => {
        this.suggestionsActionError = true;
        this.suggestionsBusy = false;
      },
    });
  }

  private announce(key: string, params?: Record<string, unknown>): void {
    this.announcer.announce(this.translate.instant(key, params), 'polite');
  }
}
