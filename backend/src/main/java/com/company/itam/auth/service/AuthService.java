package com.company.itam.auth.service;

import com.company.itam.auth.dto.request.LoginRequest;
import com.company.itam.auth.dto.response.CurrentUserResponse;
import com.company.itam.auth.dto.response.LoginResponse;
import com.company.itam.auth.dto.response.LoginUserInfo;
import com.company.itam.auth.security.JwtTokenProvider;
import com.company.itam.common.exception.AppException;
import com.company.itam.role.entity.RoleEntity;
import com.company.itam.user.entity.UserEntity;
import com.company.itam.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xử lý nghiệp vụ xác thực: đăng nhập, lấy thông tin người dùng hiện tại, đăng xuất.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String INVALID_CREDENTIALS = "LOGIN_INVALID_CREDENTIALS";
    private static final String ACCOUNT_LOCKED = "LOGIN_ACCOUNT_LOCKED";
    private static final String UNAUTHORIZED = "UNAUTHORIZED";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Xác thực email/password, sinh JWT và trả về thông tin người dùng.
     *
     * @param request yêu cầu đăng nhập
     * @return JWT kèm thông tin user
     * @throws AppException 401 nếu thông tin đăng nhập không hợp lệ
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.debug("Đăng nhập thất bại: email '{}' không tồn tại", email);
                    return new AppException(
                            HttpStatus.UNAUTHORIZED,
                            INVALID_CREDENTIALS,
                            "Invalid email or password"
                    );
                });

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.debug("Đăng nhập thất bại: mật khẩu không khớp cho email '{}'", email);
            throw new AppException(
                    HttpStatus.UNAUTHORIZED,
                    INVALID_CREDENTIALS,
                    "Invalid email or password"
            );
        }

        if (user.getAccountStatus() != null && user.getAccountStatus().name().equals("LOCKED")) {
            log.debug("Từ chối đăng nhập: tài khoản '{}' đã bị khóa", email);
            throw new AppException(
                    HttpStatus.UNAUTHORIZED,
                    ACCOUNT_LOCKED,
                    "Account has been locked"
            );
        }

        RoleEntity role = user.getRole();
        String roleCode = role != null && role.getCode() != null ? role.getCode().name() : "USER";
        String token = jwtTokenProvider.generateToken(user.getEmail(), roleCode);

        LoginUserInfo info = new LoginUserInfo(
                user.getUserId(),
                user.getEmail(),
                user.getFullName(),
                roleCode
        );
        return new LoginResponse(token, info);
    }

    /**
     * Lấy thông tin user hiện tại từ SecurityContext.
     *
     * @return thông tin user
     * @throws AppException 401 nếu chưa đăng nhập
     */
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())
                || authentication.getName() == null) {
            throw new AppException(
                    HttpStatus.UNAUTHORIZED,
                    UNAUTHORIZED,
                    "Authentication required"
            );
        }
        String email = authentication.getName();
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(
                        HttpStatus.UNAUTHORIZED,
                        UNAUTHORIZED,
                        "User not found"
                ));
        return CurrentUserResponse.fromEntity(user);
    }

    /**
     * Đăng xuất: với prototype, đơn giản trả về thành công. Client phải xóa token.
     */
    public void logout() {
        SecurityContextHolder.clearContext();
    }
}
