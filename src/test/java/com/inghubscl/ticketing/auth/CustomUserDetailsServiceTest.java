package com.inghubscl.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.inghubscl.ticketing.user.Role;
import com.inghubscl.ticketing.user.User;
import com.inghubscl.ticketing.user.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomUserDetailsServiceTest {

  @Mock UserRepository userRepository;
  @InjectMocks CustomUserDetailsService service;

  @Test
  void mapsRolesToRolePrefixedAuthorities() {
    User user = new User("u@e.com", "encoded", Set.of(Role.ADMIN, Role.ORGANIZER));
    setId(user, java.util.UUID.randomUUID());
    when(userRepository.findByEmail("u@e.com")).thenReturn(Optional.of(user));

    var details = service.loadUserByUsername("u@e.com");

    assertThat(details.getPassword()).isEqualTo("encoded");
    assertThat(details.getAuthorities())
        .extracting(a -> a.getAuthority())
        .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORGANIZER");
  }

  @Test
  void throwsForUnknownEmail() {
    when(userRepository.findByEmail("missing@e.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loadUserByUsername("missing@e.com"))
        .isInstanceOf(UsernameNotFoundException.class);
  }

  private static void setId(User user, java.util.UUID id) {
    try {
      var f = User.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(user, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
