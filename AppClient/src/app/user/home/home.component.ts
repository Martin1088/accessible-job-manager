import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';

import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService, UserMe } from '../../core/auth.service';

@Component({
  selector: 'app-home',
  imports: [RouterLink, FormsModule, TranslatePipe],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  profile: UserMe | null = null;
  profileError = false;

  jobUrl = '';

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

  searchJobPosting(): void {
    // TODO: fetch and parse the job posting at this.jobUrl
  }

  submitAsCompany(): void {
    // TODO: navigate to /companies/new prefilled from the parsed posting
  }
}
