import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth.service';
import { DocumentLanguage } from '../../model/document';
import { CoverLetterFormComponent } from '../cover-letter-form/cover-letter-form.component';

// Reuses the LANGUAGE.* UI-language keys (EN/DE/NL), same as the documents list.
const LANGUAGE_KEY: Record<DocumentLanguage, string> = {
  ENGLISH: 'LANGUAGE.EN',
  GERMAN:  'LANGUAGE.DE',
  DUTCH:   'LANGUAGE.NL',
};

@Component({
  selector: 'app-cover-letter-template',
  imports: [FormsModule, TranslatePipe, CoverLetterFormComponent],
  templateUrl: './cover-letter-template.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './cover-letter-template.component.scss'
})
export class CoverLetterTemplateComponent implements OnInit {
  profileError = false;

  /** Which provider's panel is mounted. Only one is in the DOM at a time. */
  provider: 'HTML' | 'WORD' = 'HTML';

  senderName = '';
  senderStreet = '';
  senderPostalCode = '';
  senderCity = '';
  senderEmail = '';
  templateLanguage: DocumentLanguage = 'GERMAN';
  downloadingTemplate = false;
  templateError = false;

  readonly languageOptions: { value: DocumentLanguage; label: string }[] = [
    { value: 'GERMAN',  label: LANGUAGE_KEY.GERMAN },
    { value: 'ENGLISH', label: LANGUAGE_KEY.ENGLISH },
    { value: 'DUTCH',   label: LANGUAGE_KEY.DUTCH },
  ];

  constructor(private auth: AuthService, private http: HttpClient) {}

  ngOnInit(): void {
    this.auth.me$.subscribe({
      next: (me) => {
        if (!me) { this.profileError = true; return; }
        this.senderName = me.name ?? '';
        this.senderEmail = me.email ?? '';
      },
      error: () => this.profileError = true,
    });
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
      .set('senderEmail', this.senderEmail.trim())
      .set('language', this.templateLanguage);

    this.http.post('/api/word/cover-letter/personalize', null, { params, responseType: 'blob', observe: 'response' }).subscribe({
      next: (response) => {
        const filename = this.extractFilename(response.headers.get('Content-Disposition')) ?? 'Anschreiben_personal.docx';
        const url = URL.createObjectURL(response.body!);
        const a = document.createElement('a');
        a.href = url; a.download = filename; a.click();
        URL.revokeObjectURL(url);
        this.downloadingTemplate = false;
      },
      error: () => {
        this.templateError = true;
        this.downloadingTemplate = false;
      },
    });
  }

  private extractFilename(contentDisposition: string | null): string | null {
    return contentDisposition ? /filename=([^;]+)/.exec(contentDisposition)?.[1]?.trim() ?? null : null;
  }
}
