import { Component, OnInit, ChangeDetectionStrategy, inject } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { NgTemplateOutlet } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, switchMap, take, tap } from 'rxjs';
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
import { DocumentLanguage, uiToLetterLanguage } from '../../model/document';

/**
 * Escapes text before it is written into the letter's markup. Without it a link
 * text containing an ampersand or a quote would produce broken markup that the
 * server's sanitizer then has to salvage.
 */
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/** The three languages a letter can be written in, independent of the UI's. */
const LETTER_LANGUAGE_OPTIONS: { value: DocumentLanguage; label: string }[] = [
  { value: 'GERMAN',  label: 'LANGUAGE.DE' },
  { value: 'ENGLISH', label: 'LANGUAGE.EN' },
  { value: 'DUTCH',   label: 'LANGUAGE.NL' },
];

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
  // FormsModule for the link editor's two standalone inputs; NgTemplateOutlet
  // renders that one editor next to whichever field opened it.
  imports: [ReactiveFormsModule, FormsModule, NgTemplateOutlet, TranslatePipe],
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
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

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

  readonly languageOptions = LETTER_LANGUAGE_OPTIONS;

  /**
   * The last suggestion the server gave, per slot. Switching the letter
   * language replaces a field only while it still holds this - that is what
   * distinguishes text the user has not touched from text they wrote.
   */
  private suggested: { subject: string; greeting: string; closing: string; blocks: string[] } =
    { subject: '', greeting: '', closing: '', blocks: [] };

  suggestionsLoading = false;

  // --- link editor ---------------------------------------------------------
  // One editor serves every field; `linkTarget` says which one it was opened
  // from. Splicing an anchor in for the user is the point: the letter accepts a
  // small set of inline HTML, but typing angle brackets by hand is not
  // something this app should ask of anyone.

  /** The field the editor is open for; null while it is closed. */
  linkTarget: { block: number; item: number | null } | null = null;
  linkText = '';
  linkUrl = '';
  linkError = '';

  /** Where in that field's value the anchor goes - the caret when it opened. */
  private linkCaret = 0;
  private linkSelectionEnd = 0;

  readonly letter = this.fb.group({
    // What the user calls this template; shown as its label in the documents list.
    // Required, because the name is how a stored letter is picked out again.
    name: ['', Validators.required],
    // Which language the letter is written in - not which language the app is
    // shown in. Starts at the UI's, so the common case needs no choice at all.
    letterLanguage: this.fb.control<DocumentLanguage>(uiToLetterLanguage(this.language.current())),
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

    this.auth.me$.pipe(take(1)).subscribe(me => {
      if (!me) return;
      const id = this.route.snapshot.paramMap.get('id');
      if (id) this.openTemplate(id);
      else this.startNewTemplate();
    });
  }

  /** Opens one stored template, named in the route by the documents view. */
  private openTemplate(id: string): void {
    this.coverLetters.getTemplate(id).subscribe({
      next: (template) => this.applyTemplate(template),
      error: () => this.loadError = true,
    });
  }

  /**
   * Starts a letter that is not yet stored: the form is blank, `templateId` is
   * null, and the first save creates a new template rather than overwriting an
   * existing one.
   *
   * <p>This is the default the editor opens in. Editing whatever template
   * happened to be stored first is what made every letter overwrite the last
   * one - a second letter has to begin as a second letter.
   */
  startNewTemplate(): void {
    this.templateId = null;
    this.slotIds.clear();
    this.blocks.clear();
    this.attachments.clear();
    this.letter.patchValue({
      name: '',
      applicationId: null,
      subject: '',
      greeting: '',
      closing: '',
      letterLanguage: uiToLetterLanguage(this.language.current()),
    });
    this.savedAt = false;

    this.suggestionsLoading = true;
    this.coverLetters.suggestions(this.letterLanguage()).subscribe({
      next: (blocks) => {
        this.fillFrom(blocks);
        this.suggestionsLoading = false;
      },
      error: () => {
        this.suggestionsLoading = false;
        this.loadError = true;
      },
    });
  }

  /** The editor's "start another letter" action; also clears the id from the URL. */
  newTemplate(): void {
    this.router.navigate(['/cover-letter-template']);
    this.startNewTemplate();
    this.announce('COVER_LETTER_FORM.NEW_STARTED');
  }

  /**
   * Fills a blank form from a suggestion, creating one block per suggested
   * paragraph - unlike the in-place reseed, which only rewrites blocks that
   * already exist.
   */
  private fillFrom(blocks: LetterBlock[]): void {
    this.blocks.clear();
    let subject = '';
    let greeting = '';
    let closing = '';

    for (const block of blocks) {
      if (block.key === 'SUBJECT') subject = block.content;
      else if (block.key === 'SALUTATION') greeting = block.content;
      else if (block.key === 'REGARDS') closing = block.content;
      else this.blocks.push(this.blockGroup(block.key as BlockType, block.content, block.items));
    }

    this.letter.patchValue({ subject, greeting, closing });
    this.suggested = this.toSlots(blocks);
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

    this.letter.patchValue({
      name: template.name ?? '',
      letterLanguage: template.language ?? this.letter.controls.letterLanguage.value,
      subject,
      greeting,
      closing,
    });
    this.loadBaseline(template.language ?? this.letterLanguage());
  }

  /**
   * Records what the server *would* suggest for the language this template is
   * stored in, so an edited field can be told apart from an untouched one.
   *
   * <p>Comparing against the stored text instead would defeat the purpose: it
   * matches itself, so every field would count as untouched and a language
   * switch would overwrite work the user had already saved.
   */
  private loadBaseline(language: DocumentLanguage): void {
    this.coverLetters.suggestions(language).subscribe({
      next: (blocks) => this.suggested = this.toSlots(blocks),
      // Baseline unknown: everything then reads as user-written and is kept,
      // which is the side to fail on.
      error: () => this.suggested = { subject: '', greeting: '', closing: '', blocks: [] },
    });
  }

  // --- letter language -----------------------------------------------------

  /**
   * Switching the language re-seeds the suggested text, but only where the user
   * has left it as it came: a field they wrote themselves keeps its content, so
   * changing the language can never discard work. `insertSuggestions()` is the
   * way to replace text deliberately.
   */
  onLetterLanguageChange(): void {
    this.applySuggestions(false);
  }

  /** Replaces the letter's text with the suggestions for the chosen language. */
  insertSuggestions(): void {
    this.applySuggestions(true);
  }

  private applySuggestions(replaceEdited: boolean): void {
    this.suggestionsLoading = true;
    this.coverLetters.suggestions(this.letterLanguage()).subscribe({
      next: (blocks) => {
        // Inserting on request rebuilds the body, so a user who deleted the
        // blocks gets them back; a language switch only rewrites what is there.
        if (replaceEdited) this.fillFrom(blocks);
        else this.seed(blocks, false);
        this.suggestionsLoading = false;
        this.announce(replaceEdited
          ? 'COVER_LETTER_FORM.SUGGESTIONS_INSERTED'
          : 'COVER_LETTER_FORM.LANGUAGE_CHANGED');
      },
      error: () => {
        this.suggestionsLoading = false;
        this.loadError = true;
      },
    });
  }

  /** Fans a suggestion's blocks out into the form's fixed slots plus its body. */
  private toSlots(blocks: LetterBlock[]): { subject: string; greeting: string; closing: string; blocks: string[] } {
    const slots = { subject: '', greeting: '', closing: '', blocks: [] as string[] };
    for (const block of blocks) {
      if (block.key === 'SUBJECT') slots.subject = block.content;
      else if (block.key === 'SALUTATION') slots.greeting = block.content;
      else if (block.key === 'REGARDS') slots.closing = block.content;
      else slots.blocks.push(block.content);
    }
    return slots;
  }

  private seed(blocks: LetterBlock[], replaceEdited: boolean): void {
    const next = this.toSlots(blocks);
    const value = this.letter.getRawValue();
    this.letter.patchValue({
      subject: this.reseed(value.subject ?? '', this.suggested.subject, next.subject, replaceEdited),
      greeting: this.reseed(value.greeting ?? '', this.suggested.greeting, next.greeting, replaceEdited),
      closing: this.reseed(value.closing ?? '', this.suggested.closing, next.closing, replaceEdited),
    });

    // Body blocks line up by position: the skeleton's Nth paragraph replaces the
    // Nth one here. Blocks the user added beyond the skeleton have no counterpart
    // and are left alone.
    this.blocks.controls.forEach((control, index) => {
      const text = control.get('text')!;
      text.setValue(this.reseed(
        text.value ?? '', this.suggested.blocks[index] ?? '', next.blocks[index] ?? '', replaceEdited));
    });

    this.suggested = next;
  }

  /** Keeps a field the user wrote; swaps one that still holds the old suggestion. */
  private reseed(current: string, previousSuggestion: string, nextSuggestion: string, replaceEdited: boolean): string {
    if (replaceEdited) return nextSuggestion;
    const untouched = current.trim() === '' || current.trim() === previousSuggestion.trim();
    return untouched ? nextSuggestion : current;
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

  // --- links ---------------------------------------------------------------

  isLinkEditorOpen(blockIndex: number, itemIndex: number | null): boolean {
    return this.linkTarget?.block === blockIndex && this.linkTarget?.item === itemIndex;
  }

  /**
   * Opens the editor for one field, remembering the caret so the anchor lands
   * where the user was typing. Text they had selected becomes the link text,
   * which is the behaviour any editor makes you expect.
   */
  openLinkEditor(blockIndex: number, itemIndex: number | null): void {
    const field = this.linkField(blockIndex, itemIndex);
    const start = field?.selectionStart ?? field?.value.length ?? 0;
    const end = field?.selectionEnd ?? start;

    this.linkCaret = start;
    this.linkSelectionEnd = end;
    this.linkText = field ? field.value.substring(start, end) : '';
    this.linkUrl = '';
    this.linkError = '';
    this.linkTarget = { block: blockIndex, item: itemIndex };
    this.focusAfterRender('link-text');
  }

  closeLinkEditor(): void {
    const target = this.linkTarget;
    this.linkTarget = null;
    if (target) this.focusAfterRender(this.linkFieldId(target.block, target.item));
  }

  /**
   * Splices the anchor into the field. The URL is checked against the same
   * schemes the server keeps: anything else has its href stripped there, so
   * accepting it here would store a link that silently stops being one.
   */
  insertLink(): void {
    const url = this.linkUrl.trim();
    const text = this.linkText.trim();

    if (!url || !this.isAllowedUrl(url)) {
      this.linkError = this.translate.instant('COVER_LETTER_FORM.LINK_URL_INVALID');
      this.focusAfterRender('link-url');
      return;
    }
    if (!text) {
      this.linkError = this.translate.instant('COVER_LETTER_FORM.LINK_TEXT_REQUIRED');
      this.focusAfterRender('link-text');
      return;
    }

    const target = this.linkTarget;
    if (!target) return;

    const control = this.linkControl(target.block, target.item);
    if (!control) return;

    const value: string = control.value ?? '';
    const anchor = `<a href="${escapeHtml(url)}">${escapeHtml(text)}</a>`;
    control.setValue(value.slice(0, this.linkCaret) + anchor + value.slice(this.linkSelectionEnd));

    this.linkTarget = null;
    this.announce('COVER_LETTER_FORM.LINK_INSERTED', { text });
    this.focusAfterRender(this.linkFieldId(target.block, target.item));
  }

  /** Mirrors MarkupSanitizer's protocol whitelist. */
  private isAllowedUrl(url: string): boolean {
    return /^(https?:\/\/|mailto:)/i.test(url);
  }

  private linkFieldId(blockIndex: number, itemIndex: number | null): string {
    return itemIndex === null
      ? `block-text-${blockIndex}`
      : `block-${blockIndex}-item-${itemIndex}`;
  }

  private linkField(blockIndex: number, itemIndex: number | null): HTMLInputElement | HTMLTextAreaElement | null {
    return document.getElementById(this.linkFieldId(blockIndex, itemIndex)) as
      HTMLInputElement | HTMLTextAreaElement | null;
  }

  private linkControl(blockIndex: number, itemIndex: number | null) {
    const block = this.blocks.at(blockIndex);
    if (!block) return null;
    return itemIndex === null ? block.get('text') : this.items(block).at(itemIndex);
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
  /**
   * Refuses to store an unnamed letter and puts the user in the name field.
   * Checked before preview and PDF too, since both save first.
   */
  private nameMissing(): boolean {
    const name = this.letter.controls.name;
    if (name.valid) return false;
    name.markAsTouched();
    this.announce('COVER_LETTER_FORM.NAME_REQUIRED');
    this.focusAfterRender('template-name');
    return true;
  }

  /** Stores the template on its own, without rendering anything. */
  save(): void {
    if (this.nameMissing()) return;
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
    if (this.nameMissing()) return;
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
    if (this.nameMissing()) return;
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

  /**
   * Stores the letter: an update once it has an id, otherwise a new template.
   * A blank editor therefore always adds one instead of replacing another.
   */
  private saveTemplate(): Observable<HtmlLetterTemplate> {
    const request = this.templateRequest();
    const language = this.letterLanguage();
    const creating = !this.templateId;
    const saved = this.templateId
      ? this.coverLetters.updateTemplate(this.templateId, request, language)
      : this.coverLetters.createTemplate(request, language);
    return saved.pipe(tap(template => {
      this.templateId = template.id;
      // Put the new template's id in the URL, so a reload reopens this letter
      // rather than silently starting yet another one.
      if (creating) this.router.navigate(['/cover-letter-template', template.id], { replaceUrl: true });
    }));
  }

  /**
   * Folds the form's fixed slots and its block list into one ordered block list.
   * `style` is deliberately never sent: the geometry belongs to the server.
   */
  private templateRequest(): HtmlLetterTemplateRequest {
    const value = this.letter.getRawValue();
    const blocks: LetterBlock[] = [];
    const name = value.name?.trim();

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

    // Omitted rather than sent empty: the server keeps the stored name in that case
    // instead of overwriting it, and picks a localized default on first creation.
    return name ? { name, blocks } : { blocks };
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
    return this.letter.controls.letterLanguage.value ?? uiToLetterLanguage(this.language.current());
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
