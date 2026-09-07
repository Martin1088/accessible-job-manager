package de.samply.manager.security;

import de.samply.manager.exception.ApiException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.Security;
import java.util.Arrays;
import java.util.Locale;

/**
 * The one check a user-supplied URL passes before this server fetches it.
 *
 * <p>It used to exist twice, character for character, in
 * {@code JobPostingParserService} and {@code JobPostingSnapshotService}. Two
 * copies of a security control is one copy that gets hardened and one that does
 * not, which is why every caller now comes through here instead.
 *
 * <p>What it does <em>not</em> cover: the job posting snapshot is rendered by
 * Gotenberg, whose Chromium fetches the URL itself, from its own container and
 * following its own redirects. Nothing decided here reaches it. That gap is
 * closed on the network side - see the Gotenberg isolation section in
 * {@code Readme.md} - not in this class.
 */
@Component
public class OutboundUrlGuard {

    private final MessageSource messageSource;

    public OutboundUrlGuard(MessageSource messageSource,
                            @Value("${job-manager.outbound.dns-cache-seconds:30}") int dnsCacheSeconds) {
        this.messageSource = messageSource;
        pinDnsCache(dnsCacheSeconds);
    }

    /**
     * This class resolves a host, approves its addresses, and then hands back a
     * URI that the caller connects to <em>by name</em> - so the guarantee only
     * holds while the JVM's positive DNS cache outlives the gap between the two.
     * That is the JDK default (30s with no SecurityManager), but a container
     * platform that sets {@code -Dnetworkaddress.cache.ttl=0} for service
     * discovery would silently reopen the rebinding window, so the value is
     * pinned rather than assumed. A <em>positive</em> TTL is the safe direction
     * here, which is the opposite of the usual advice.
     *
     * <p>A request slower than the TTL is a residual window this cannot close;
     * the egress policy in the deployment notes is what covers that.
     */
    private static void pinDnsCache(int seconds) {
        Security.setProperty("networkaddress.cache.ttl", String.valueOf(seconds));
        Security.setProperty("networkaddress.cache.negative.ttl", "0");
    }

    /**
     * @return the parsed URI, when it is an http(s) URL whose host resolves
     *         entirely to addresses outside this network
     * @throws ApiException.BadRequest otherwise - with the reason, so the caller
     *         is not told to re-check a URL that was never the problem
     */
    public URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ApiException.BadRequest(message("error.url.empty"));
        }

        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new ApiException.BadRequest(message("error.url.malformed"));
        }

        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ApiException.BadRequest(message("error.url.scheme"));
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ApiException.BadRequest(message("error.url.host"));
        }

        rejectIfDisallowedHost(uri.getHost());
        return uri;
    }

    /**
     * Every address the host resolves to has to be acceptable, not merely the
     * first: a name that answers with one public and one private address would
     * otherwise get through on the strength of the public one.
     */
    private void rejectIfDisallowedHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new ApiException.BadRequest(message("error.url.hostUnresolved"));
        }
        for (InetAddress address : addresses) {
            if (isDisallowed(address)) {
                throw new ApiException.BadRequest(message("error.url.disallowedHost"));
            }
        }
    }

    private boolean isDisallowed(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        // The JDK's own predicates above miss several ranges, and miss all of
        // them when an IPv4 address arrives wrapped as ::ffff:a.b.c.d - an
        // Inet6Address, for which isSiteLocalAddress() is false even for
        // ::ffff:10.0.0.1. So the numeric table below decides, on the 4-byte
        // form wherever there is one.
        byte[] bytes = address.getAddress();
        byte[] ipv4 = asIpv4(bytes);
        return ipv4 != null ? isReservedIpv4(ipv4) : isUniqueLocalIpv6(bytes);
    }

    /** The 4-byte form of an IPv4 or IPv4-mapped IPv6 address; null for a real IPv6 one. */
    private static byte[] asIpv4(byte[] bytes) {
        if (bytes.length == 4) {
            return bytes;
        }
        if (bytes.length == 16 && isIpv4Mapped(bytes)) {
            return Arrays.copyOfRange(bytes, 12, 16);
        }
        return null;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) return false;
        }
        return (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
    }

    private static boolean isReservedIpv4(byte[] bytes) {
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;

        if (first == 0 || first == 10 || first == 127) return true;              // this-network, private, loopback
        if (first == 100 && second >= 64 && second <= 127) return true;          // CGNAT, 100.64.0.0/10
        if (first == 169 && second == 254) return true;                          // link-local, incl. cloud metadata
        if (first == 172 && second >= 16 && second <= 31) return true;           // private, 172.16.0.0/12
        if (first == 192 && second == 0 && third == 0) return true;              // IETF assignments, 192.0.0.0/24
        if (first == 192 && second == 168) return true;                          // private, 192.168.0.0/16
        if (first == 198 && (second == 18 || second == 19)) return true;         // benchmarking, 198.18.0.0/15
        return first >= 224;                                                     // multicast, reserved, broadcast
    }

    /** Unique local addresses, fc00::/7 - the IPv6 equivalent of the private ranges above. */
    private static boolean isUniqueLocalIpv6(byte[] bytes) {
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }
}
