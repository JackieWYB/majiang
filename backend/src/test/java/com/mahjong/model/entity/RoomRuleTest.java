package com.mahjong.model.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mahjong.model.config.RoomConfig;
import com.mahjong.model.config.ScoreConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomRuleTest {
    
    private RoomRule roomRule;
    private RoomConfig config;
    
    @BeforeEach
    void setUp() {
        config = new RoomConfig();
        config.setPlayers(3);
        config.setTiles("WAN_ONLY");
        config.setAllowPeng(true);
        config.setAllowGang(true);
        config.setAllowChi(false);
        
        roomRule = new RoomRule("Test Rule", config);
    }
    
    @Test
    void testRoomRuleCreation() {
        assertNotNull(roomRule);
        assertEquals("Test Rule", roomRule.getName());
        assertNotNull(roomRule.getConfig());
        assertEquals(3, roomRule.getConfig().getPlayers());
        assertEquals("WAN_ONLY", roomRule.getConfig().getTiles());
        assertTrue(roomRule.getConfig().getAllowPeng());
        assertTrue(roomRule.getConfig().getAllowGang());
        assertFalse(roomRule.getConfig().getAllowChi());
        assertFalse(roomRule.getIsDefault());
        assertTrue(roomRule.getIsActive());
    }
    
    @Test
    void testDefaultConstructor() {
        RoomRule defaultRule = new RoomRule();
        assertNotNull(defaultRule);
        assertNull(defaultRule.getName());
        assertNull(defaultRule.getConfig());
        assertFalse(defaultRule.getIsDefault());
        assertTrue(defaultRule.getIsActive());
    }
    
    @Test
    void testConfigSerialization() {
        // Config should be automatically serialized to JSON
        assertNotNull(roomRule.getConfigJson());
        assertTrue(roomRule.getConfigJson().contains("\"players\":3"));
        assertTrue(roomRule.getConfigJson().contains("\"tiles\":\"WAN_ONLY\""));
        assertTrue(roomRule.getConfigJson().contains("\"allowPeng\":true"));
    }
    
    @Test
    void testConfigDeserialization() {
        String jsonConfig = "{\"players\":3,\"tiles\":\"WAN_ONLY\",\"allowPeng\":true,\"allowGang\":false,\"allowChi\":true}";
        
        RoomRule rule = new RoomRule();
        rule.setConfigJson(jsonConfig);
        
        RoomConfig deserializedConfig = rule.getConfig();
        assertNotNull(deserializedConfig);
        assertEquals(3, deserializedConfig.getPlayers());
        assertEquals("WAN_ONLY", deserializedConfig.getTiles());
        assertTrue(deserializedConfig.getAllowPeng());
        assertFalse(deserializedConfig.getAllowGang());
        assertTrue(deserializedConfig.getAllowChi());
    }
    
    @Test
    void testConfigUpdate() {
        RoomConfig newConfig = new RoomConfig();
        newConfig.setPlayers(3);
        newConfig.setTiles("ALL_SUITS");
        newConfig.setAllowPeng(false);
        
        roomRule.setConfig(newConfig);
        
        assertEquals(newConfig, roomRule.getConfig());
        assertTrue(roomRule.getConfigJson().contains("\"tiles\":\"ALL_SUITS\""));
        assertTrue(roomRule.getConfigJson().contains("\"allowPeng\":false"));
    }
    
    @Test
    void testInvalidJsonHandling() {
        RoomRule rule = new RoomRule();
        
        assertThrows(RuntimeException.class, () -> {
            rule.setConfigJson("invalid json");
        });
    }
    
    @Test
    void testBusinessMethods() {
        assertFalse(roomRule.isDefaultRule());
        assertTrue(roomRule.isActiveRule());
        
        roomRule.setIsDefault(true);
        assertTrue(roomRule.isDefaultRule());
        
        roomRule.deactivate();
        assertFalse(roomRule.isActiveRule());
        assertFalse(roomRule.getIsActive());
        
        roomRule.activate();
        assertTrue(roomRule.isActiveRule());
        assertTrue(roomRule.getIsActive());
    }
    
    @Test
    void testEqualsAndHashCode() {
        RoomRule rule1 = new RoomRule("Rule1", config);
        rule1.setId(1L);
        
        RoomRule rule2 = new RoomRule("Rule1", config);
        rule2.setId(1L);
        
        RoomRule rule3 = new RoomRule("Rule2", config);
        rule3.setId(2L);
        
        assertEquals(rule1, rule2);
        assertEquals(rule1.hashCode(), rule2.hashCode());
        
        assertNotEquals(rule1, rule3);
        assertNotEquals(rule1.hashCode(), rule3.hashCode());
    }
    
    @Test
    void testToString() {
        roomRule.setId(1L);
        roomRule.setIsDefault(true);
        
        String toString = roomRule.toString();
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("name='Test Rule'"));
        assertTrue(toString.contains("isDefault=true"));
        assertTrue(toString.contains("isActive=true"));
    }
    
    @Test
    void testComplexConfigSerialization() {
        RoomConfig complexConfig = new RoomConfig();
        complexConfig.setPlayers(3);
        
        ScoreConfig scoreConfig = new ScoreConfig();
        scoreConfig.setBaseScore(4);
        scoreConfig.setMaxScore(32);
        scoreConfig.setDealerMultiplier(2.5);
        complexConfig.setScore(scoreConfig);
        
        RoomRule rule = new RoomRule("Complex Rule", complexConfig);
        
        assertNotNull(rule.getConfigJson());
        assertTrue(rule.getConfigJson().contains("\"baseScore\":4"));
        assertTrue(rule.getConfigJson().contains("\"maxScore\":32"));
        assertTrue(rule.getConfigJson().contains("\"dealerMultiplier\":2.5"));
        
        // Test deserialization
        RoomConfig retrievedConfig = rule.getConfig();
        assertEquals(4, retrievedConfig.getScore().getBaseScore());
        assertEquals(32, retrievedConfig.getScore().getMaxScore());
        assertEquals(2.5, retrievedConfig.getScore().getDealerMultiplier());
    }
    
    @Test
    void testSettersAndGetters() {
        roomRule.setId(123L);
        assertEquals(123L, roomRule.getId());
        
        roomRule.setName("Updated Rule");
        assertEquals("Updated Rule", roomRule.getName());
        
        roomRule.setDescription("Test description");
        assertEquals("Test description", roomRule.getDescription());
        
        roomRule.setIsDefault(true);
        assertTrue(roomRule.getIsDefault());
        
        roomRule.setIsActive(false);
        assertFalse(roomRule.getIsActive());
    }
}