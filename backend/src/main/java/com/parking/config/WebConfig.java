package com.parking.config;

import com.parking.common.AdminOnly;
import com.parking.common.BusinessException;
import com.parking.common.PublicEndpoint;
import com.parking.common.Types.Role;
import com.parking.common.UserContext;
import com.parking.service.AuthService;
import com.parking.vo.UserView;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final AuthService auth;

  public WebConfig(AuthService auth) {
    this.auth = auth;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  jakarta.servlet.http.HttpServletRequest request,
                  jakarta.servlet.http.HttpServletResponse response,
                  Object handler) {
                response.setHeader("Cache-Control", "no-store");
                if (!(handler instanceof HandlerMethod endpoint)) return true;
                if (endpoint.hasMethodAnnotation(PublicEndpoint.class)
                    || endpoint.getBeanType().isAnnotationPresent(PublicEndpoint.class))
                  return true;
                UserView user = auth.authenticate(request.getHeader("Authorization"));
                request.setAttribute(UserContext.ATTRIBUTE, user);
                if ((endpoint.hasMethodAnnotation(AdminOnly.class)
                        || endpoint.getBeanType().isAnnotationPresent(AdminOnly.class))
                    && user.role() != Role.ADMIN) throw BusinessException.forbidden();
                return true;
              }
            })
        .addPathPatterns("/api/**");
  }

  @Bean
  public static Clock clock() {
    return Clock.tickMillis(ZoneOffset.UTC);
  }

  @Bean
  public static PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("泊序 Parking · 预约 履约 消息与搜索接口")
                .version("3.0.0")
                .description("时段预约、异步设备事件、停车充电结算、运营、站内通知及附近搜索；AI模块暂缓。设备操作仅限管理员。"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "BearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("Opaque session token")));
  }
}
