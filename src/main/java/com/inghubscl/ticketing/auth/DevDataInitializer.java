package com.inghubscl.ticketing.auth;

import com.inghubscl.ticketing.user.Role;
import com.inghubscl.ticketing.user.User;
import com.inghubscl.ticketing.user.UserRepository;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevDataInitializer implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public DevDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(String... args) {
    seedIfMissing("admin@example.com", "Admin123!", Role.ADMIN);
    seedIfMissing("organizer@example.com", "Organizer123!", Role.ORGANIZER);
    seedIfMissing("customer@example.com", "Customer123!", Role.CUSTOMER);
  }

  private void seedIfMissing(String email, String rawPassword, Role role) {
    if (userRepository.existsByEmail(email)) {
      return;
    }
    User user = new User(email, passwordEncoder.encode(rawPassword), Set.of(role));
    userRepository.save(user);
    log.info("Seeded {} user: {}", role, email);
  }
}
