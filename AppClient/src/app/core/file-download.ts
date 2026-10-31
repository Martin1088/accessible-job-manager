import { HttpResponse } from '@angular/common/http';

/**
 * Saves a blob response the browser fetched itself, rather than pointing a link at
 * the endpoint. Going through HttpClient is what makes the request carry the session
 * cookie and lets a failure surface as an error instead of a broken tab, so the file
 * has to be handed to the user from memory once it has arrived.
 */
export function saveBlobResponse(response: HttpResponse<Blob>, fallbackFilename: string): void {
  saveBlob(response.body!, filenameFrom(response.headers.get('Content-Disposition')) ?? fallbackFilename);
}

/** Hands a blob already in memory to the user as a file named `filename`. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

/** The filename the server named in Content-Disposition, quoted or bare. */
export function filenameFrom(contentDisposition: string | null): string | null {
  return contentDisposition ? /filename="?([^";]+)"?/.exec(contentDisposition)?.[1]?.trim() ?? null : null;
}
