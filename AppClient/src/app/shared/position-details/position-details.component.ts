import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { Company, CompanyLocation, CompanyPosition } from '../../model/company';

export function formatLocation(loc: CompanyLocation): string {
  return [loc.street, loc.postcode, loc.city, loc.country].filter(v => v).join(', ');
}

export function formatContact(position?: CompanyPosition): string {
  if (!position) return '';
  return [position.contactTitle, position.contactLastName].filter(v => v).join(' ');
}

/**
 * One position and its company as a definition list, leaving out every field that is
 * empty - a list of blank labels is noise to read past, and more so with a screen reader.
 * Shared by the Companies and Applications pages so both show the same details.
 */
@Component({
  selector: 'app-position-details',
  imports: [TranslatePipe],
  templateUrl: './position-details.component.html',
  styleUrl: './position-details.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PositionDetailsComponent {
  readonly company = input.required<Company>();
  readonly position = input.required<CompanyPosition>();

  readonly locations = computed(() =>
    this.company().locations.map(formatLocation).filter(v => v));
  readonly contact = computed(() => formatContact(this.position()));
  /** UNKNOWN says nothing the reader can act on, so it is treated as empty. */
  readonly method = computed(() => {
    const m = this.position().applicationMethod;
    return m && m !== 'UNKNOWN' ? m : null;
  });
  readonly addedOn = computed(() => this.position().createdAt?.substring(0, 10) ?? '');
}
