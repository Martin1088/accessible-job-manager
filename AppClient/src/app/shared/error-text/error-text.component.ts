import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { HttpFailure } from '../../core/http-error';

/**
 * The one place a failed request turns into a sentence.
 *
 * Renders inline text only - no element of its own to style - so it drops into
 * whatever alert container the caller already has and inherits its `role` and
 * its `.notice` styling. The caller keeps the live region; this only decides
 * what that region says.
 *
 * `fallbackKey` is the caller's own "this particular thing did not work"
 * wording, and is deliberately reached only when the server errored without
 * saying why. A request that never arrived, or a session that expired, has a
 * cause of its own that no per-endpoint sentence describes - conflating those
 * with "check the URL and try again" is the bug this component exists to stop.
 *
 * Branching happens here in the template rather than by resolving a string in
 * TypeScript, so the text follows a language switch like every other label.
 */
@Component({
  selector: 'app-error-text',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './error-text.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class ErrorTextComponent {
  @Input() failure: HttpFailure | null = null;

  /** Translation key for the caller's own wording, used when the server gave no reason. */
  @Input() fallbackKey = '';
}
