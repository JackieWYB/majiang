package com.example.mahjong.controller;

import com.example.mahjong.dto.LoginResponse;
import com.example.mahjong.dto.WechatLoginRequest;
import com.example.mahjong.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/wechat/login")
    public ResponseEntity<LoginResponse> login(@RequestBody WechatLoginRequest loginRequest) {
        LoginResponse loginResponse = authService.wechatLogin(loginRequest);
        return ResponseEntity.ok(loginResponse);
    }
}
