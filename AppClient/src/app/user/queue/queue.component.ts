import { ChangeDetectionStrategy, Component, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { HttpFailure, describeHttpFailure } from '../../core/http-error';
import { DataTableComponent, TableAction, TableColumn } from '../../shared/data-table/data-table.component';
import { ErrorTextComponent } from '../../shared/error-text/error-text.component';

/** One waiting position, as the queue endpoint returns it. */
export interface QueuedPosition {
  id: number;
  title: string;
  companyId: number;
  companyName: string;
  city: string | null;
  createdAt: string | null;
}

/** A queue entry with the two display-only fields the table renders. */
interface QueueRow extends QueuedPosition {
  city: string;
  found: string;
}

interface TriageResult {
  remaining: number;
}

/**
 * The review queue: everything that has turned up but has not been looked at.
 *
 * <p>Its own route rather than a tab in the company list. Two lists that mean
 * different things in one container cost the reader an orientation step on
 * every visit - "which of the two am I in" is a question a screen reader user
 * answers by reading, not by glancing.
 *
 * The order of what happens after an action is the part that is easy to get
 * wrong and hard to notice:
 *
 * <ol>
 *   <li>Focus moves to the same action in the next row, while that row still
 *       exists.</li>
 *   <li>The acted-on row is removed.</li>
 *   <li>The outcome and the remaining count are announced.</li>
 * </ol>
 *
 * Removing first would drop focus to {@code document.body}: the position in the
 * list is lost, and the next action means finding the table again from the top.
 * That is the failure this component exists to avoid, and what its spec pins
 * down.
 */
@Component({
  selector: 'app-queue',
  standalone: true,
  imports: [DataTableComponent, ErrorTextComponent, TranslatePipe],
  templateUrl: './queue.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './queue.component.scss'
})
export class QueueComponent implements OnInit {

  rows: QueueRow[] = [];
  loadFailure: HttpFailure | null = null;
  actionFailure: HttpFailure | null = null;

  /** Where focus goes when the row acted on was the last one. */
  @ViewChild('heading') heading!: ElementRef<HTMLHeadingElement>;

  private readonly http = inject(HttpClient);
  private readonly translate = inject(TranslateService);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly host = inject(ElementRef);

  readonly columns: TableColumn[] = [
    { label: 'QUEUE.COLUMN_POSITION', field: 'title', sortable: true },
    { label: 'QUEUE.COLUMN_COMPANY', field: 'companyName', sortable: true },
    { label: 'QUEUE.COLUMN_CITY', field: 'city', sortable: true },
    { label: 'QUEUE.COLUMN_FOUND', field: 'found', sortable: true },
  ];

  readonly actions: TableAction[] = [
    {
      name: 'accept',
      label: 'QUEUE.ACTION_ACCEPT',
      // Named actions on their own ("Accept", four times over) leave the reader
      // to work out which row they are in from context that is no longer there
      // once the buttons are tabbed through.
      ariaLabel: (row) => this.translate.instant('QUEUE.ACTION_ACCEPT_ARIA',
        { position: row.title, company: row.companyName }),
      handler: (row) => this.triage(row, 'accept'),
    },
    {
      name: 'dismiss',
      label: 'QUEUE.ACTION_DISMISS',
      ariaLabel: (row) => this.translate.instant('QUEUE.ACTION_DISMISS_ARIA',
        { position: row.title, company: row.companyName }),
      handler: (row) => this.triage(row, 'dismiss'),
    },
  ];

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.http.get<QueuedPosition[]>('/api/positions/queue').subscribe({
      next: (queue) => {
        this.loadFailure = null;
        this.rows = queue.map(entry => ({
          ...entry,
          city: entry.city || '—',
          found: entry.createdAt ? entry.createdAt.substring(0, 10) : '—',
        }));
      },
      error: (error) => this.loadFailure = describeHttpFailure(error),
    });
  }

  private triage(row: QueueRow, action: 'accept' | 'dismiss'): void {
    this.http.post<TriageResult>(`/api/positions/${row.id}/${action}`, null).subscribe({
      next: (result) => {
        this.actionFailure = null;
        this.moveFocusOn(row, action);
        this.rows = this.rows.filter(r => r.id !== row.id);
        this.announcer.announce(this.translate.instant(
          action === 'accept' ? 'QUEUE.ANNOUNCE_ACCEPTED' : 'QUEUE.ANNOUNCE_DISMISSED',
          { remaining: result.remaining }), 'polite');
      },
      // The row stays where it is on failure - removing it would claim the
      // decision was recorded.
      error: (error) => this.actionFailure = describeHttpFailure(error),
    });
  }

  /**
   * Focus follows the list, not the DOM's default. The next row is taken from
   * the rendered table rather than from `rows`, because the table sorts its
   * own copy: after sorting by company, the row below is not the next entry in
   * this component's array.
   */
  private moveFocusOn(row: QueueRow, action: 'accept' | 'dismiss'): void {
    const element = this.host.nativeElement as HTMLElement;
    const button = element.querySelector<HTMLElement>(`tr[data-row-id="${row.id}"] [data-action="${action}"]`);
    const next = button?.closest('tr')?.nextElementSibling
      ?.querySelector<HTMLElement>(`[data-action="${action}"]`);

    (next ?? this.heading?.nativeElement)?.focus();
  }
}
