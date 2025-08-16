package com.mahjong.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * WeChat Mini Program authentication service
 * Handles code2Session API integration for user authentication
 */
@Service
public class WeChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(WeChatService.class);
    
    private final WebClient webClient;
    private final String appId;
    private final String appSecret;
    private final String code2SessionUrl;
    private final ObjectMapper objectMapper;
    
    public WeChatService(
            WebClient.Builder webClientBuilder,
            @Value("${wechat.mini-program.app-id}") String appId,
            @Value("${wechat.mini-program.app-secret}") String appSecret,
            @Value("${wechat.mini-program.code2session-url}") String code2SessionUrl,
            ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        this.appId = appId;
        this.appSecret = appSecret;
        this.code2SessionUrl = code2SessionUrl;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Exchange WeChat login code for session information
     * 
     * @param code WeChat login code from mini program
     * @return WeChat session information containing openId and sessionKey
     * @throws WeChatAuthException if authentication fails
     */
    public WeChatSession code2Session(String code) {
        logger.debug("Exchanging WeChat code for session: {}", code);
        
        try {
            Mono<WeChatSessionResponse> responseMono = webClient.get()
                    .uri(code2SessionUrl + "?appid={appid}&secret={secret}&js_code={code}&grant_type={grant_type}",
                            appId, appSecret, code, "authorization_code")
                    .retrieve()
                    .bodyToMono(WeChatSessionResponse.class)
                    .timeout(Duration.ofSeconds(10));
            
            WeChatSessionResponse response = responseMono.block();
            
            if (response == null) {
                throw new WeChatAuthException("Empty response from WeChat API");
            }
            
            if (response.getErrcode() != null && response.getErrcode() != 0) {
                logger.error("WeChat API error: code={}, message={}", response.getErrcode(), response.getErrmsg());
                throw new WeChatAuthException("WeChat authentication failed: " + response.getErrmsg());
            }
            
            if (response.getOpenid() == null || response.getSessionKey() == null) {
                throw new WeChatAuthException("Invalid response from WeChat API: missing openid or session_key");
            }
            
            logger.debug("Successfully obtained WeChat session for openId: {}", response.getOpenid());
            
            return new WeChatSession(
                    response.getOpenid(),
                    response.getUnionid(),
                    response.getSessionKey()
            );
            
        } catch (Exception e) {
            logger.error("Failed to exchange WeChat code for session", e);
            if (e instanceof WeChatAuthException) {
                throw e;
            }
            throw new WeChatAuthException("Failed to authenticate with WeChat: " + e.getMessage(), e);
        }
    }
    
    /**
     * WeChat session information
     */
    public static class WeChatSession {
        private final String openId;
        private final String unionId;
        private final String sessionKey;
        
        public WeChatSession(String openId, String unionId, String sessionKey) {
            this.openId = openId;
            this.unionId = unionId;
            this.sessionKey = sessionKey;
        }
        
        public String getOpenId() {
            return openId;
        }
        
        public String getUnionId() {
            return unionId;
        }
        
        public String getSessionKey() {
            return sessionKey;
        }
    }
    
    /**
     * WeChat API response structure
     */
    private static class WeChatSessionResponse {
        @JsonProperty("openid")
        private String openid;
        
        @JsonProperty("unionid")
        private String unionid;
        
        @JsonProperty("session_key")
        private String sessionKey;
        
        @JsonProperty("errcode")
        private Integer errcode;
        
        @JsonProperty("errmsg")
        private String errmsg;
        
        public String getOpenid() {
            return openid;
        }
        
        public void setOpenid(String openid) {
            this.openid = openid;
        }
        
        public String getUnionid() {
            return unionid;
        }
        
        public void setUnionid(String unionid) {
            this.unionid = unionid;
        }
        
        public String getSessionKey() {
            return sessionKey;
        }
        
        public void setSessionKey(String sessionKey) {
            this.sessionKey = sessionKey;
        }
        
        public Integer getErrcode() {
            return errcode;
        }
        
        public void setErrcode(Integer errcode) {
            this.errcode = errcode;
        }
        
        public String getErrmsg() {
            return errmsg;
        }
        
        public void setErrmsg(String errmsg) {
            this.errmsg = errmsg;
        }
    }
    
    /**
     * WeChat authentication exception
     */
    public static class WeChatAuthException extends RuntimeException {
        public WeChatAuthException(String message) {
            super(message);
        }
        
        public WeChatAuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}