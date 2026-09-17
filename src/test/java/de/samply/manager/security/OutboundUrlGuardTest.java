package de.samply.manager.security;

import de.samply.manager.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The validator used to live twice, in the parser and the snapshot service, and
 * only the parser's copy was under test. These are that test's cases plus the
 * ranges neither copy covered.
 */
class OutboundUrlGuardTest {

    private final OutboundUrlGuard guard = new OutboundUrlGuard(messages(), 30);

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/",
            "http://localhost/",
            "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.5/",
            "http://192.168.1.1/",
            "http://172.16.0.1/",
            "http://0.0.0.0/",
    })
    void rejectsTheAddressRangesTheJdkPredicatesAlreadyCovered(String url) {
        assertThatThrownBy(() -> guard.validate(url))
                .isInstanceOf(ApiException.BadRequest.class)
                .extracting(e -> ((ApiException) e).getStatus().value())
                .isEqualTo(400);
    }

    /**
     * None of these are caught by isSiteLocalAddress/isLinkLocalAddress, which is
     * why the guard carries its own numeric table. 100.64/10 is carrier-grade
     * NAT - the range a cloud provider's internal fabric often sits on.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://100.64.0.1/",     // CGNAT, 100.64.0.0/10
            "http://100.127.255.1/",  // still CGNAT, top of the range
            "http://192.0.0.1/",      // IETF protocol assignments, 192.0.0.0/24
            "http://198.18.0.1/",     // benchmarking, 198.18.0.0/15
            "http://198.19.255.1/",   // still benchmarking
            "http://240.0.0.1/",      // reserved
            "http://255.255.255.255/",
    })
    void rejectsTheRangesTheJdkPredicatesMiss(String url) {
        assertThatThrownBy(() -> guard.validate(url))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    /**
     * ::ffff:10.0.0.1 is an IPv4 address wearing an IPv6 coat. Depending on how
     * it arrives, isSiteLocalAddress() may be asked about an Inet6Address and
     * answer false, so the guard re-checks the embedded four bytes.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://[::1]/",
            "http://[fc00::1]/",
            "http://[fd00::1]/",
            "http://[::ffff:10.0.0.1]/",
            "http://[::ffff:127.0.0.1]/",
            "http://[::ffff:169.254.169.254]/",
    })
    void rejectsIpv6LoopbackUniqueLocalAndIpv4Mapped(String url) {
        assertThatThrownBy(() -> guard.validate(url))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/",
            "file:///etc/passwd",
            "gopher://example.com/",
            "not a url",
            "",
    })
    void rejectsAnythingThatIsNotAnHttpUrl(String url) {
        assertThatThrownBy(() -> guard.validate(url))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    void rejectsANullUrl() {
        assertThatThrownBy(() -> guard.validate(null))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("must not be empty");
    }

    /**
     * A public literal has to survive, or the guard has simply broken the
     * feature. Uses an address rather than a name so the test needs no DNS.
     */
    @Test
    void allowsAPublicAddress() {
        assertThatCode(() -> guard.validate("https://93.184.216.34/jobs/1"))
                .doesNotThrowAnyException();

        assertThat(guard.validate("https://93.184.216.34/jobs/1").getPath()).isEqualTo("/jobs/1");
    }

    /**
     * The guard's own promise - that the address it approved is the one the
     * caller will reach - depends on the JVM reusing the resolution rather than
     * asking again, so it pins the cache instead of trusting the default.
     */
    @Test
    void pinsAPositiveDnsCacheTtl() {
        new OutboundUrlGuard(messages(), 45);

        assertThat(java.security.Security.getProperty("networkaddress.cache.ttl")).isEqualTo("45");
        assertThat(java.security.Security.getProperty("networkaddress.cache.negative.ttl")).isEqualTo("0");
    }
}
