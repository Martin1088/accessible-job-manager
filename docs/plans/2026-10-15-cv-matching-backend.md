# Plan: Backend für den CV-Abgleich („Passung prüfen“)

Stand: feature/test, 15.10.2026 – Backend gebaut, Tests grün bis auf den letzten Umbau
(siehe „Offen“). Nichts davon committet.

## Ziel

Der Abgleich hält den Standard-Lebenslauf des Nutzers gegen eine Stellenanzeige und
liefert je Anforderung: erfüllt / teilweise / fehlt, mit Zitat aus Anzeige und Lebenslauf,
Begründung und Vorschlag. Bis heute antwortete nur der Demo. Das Modell ist eigenständig
konfigurierbar, fällt aber auf das des Posting-Parsers zurück.

## Entscheidungen (mit dem Nutzer abgestimmt)

- Verglichen wird **nur der Standard-CV in der Oberflächensprache**, kein Ausweichen auf
  andere Sprachen oder den neuesten Upload. Ohne ihn zeigt die Seite einen Hinweis mit Link.
- Auf der Startseite öffnet „Mit Lebenslauf abgleichen“ (neben „Für neues Unternehmen
  verwenden“) den Abgleich mit der Anzeige des Imports. Der Button erscheint **überall**,
  nicht nur im Demo. Er **zeigt nur an**, gestartet wird erst mit „Passung prüfen“.
- Unter dem Ergebnis steht „Für neues Unternehmen verwenden“ noch einmal.
- Modell: eigener Block `cv-matching` in `application.yml`, jeder Wert fällt auf
  `job-posting.parser` zurück.

## Erledigt

### Frontend
- `cv-match`: Standard-CV statt Auswahlliste, folgt dem Sprachwechsel; `presetPosting`,
  `focusOnOpen`, `focusHeading()`; neues Preset leert das Ergebnis.
- `job-posting-import`: `offerMatch` + `matchRequested` (URL, sonst Text, sonst `null`).
- `home`: Abgleich per `@defer` erst bei Bedarf, per Template-Referenz angesprochen
  (Klassenreferenz hätte ihn ins Start-Bundle gezogen); Teaser-Link und fester Demo-Block weg.
- `documents`: Fokus springt nach Dateiauswahl/Bearbeiten ins Formular (nach oben
  gescrollt) und danach zurück; Überschrift „Neues Dokument“ statt „Neue Vorlage“ bei PDFs.
- Nebenbei: Ergebnisliste des Imports schob die Seite auf dem Handy 45px seitwärts.
- i18n in allen vier Sprachen; Demo-Seed hat zusätzlich einen englischen Standard-CV.

### Backend (`de.ajm.manager.matching`)
- `CvMatchingProperties` (Fallback per Platzhalter, Validierung beim Start),
  `CvMatchingModelConfig` + `CvMatchingModel` (eigene Client-Instanz, **keine** zweite
  `JobPostingLlmClient`-Bean – sonst wäre die Parser-Injektion mehrdeutig).
- `CvMatchingService`: CV zuerst (Eigentum + Typ CV), dann Anzeige über
  `JobPostingParserService.renderedPostingText` / `requirePostingText` (neu, aus
  `overviewFromText` herausgezogen), CV-Text über `PostingPdfTextExtractor`, max. 12 000 Zeichen.
- `CvMatchSpec`: Prompt + Schema, Antwort als reine Strings (`RawMatch`); Reihenfolge
  Anforderungen → level → summary.
- `CvMatchGuard`: Zitate gegen ihre Quelle geprüft (sonst `null`), ungültige Einträge und
  Dubletten fallen weg, `level` notfalls abgeleitet, Sprachcodes bereinigt.
- `CvMatchingController`: `POST /api/matching`, `POST /api/matching/text`.
- Ollama-Client: optionales `num_ctx` (`context-window`, Parser sendet weiterhin keins).
- Beide Clients: Antwort am Token-Limit (`done_reason`/`finish_reason` = `length`) wird als
  `error.llm.truncated` gemeldet statt als „nicht lesbar“; „nicht lesbar“ behält die Ursache.
- Doku: `.env.example` (auskommentiert – leere Variable hebelt den Fallback aus), Helm
  `llm.ollama.matchingModel` (zieht das Modell mit; **nicht gerendert**, Helm fehlt hier),
  CLAUDE.md-Abschnitt „CV matching“, CHANGELOG.

