package com.example.mahjong.repository;

import com.example.mahjong.entity.RoomRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomRuleRepository extends JpaRepository<RoomRule, Long> {
}
