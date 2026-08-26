package de.samply.manager.controller;

import de.samply.manager.security.GroupsGrantedAuthoritiesMapper;
import de.samply.manager.security.RoleCheckSuccessHandler;
import de.samply.manager.security.SecurityConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Every route the Angular router knows has to survive a hard reload, which means
 * {@link WebController} must forward it to {@code index.html}. Its mappings only reach
 * four path segments and reject any segment containing a dot, so a deeper or
 * dot-bearing route added on the frontend would 404 on refresh while working fine
 * during in-app navigation - a break that only shows up in production.
 * <p>
 * The route list is read from {@code app.routes.ts} rather than restated here, so
 * adding a route on the frontend is what fails this test, not forgetting to update it.
 */
@WebMvcTest(WebController.class)
@Import(SecurityConfig.class)
class SpaRouteCoverageTest {

    private static final Path ROUTES = Path.of("AppClient/src/app/app.routes.ts");
    private static final Pattern PATH_LITERAL = Pattern.compile("path:\\s*'([^']*)'");

    @Autowired MockMvc mvc;

    @MockitoBean RoleCheckSuccessHandler roleCheckSuccessHandler;
    @MockitoBean GroupsGrantedAuthoritiesMapper groupsGrantedAuthoritiesMapper;

    @Test
    void everyAngularRouteIsForwardedToIndexOnAHardReload() throws Exception {
        List<String> routes = declaredRoutes();
        assertThat(routes).isNotEmpty();

        List<String> notForwarded = new ArrayList<>();
        for (String route : routes) {
            String url = "/" + route.replaceAll(":[^/]+", "42");
            String forwarded = mvc.perform(get(url).with(oidcLogin()))
                    .andReturn().getResponse().getForwardedUrl();
            if (!"/index.html".equals(forwarded)) {
                notForwarded.add(url + " -> " + forwarded);
            }
        }

        assertThat(notForwarded)
                .describedAs("Angular routes that WebController does not forward to index.html")
                .isEmpty();
    }

    private static List<String> declaredRoutes() throws IOException {
        Assumptions.assumeTrue(Files.exists(ROUTES),
                "app.routes.ts not readable from the working directory; skipping");

        List<String> routes = new ArrayList<>();
        Matcher matcher = PATH_LITERAL.matcher(Files.readString(ROUTES));
        while (matcher.find()) {
            String path = matcher.group(1);
            if (!path.isEmpty() && !path.equals("**")) {
                routes.add(path);
            }
        }
        return routes;
    }
}
