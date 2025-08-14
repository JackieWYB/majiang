package com.example.mahjong.service;

import com.example.mahjong.dto.LoginResponse;
import com.example.mahjong.dto.WechatLoginRequest;
import com.example.mahjong.entity.User;
import com.example.mahjong.repository.UserRepository;
import com.example.mahjong.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Transactional
    public LoginResponse wechatLogin(WechatLoginRequest loginRequest) {
        // Step 1: Exchange code for openId (mocked for now)
        // In a real application, this would involve an HTTP call to WeChat's API
        String openId = mockGetOpenIdFromWechat(loginRequest.getCode());

        // Step 2: Find user by openId, or create a new one
        User user = userRepository.findByOpenId(openId).orElseGet(() -> createNewUser(openId));

        // Step 3: Generate JWT
        String token = jwtUtil.generateToken(user);

        // Step 4: Create response DTO
        LoginResponse.UserDto userDto = new LoginResponse.UserDto(user.getId(), user.getNickname(), user.getAvatar());

        return new LoginResponse(token, userDto);
    }

    private String mockGetOpenIdFromWechat(String code) {
        // For demonstration, we'll just use the code as the openId if it's not empty,
        // otherwise generate a random one.
        if (code != null && !code.isEmpty()) {
            return "mock-openid-for-code-" + code;
        }
        return "mock-openid-" + UUID.randomUUID().toString();
    }

    private User createNewUser(String openId) {
        User newUser = new User();
        newUser.setOpenId(openId);
        newUser.setNickname("微信用户" + UUID.randomUUID().toString().substring(0, 6));
        newUser.setAvatar("default_avatar_url"); // Placeholder avatar
        newUser.setStatus("active");
        return userRepository.save(newUser);
    }
}
