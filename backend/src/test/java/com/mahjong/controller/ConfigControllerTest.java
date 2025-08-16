package com.mahjong.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahjong.model.config.*;
import com.mahjong.model.entity.RoomRule;
import com.mahjong.model.entity.User;
import com.mahjong.model.enums.UserRole;
import com.mahjong.model.enums.UserStatus;
import com.mahjong.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConfigController.class)
class ConfigControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private EntityManager entityManager;
    
    @MockBean
    private UserService userService;
    
    @MockBean
    private TypedQuery<RoomRule> roomRuleQuery;
    
    @MockBean
    private TypedQuery<Long> longQuery;
    
    private User adminUser;
    private User regularUser;
    private RoomRule testRule;
    private RoomConfig testConfig;
    
    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setOpenId("admin_open_id");
        adminUser.setNickname("Admin User");
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setRole(UserRole.ADMIN);
        
        regularUser = new User();
        regularUser.setId(2L);
        regularUser.setOpenId("user_open_id");
        regularUser.setNickname("Regular User");
        regularUser.setStatus(UserStatus.ACTIVE);
        regularUser.setRole(UserRole.USER);
        
        testConfig = new RoomConfig();
        testConfig.setPlayers(3);
        testConfig.setTiles("WAN_ONLY");
        testConfig.setAllowPeng(true);
        testConfig.setAllowGang(true);
        testConfig.setAllowChi(false);
        testConfig.setReplay(true);
        testConfig.setHuTypes(new HuTypes());
        testConfig.setScore(new ScoreConfig());
        testConfig.setTurn(new TurnConfig());
        testConfig.setDealer(new DealerConfig());
        testConfig.setDismiss(new DismissConfig());
        
        testRule = new RoomRule();
        testRule.setId(1L);
        testRule.setName("Test Rule");
        testRule.setDescription("Test rule description");
        testRule.setConfig(testConfig);
        testRule.setIsDefault(true);
        testRule.setIsActive(true);
    }
    
    @Test
    @WithMockUser(username = "user_open_id")
    void getRoomRules_ShouldReturnActiveRules() throws Exception {
        List<RoomRule> rules = Arrays.asList(testRule);
        
        when(entityManager.createQuery(anyString(), eq(RoomRule.class))).thenReturn(roomRuleQuery);
        when(roomRuleQuery.getResultList()).thenReturn(rules);
        
        mockMvc.perform(get("/api/config/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Test Rule"))
                .andExpect(jsonPath("$.data.content[0].isDefault").value(true))
                .andExpect(jsonPath("$.data.content[0].isActive").value(true));
        
        verify(entityManager).createQuery(anyString(), eq(RoomRule.class));
        verify(roomRuleQuery).getResultList();
    }
    
    @Test
    @WithMockUser(username = "user_open_id")
    void getDefaultRoomRules_ShouldReturnDefaultRules() throws Exception {
        List<RoomRule> defaultRules = Arrays.asList(testRule);
        
        when(entityManager.createQuery(anyString(), eq(RoomRule.class))).thenReturn(roomRuleQuery);
        when(roomRuleQuery.getResultList()).thenReturn(defaultRules);
        
        mockMvc.perform(get("/api/config/rules/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Test Rule"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
        
        verify(entityManager).createQuery(anyString(), eq(RoomRule.class));
        verify(roomRuleQuery).getResultList();
    }
    
    @Test
    @WithMockUser(username = "user_open_id")
    void getRoomRule_ShouldReturnSpecificRule() throws Exception {
        when(entityManager.find(RoomRule.class, 1L)).thenReturn(testRule);
        
        mockMvc.perform(get("/api/config/rules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Test Rule"))
                .andExpect(jsonPath("$.data.config.players").value(3))
                .andExpect(jsonPath("$.data.config.tiles").value("WAN_ONLY"));
        
        verify(entityManager).find(RoomRule.class, 1L);
    }
    
    @Test
    @WithMockUser(username = "user_open_id")
    void getRoomRule_NotFound_ShouldReturn404() throws Exception {
        when(entityManager.find(RoomRule.class, 999L)).thenReturn(null);
        
        mockMvc.perform(get("/api/config/rules/999"))
                .andExpect(status().isNotFound());
        
        verify(entityManager).find(RoomRule.class, 999L);
    }
    
    @Test
    @WithMockUser(username = "admin_open_id")
    void createRoomRule_AdminAccess_ShouldCreateRule() throws Exception {
        ConfigController.CreateRoomRuleRequest request = new ConfigController.CreateRoomRuleRequest();
        request.setName("New Rule");
        request.setDescription("New rule description");
        request.setConfig(testConfig);
        request.setIsDefault(false);
        
        when(userService.getCurrentUser(any())).thenReturn(adminUser);
        
        mockMvc.perform(post("/api/config/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        
        verify(userService).getCurrentUser(any());
        verify(entityManager).persist(any(RoomRule.class));
        verify(entityManager).flush();
    }
    
    @Test
    @WithMockUser(username = "user_open_id")
    void createRoomRule_NonAdminAccess_ShouldReturn403() throws Exception {
        ConfigController.CreateRoomRuleRequest request = new ConfigController.CreateRoomRuleRequest();
        request.setName("New Rule");
        request.setConfig(testConfig);
        
        when(userService.getCurrentUser(any())).thenReturn(regularUser);
        
        mockMvc.perform(post("/api/config/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: Admin role required"));
        
        verify(userService).getCurrentUser(any());
        verify(entityManager, never()).persist(any());
    }
    
    @Test
    @WithMockUser(username = "admin_open_id")
    void updateRoomRule_AdminAccess_ShouldUpdateRule() throws Exception {
        ConfigController.UpdateRoomRuleRequest request = new ConfigController.UpdateRoomRuleRequest();
        request.setName("Updated Rule");
        request.setDescription("Updated description");
        
        when(userService.getCurrentUser(any())).thenReturn(adminUser);
        when(entityManager.find(RoomRule.class, 1L)).thenReturn(testRule);
        
        mockMvc.perform(put("/api/config/rules/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        
        verify(userService).getCurrentUser(any());
        verify(entityManager).find(RoomRule.class, 1L);
        verify(entityManager).merge(any(RoomRule.class));
        verify(entityManager).flush();
    }
    
    @Test
    @WithMockUser(username = "user_open_id")
    void updateRoomRule_NonAdminAccess_ShouldReturn403() throws Exception {
        ConfigController.UpdateRoomRuleRequest request = new ConfigController.UpdateRoomRuleRequest();
        request.setName("Updated Rule");
        
        when(userService.getCurrentUser(any())).thenReturn(regularUser);
        
        mockMvc.perform(put("/api/config/rules/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: Admin role required"));
        
        verify(userService).getCurrentUser(any());
        verify(entityManager, never()).find(eq(RoomRule.class), anyLong());
    }
    
    @Test
    @WithMockUser(username = "admin_open_id")
    void deleteRoomRule_AdminAccess_ShouldSoftDeleteRule() throws Exception {
        when(userService.getCurrentUser(any())).thenReturn(adminUser);
        when(entityManager.find(RoomRule.class, 1L)).thenReturn(testRule);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(longQuery);
        when(longQuery.setParameter(anyString(), any())).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(0L); // No active rooms using this rule
        
        mockMvc.perform(delete("/api/config/rules/1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        
        verify(userService).getCurrentUser(any());
        verify(entityManager).find(RoomRule.class, 1L);
        verify(entityManager).merge(any(RoomRule.class));
        verify(entityManager).flush();
    }
    
    @Test
    @WithMockUser(username = "admin_open_id")
    void deleteRoomRule_RuleInUse_ShouldReturn400() throws Exception {
        when(userService.getCurrentUser(any())).thenReturn(adminUser);
        when(entityManager.find(RoomRule.class, 1L)).thenReturn(testRule);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(longQuery);
        when(longQuery.setParameter(anyString(), any())).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(5L); // 5 active rooms using this rule
        
        mockMvc.perform(delete("/api/config/rules/1")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cannot delete rule that is being used by active rooms"));
        
        verify(userService).getCurrentUser(any());
        verify(entityManager).find(RoomRule.class, 1L);
        verify(entityManager, never()).merge(any());
    }
    
    @Test
    @WithMockUser(username = "user_open_id")
    void getConfigTemplate_ShouldReturnTemplate() throws Exception {
        mockMvc.perform(get("/api/config/template"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.players").value(3))
                .andExpect(jsonPath("$.data.tiles").value("WAN_ONLY"))
                .andExpect(jsonPath("$.data.allowPeng").value(true))
                .andExpect(jsonPath("$.data.allowGang").value(true))
                .andExpect(jsonPath("$.data.allowChi").value(false))
                .andExpect(jsonPath("$.data.replay").value(true));
    }
    
    @Test
    @WithMockUser(username = "user_open_id")
    void validateConfig_ValidConfig_ShouldReturnValid() throws Exception {
        mockMvc.perform(post("/api/config/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testConfig)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true));
    }
    
    @Test
    @WithMockUser(username = "user_open_id")
    void validateConfig_InvalidConfig_ShouldReturnInvalid() throws Exception {
        RoomConfig invalidConfig = new RoomConfig();
        invalidConfig.setPlayers(4); // Invalid - should be 3
        invalidConfig.setTiles("INVALID_TILES");
        
        mockMvc.perform(post("/api/config/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidConfig)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors").isArray())
                .andExpect(jsonPath("$.data.errors").isNotEmpty());
    }
    
    @Test
    @WithMockUser(username = "admin_open_id")
    void createRoomRule_InvalidConfig_ShouldReturn400() throws Exception {
        ConfigController.CreateRoomRuleRequest request = new ConfigController.CreateRoomRuleRequest();
        request.setName("Invalid Rule");
        
        RoomConfig invalidConfig = new RoomConfig();
        invalidConfig.setPlayers(4); // Invalid
        request.setConfig(invalidConfig);
        
        when(userService.getCurrentUser(any())).thenReturn(adminUser);
        
        mockMvc.perform(post("/api/config/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid room configuration"));
        
        verify(userService).getCurrentUser(any());
        verify(entityManager, never()).persist(any());
    }
    
    @Test
    @WithMockUser(username = "admin_open_id")
    void createRoomRule_InvalidName_ShouldReturn400() throws Exception {
        ConfigController.CreateRoomRuleRequest request = new ConfigController.CreateRoomRuleRequest();
        request.setName(""); // Invalid - blank name
        request.setConfig(testConfig);
        
        mockMvc.perform(post("/api/config/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @WithMockUser(username = "admin_open_id")
    void updateRoomRule_NotFound_ShouldReturn404() throws Exception {
        ConfigController.UpdateRoomRuleRequest request = new ConfigController.UpdateRoomRuleRequest();
        request.setName("Updated Rule");
        
        when(userService.getCurrentUser(any())).thenReturn(adminUser);
        when(entityManager.find(RoomRule.class, 999L)).thenReturn(null);
        
        mockMvc.perform(put("/api/config/rules/999")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        
        verify(userService).getCurrentUser(any());
        verify(entityManager).find(RoomRule.class, 999L);
        verify(entityManager, never()).merge(any());
    }
}