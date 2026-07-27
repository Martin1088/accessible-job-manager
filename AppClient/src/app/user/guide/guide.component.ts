import { CommonModule } from '@angular/common';
import { Component, ChangeDetectionStrategy } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth.service';

@Component({
  standalone: true,
  selector: 'app-guide',
  imports: [CommonModule, TranslatePipe],
  templateUrl: './guide.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './guide.component.scss'
})
export class GuideComponent {
  constructor(public auth: AuthService) {}
}
