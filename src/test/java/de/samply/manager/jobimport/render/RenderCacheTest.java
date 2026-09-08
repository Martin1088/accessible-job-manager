package de.samply.manager.jobimport.render;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cache is what keeps one import at two Chromium renders rather than four,
 * so the cases that would silently double the render count are the ones worth
 * pinning: a miss on the wrong key, and an entry that outlives its usefulness.
 */
class RenderCacheTest {

    private static final byte[] PDF = "%PDF-1.7 rendered".getBytes(StandardCharsets.UTF_8);
    private static final String URL = "https://example.com/job";

    private static RenderCache cache(int ttlSeconds, int maxEntries, long maxBytes) {
        return new RenderCache(ttlSeconds, maxEntries, maxBytes);
    }

    @Test
    void servesASecondRequestForTheSameUrlProfileAndUser() {
        RenderCache cache = cache(120, 32, 1_000_000);
        cache.put("user-1", URL, RenderProfile.SNAPSHOT, PDF);

        assertThat(cache.get("user-1", URL, RenderProfile.SNAPSHOT)).isEqualTo(PDF);
    }

    /**
     * The two profiles produce different documents from one URL. Serving one
     * where the other was asked for would put a single-page extraction render
     * into someone's archive.
     */
    @Test
    void doesNotServeOneProfileForTheOther() {
        RenderCache cache = cache(120, 32, 1_000_000);
        cache.put("user-1", URL, RenderProfile.EXTRACTION, PDF);

        assertThat(cache.get("user-1", URL, RenderProfile.SNAPSHOT)).isNull();
    }

    @Test
    void doesNotServeOneUsersRenderToAnother() {
        RenderCache cache = cache(120, 32, 1_000_000);
        cache.put("user-1", URL, RenderProfile.SNAPSHOT, PDF);

        assertThat(cache.get("user-2", URL, RenderProfile.SNAPSHOT)).isNull();
    }

    @Test
    void forgetsAnEntryOnceItsTtlHasPassed() {
        RenderCache cache = cache(0, 32, 1_000_000);
        cache.put("user-1", URL, RenderProfile.SNAPSHOT, PDF);

        assertThat(cache.get("user-1", URL, RenderProfile.SNAPSHOT)).isNull();
    }

    @Test
    void evictsTheOldestEntryOnceTheCountLimitIsReached() {
        RenderCache cache = cache(120, 2, 1_000_000);
        cache.put("user-1", "https://example.com/a", RenderProfile.SNAPSHOT, PDF);
        cache.put("user-1", "https://example.com/b", RenderProfile.SNAPSHOT, PDF);
        cache.put("user-1", "https://example.com/c", RenderProfile.SNAPSHOT, PDF);

        assertThat(cache.get("user-1", "https://example.com/a", RenderProfile.SNAPSHOT)).isNull();
        assertThat(cache.get("user-1", "https://example.com/c", RenderProfile.SNAPSHOT)).isEqualTo(PDF);
    }

    /**
     * A render larger than the whole budget is not worth evicting everything
     * else for, so it is simply not cached - and must not corrupt the byte
     * accounting on its way past.
     */
    @Test
    void refusesToCacheARenderLargerThanTheWholeBudget() {
        RenderCache cache = cache(120, 32, 4);
        cache.put("user-1", URL, RenderProfile.SNAPSHOT, PDF);

        assertThat(cache.get("user-1", URL, RenderProfile.SNAPSHOT)).isNull();
    }

    /**
     * Re-rendering the same key must replace the entry rather than double-count
     * its bytes, or the byte budget drifts down until the cache holds nothing.
     */
    @Test
    void replacingAnEntryDoesNotLeakItsBytesFromTheBudget() {
        RenderCache cache = cache(120, 32, PDF.length * 2L);
        cache.put("user-1", URL, RenderProfile.SNAPSHOT, PDF);
        cache.put("user-1", URL, RenderProfile.SNAPSHOT, PDF);
        cache.put("user-1", "https://example.com/other", RenderProfile.SNAPSHOT, PDF);

        assertThat(cache.get("user-1", URL, RenderProfile.SNAPSHOT)).isEqualTo(PDF);
        assertThat(cache.get("user-1", "https://example.com/other", RenderProfile.SNAPSHOT)).isEqualTo(PDF);
    }
}
