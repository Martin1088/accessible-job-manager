package de.samply.manager.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Method security throws inside the controller invocation, so the exception is
 * offered to the advice before Spring Security's ExceptionTranslationFilter sees
 * it. Without an explicit handler the catch-all on Exception claims it and a
 * denied {@code @PreAuthorize} is reported as a server fault.
 * <p>
 * Driven standalone rather than through a slice: the filter chain also rejects
 * {@code /api/advisor/**} for a non-advisor, which would answer 403 whatever the
 * advice did and prove nothing about it.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @RestController
    static class ThrowingController {

        @GetMapping("/denied")
        String denied() {
            throw new AuthorizationDeniedException("Access Denied");
        }

        @GetMapping("/broken")
        String broken() {
            throw new IllegalStateException("something actually broke");
        }

        @PostMapping("/echo")
        String echo(@RequestBody Map<String, String> body) {
            return "ok";
        }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler(messages()))
                .build();
    }

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    @Test
    void aDeniedAuthorizationIsForbiddenNotAServerError() throws Exception {
        mvc.perform(get("/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void anActualFaultIsStillAServerError() throws Exception {
        mvc.perform(get("/broken"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    void anUnmappedPathIsNotFoundNotAServerError() throws Exception {
        mvc.perform(get("/no/such/endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void anUnreadableBodyIsABadRequestNotAServerError() throws Exception {
        mvc.perform(post("/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
