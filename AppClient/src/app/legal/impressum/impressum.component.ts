import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-impressum',
  standalone: true,
  imports: [TranslatePipe, RouterLink],
  templateUrl: './impressum.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './impressum.component.scss'
})
export class ImpressumComponent {}
