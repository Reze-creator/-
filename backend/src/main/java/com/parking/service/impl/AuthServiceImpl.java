package com.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.parking.common.BusinessException;
import com.parking.common.Hashing;
import com.parking.common.Types.Role;
import com.parking.dto.LoginRequest;
import com.parking.dto.RegisterRequest;
import com.parking.entity.SessionEntity;
import com.parking.entity.UserEntity;
import com.parking.mapper.SessionMapper;
import com.parking.mapper.UserMapper;
import com.parking.service.AuthService;
import com.parking.vo.LoginView;
import com.parking.vo.UserView;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
  private final UserMapper users;
  private final SessionMapper sessions;
  private final PasswordEncoder passwords;
  private final Clock clock;
  private final int sessionHours;
  private final SecureRandom random = new SecureRandom();
  private final String dummyHash;

  public AuthServiceImpl(
      UserMapper users,
      SessionMapper sessions,
      PasswordEncoder passwords,
      Clock clock,
      @Value("${parking.session-hours}") int sessionHours) {
    this.users = users;
    this.sessions = sessions;
    this.passwords = passwords;
    this.clock = clock;
    this.sessionHours = sessionHours;
    this.dummyHash = passwords.encode("unknown-account-placeholder");
  }

  @Override
  public LoginView login(LoginRequest request) {
    validatePasswordBytes(request.password());
    UserEntity user =
        users.selectOne(new QueryWrapper<UserEntity>().eq("username", request.username()));
    boolean valid =
        passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
    if (user == null || !valid)
      throw new BusinessException(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
    return issue(user);
  }

  @Override
  @Transactional
  public LoginView register(RegisterRequest request) {
    validatePasswordBytes(request.password());
    UserEntity user = new UserEntity();
    user.setId(UUID.randomUUID().toString());
    user.setUsername(request.username());
    user.setDisplayName(request.displayName().trim());
    if (user.getDisplayName().isEmpty()) throw BusinessException.invalid("昵称不能为空");
    user.setPasswordHash(passwords.encode(request.password()));
    user.setRole(Role.USER.name());
    user.setCreatedAt(clock.instant());
    users.insert(user);
    return issue(user);
  }

  @Override
  public UserView authenticate(String authorization) {
    String raw = token(authorization);
    SessionEntity session = sessions.selectById(Hashing.sha256(raw));
    if (session == null || !session.getExpiresAt().isAfter(clock.instant()))
      throw BusinessException.unauthorized();
    UserEntity user = users.selectById(session.getUserId());
    if (user == null) throw BusinessException.unauthorized();
    return view(user);
  }

  @Override
  public void logout(String authorization) {
    sessions.deleteById(Hashing.sha256(token(authorization)));
  }

  private LoginView issue(UserEntity user) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    SessionEntity session = new SessionEntity();
    session.setId(Hashing.sha256(raw));
    session.setUserId(user.getId());
    session.setCreatedAt(clock.instant());
    session.setExpiresAt(clock.instant().plusSeconds(sessionHours * 3600L));
    sessions.insert(session);
    return new LoginView(raw, session.getExpiresAt(), view(user));
  }

  private static String token(String authorization) {
    if (authorization == null || !authorization.matches("Bearer [A-Za-z0-9_-]{43}"))
      throw BusinessException.unauthorized();
    return authorization.substring(7);
  }

  private static void validatePasswordBytes(String value) {
    if (value.getBytes(StandardCharsets.UTF_8).length > 72)
      throw BusinessException.invalid("密码UTF-8长度不能超过72字节");
  }

  private static UserView view(UserEntity user) {
    return new UserView(
        user.getId(), user.getUsername(), user.getDisplayName(), Role.valueOf(user.getRole()));
  }
}
