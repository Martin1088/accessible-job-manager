import { Component, OnInit, ChangeDetectionStrategy, ElementRef, ViewChild, inject } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth.service';
import { LanguageService } from '../../core/language.service';
import { ApplicationService } from '../../services/application.service';
import { CoverLetterService } from '../../services/cover-letter.service';
import { Application } from '../../model/application';
import { BlockType, CoverLetterTemplate } from '../../model/cover-letter';
import { DocumentLanguage } from '../../model/document';

const UI_TO_LETTER_LANGUAGE: Record<string, DocumentLanguage> = {
  de: 'GERMAN',
  en: 'ENGLISH',
  nl: 'DUTCH',
};

/**
 * The HTML cover letter form. Every field here maps 1:1 onto a slot the server
 * understands; nothing describes where anything sits on the page. The DIN 5008
 * geometry never leaves the backend, so no input can break the norm.
 *
 * Seven of the letter's parts are never asked for, because the server derives them
 * from the chosen application: the recipient block, the return address, the
 * salutation, the date, and the signature.
 */
@Component({
  selector: 'app-cover-letter-form',
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './cover-letter-form.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './cover-letter-form.component.scss'
})
export class CoverLetterFormComponent implements OnInit {
  @ViewChild('errorSummary') errorSummary?: ElementRef<HTMLElement>;

