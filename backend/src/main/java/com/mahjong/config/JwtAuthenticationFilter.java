package com.mahjong.config;

import com.mahjong.service.JwtTokenService;
import com.mahjong.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JWT authentication filter to validate tokens and set security context
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    
    private final JwtTokenService jwtTokenService;
    private final UserService userService;
    
    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, UserService userService) {
        this.jwtTokenService = jwtTokenService;
        this.userService = userService;
    }
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String token = extractTokenFromRequest(request);
            
            if (token != null && jwtTokenService.validateToken(token)) {
                String tokenType = jwtTokenService.extractTokenType(token);
                
                // Only allow access tokens for API access
                if ("access".equals(tokenType)) {
                    Long userId = jwtTokenService.extractUserId(token);
                    
                    // Check if user is still active
                    if (userService.isUserActive(userId)) {
                        // Extract role from token and create authorities
                        String role = jwtTokenService.extractRole(token);
                        List<GrantedAuthority> authorities = new ArrayList<>();
                        
                        if (role != null) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                        } else {
                            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                        }
                        
                        UsernamePasswordAuthenticationToken authentication = 
                                new UsernamePasswordAuthenticationToken(
                                        userId.toString(),
                                        null,
                                        authorities
                                );
                        
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        
                        logger.debug("Set authentication for user: {} with role: {}", userId, role);
                    } else {
                        logger.warn("Inactive user attempted access: {}", userId);
                    }
                } else {
                    logger.debug("Non-access token used for API access: {}", tokenType);
                }
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication", e);
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Extract JWT token from Authorization header
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        
        return null;
    }
}