Nebenbei behoben: Uploads zu Garage scheiterten seit AWS SDK 2.55 (`S3Config`,
Checksummen `WHEN_REQUIRED`).

## Diagnose des ersten echten Laufs

Fehler im Browser: „Could not parse extraction result“. Nachgestellt mit derselben Anzeige
(OFD Baden-Württemberg, 4 708 Zeichen) und dem echten Standard-CV (8 292 Zeichen), Modell
`qwen2.5:3b`, 4 000 Ausgabe-Tokens, `num_ctx` 16 384:

| Variante                           | Dauer | done_reason | Befund |
|------------------------------------|-------|-------------|--------|
| Beispiel-CV (kurz)                 | 41 s  | stop        | 1 195 Tokens, gültig |
| echter CV, erster Prompt           | 191 s | length      | 4 000 Tokens, JSON abgeschnitten |
| echter CV, Prompt „kurz fassen“    | 201 s | length      | unverändert – Prompt wird ignoriert |
| + `maxItems: 12`                   | 207 s | length      | 12 verschiedene Anforderungen, aber trotzdem abgeschnitten |
| dto., erneuter Lauf                | 173 s | length      | Schleife: 12 Einträge, 2 Namen (einer 11×); `summary` läuft ab Zeichen 7 412 rund 7 000 Zeichen weiter |

Ursache: Das 3B-Modell gerät bei langem CV in **Wiederholungsschleifen** und schreibt die
Zusammenfassung endlos fort, bis das Token-Limit greift. Prompt-Anweisungen helfen nicht.

## Offen

1. **`maxLength` auf allen Texten (nur Ollama) ist eingebaut, aber ungetestet.** Der Lauf
   damit lieferte kein Ergebnis (Tool-Fehler). Obergrenzen in `CvMatchSpec`: Name 60,
   Zitat 160, Satz 240, Summary 500, Titel 150, Sprache 5 Zeichen; zusammen mit
   `maxItems: 12` rund 10 000 Zeichen, also unter 4 000 Tokens. Nächster Schritt: echten Lauf
   wiederholen (Diagnose-Test wurde gelöscht; `CvMatchingLiveTest` mit
   `-Dmatching.live=true` nutzt nur den Beispiel-CV). Prüfen, ob Ollama `maxLength`
   tatsächlich in die Grammatik übernimmt.
2. **Specs nachziehen:** `CvMatchSpecTest` prüft weder `maxItems` noch `maxLength`, und
   dass beides bei Azure (`bounded=false`) fehlt. `./gradlew check` seit dem Umbau nicht
   erneut gelaufen.
3. **Schleifen bleiben inhaltlich ein Problem.** Mit Grenzen wird die Antwort gültig, der
   Guard entfernt Dubletten – bei einer Schleife bleiben aber nur wenige Anforderungen übrig.
   Für brauchbare Ergebnisse ein größeres Modell setzen (`CV_MATCHING_MODEL`, z. B.
   `qwen2.5:7b` oder `:14b`); Laufzeit auf CPU beachten (Lesezeit 300 s).
4. **CHANGELOG/CLAUDE.md** noch nicht um `error.llm.truncated`, `maxItems`/`maxLength` und
   die Schleifen-Erkenntnis ergänzt.
5. Rückfragen an den Nutzer, noch unbeantwortet:
   - Grund für deaktiviertes „Passung prüfen“ direkt am Button anzeigen?
   - Sprache beim Hochladen standardmäßig = Oberflächensprache (heute immer Englisch,
     dadurch landet ein deutscher CV leicht als englischer Standard)?
   - Eigene Fehlertexte für den Abgleich (heute „Job posting extraction model …“)?
6. Helm-Templates mit `helm template` prüfen.

## Hinweise für die Fortsetzung

- Der echte CV liegt SSE-C-verschlüsselt in Garage; ohne `dev/.env`-Variablen im
  Test-JVM (Gradle mit `--no-daemon` nach `set -a; . dev/.env`) ist er nicht lesbar.
  Keine Kopie im Arbeitsbaum ablegen.
- Laufender `bootRun` muss nach Backend-Änderungen neu gestartet werden.
