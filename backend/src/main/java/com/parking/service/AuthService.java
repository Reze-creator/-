package com.parking.service;

import com.parking.dto.LoginRequest;
import com.parking.dto.RegisterRequest;
import com.parking.vo.LoginView;
import com.parking.vo.UserView;

public interface AuthService {
  LoginView login(LoginRequest request);

  LoginView register(RegisterRequest request);

  UserView authenticate(String authorization);

  void logout(String authorization);
}
