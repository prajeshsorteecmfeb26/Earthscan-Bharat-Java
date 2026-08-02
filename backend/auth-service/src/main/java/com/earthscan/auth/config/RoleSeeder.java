package com.earthscan.auth.config;

import com.earthscan.auth.domain.Role;
import com.earthscan.auth.domain.User;
import com.earthscan.auth.repository.RoleRepository;
import com.earthscan.auth.repository.UserRepository;
import com.earthscan.common.security.RoleName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Populates the {@code roles} lookup table and, on an empty database, one bootstrap administrator.
 *
 * <p>Runs on every startup but is idempotent, so it is safe against an existing database. The
 * bootstrap admin exists because the chicken-and-egg problem is otherwise unsolvable: creating an
 * admin through {@code /api/admin/users} requires already being an admin.</p>
 */
@Component
public class RoleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleSeeder.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedAdmin;
    private final String adminEmail;
    private final String adminPassword;

    public RoleSeeder(RoleRepository roleRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${earthscan.bootstrap.seed-admin:true}") boolean seedAdmin,
                      @Value("${earthscan.bootstrap.admin-email:admin@earthscan.in}") String adminEmail,
                      @Value("${earthscan.bootstrap.admin-password:}") String adminPassword) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedAdmin = seedAdmin;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        if (seedAdmin) {
            seedBootstrapAdmin();
        }
    }

    private void seedRoles() {
        for (RoleName roleName : RoleName.values()) {
            if (!roleRepository.existsByName(roleName)) {
                roleRepository.save(new Role(roleName, describe(roleName)));
                log.info("Seeded role {}", roleName);
            }
        }
    }

    private void seedBootstrapAdmin() {
        // Seed Admin
        if (!userRepository.existsByEmailIgnoreCase(adminEmail) && adminPassword != null && !adminPassword.isBlank()) {
            Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
            User admin = new User("Platform Administrator", adminEmail.toLowerCase(), passwordEncoder.encode(adminPassword));
            admin.assignRole(adminRole);
            userRepository.save(admin);
            log.info("Created bootstrap administrator {}.", adminEmail);
        }

        // Seed Demo Farmer
        if (!userRepository.existsByEmailIgnoreCase("farmer@earthscan.in")) {
            Role farmerRole = roleRepository.findByName(RoleName.FARMER).orElseThrow();
            User farmer = new User("Rajesh Kumar", "farmer@earthscan.in", passwordEncoder.encode("Farmer@123"));
            farmer.assignRole(farmerRole);
            userRepository.save(farmer);
            log.info("Seeded demo farmer@earthscan.in");
        }

        // Seed Demo Buyer
        if (!userRepository.existsByEmailIgnoreCase("buyer@earthscan.in")) {
            Role buyerRole = roleRepository.findByName(RoleName.LAND_BUYER).orElseThrow();
            User buyer = new User("Priya Sharma", "buyer@earthscan.in", passwordEncoder.encode("Buyer@123"));
            buyer.assignRole(buyerRole);
            userRepository.save(buyer);
            log.info("Seeded demo buyer@earthscan.in");
        }

        // Seed Demo Expert
        if (!userRepository.existsByEmailIgnoreCase("expert@earthscan.in")) {
            Role expertRole = roleRepository.findByName(RoleName.AGRICULTURE_EXPERT).orElseThrow();
            User expert = new User("Dr. Anita Desai", "expert@earthscan.in", passwordEncoder.encode("Expert@123"));
            expert.assignRole(expertRole);
            userRepository.save(expert);
            log.info("Seeded demo expert@earthscan.in");
        }
    }

    private String describe(RoleName roleName) {
        return switch (roleName) {
            case FARMER -> "Owns or cultivates land; uses water, crop and mandi advisory tools";
            case LAND_BUYER -> "Searches, compares and analyses land listings for investment";
            case AGRICULTURE_EXPERT -> "Answers farmer queries and curates crop reference data";
            case ADMIN -> "Full platform administration including user management";
        };
    }
}
