package de.samply.manager.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SecurityRolesPropertiesTest} exercises the compact constructor directly;
 * this proves Spring Boot's actual property binder calls it during context startup
 * and that a validation failure is fatal to boot, the way {@code job-manager.security}
 * env vars would reach it in a real deployment.
 */
class SecurityRolesPropertiesWiringTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityRolesProperties.class)
    static class SecurityRolesConfiguration {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityRolesConfiguration.class);

    @Test
    void defaultConfigurationStartsTheContext() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SecurityRolesProperties.class).roleGroups())
                    .containsEntry(AppRole.ADVISOR, java.util.List.of("Advisor"));
        });
    }

    @Test
    void anUnmappedRoleFailsStartup() {
        runner.withPropertyValues(
                "job-manager.security.role-groups.USER=User",
                "job-manager.security.role-groups.REVIEWER=Reviewer"
                // ADVISOR deliberately absent
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("job-manager.security.role-groups.ADVISOR is not mapped to any"
                            + " identity provider group. Every role must be held by at least one group,"
                            + " otherwise nobody can be granted it.");
        });
    }

    @Test
    void aGroupMappedToTwoRolesFailsStartup() {
        runner.withPropertyValues(
                "job-manager.security.role-groups.USER=Staff",
                "job-manager.security.role-groups.ADVISOR=Staff",
                "job-manager.security.role-groups.REVIEWER=Reviewer"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .rootCause().hasMessageContaining("exactly one role");
        });
    }

    @Test
    void ablankGroupValueFailsStartup() {
        runner.withPropertyValues(
                "job-manager.security.role-groups.USER=User",
                "job-manager.security.role-groups.ADVISOR=  ",
                "job-manager.security.role-groups.REVIEWER=Reviewer"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void severalGroupValuesBindFromACommaSeparatedEnvStyleValue() {
        runner.withPropertyValues(
                "job-manager.security.role-groups.USER=User",
                "job-manager.security.role-groups.ADVISOR=Advisor,Berater",
                "job-manager.security.role-groups.REVIEWER=Reviewer"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SecurityRolesProperties.class).roleGroups())
                    .containsEntry(AppRole.ADVISOR, java.util.List.of("Advisor", "Berater"));
        });
    }

    @Test
    void anEntraObjectIdBindsAsAnOrdinaryGroupValue() {
        String guid = "8f2c1d9e-4b7a-4a1e-9c33-2f7b5a0d61ce";
        runner.withPropertyValues(
                "job-manager.security.claim=roles",
                "job-manager.security.role-groups.USER=User",
                "job-manager.security.role-groups.ADVISOR=" + guid,
                "job-manager.security.role-groups.REVIEWER=Reviewer"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            SecurityRolesProperties properties = context.getBean(SecurityRolesProperties.class);
            assertThat(properties.claim()).isEqualTo("roles");
            assertThat(properties.groupToRole()).containsEntry(guid.toLowerCase(), AppRole.ADVISOR);
        });
    }

    @Test
    void anUnknownRoleKeyFailsStartup() {
        runner.withPropertyValues(
                "job-manager.security.role-groups.USER=User",
                "job-manager.security.role-groups.ADVISOR=Advisor",
                "job-manager.security.role-groups.REVIEWER=Reviewer",
                "job-manager.security.role-groups.SUPERADMIN=Everything"
        ).run(context -> assertThat(context).hasFailed());
    }
}
