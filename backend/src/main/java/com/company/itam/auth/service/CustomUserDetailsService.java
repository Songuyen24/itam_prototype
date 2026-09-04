package com.company.itam.auth.service;

import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Cung cấp {@link UserDetails} cho Spring Security dựa trên bảng {@code users}.
 *
 * <p>Authority được sinh từ mã role (ví dụ: {@code ADMIN}, {@code IT_STAFF}, {@code PUR_STAFF},
 * {@code USER}) để tương thích với các {@code @PreAuthorize("hasAnyAuthority(...)")}
 * đã được dùng trong các controller hiện hữu.</p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(email == null ? "" : email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng: " + email));

        String roleCode = user.getRole() != null && user.getRole().getCode() != null
                ? user.getRole().getCode().name()
                : "USER";

        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .authorities(List.of(new SimpleGrantedAuthority(roleCode)))
                .accountLocked(user.getAccountStatus() != null
                        && user.getAccountStatus().name().equals("LOCKED"))
                .disabled(user.getAccountStatus() != null
                        && user.getAccountStatus().name().equals("LOCKED"))
                .build();
    }
}
