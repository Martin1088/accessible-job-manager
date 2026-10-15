# Plan: Unterlagen einer Bewerbung (Anlagen)

Stand: feature/test, 15.10.2026 – Phase 1–3 umgesetzt und grün, Phase 1 in `81d6cf8`, 2 und 3 noch nicht committet.

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

### Abgeschlossen am 15.10.

Der mobile Umbruch ist behoben: `.document-text` fasst Name und Sprache zusammen, `nowrap`
und `flex-start`. Probe bei 375px auf Deutsch: „(Deutsch)“ bricht unter den Namen,
nicht unter die Checkbox, kein horizontales Scrollen. CHANGELOG und CLAUDE.md
(Abschnitt „Application documents“) sind ergänzt.

## Phase 2 – Anlagenvermerk (umgesetzt)

- `ApplicationDocumentService.enclosed(applicationId, userId)` prüft das Eigentum und
  liefert die Dokumente in `READING_ORDER`.
- `CoverLetterHtmlService.withEnclosures` hängt die Labels nach `assemble` an die Zeilen
  aus dem Request an (`CoverLetterModel.withAttachments`). Dubletten werden case-insensitiv
  übersprungen, leere Labels ignoriert.
- **Abweichung vom ursprünglichen Plan:** Die Labels laufen weder durch `MarkupSanitizer`
  noch durch `PlaceholderResolver`. Anlagen-Zeilen sind Klartext (`th:text`), und ein Label
  `{{company}}` soll wörtlich bleiben.
- Betroffen sind `render` (PDF/HTML/Text) und `renderAsEmail`, `renderSample` nicht.
  Word-Provider unverändert.
- Tests: `CoverLetterHtmlServiceTest` (8), `ApplicationDocumentServiceTest` +2.

## Phase 3 – Bewerbungs-PDF (umgesetzt)

- **Abweichung:** Der Nutzer hat klargestellt, dass das Anschreiben auch eine .docx-Vorlage
  sein kann. Daher gibt es zwei Endpunkte, je Provider einen, statt `?provider=`:
  `POST /api/html/cover-letter/{applicationId}/bundle/{templateId}` (mit
  `CoverLetterRenderRequest`) und `POST /api/word/cover-letter/{applicationId}/fill/{documentId}/bundle`.
- `ApplicationBundleService` (`application/attachment/`) ist provider-unabhängig: Er nimmt
  das fertige Anschreiben-PDF und die Sprache und hängt die Unterlagen über
  `DocumentService.bytes` an. Gemergt wird mit `PDFMergerUtility` und
  `IOUtils.createTempFileOnlyStreamCache()`. Dateiname: `coverLetter.bundleFileLabel` + Firma.
- Ein verschlüsseltes oder kaputtes PDF wird vorab geöffnet und ergibt einen 400 mit dem
  Dokumentnamen (`error.application.bundle.unreadable`).
- Die Word-`RenderedDocument` trägt jetzt die Sprache des Briefs.
- Frontend: Button „Bewerbungs-PDF“ je Zeile der Bewerbungsliste für beide Provider. Der
  Dateiname kommt aus `Content-Disposition`. Die Server-Begründung wird per
  `readBlobMessage` (nach `core/http-error.ts` verschoben) über `app-error-text` gezeigt.
- Demo: beide Routen liefern `SAMPLE_PDF`.
- Tests: `ApplicationBundleServiceTest` (6, echte PDFBox-PDFs inkl. verschlüsseltem),
  `CoverLetterBundleEndpointTest` (3), Specs für die Bewerbungsliste (+3) und `readBlobMessage` (+3).

## Nebenbei behoben

Uploads zu Garage scheiterten seit AWS SDK 2.55 mit `Invalid payload signature`
(CRC32-Checksumme als `aws-chunked`). Lösung: `S3Config` mit
`requestChecksumCalculation`/`responseChecksumValidation` `WHEN_REQUIRED`.

## Letzter Lauf (15.10.)

326 Frontend-Tests, `lint:a11y`, `ng build`, `build:demo`, `check:demo`;
`./gradlew check` unter CI-Bedingungen: 551 Tests, 7 übersprungen (Gotenberg/Ollama),
Zeilenabdeckung 69,02 %, alles grün. Nicht live geprüft: der Merge gegen das
echte Gotenberg und Garage, mit Anschreiben aus beiden Providern.

## Arbeitsbaum

Phase 1 und die früheren Features sind committet. Uncommittet liegen Phase 2, Phase 3
und der S3-Fix. Getrennte Commits sind möglich: Der S3-Fix steckt nur in `S3Config.java`
plus seinem CHANGELOG-/CLAUDE.md-Absatz.
