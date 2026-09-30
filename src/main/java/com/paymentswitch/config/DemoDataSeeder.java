package com.paymentswitch.config;

import com.paymentswitch.model.AppUser;
import com.paymentswitch.model.Role;
import com.paymentswitch.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the two demo accounts documented in the README. Disable with
 * {@code app.seed-demo-users=false} for anything other than local demos.
 */
@Component
@ConditionalOnProperty(prefix = "app", name = "seed-demo-users", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seed("admin", "admin123", Role.ADMIN);
        seed("operator", "operator123", Role.OPERATOR);
    }

    private void seed(String username, String rawPassword, Role role) {
        if (users.findByUsername(username).isEmpty()) {
            users.save(new AppUser(username, passwordEncoder.encode(rawPassword), role));
            log.info("Seeded demo user '{}' with role {}", username, role);
        }
    }
}
