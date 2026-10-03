import { LegalDocument, LegalSection } from '../../legal/legal-document.model';

/**
 * The demo's own legal documents.
 *
 * The demo is a deployment like any other, and it is the first consumer of the mechanism
 * that lets a deployment publish its own text. Its privacy statement had to be written
 * rather than copied: the bundled one names a database, an identity provider and S3
 * object storage, none of which exist here. DemoBackend answers every `/api/` call from
 * memory, so nothing a visitor types is transmitted anywhere - and saying otherwise on a
 * privacy page would be exactly the kind of false statement this feature exists to
 * prevent.
 */

const EMAIL = 'access.job.manager@gmail.com';

interface Copy {
  readonly privacyTitle: string;
  readonly privacyIntro: string;
  readonly nothingHeading: string;
  readonly nothingBody: string;
  readonly browserHeading: string;
  readonly browserIntro: string;
  readonly browserLanguage: string;
  readonly browserOutro: string;
  readonly realHeading: string;
  readonly realBody: string;

  readonly imprintTitle: string;
  readonly imprintIntro: string;
  readonly responsibleHeading: string;
  readonly name: string;
  readonly nameValue: string;
  readonly email: string;
  readonly natureHeading: string;
  readonly natureBody: string;
}

const COPY: Record<string, Copy> = {
  en: {
    privacyTitle: 'Privacy policy',
    privacyIntro: 'This is a demonstration of the Job Application Manager. It has no server and no account.',
    nothingHeading: 'What data is processed',
    nothingBody: 'Nothing you type here leaves your browser. The demo answers every request from sample data held in memory, so there is no database, no document storage and no sign-in. Reloading the page discards everything you entered and restores the samples.',
    browserHeading: 'What is stored in your browser',
    browserIntro: 'Exactly one thing outlives the page, in your browser’s local storage, readable by nobody but you:',
    browserLanguage: 'The interface language you picked.',
    browserOutro: 'Everything else — including your accessibility settings for contrast, text size and reduced motion — is held in memory for this visit only and is gone as soon as you reload.',
    realHeading: 'The real application',
    realBody: 'A real deployment signs you in through an identity provider, stores your applications in a database and your documents in object storage, and publishes its own privacy policy in place of this one.',
    imprintTitle: 'Legal notice',
    imprintIntro: 'Information according to § 5 DDG (Digital Services Act, Germany).',
    responsibleHeading: 'Responsible for the content of this demonstration',
    name: 'Name',
    nameValue: 'Martin Jurk',
    email: 'Email',
    natureHeading: 'Nature of this demonstration',
    natureBody: 'This is a privately operated, non-commercial demonstration of an open-source application. It runs entirely in your browser and is offered for evaluation, not as a service.'
  },
  de: {
    privacyTitle: 'Datenschutzerklärung',
    privacyIntro: 'Dies ist eine Demonstration des Job Application Manager. Sie hat keinen Server und kein Benutzerkonto.',
    nothingHeading: 'Welche Daten verarbeitet werden',
    nothingBody: 'Nichts, was Sie hier eingeben, verlässt Ihren Browser. Die Demo beantwortet jede Anfrage aus Beispieldaten im Arbeitsspeicher; es gibt keine Datenbank, keinen Dokumentenspeicher und keine Anmeldung. Ein Neuladen der Seite verwirft alle Eingaben und stellt die Beispiele wieder her.',
    browserHeading: 'Was in Ihrem Browser gespeichert wird',
    browserIntro: 'Genau eine Angabe überdauert die Seite, im lokalen Speicher Ihres Browsers und für niemanden außer Ihnen lesbar:',
    browserLanguage: 'Die von Ihnen gewählte Oberflächensprache.',
    browserOutro: 'Alles andere – auch Ihre Barrierefreiheits-Einstellungen für Kontrast, Schriftgröße und reduzierte Bewegung – wird nur für diesen Besuch im Arbeitsspeicher gehalten und ist nach einem Neuladen verschwunden.',
    realHeading: 'Die echte Anwendung',
    realBody: 'Eine echte Installation meldet Sie über einen Identitätsanbieter an, speichert Ihre Bewerbungen in einer Datenbank und Ihre Dokumente in einem Objektspeicher – und veröffentlicht ihre eigene Datenschutzerklärung anstelle dieser hier.',
    imprintTitle: 'Impressum',
    imprintIntro: 'Angaben gemäß § 5 DDG (Digitale-Dienste-Gesetz).',
    responsibleHeading: 'Verantwortlich für den Inhalt dieser Demonstration',
    name: 'Name',
    nameValue: 'Martin Jurk',
    email: 'E-Mail',
    natureHeading: 'Art dieser Demonstration',
    natureBody: 'Dies ist eine privat betriebene, nicht-kommerzielle Demonstration einer Open-Source-Anwendung. Sie läuft vollständig in Ihrem Browser und dient der Erprobung, nicht als Dienst.'
  },
  nl: {
    privacyTitle: 'Privacyverklaring',
    privacyIntro: 'Dit is een demonstratie van de Job Application Manager. Er is geen server en geen account.',
    nothingHeading: 'Welke gegevens worden verwerkt',
    nothingBody: 'Niets van wat u hier invoert verlaat uw browser. De demo beantwoordt elk verzoek met voorbeeldgegevens uit het werkgeheugen; er is geen database, geen documentopslag en geen aanmelding. De pagina opnieuw laden wist alles wat u hebt ingevoerd en herstelt de voorbeelden.',
    browserHeading: 'Wat in uw browser wordt bewaard',
    browserIntro: 'Precies één gegeven blijft na het sluiten van de pagina bestaan, in de lokale opslag van uw browser en door niemand anders dan u te lezen:',
    browserLanguage: 'De door u gekozen taal van de interface.',
    browserOutro: 'Al het overige – ook uw toegankelijkheidsinstellingen voor contrast, tekstgrootte en verminderde beweging – wordt alleen voor dit bezoek in het werkgeheugen bewaard en is na het herladen verdwenen.',
    realHeading: 'De echte toepassing',
    realBody: 'Een echte installatie meldt u aan via een identiteitsprovider, bewaart uw sollicitaties in een database en uw documenten in objectopslag, en publiceert haar eigen privacyverklaring in plaats van deze.',
    imprintTitle: 'Colofon',
    imprintIntro: 'Gegevens conform § 5 DDG (Duitse wet inzake digitale diensten).',
    responsibleHeading: 'Verantwoordelijk voor de inhoud van deze demonstratie',
    name: 'Naam',
    nameValue: 'Martin Jurk',
    email: 'E-mail',
    natureHeading: 'Aard van deze demonstratie',
    natureBody: 'Dit is een particulier beheerde, niet-commerciële demonstratie van een opensource-toepassing. Zij draait volledig in uw browser en is bedoeld om uit te proberen, niet als dienst.'
  },
  es: {
    privacyTitle: 'Política de privacidad',
    privacyIntro: 'Esta es una demostración del Job Application Manager. No tiene servidor ni cuenta de usuario.',
    nothingHeading: 'Qué datos se tratan',
    nothingBody: 'Nada de lo que escriba aquí sale de su navegador. La demostración responde a cada solicitud con datos de ejemplo guardados en memoria; no hay base de datos, ni almacenamiento de documentos, ni inicio de sesión. Al recargar la página se descarta todo lo introducido y se restauran los ejemplos.',
    browserHeading: 'Qué se guarda en su navegador',
    browserIntro: 'Solo un dato sobrevive a la página, en el almacenamiento local de su navegador y legible únicamente por usted:',
    browserLanguage: 'El idioma de la interfaz que haya elegido.',
    browserOutro: 'Todo lo demás — incluidos sus ajustes de accesibilidad de contraste, tamaño del texto y movimiento reducido — se mantiene en memoria solo durante esta visita y desaparece al recargar.',
    realHeading: 'La aplicación real',
    realBody: 'Una instalación real le identifica mediante un proveedor de identidad, guarda sus candidaturas en una base de datos y sus documentos en almacenamiento de objetos, y publica su propia política de privacidad en lugar de esta.',
    imprintTitle: 'Aviso legal',
    imprintIntro: 'Información conforme al § 5 DDG (Ley alemana de servicios digitales).',
    responsibleHeading: 'Responsable del contenido de esta demostración',
    name: 'Nombre',
    nameValue: 'Martin Jurk',
    email: 'Correo electrónico',
    natureHeading: 'Naturaleza de esta demostración',
    natureBody: 'Se trata de una demostración privada y no comercial de una aplicación de código abierto. Funciona íntegramente en su navegador y se ofrece para su evaluación, no como servicio.'
  }
};