  private readonly fb = inject(FormBuilder);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly translate = inject(TranslateService);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);
  private readonly applications = inject(ApplicationService);
  private readonly coverLetters = inject(CoverLetterService);

  readonly blockTypes: BlockType[] = ['PARAGRAPH', 'HEADING', 'BULLET_LIST'];

  applicationOptions: Application[] = [];
  loadError = false;
  submitted = false;

  previewText = '';
  previewing = false;
  renderError = false;

  downloading = false;
  pdfUrl: string | null = null;
  pdfFilename = '';

  readonly letter = this.fb.group({
    applicationId: this.fb.control<number | null>(null, Validators.required),
    sender: this.fb.group({
      name: ['', Validators.required],
      street: ['', Validators.required],
      postalCode: ['', Validators.required],
      city: ['', Validators.required],
      email: ['', Validators.email],
      phone: [''],
    }),
    subject: [''],
    greeting: [''],
    blocks: this.fb.array<FormGroup>([]),
    closing: [''],
    attachments: this.fb.array<ReturnType<FormBuilder['control']>>([]),
  });

  get blocks(): FormArray<FormGroup> {
    return this.letter.get('blocks') as FormArray<FormGroup>;
  }

  get attachments(): FormArray {
    return this.letter.get('attachments') as FormArray;
  }

  ngOnInit(): void {
    this.applications.getAll().subscribe({
      next: (list) => this.applicationOptions = list.filter(a => a.id != null),
      error: () => this.loadError = true,
    });

    // The sender name/email are the only address parts the profile knows; street,
    // postal code, city and phone have no home on UserProfile yet and stay typed.
    this.auth.me$.subscribe(me => {
      if (!me) return;
      this.letter.controls.sender.patchValue({ name: me.name ?? '', email: me.email ?? '' });
      this.loadSkeleton();
    });
  }

  /**
   * Pulls the starting blocks and the localized closing formula from the server. The
   * closing default lives in the backend message bundle, so it is fetched rather than
   * duplicated here in a second language file.
   */
  private loadSkeleton(): void {
    const language = UI_TO_LETTER_LANGUAGE[this.language.current() ?? 'de'] ?? 'GERMAN';
    this.coverLetters.defaultTemplate(this.senderValue(), language).subscribe({
      next: (template) => {
        this.letter.patchValue({ closing: template.closing ?? '' });
        this.blocks.clear();
        template.blocks.forEach(block => this.blocks.push(this.blockGroup(block.type, block.text)));
      },
      error: () => this.loadError = true,
    });
  }

  private blockGroup(type: BlockType, text: string): FormGroup {
    return this.fb.group({
      type: this.fb.control<BlockType>(type),
      text: [text],
      items: this.fb.array<ReturnType<FormBuilder['control']>>([]),
    });
  }

  items(block: FormGroup): FormArray {
    return block.get('items') as FormArray;
  }

  isList(block: FormGroup): boolean {
    return block.get('type')!.value === 'BULLET_LIST';
  }

  // --- block editing -------------------------------------------------------
  // Each mutation announces itself once and then moves focus to where the user
  // would want to type next, so the list stays navigable without sight.

  addBlock(): void {
    this.blocks.push(this.blockGroup('PARAGRAPH', ''));
    const index = this.blocks.length - 1;
    this.announce('COVER_LETTER_FORM.BLOCK_ADDED', { number: index + 1 });
    this.focusAfterRender(`block-text-${index}`);
  }

  removeBlock(index: number): void {
    this.blocks.removeAt(index);
    this.announce('COVER_LETTER_FORM.BLOCK_REMOVED', { number: index + 1 });
    this.focusAfterRender(this.blocks.length ? `block-type-${Math.max(0, index - 1)}` : 'add-block');
  }

  moveBlock(index: number, delta: number): void {
    const target = index + delta;
    if (target < 0 || target >= this.blocks.length) return;
    const group = this.blocks.at(index);
    this.blocks.removeAt(index);
    this.blocks.insert(target, group);
    this.announce('COVER_LETTER_FORM.BLOCK_MOVED', { number: target + 1 });
    this.focusAfterRender(`block-move-${delta < 0 ? 'up' : 'down'}-${target}`);
  }

  addItem(block: FormGroup, blockIndex: number): void {
    this.items(block).push(this.fb.control(''));
    const index = this.items(block).length - 1;
    this.announce('COVER_LETTER_FORM.ITEM_ADDED', { number: index + 1 });
    this.focusAfterRender(`block-${blockIndex}-item-${index}`);
  }

  removeItem(block: FormGroup, blockIndex: number, index: number): void {
    this.items(block).removeAt(index);
    this.announce('COVER_LETTER_FORM.ITEM_REMOVED', { number: index + 1 });
    this.focusAfterRender(`block-${blockIndex}-add-item`);
  }

  addAttachment(): void {
    this.attachments.push(this.fb.control(''));
    const index = this.attachments.length - 1;
    this.announce('COVER_LETTER_FORM.ATTACHMENT_ADDED', { number: index + 1 });
    this.focusAfterRender(`attachment-${index}`);
  }

  removeAttachment(index: number): void {
    this.attachments.removeAt(index);
    this.announce('COVER_LETTER_FORM.ATTACHMENT_REMOVED', { number: index + 1 });
    this.focusAfterRender(this.attachments.length ? `attachment-${Math.max(0, index - 1)}` : 'add-attachment');
  }

  // --- validation wiring ---------------------------------------------------

  invalid(path: string): boolean {
    const control = this.letter.get(path);
    return !!control && control.invalid && (control.touched || this.submitted);
  }

  /**
   * Only ever names elements that are actually in the DOM. An aria-describedby that
   * points at a missing id makes some screen readers drop the whole hint.
   */
  describedBy(path: string, ...ids: string[]): string | null {
    const present = ids.filter(id => id.endsWith('-error') ? this.invalid(path) : true);
    return present.length ? present.join(' ') : null;
  }

  get invalidFields(): { path: string; id: string; label: string }[] {
    const candidates = [
      { path: 'applicationId', id: 'application-id', label: 'COVER_LETTER_FORM.APPLICATION_LABEL' },
      { path: 'sender.name', id: 'sender-name', label: 'COVER_LETTER_FORM.SENDER_NAME_LABEL' },
      { path: 'sender.street', id: 'sender-street', label: 'COVER_LETTER_FORM.SENDER_STREET_LABEL' },
      { path: 'sender.postalCode', id: 'sender-postal-code', label: 'COVER_LETTER_FORM.SENDER_POSTAL_CODE_LABEL' },
      { path: 'sender.city', id: 'sender-city', label: 'COVER_LETTER_FORM.SENDER_CITY_LABEL' },
      { path: 'sender.email', id: 'sender-email', label: 'COVER_LETTER_FORM.SENDER_EMAIL_LABEL' },
    ];
    return candidates.filter(field => this.invalid(field.path));
  }

  get selectedApplication(): Application | null {
    const id = this.letter.controls.applicationId.value;
    return this.applicationOptions.find(a => a.id === id) ?? null;
  }

  // --- rendering -----------------------------------------------------------

  /**
   * The proofreading pass: the server linearizes the very letter it would print, so
   * the text below is never a second implementation of the layout.
   */
  preview(): void {
    if (!this.validateForRender()) return;
    this.previewing = true;
    this.renderError = false;
    this.coverLetters.renderText(this.letter.controls.applicationId.value!, this.payload()).subscribe({
      next: (text) => {
        this.previewText = text;
        this.previewing = false;
        this.announce('COVER_LETTER_FORM.PREVIEW_READY');
      },
      error: () => {
        this.renderError = true;
        this.previewing = false;
      },
    });
  }

  downloadPdf(): void {
    if (!this.validateForRender()) return;
    this.downloading = true;
    this.renderError = false;
    this.revokePdfUrl();
    this.coverLetters.renderPdf(this.letter.controls.applicationId.value!, this.payload()).subscribe({
      next: (response) => {
        this.pdfUrl = URL.createObjectURL(response.body!);
        this.pdfFilename = this.extractFilename(response.headers.get('Content-Disposition')) ?? 'Anschreiben.pdf';
        this.downloading = false;
        this.announce('COVER_LETTER_FORM.PDF_READY');
        this.focusAfterRender('pdf-download');
      },
      error: () => {
        this.renderError = true;
        this.downloading = false;
      },
    });
  }

  private validateForRender(): boolean {
    this.submitted = true;
    if (this.letter.valid) return true;
    this.letter.markAllAsTouched();
    this.focusElement(this.errorSummary?.nativeElement);
    return false;
  }

  /** `style` is deliberately never sent: the geometry belongs to the server. */
  private payload(): CoverLetterTemplate {
    const value = this.letter.getRawValue();
    return {
      sender: this.senderValue(),
      subject: value.subject?.trim() || null,
      greeting: value.greeting?.trim() || null,
      blocks: this.blocks.controls.map(block => ({
        type: block.get('type')!.value as BlockType,
        text: block.get('text')!.value ?? '',
        items: (this.items(block).value as string[]).filter(item => !!item?.trim()),
      })),
      closing: value.closing?.trim() || null,
      attachments: (this.attachments.value as string[]).filter(a => !!a?.trim()),
    };
  }

  private senderValue() {
    const sender = this.letter.controls.sender.getRawValue();
    return {
      name: sender.name ?? '',
      street: sender.street ?? '',
      postalCode: sender.postalCode ?? '',
      city: sender.city ?? '',
      email: sender.email ?? '',
      phone: sender.phone ?? '',
    };
  }

  private extractFilename(contentDisposition: string | null): string | null {
    return contentDisposition ? /filename="?([^";]+)"?/.exec(contentDisposition)?.[1]?.trim() ?? null : null;
  }

  private revokePdfUrl(): void {
    if (this.pdfUrl) {
      URL.revokeObjectURL(this.pdfUrl);
      this.pdfUrl = null;
    }
  }

  // --- announcement / focus helpers ---------------------------------------

  private announce(key: string, params?: Record<string, unknown>): void {
    this.announcer.announce(this.translate.instant(key, params), 'polite');
  }

  private focusAfterRender(id: string): void {
    setTimeout(() => this.focusElement(document.getElementById(id)));
  }

  private focusElement(element: HTMLElement | null | undefined): void {
    element?.focus();
  }
}
