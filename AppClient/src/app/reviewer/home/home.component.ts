import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

interface SharedDocument {
  id: string;
  label: string;
  filename: string;
  type: string;
  grantedAt: string;
}

interface ReviewerUser {
  userId: string;
  name: string;
  email: string;
  documents: SharedDocument[];
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  users: ReviewerUser[] = [];
  downloading: Record<string, boolean> = {};
  errorMessage = '';

  constructor(private http: HttpClient, private translate: TranslateService) {}

  ngOnInit(): void {
    this.http.get<ReviewerUser[]>('/api/reviewer/users').subscribe({
      next: (users) => this.users = users,
      error: () => this.errorMessage = this.translate.instant('REVIEWER.ERROR_LOAD_USERS'),
    });
  }

  download(doc: SharedDocument): void {
    this.downloading[doc.id] = true;
    this.http.get(`/api/reviewer/documents/${doc.id}/download`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = doc.filename; a.click();
        URL.revokeObjectURL(url);
        this.downloading[doc.id] = false;
      },
      error: () => {
        this.errorMessage = this.translate.instant('REVIEWER.ERROR_DOWNLOAD');
        this.downloading[doc.id] = false;
      },
    });
  }
}
