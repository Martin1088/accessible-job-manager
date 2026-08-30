#!/usr/bin/env python3
"""Refuses a sample cover letter that is not a tagged PDF.

The demo's one substantive claim is that the letter it produces is a tagged
PDF/UA document - that is what separates it from any tool that can also put text
on a page. A file that merely exists is not evidence of that: an export through
the LibreOffice route produces a perfectly readable PDF 1.4 with no structure
tree at all, and it looks identical to a sighted reviewer.

This is a necessary-conditions check, not a conformance check. veraPDF
(CoverLetterPdfUaTest) is the real verdict; this catches the file that never had
a chance of passing it, before it is published.

    python3 tools/check-sample-pdf.py public/demo/anschreiben-muster.pdf
"""
import sys

try:
    import pikepdf
except ImportError:
    sys.exit("pikepdf fehlt:  pip install pikepdf")

path = sys.argv[1] if len(sys.argv) > 1 else "public/demo/anschreiben-muster.pdf"

try:
    pdf = pikepdf.open(path)
except Exception as error:
    sys.exit(f"{path} liess sich nicht oeffnen: {error}")

root = pdf.Root
marked = bool(root.get("/MarkInfo", {}).get("/Marked", False))
problems = []

if "/StructTreeRoot" not in root:
    problems.append("kein /StructTreeRoot - das Dokument ist nicht getaggt")
if not marked:
    problems.append("/MarkInfo /Marked fehlt oder ist false")
if "/Lang" not in root:
    problems.append("kein /Lang im Katalog - Screenreader raten die Sprache")
if "/Metadata" not in root:
    problems.append("kein XMP-Metadatenpaket - ohne pdfuaid:part keine PDF/UA-Kennzeichnung")

print(f"{path}: PDF {pdf.pdf_version}, {len(pdf.pages)} Seite(n)")
if problems:
    print("\nFEHLGESCHLAGEN - das Muster ist kein getaggtes PDF:")
    for problem in problems:
        print(f"  - {problem}")
    print(
        "\nErzeugen ueber den HTML-Weg (Thymeleaf -> Chromium -> Gotenberg), nicht ueber\n"
        "den .docx-Weg: nur HtmlToPdfConverter sendet generateTaggedPdf. Danach mit\n"
        "veraPDF gegenpruefen."
    )
    sys.exit(1)

print("OK: getaggt, /Lang und XMP vorhanden. veraPDF entscheidet endgueltig.")
