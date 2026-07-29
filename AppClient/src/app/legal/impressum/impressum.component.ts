import { Component, ChangeDetectionStrategy } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-impressum',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './impressum.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './impressum.component.scss'
})
export class ImpressumComponent {}
