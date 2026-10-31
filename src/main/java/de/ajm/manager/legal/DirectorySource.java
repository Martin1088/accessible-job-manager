package de.ajm.manager.legal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Reads the documents from a directory, e.g. a mounted ConfigMap. */
final class DirectorySource implements LegalDocumentSource {

    private final Path dir;

    DirectorySource(Path dir) {
        this.dir = dir;
    }

    @Override
    public String property() {
        return "job-manager.legal.documents-dir";
    }

    @Override
    public String location() {
        return dir.toString();
    }

    @Override
    public List<Entry> list() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(file -> new Entry(file.getFileName().toString(), () -> Files.newInputStream(file)))
                    .toList();
        }
    }

    /** Name, size and mtime of every candidate file - enough to notice any edit, cheap to take. */
    @Override
    public String fingerprint() {
        StringBuilder out = new StringBuilder();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> LegalDocumentLoader.keyOf(file.getFileName().toString()) != null)
                    .sorted()
                    .forEach(file -> {
                        try {
                            out.append(file.getFileName()).append(':')
                                    .append(Files.size(file)).append(':')
                                    .append(Files.getLastModifiedTime(file).toMillis()).append('\n');
                        } catch (IOException e) {
                            out.append(file.getFileName()).append(":unreadable\n");
                        }
                    });
        } catch (IOException e) {
            return "unreadable";
        }
        return out.toString();
    }
}
