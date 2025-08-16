package com.mahjong.model.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahjong.model.config.RoomConfig;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Room rule entity for storing game configuration templates
 */
@Entity
@Table(name = "t_room_rule", indexes = {
    @Index(name = "idx_room_rule_name", columnList = "name")
})
public class RoomRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;
    
    @NotNull
    @Column(name = "config", nullable = false, columnDefinition = "JSON")
    private String configJson;
    
    @NotNull
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;
    
    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Transient field for the actual config object
    @Transient
    private RoomConfig config;
    
    @Transient
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Constructors
    public RoomRule() {}
    
    public RoomRule(String name, RoomConfig config) {
        this.name = name;
        this.setConfig(config);
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getConfigJson() {
        return configJson;
    }
    
    public void setConfigJson(String configJson) {
        this.configJson = configJson;
        // Parse JSON to config object when setting JSON
        if (configJson != null) {
            try {
                this.config = objectMapper.readValue(configJson, RoomConfig.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse room config JSON", e);
            }
        }
    }
    
    public RoomConfig getConfig() {
        if (config == null && configJson != null) {
            try {
                config = objectMapper.readValue(configJson, RoomConfig.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse room config JSON", e);
            }
        }
        return config;
    }
    
    public void setConfig(RoomConfig config) {
        this.config = config;
        // Serialize config to JSON when setting config object
        if (config != null) {
            try {
                this.configJson = objectMapper.writeValueAsString(config);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize room config to JSON", e);
            }
        }
    }
    
    public Boolean getIsDefault() {
        return isDefault;
    }
    
    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // JPA lifecycle callbacks
    @PostLoad
    private void postLoad() {
        // Ensure config is loaded after entity is loaded from database
        if (configJson != null && config == null) {
            try {
                config = objectMapper.readValue(configJson, RoomConfig.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse room config JSON", e);
            }
        }
    }
    
    @PrePersist
    @PreUpdate
    private void prePersist() {
        // Ensure JSON is updated before saving
        if (config != null) {
            try {
                configJson = objectMapper.writeValueAsString(config);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize room config to JSON", e);
            }
        }
    }
    
    // Business methods
    public boolean isDefaultRule() {
        return Boolean.TRUE.equals(isDefault);
    }
    
    public boolean isActiveRule() {
        return Boolean.TRUE.equals(isActive);
    }
    
    public void activate() {
        this.isActive = true;
    }
    
    public void deactivate() {
        this.isActive = false;
    }
    
    // equals and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoomRule roomRule = (RoomRule) o;
        return Objects.equals(id, roomRule.id) && Objects.equals(name, roomRule.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
    
    @Override
    public String toString() {
        return "RoomRule{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", isDefault=" + isDefault +
                ", isActive=" + isActive +
                '}';
    }
}