package com.inghubscl.ticketing.auth;

import com.inghubscl.ticketing.auth.dto.LoginRequest;
import com.inghubscl.ticketing.auth.dto.RefreshRequest;
import com.inghubscl.ticketing.auth.dto.RegisterRequest;
import com.inghubscl.ticketing.auth.dto.TokenResponse;

public interface AuthService {

  TokenResponse register(RegisterRequest request);

  TokenResponse login(LoginRequest request);

  TokenResponse refresh(RefreshRequest request);
}
