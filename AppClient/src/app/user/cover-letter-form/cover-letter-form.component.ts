import { Component, OnInit, ChangeDetectionStrategy, inject } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable, of, switchMap, take, tap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { LanguageService } from '../../core/language.service';
import { ApplicationService } from '../../services/application.service';
import { CoverLetterService } from '../../services/cover-letter.service';
import { Application } from '../../model/application';
import {
  BlockKey,
  BlockType,
  CoverLetterRenderRequest,
  HtmlLetterTemplate,
  HtmlLetterTemplateRequest,
  LetterBlock,
} from '../../model/cover-letter';
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
 * Nothing about the sender or the recipient is asked for here. The recipient block,
 * salutation, date and signature are derived from the chosen application; the sender
 * block is read from the profile, where it is maintained once.
 *
 * Subject, greeting, blocks and closing are stored as one reusable template; the
 * attachments belong to a single sending and travel with the render call. Rendering
 * saves first, so what is printed is always what is stored.
 */
@Component({
  selector: 'app-cover-letter-form',
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './cover-letter-form.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './cover-letter-form.component.scss'
})
export class CoverLetterFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly translate = inject(TranslateService);
  private readonly auth = inject(AuthService);
  private readonly language = inject(LanguageService);
  private readonly applications = inject(ApplicationService);
  private readonly coverLetters = inject(CoverLetterService);

  readonly blockTypes: BlockType[] = ['PARAGRAPH', 'HEADING', 'BULLET_LIST'];

  /** The template being edited; null until the first save has returned one. */
  templateId: string | null = null;

  /** Ids of the single-occurrence blocks, so saving twice does not churn them. */
  private readonly slotIds = new Map<BlockKey, string>();

  applicationOptions: Application[] = [];
  loadError = false;

  saving = false;
  saveError = false;
  savedAt = false;

  previewText = '';
  previewing = false;
  renderError = false;

  downloading = false;
  pdfUrl: string | null = null;
  pdfFilename = '';

  readonly letter = this.fb.group({
    // Optional: with no application chosen the letter is rendered against the sample
    // recipient, so a template can be proofread before it is tied to an application.
    applicationId: this.fb.control<number | null>(null),
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

    // take(1) matters: a second emission would create a second template.
    this.auth.me$.pipe(take(1)).subscribe(me => {
      if (me) this.loadTemplate();
    });
  }

  /**
   * Opens the caller's template, creating one on first use. Passing no blocks lets the
   * server store its localized skeleton, so the starting blocks and the closing formula
   * come from the backend message bundle rather than a second language file here.
   */
  private loadTemplate(): void {
    this.coverLetters.listTemplates().pipe(
      switchMap(templates => templates.length
        ? of(templates[0])
        : this.coverLetters.createTemplate({}, this.letterLanguage())),
    ).subscribe({
      next: (template) => this.applyTemplate(template),
      error: () => this.loadError = true,
    });
  }

  /** Fans the stored blocks back out into the form's fixed slots and its block list. */
  private applyTemplate(template: HtmlLetterTemplate): void {
    this.templateId = template.id;
    this.slotIds.clear();
    this.blocks.clear();

    let subject = '';
    let greeting = '';
    let closing = '';

    for (const block of template.blocks) {
      if (block.key === 'SUBJECT' || block.key === 'SALUTATION' || block.key === 'REGARDS') {
        if (block.id) this.slotIds.set(block.key, block.id);
        if (block.key === 'SUBJECT') subject = block.content;
        if (block.key === 'SALUTATION') greeting = block.content;
        if (block.key === 'REGARDS') closing = block.content;
      } else {
        this.blocks.push(this.blockGroup(block.key, block.content, block.items, block.id));
      }
    }

    this.letter.patchValue({ subject, greeting, closing });
  }

  private blockGroup(type: BlockType, text: string, items: string[] = [], id: string | null = null): FormGroup {
    return this.fb.group({
      id: this.fb.control<string | null>(id),
      type: this.fb.control<BlockType>(type),
      text: [text],
      items: this.fb.array(items.map(item => this.fb.control(item))),
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

  get selectedApplication(): Application | null {
    const id = this.letter.controls.applicationId.value;
    return this.applicationOptions.find(a => a.id === id) ?? null;
  }

  // --- rendering -----------------------------------------------------------

  /**
   * The proofreading pass: the server linearizes the very letter it would print, so
   * the text below is never a second implementation of the layout.
   */
  /** Stores the template on its own, without rendering anything. */
  save(): void {
    this.saving = true;
    this.saveError = false;
    this.savedAt = false;
    this.saveTemplate().subscribe({
      next: () => {
        this.saving = false;
        this.savedAt = true;
        this.announce('COVER_LETTER_FORM.SAVED');
      },
      error: () => {
        this.saveError = true;
        this.saving = false;
      },
    });
  }

  preview(): void {
    this.previewing = true;
    this.renderError = false;
    const applicationId = this.letter.controls.applicationId.value;
    this.saveTemplate().pipe(
      switchMap(template => applicationId
        ? this.coverLetters.renderText(applicationId, template.id, this.renderRequest())
        : this.coverLetters.previewText(template.id, this.renderRequest(), this.letterLanguage())),
    ).subscribe({
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
    this.downloading = true;
    this.renderError = false;
    this.revokePdfUrl();
    const applicationId = this.letter.controls.applicationId.value;
    this.saveTemplate().pipe(
      switchMap(template => applicationId
        ? this.coverLetters.renderPdf(applicationId, template.id, this.renderRequest())
        : this.coverLetters.previewPdf(template.id, this.renderRequest(), this.letterLanguage())),
    ).subscribe({
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

  /** Saves the edited template, creating it if this is the first save. */
  private saveTemplate(): Observable<HtmlLetterTemplate> {
    const request = this.templateRequest();
    const language = this.letterLanguage();
    const saved = this.templateId
      ? this.coverLetters.updateTemplate(this.templateId, request, language)
      : this.coverLetters.createTemplate(request, language);
    return saved.pipe(tap(template => this.templateId = template.id));
  }

  /**
   * Folds the form's fixed slots and its block list into one ordered block list.
   * `style` is deliberately never sent: the geometry belongs to the server.
   */
  private templateRequest(): HtmlLetterTemplateRequest {
    const value = this.letter.getRawValue();
    const blocks: LetterBlock[] = [];

    const subject = value.subject?.trim();
    if (subject) blocks.push(this.slotBlock('SUBJECT', subject));

    const greeting = value.greeting?.trim();
    if (greeting) blocks.push(this.slotBlock('SALUTATION', greeting));

    for (const block of this.blocks.controls) {
      blocks.push({
        id: block.get('id')!.value ?? crypto.randomUUID(),
        key: block.get('type')!.value as BlockType,
        content: block.get('text')!.value ?? '',
        items: (this.items(block).value as string[]).filter(item => !!item?.trim()),
      });
    }

    const closing = value.closing?.trim();
    if (closing) blocks.push(this.slotBlock('REGARDS', closing));

    return { blocks };
  }

  private slotBlock(key: BlockKey, content: string): LetterBlock {
    const id = this.slotIds.get(key) ?? crypto.randomUUID();
    this.slotIds.set(key, id);
    return { id, key, content, items: [] };
  }

  /**
   * The parts that belong to this one sending rather than to the template. The sender
   * is absent on purpose: the server reads it from the profile.
   */
  private renderRequest(): CoverLetterRenderRequest {
    return { attachments: (this.attachments.value as string[]).filter(a => !!a?.trim()) };
  }

  private letterLanguage(): DocumentLanguage {
    return UI_TO_LETTER_LANGUAGE[this.language.current() ?? 'de'] ?? 'GERMAN';
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
