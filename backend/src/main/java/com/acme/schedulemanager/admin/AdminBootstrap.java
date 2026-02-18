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

    @Value("${app.admin.seed-nickname:관리자}")
    private String adminNickname;

    public AdminBootstrap(UserAccountRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void seedAdmin() {
        if (!seedEnabled) return;
        var existing = userRepo.findByEmail(adminEmail.toLowerCase());
        if (existing.isPresent()) {
            UserAccount user = existing.get();
            if (!"ADMIN".equals(user.getRole())) {
                user.setRole("ADMIN");
            }
            user.setPasswordHash(passwordEncoder.encode(adminPassword));
            if (user.getNickname() == null || user.getNickname().isBlank()) {
                user.setNickname(adminNickname);
            }
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            userRepo.save(user);
            return;
        }

        UserAccount admin = new UserAccount();
        admin.setEmail(adminEmail.toLowerCase());
        admin.setNickname(adminNickname);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        admin.setFailedLoginCount(0);
        userRepo.save(admin);
    }
}
