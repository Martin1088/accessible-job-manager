import { Component, OnInit, ChangeDetectionStrategy, ElementRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { forkJoin } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth.service';
import { UserProfileService } from '../../services/user-profile.service';
import { UserProfile } from '../../model/user-profile';
import { PreferencesService } from '../../services/preferences.service';
import { ExportFormat, ExportService } from '../../services/export.service';
import { LanguageService } from '../../core/language.service';
import { uiToLetterLanguage } from '../../model/document';
import { saveBlobResponse } from '../../core/file-download';
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

/**
 * The profile form. Its six fields are the sender block of every letter, maintained
 * once here so no cover letter form has to ask for a postal address again.
 *
 * Below the form, a user manages their links to advisors and reviewers: the
 * directory lists everyone who holds one of those roles, and a request goes out
 * from here for the counterpart to accept on their own dashboard.
 */
@Component({
  standalone: true,
  selector: 'app-profile',
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe, DataTableComponent],
  templateUrl: './profile.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './profile.component.scss'
})
export class ProfileComponent implements OnInit {
  @ViewChild('errorSummary') errorSummary?: ElementRef<HTMLElement>;

  private readonly fb = inject(FormBuilder);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly translate = inject(TranslateService);
  private readonly profiles = inject(UserProfileService);
  private readonly preferences = inject(PreferencesService);
  private readonly exports = inject(ExportService);
  private readonly language = inject(LanguageService);
  private readonly relationships = inject(RelationshipService);
  private readonly directory = inject(DirectoryService);
  readonly auth = inject(AuthService);

  profile: UserProfile | null = null;
  loadError = false;
  saveError = false;
  saving = false;
  submitted = false;

  /** Which export is in flight, so only the button pressed shows its busy label. */
  exporting: ExportFormat | null = null;
  exportError = false;

  /** Advisor/reviewer directory + the caller's links, shown only to users. */
  readonly isUser$ = this.auth.isUser$;
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

  readonly form = this.fb.group({
    name: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    street: ['', Validators.required],
    postalCode: ['', Validators.required],
    city: ['', Validators.required],
    phone: [''],
  });

  constructor() {
    // The status and role cells are translated up front, so a language switch
    // has to rebuild them - the same reason the advisor dashboard rebuilds its
    // rows on this event.
    this.translate.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.network) {
        this.networkRows = this.toRows(this.network);
      }
    });
  }

  ngOnInit(): void {
    this.profiles.get().subscribe({
      next: (profile) => this.applyProfile(profile),
      error: () => this.loadError = true,
    });

    this.auth.me$.subscribe(me => {
      if (me?.roles?.includes('USER')) {
        this.loadNetwork();
      }
    });
  }

  private applyProfile(profile: UserProfile): void {
    this.profile = profile;
    // Seeds the shared preferences cache from the response this page already fetched,
    // rather than depending on AccessibilityService's bootstrap-time load having run.
    this.preferences.seed(profile.preferences);
    this.form.patchValue({
      name: profile.name ?? '',
      email: profile.email ?? '',
      street: profile.street ?? '',
      postalCode: profile.postalCode ?? '',
      city: profile.city ?? '',
      phone: profile.phone ?? '',
    });
  }

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
        this.networkRows = this.toRows(data);
        this.networkLoading = false;
      },
      error: () => {
        this.networkError = true;
        this.networkLoading = false;
      },
    });
  }

  private toRows(data: NetworkData): NetworkRow[] {
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
          this.networkRows = this.toRows(this.network);
        }
        this.networkBusy = false;
      },
      error: () => {
        this.networkActionError = true;
        this.networkBusy = false;
      },
    });
  }

  save(): void {
    this.submitted = true;
    this.saveError = false;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorSummary?.nativeElement.focus();
      return;
    }

    this.saving = true;
    this.profiles.update(this.form.getRawValue()).subscribe({
      next: (profile) => {
        this.applyProfile(profile);
        this.saving = false;
        this.submitted = false;
        this.announce('PROFILE.SAVED');
      },
      error: () => {
        this.saveError = true;
        this.saving = false;
      },
    });
  }

  /**
   * Downloads the caller's companies, positions and applications as one spreadsheet.
   * The column headers are written in the language the UI is being read in - the page
   * offers no separate picker for it, since a second language choice here would only
   * ever be answered with the one already made in the header.
   */
  exportData(format: ExportFormat): void {
    if (this.exporting) {
      return;
    }
    this.exportError = false;
    this.exporting = format;

    this.exports.exportCompanies(format, uiToLetterLanguage(this.language.current())).subscribe({
      next: (response) => {
        saveBlobResponse(response, format === 'CSV' ? 'companies-export.csv' : 'companies-export.xlsx');
        this.exporting = null;
        this.announce('PROFILE.EXPORT_STARTED');
      },
      error: () => {
        this.exportError = true;
        this.exporting = null;
      },
    });
  }

  invalid(path: string): boolean {
    const control = this.form.get(path);
    return !!control && control.invalid && (control.touched || this.submitted);
  }

  /** Never names an element that is absent: a dangling aria-describedby is dropped. */
  describedBy(path: string, ...ids: string[]): string | null {
    const present = ids.filter(id => id.endsWith('-error') ? this.invalid(path) : true);
    return present.length ? present.join(' ') : null;
  }

  get invalidFields(): { path: string; id: string; label: string }[] {
    const candidates = [
      { path: 'name', id: 'profile-name', label: 'PROFILE.NAME' },
      { path: 'email', id: 'profile-email', label: 'PROFILE.EMAIL' },
      { path: 'street', id: 'profile-street', label: 'PROFILE.STREET' },
      { path: 'postalCode', id: 'profile-postal-code', label: 'PROFILE.POSTAL_CODE' },
      { path: 'city', id: 'profile-city', label: 'PROFILE.CITY' },
    ];
    return candidates.filter(field => this.invalid(field.path));
  }

  private announce(key: string, params?: Record<string, unknown>): void {
    this.announcer.announce(this.translate.instant(key, params), 'polite');
  }
}
