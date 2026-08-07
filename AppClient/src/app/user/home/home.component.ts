import { Component, OnInit, ChangeDetectionStrategy, ElementRef, HostListener, ViewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { HttpClient, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService, UserMe } from '../../core/auth.service';

interface JobPostingExtraction {
  title: string | null;
  company: string | null;
  location: string | null;
  employmentType: string | null;
}

@Component({
  selector: 'app-home',
  imports: [RouterLink, FormsModule, TranslatePipe],
  templateUrl: './home.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  @ViewChild('jobUrlInput') jobUrlInput?: ElementRef<HTMLInputElement>;

  // Same platform gating as AppComponent's global shortcuts: Windows screen readers
  // reserve bare letters for their own browse-mode navigation.
  readonly isWindows = /Win/i.test(navigator.userAgent);

  profile: UserMe | null = null;
  profileError = false;

  jobUrl = '';
  searchingJobPosting = false;
  jobSearchError = false;
  jobPosting: JobPostingExtraction | null = null;

  senderName = '';
  senderStreet = '';
  senderPostalCode = '';
  senderCity = '';
  senderEmail = '';
  downloadingTemplate = false;
  templateError = false;

  constructor(private auth: AuthService, private router: Router, private http: HttpClient) {}

  // Page-scoped shortcut: jump focus into the job-posting search field.
  // Distinct from AppComponent's route-navigation shortcuts.
  @HostListener('document:keydown', ['$event'])
  onKeydown(e: KeyboardEvent): void {
    const tag = (e.target as HTMLElement).tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
    if (!this.jobUrlInput) return;

    if (this.isWindows) {
      if (!e.altKey || !e.shiftKey || e.ctrlKey || e.metaKey) return;
    } else {
      if (e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return;
    }

    if (e.key.toLowerCase() === 's') {
      e.preventDefault();
      this.jobUrlInput.nativeElement.focus();
    }
  }

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
    if (!this.jobUrl.trim()) return;
    this.searchingJobPosting = true;
    this.jobSearchError = false;
    this.jobPosting = null;
    const params = new HttpParams().set('url', this.jobUrl.trim());

    this.http.post<JobPostingExtraction>('/api/posting/overview', null, { params }).subscribe({
      next: (result) => {
        this.jobPosting = result;
        this.searchingJobPosting = false;
      },
      error: () => {
        this.jobSearchError = true;
        this.searchingJobPosting = false;
      },
    });
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
