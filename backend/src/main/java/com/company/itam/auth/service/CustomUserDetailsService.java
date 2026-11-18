package com.company.itam.auth.service;

import java.util.List;
import java.util.Locale;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.company.itam.role.entity.RoleEntity;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;

/**
 * Uses role codes as authorities to match the controllers' {@code hasAnyAuthority} checks.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Load role and department before the repository transaction closes.
        UserEntity user = userRepository
                .findByLogin(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng: " + email));

        RoleEntity role = user.getRole();
        String roleCode =
                role != null && role.getCode() != null ? role.getCode().name() : "USER";

        return User.withUsername("user:" + user.getUserId())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .authorities(List.of(new SimpleGrantedAuthority(roleCode)))
                .accountLocked(user.isLoginBlocked())
                .disabled(!user.canLogin())
                .build();
    }
}
