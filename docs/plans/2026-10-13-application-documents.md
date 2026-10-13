# Plan: Unterlagen einer Bewerbung (Anlagen)

Stand: feature/test, 13.10.2026 – Phase 1 fast fertig, nichts committet.

## Ziel

Eine Bewerbung weiß, welche Dokumente zu ihr gehören (Lebenslauf, Zeugnisse,
sonstige PDFs). Daraus folgen drei Dinge, in drei Phasen:

1. **Festhalten** – welche Unterlagen gingen an welche Firma (Phase 1).
2. **Anlagenvermerk** – die Anlagen-Zeilen im Anschreiben stammen aus den
   angehängten Dokumenten (Phase 2).
3. **Bewerbungs-PDF** – Anschreiben + Lebenslauf + Zeugnisse als eine Datei (Phase 3).

`mailto:` kann keine Dateien anhängen; deshalb ist Phase 3 ein Download, den die
Person selbst an die Mail hängt oder in ein Webformular lädt.

## Entscheidungen (mit dem Nutzer abgestimmt)

- Phase 1 zuerst, 2 und 3 danach.
- **Feste Reihenfolge**, kein manuelles Sortieren: CV → Zeugnisse → Sonstiges,
  innerhalb eines Typs älteste Hochladung zuerst, dann Bezeichnung.
- **Phase 2: angehängte Dokumente werden an die vorhandenen Anlagen-Zeilen der
  Vorlage angefügt**, nicht ersetzt.
- Vorauswahl im Panel: Standard-CV und Standard-Zeugnis in der Sprache der
  Stelle (`applyLanguage`) – nur Vorschlag, gespeichert erst mit „Speichern“.

## Phase 1 – Stand

### Erledigt und grün

Backend (`src/main/java/de/ajm/manager/application/attachment/`):
- `ApplicationDocument` – Tabelle `application_documents`, unique
  `(application_id, document_id)`, beide FKs `@OnDelete(CASCADE)`.
- `ApplicationDocumentRepository`, `ApplicationDocumentsDto`,
  `ApplicationDocumentsRequest`, `ApplicationDocumentService`
  (`ENCLOSABLE` = CV/CERTIFICATE/OTHER, `READING_ORDER`), `ApplicationDocumentController`.
- Endpunkte: `GET /api/applications/documents` (alle Bewerbungen mit Unterlagen,
  ein Request), `PUT /api/applications/{id}/documents` (`{documentIds}`, ersetzt den
  Satz). Ein einzelnes `GET /{id}/documents` gibt es bewusst nicht – die Liste
  braucht nur den Sammelabruf.
- `messages.properties`: `error.application.document.notEnclosable`.
- Tests: `ApplicationDocumentServiceTest` (6), `ApplicationDocumentControllerTest` (4),
  `ApplicationDocumentSchemaTest` (3, `@PostgresDataJpaTest`: Cascade bei Dokument-
  und Bewerbungslöschung, Eindeutigkeit). Mutationsprobe: ohne `@OnDelete` auf der
  Dokumentseite schlägt der Schematest fehl.

Frontend:
- `model/application.ts`: `ApplicationDocuments`; `services/application.service.ts`:
  `getDocuments()`, `setDocuments()` + neuer `application.service.spec.ts`.
- `user/application-list/`: Spalte „Unterlagen“ (Text „2 Unterlagen“ / „—“, numerisch
  sortiert über `documentsCount`), Aktion „Unterlagen“ je Zeile, Panel mit
  Checkboxen je Typ (`<fieldset>`/`<legend>`), Vorauswahl aus Standards,
  Speichern mit `LiveAnnouncer`, Fehler als `role="alert"`, leerer Zustand mit
  Button „Zu den Dokumenten“.
- Fokus: beim Öffnen auf die Panel-Überschrift, nach Speichern/Abbrechen zurück
  auf den Zeilen-Button – per `afterNextRender` und stabiler id
  `documents-{applicationId}`, weil Speichern die Zeilen neu aufbaut.
- Leerzeichen zwischen Name und Sprache explizit (`{{ ' ' }}`), sonst lautet der
  zugängliche Name „Lebenslauf(Deutsch)“.
- i18n (4 Sprachen): `APPLICATIONS.COL_DOCUMENTS`, `DOCUMENTS_COUNT_ONE/OTHER`,
  `ACTION_DOCUMENTS(_ARIA)`, `DOCUMENTS_HEADING`, `DOCUMENTS_LEAD`,
  `DOCUMENTS_LOADING`, `DOCUMENTS_PREFILLED`, `DOCUMENTS_GROUP_*`,
  `DOCUMENTS_EMPTY(_ACTION)`, `DOCUMENTS_SAVE`, `DOCUMENTS_SAVED`,
  `ERROR_DOCUMENTS_SAVE`, `ERROR_DOCUMENTS_LOAD`.