type Body = Omit<LegalDocument, 'slug' | 'requestedLanguage'>;

function privacy(language: string, copy: Copy): Body {
  const sections: LegalSection[] = [
    { heading: copy.nothingHeading, blocks: [{ type: 'paragraph', text: copy.nothingBody }] },
    {
      heading: copy.browserHeading,
      blocks: [
        { type: 'paragraph', text: copy.browserIntro },
        { type: 'list', items: [copy.browserLanguage] },
        // Accessibility settings deliberately are NOT claimed as stored: they go through
        // PreferencesService into DemoDb, which holds them in memory for the visit. The
        // only localStorage key this app writes is `lang` (LanguageService).
        { type: 'paragraph', text: copy.browserOutro }
      ]
    },
    { heading: copy.realHeading, blocks: [{ type: 'paragraph', text: copy.realBody }] }
  ];
  return { language, title: copy.privacyTitle, intro: [copy.privacyIntro], sections };
}

function imprint(language: string, copy: Copy): Body {
  const sections: LegalSection[] = [
    {
      heading: copy.responsibleHeading,
      blocks: [{
        type: 'definitions',
        items: [
          { term: copy.name, value: copy.nameValue },
          { term: copy.email, value: EMAIL, href: `mailto:${EMAIL}` }
        ]
      }]
    },
    { heading: copy.natureHeading, blocks: [{ type: 'paragraph', text: copy.natureBody }] }
  ];
  return { language, title: copy.imprintTitle, intro: [copy.imprintIntro], sections };
}

function build(make: (language: string, copy: Copy) => Body): Record<string, Body> {
  return Object.fromEntries(Object.entries(COPY).map(([language, copy]) => [language, make(language, copy)]));
}

export const DEMO_LEGAL: Record<string, Record<string, Body>> = {
  datenschutz: build(privacy),
  impressum: build(imprint)
};
