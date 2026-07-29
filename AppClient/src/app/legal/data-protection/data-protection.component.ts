import { Component, ChangeDetectionStrategy } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-data-protection',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './data-protection.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './data-protection.component.scss'
})
export class DataProtectionComponent {}
