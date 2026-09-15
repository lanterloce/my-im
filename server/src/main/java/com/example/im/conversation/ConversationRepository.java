package com.example.im.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    List<Conversation> findByNameContainingIgnoreCase(String name);
}