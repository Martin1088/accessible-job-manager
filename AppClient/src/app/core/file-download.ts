import { HttpResponse } from '@angular/common/http';

/**
 * Saves a blob response the browser fetched itself, rather than pointing a link at
 * the endpoint. Going through HttpClient is what makes the request carry the session
 * cookie and lets a failure surface as an error instead of a broken tab, so the file
 * has to be handed to the user from memory once it has arrived.
 */
export function saveBlobResponse(response: HttpResponse<Blob>, fallbackFilename: string): void {
  const filename = filenameFrom(response.headers.get('Content-Disposition')) ?? fallbackFilename;
  const url = URL.createObjectURL(response.body!);
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
