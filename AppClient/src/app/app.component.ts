import {Router, RouterLink, RouterOutlet} from '@angular/router';
import {catchError, firstValueFrom, Observable, of, throwError} from 'rxjs';
import {HttpClient} from '@angular/common/http';
import {AsyncPipe, JsonPipe, NgIf} from '@angular/common';
import {Component, inject} from '@angular/core';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, JsonPipe, NgIf, AsyncPipe],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  private router = inject(Router);
}
