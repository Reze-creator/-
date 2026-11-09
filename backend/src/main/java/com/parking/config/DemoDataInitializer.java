package com.parking.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.parking.entity.ResourceEntity;
import com.parking.entity.UserEntity;
import com.parking.mapper.ResourceMapper;
import com.parking.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent local-demo seed. Never resets an existing account's password or booking history. */
@Component
@Order(0)
@ConditionalOnProperty(name = "parking.seed-enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {
  private final UserMapper users;
  private final ResourceMapper resources;
  private final PasswordEncoder passwords;
  private final Clock clock;

  public DemoDataInitializer(
      UserMapper users, ResourceMapper resources, PasswordEncoder passwords, Clock clock) {
    this.users = users;
    this.resources = resources;
    this.passwords = passwords;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    seedUser("demo", "Demo@123", "林同学", "USER");
    seedUser("admin", "Admin@123", "园区管理员", "ADMIN");
    seedPark("north", "云栖 · 科创园", "杭州市 · 西湖区 · 科技园路88号", "A", 6);
    seedPark("south", "青禾 · 商务中心", "杭州市 · 滨江区 · 江南大道168号", "B", 6);
  }

  private void seedUser(String username, String password, String displayName, String role) {
    if (users.selectCount(new QueryWrapper<UserEntity>().eq("username", username)) > 0) return;
    UserEntity user = new UserEntity();
    user.setId(UUID.randomUUID().toString());
    user.setUsername(username);
    user.setPasswordHash(passwords.encode(password));
    user.setDisplayName(displayName);
    user.setRole(role);
    user.setCreatedAt(clock.instant());
    users.insert(user);
  }

  private void seedPark(String code, String name, String location, String prefix, int count) {
    for (int index = 1; index <= count; index++) {
      String resourceCode = prefix + "-" + String.format("%03d", index);
      if (resources.selectCount(new QueryWrapper<ResourceEntity>().eq("code", resourceCode)) > 0)
        continue;
      boolean charging = index > 3;
      ResourceEntity resource = new ResourceEntity();
      resource.setId(UUID.randomUUID().toString());
      resource.setCode(resourceCode);
      resource.setParkCode(code);
      resource.setParkName(name);
      resource.setLocation(location);
      resource.setResourceType(charging ? "CHARGING" : "PARKING");
      resource.setPowerKw(charging ? (index == 6 ? 120 : 60) : 0);
      resource.setHourlyRate(new BigDecimal(code.equals("north") ? "6.00" : "8.00"));
      resource.setEnabled(true);
      resource.setLatitude(new BigDecimal(code.equals("north") ? "30.274100" : "30.208400"));
      resource.setLongitude(new BigDecimal(code.equals("north") ? "120.101000" : "120.211000"));
      resource.setDescription("园区停车，支持直流快充");
      resource.setSearchVersion(1L);
      resource.setCreatedAt(clock.instant());
      resources.insert(resource);
    }
  }
}
