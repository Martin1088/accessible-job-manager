import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';

import { HttpClient, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService, UserMe } from '../../core/auth.service';

@Component({
  selector: 'app-home',
  imports: [RouterLink, FormsModule, TranslatePipe],
  templateUrl: './home.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  profile: UserMe | null = null;
  profileError = false;

  jobUrl = '';

  senderName = '';
  senderStreet = '';
  senderPostalCode = '';
  senderCity = '';
  senderEmail = '';
  downloadingTemplate = false;
  templateError = false;

  constructor(private auth: AuthService, private router: Router, private http: HttpClient) {}

  ngOnInit(): void {
    this.auth.me$.subscribe({
      next: (me) => {
        if (!me) { this.profileError = true; return; }
        if (me.groups.includes('ADVISOR'))  { this.router.navigate(['/advisor']);   return; }
        if (me.groups.includes('REVIEWER')) { this.router.navigate(['/reviewer']);  return; }
        this.profile = me;
        this.senderName = me.name ?? '';
        this.senderEmail = me.email ?? '';
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

  get templateFormValid(): boolean {
    return !!(this.senderName.trim() && this.senderStreet.trim()
      && this.senderPostalCode.trim() && this.senderCity.trim() && this.senderEmail.trim());
  }

  downloadTemplate(): void {
    if (!this.templateFormValid) return;
    this.templateError = false;
    this.downloadingTemplate = true;
    const params = new HttpParams()
      .set('senderName', this.senderName.trim())
      .set('senderStreet', this.senderStreet.trim())
      .set('senderPostalCode', this.senderPostalCode.trim())
      .set('senderCity', this.senderCity.trim())
      .set('senderEmail', this.senderEmail.trim());

    this.http.post('/api/cover-letter/personalize', null, { params, responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = 'Anschreiben_personal.docx'; a.click();
        URL.revokeObjectURL(url);
        this.downloadingTemplate = false;
      },
      error: () => {
        this.templateError = true;
        this.downloadingTemplate = false;
      },
    });
  }
}
