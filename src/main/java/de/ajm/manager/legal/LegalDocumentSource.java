package de.ajm.manager.legal;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Where an operator's {@code <slug>.<lang>.yaml} files are read from: a directory, or an
 * object-storage bucket for a platform that cannot mount files into a container.
 *
 * <p>Both sides of the registry's failure policy work against this one seam - the same
 * load at startup, the same cheap fingerprint to notice an edit - so which kind of source
 * a deployment names changes where bytes come from and nothing else.
 */
interface LegalDocumentSource {

    /** One candidate file. The stream is opened only when the document is actually loaded. */
    record Entry(String filename, Opener opener) {
    }

    @FunctionalInterface
    interface Opener {
        InputStream open() throws IOException;
    }

    /** The property that names this source, for error messages: {@code job-manager.legal.…}. */
    String property();

    /** The configured location, for error messages: a directory or {@code bucket/prefix}. */
    String location();

    /** Every regular file or object, whatever it is called; the registry skips names that are not ours. */
    List<Entry> list() throws IOException;

    /** Changes whenever any candidate file changes; cheap enough to take on a page hit. */
    String fingerprint();
}
