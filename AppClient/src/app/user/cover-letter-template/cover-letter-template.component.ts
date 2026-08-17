import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
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
  imports: [FormsModule, RouterLink, TranslatePipe, CoverLetterFormComponent],
  templateUrl: './cover-letter-template.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './cover-letter-template.component.scss'
})
/**
 * The sender block is never asked for here: the server reads it from the caller's
 * profile, the one place it is maintained. Only the template language is a choice,
 * since the profile does not carry one.
 */
export class CoverLetterTemplateComponent {
  /** Which provider's panel is mounted. Only one is in the DOM at a time. */
  provider: 'HTML' | 'WORD' = 'HTML';

  templateLanguage: DocumentLanguage = 'GERMAN';
  downloadingTemplate = false;
  templateError = false;

  readonly languageOptions: { value: DocumentLanguage; label: string }[] = [
    { value: 'GERMAN',  label: LANGUAGE_KEY.GERMAN },
    { value: 'ENGLISH', label: LANGUAGE_KEY.ENGLISH },
    { value: 'DUTCH',   label: LANGUAGE_KEY.DUTCH },
  ];

  private readonly http = inject(HttpClient);

  downloadTemplate(): void {
    this.templateError = false;
    this.downloadingTemplate = true;
    const params = new HttpParams().set('language', this.templateLanguage);

    this.http.get('/api/word/cover-letter/personalize', { params, responseType: 'blob', observe: 'response' }).subscribe({
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
    return contentDisposition ? /filename="?([^";]+)"?/.exec(contentDisposition)?.[1]?.trim() ?? null : null;
  }
}
