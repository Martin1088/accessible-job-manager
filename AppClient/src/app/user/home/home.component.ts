import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService, UserMe } from '../../core/auth.service';

@Component({
  selector: 'app-home',
  imports: [RouterLink, CommonModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  profile: UserMe | null = null;
  profileError = false;

  constructor(private auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.auth.me$.subscribe({
      next: (me) => {
        if (!me) { this.profileError = true; return; }
        if (me.groups.includes('ADVISOR'))  { this.router.navigate(['/advisor']);   return; }
        if (me.groups.includes('REVIEWER')) { this.router.navigate(['/reviewer']);  return; }
        this.profile = me;
      },
      error: () => this.profileError = true,
    });
  }
}
