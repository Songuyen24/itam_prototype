package com.company.itam.auth.security;

import com.company.itam.common.enums.AccountStatus;
import com.company.itam.role.entity.RoleEntity;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Filter {@code OncePerRequestFilter} xử lý JWT Bearer token trên mỗi request.
 *
 * <p>Đọc header {@code Authorization: Bearer <token>}, xác thực qua
 * {@link JwtTokenProvider} rồi thiết lập {@link org.springframework.security.core.Authentication}
 * vào {@link SecurityContextHolder} cho request hiện tại.</p>
 *
 * <p>Nếu token không hợp lệ hoặc user không tồn tại thì bỏ qua (để filter chain xử lý
 * {@code AuthenticationEntryPoint} sau).</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractBearerToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            try {
                String email = jwtTokenProvider.extractEmail(token);
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Dùng query có JOIN FETCH để nạp role ngay trong transaction, tránh lazy-init.
                    Optional<UserEntity> userOpt = userRepository.findByEmailWithDetails(email);
                    if (userOpt.isPresent()) {
                        UserEntity user = userOpt.get();
                        // Tài khoản đã khóa thì không cấp quyền truy cập
                        if (user.getAccountStatus() == AccountStatus.LOCKED) {
                            log.debug("Từ chối cấp quyền cho tài khoản bị khóa: {}", email);
                            filterChain.doFilter(request, response);
                            return;
                        }
                        RoleEntity role = user.getRole();
                        String roleCode = role != null && role.getCode() != null
                                ? role.getCode().name()
                                : null;
                        if (roleCode == null) {
                            log.debug("User {} không có role, bỏ qua xác thực", email);
                            filterChain.doFilter(request, response);
                            return;
                        }
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        email,
                                        null,
                                        List.of(new SimpleGrantedAuthority(roleCode))
                                );
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                        );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception ex) {
                log.warn("Không thể xác thực JWT: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