- Demo: `seed/applications.ts` `ENCLOSURES` (Bewerbungen 1, 3, 6), `DemoDb.enclosures`
  (inkl. `reset()`), Routen in `demo-api.ts`; Löschen von Bewerbung oder Dokument
  entfernt die Verknüpfung.
- Specs `application-list`: 8 neue Fälle (Zählspalte, Gruppen/Reihenfolge,
  Vorauswahl, keine Vorauswahl bei vorhandenen Unterlagen, Speichern, Fokus,
  Fehler, axe).

Letzter Lauf: 319 Frontend-Tests, `lint:a11y`, `ng build`, `build:demo`,
`check:demo`, `./gradlew check` (inkl. Coverage-Gate) – alle grün. Browserprobe im
Demo-Build: Vorauswahl, Speichern, Zählung, Ansage und Fokus wie geplant; 375px
ohne horizontales Scrollen.

### Offen in Phase 1

1. **Mobiler Umbruch der Checkbox-Zeile** (Screenshot 375px, Deutsch): „(Deutsch)“
   bricht unter die Checkbox statt unter den Namen. Fix in
   `application-list.component.html/.scss`: Name und Sprache in **einen** Textblock
   neben der Checkbox:
   ```html
   <label class="document-option">
     <input type="checkbox" …>
     <span class="document-text"><span class="document-name">{{ doc.label }}</span>{{ ' ' }}<span class="document-meta">(…)</span></span>
   </label>
   ```
   `.document-option`: `flex-wrap: nowrap; align-items: flex-start;` Checkbox mit
   kleinem `margin-block-start`, damit sie zur ersten Textzeile passt. Spec-Selektor
   `.document-name` bleibt gültig.
2. Danach erneut: `npx ng test --watch=false --include="**/application-list/*.spec.ts"`,
   `npm run lint:a11y`, `npm run build:demo` + `check:demo`, Screenshot 375px Deutsch.
3. **CHANGELOG** unter `[Unreleased]` / Added: „Eine Bewerbung kann ihre Unterlagen
   festhalten (Lebenslauf, Zeugnisse, weitere PDFs) …; die Standards in der Sprache
   der Stelle werden vorgeschlagen.“ – noch nicht eingetragen.
4. Optional: `CLAUDE.md` kurz ergänzen (Paket `application/attachment/`, Cascade-
   Regel wie bei `DefaultDocument`).

## Phase 2 – Anlagenvermerk (geplant)

- `HtmlLetterTemplateService` (`render…`, Zeile ~139 nutzt `request.attachments()`):
  wenn die Bewerbung Unterlagen hat, deren **Bezeichnungen** an die Zeilen aus
  Request/Vorlage **anhängen** (Entscheidung oben), Dubletten (gleiche Zeile,
  case-insensitiv) überspringen.
- Reihenfolge der Sanitize/Substitute-Regel einhalten: Labels sind Nutzereingabe
  und müssen escaped ankommen (`MarkupSanitizer` → `PlaceholderResolver`).
- Word-Provider unverändert (kein Anlagenfeld in den Serienbrief-Vorlagen).
- `TextCoverLetterRenderer` übernimmt das automatisch (gleiches Modell), Vorschau
  und PDF bleiben deckungsgleich.
- Tests: Assembler-/Service-Test (anhängen, Dubletten, Escaping eines Labels mit
  `<`), `Din5008PdfGeometryTest` bleibt grün.

## Phase 3 – Bewerbungs-PDF (geplant)

- `GET /api/applications/{id}/bundle?templateId=…&provider=HTML|WORD`.
- Anschreiben-PDF über die bestehenden Wege, dann die angehängten PDFs in
  Lesereihenfolge per `PDFMergerUtility`, mit Datei-Puffer statt RAM.
- Jede Datei über `DocumentService.bytes` (Eigentumsprüfung, kein presigned URL).
- Verschlüsseltes/kaputtes PDF → klare Fehlermeldung mit Dokumentnamen
  (`ApiException`-Subtyp), nie 500.
- Frontend: Button „Bewerbungs-PDF herunterladen“ neben dem Anschreiben-Download.
- Demo: liefert `SAMPLE_PDF`.
- Tests: Merge mit PDFBox-generierten PDFs (Seitenzahl/Reihenfolge), kaputtes PDF.

## Arbeitsbaum

Uncommittet liegen vier Features übereinander: CV-Abgleich auf der Demo-Startseite,
Bewerbungsfrist, Standard-Dokumente, Unterlagen (Phase 1). `cv-match.component.ts`
und dessen Spec enthalten Änderungen aus CV-Abgleich **und** Standards – getrennte
Commits brauchen `git add -p`.
