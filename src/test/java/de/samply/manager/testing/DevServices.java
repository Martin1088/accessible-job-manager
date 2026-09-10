package de.samply.manager.testing;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.Optional;

/**
 * Where the local dev services are, and whether they are actually up.
 *
 * <p>Tests that need a real Gotenberg or Ollama skip themselves rather than
 * fail when the service is absent: their subject is what the service does with
 * a real input, and "not started" is a different statement from "broken". A
 * suite that went red on a stopped container would train people to ignore it.
 *
 * <p>The reachability check is a TCP connect with a short timeout rather than
 * an HTTP request, because it answers exactly the question being asked - is
 * something listening - without depending on any endpoint's shape or on the
 * service having finished its own startup.
 *
 * <p>Each URL is read from a system property first, then the environment
 * variable the application itself uses, then the same default
 * {@code application.yml} carries. Note that the system property only arrives
 * here because {@code build.gradle} forwards it into the test JVM; a bare
 * {@code -D} on the Gradle command line reaches the daemon, not this process.
 */
public final class DevServices {

    private static final int CONNECT_TIMEOUT_MS = 500;

    private DevServices() {}

    public static String gotenbergUrl() {
        return resolve("gotenberg.url", "GOTENBERG_URL", "http://localhost:3000");
    }

    public static String ollamaUrl() {
        return resolve("ollama.url", "OLLAMA_URL", "http://localhost:11434");
    }

    public static String ollamaModel() {
        return resolve("ollama.model", "OLLAMA_MODEL", "qwen2.5:3b");
    }

    public static boolean gotenbergReachable() {
        return reachable(gotenbergUrl());
    }

    public static boolean ollamaReachable() {
        return reachable(ollamaUrl());
    }

    /** True when something accepts a TCP connection at {@code url}'s host and port. */
    public static boolean reachable(String url) {
        URI uri = URI.create(url);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host(uri), port(uri)), CONNECT_TIMEOUT_MS);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static String host(URI uri) {
        return Optional.ofNullable(uri.getHost()).orElse("localhost");
    }

    private static int port(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String resolve(String systemProperty, String environmentVariable, String fallback) {
        return System.getProperty(systemProperty,
                System.getenv().getOrDefault(environmentVariable, fallback));
    }
}
