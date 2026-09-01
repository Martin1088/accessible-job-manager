import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth.service';

@Component({
  standalone: true,
  selector: 'app-guide',
  // No in-page links, by design. The sheet is navigated by heading - the screen
  // reader's own heading list, or `h`/rotor - which needs nothing from the
  // template beyond a correct `h1`/`h2`/`h3` order and one `<section>` per
  // heading. A register of `href="#…"` links was tried and removed: under
  // `<base href="/">` a bare fragment resolves to `/#…` and navigates away, and
  // the `routerLink` + `fragment` workaround only ever moved the viewport,
  // leaving the reader's cursor where it was.
  imports: [AsyncPipe, TranslatePipe],
  templateUrl: './guide.component.html',
  // No `changeDetection` here, unlike its siblings: Angular 22 defaults to
  // `OnPush`, and the sheet is static prose whose only moving parts are the
  // `| async` role checks and `| translate`. Both mark the view themselves -
  // `AsyncPipe` on emission, ngx-translate's pipe (`pure: false`) on a language
  // change - so nothing here needs the every-cycle checking that
  // `ChangeDetectionStrategy.Eager` buys. The siblings still name `Eager`
  // because that is what the v22 rename of `Default` left behind, not because
  // they were each judged to need it.
  styleUrl: './guide.component.scss'
})
export class GuideComponent {
  readonly auth = inject(AuthService);
}
