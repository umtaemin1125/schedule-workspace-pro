package com.acme.schedulemanager.admin;

import com.acme.schedulemanager.domain.entity.UserAccount;
import com.acme.schedulemanager.domain.repo.UserAccountRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap {
    private final UserAccountRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.seed-email:admin@example.com}")
    private String adminEmail;

    @Value("${app.admin.seed-password:Admin1234!}")
    private String adminPassword;

    @Value("${app.admin.seed-enabled:true}")
    private boolean seedEnabled;

    public AdminBootstrap(UserAccountRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void seedAdmin() {
        if (!seedEnabled) return;
        if (userRepo.findByEmail(adminEmail.toLowerCase()).isPresent()) return;

        UserAccount admin = new UserAccount();
        admin.setEmail(adminEmail.toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        admin.setFailedLoginCount(0);
        userRepo.save(admin);
    }
}
