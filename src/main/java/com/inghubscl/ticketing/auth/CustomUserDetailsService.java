package com.inghubscl.ticketing.auth;

import com.inghubscl.ticketing.user.User;
import com.inghubscl.ticketing.user.UserRepository;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  public CustomUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String email) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    Collection<GrantedAuthority> authorities =
        user.getRoles().stream()
            .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.name()))
            .toList();
    return new org.springframework.security.core.userdetails.User(
        user.getId().toString(), user.getPasswordHash(), authorities);
  }
}
