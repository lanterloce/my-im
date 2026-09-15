package com.example.im.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationMemberRepository
        extends JpaRepository<ConversationMember, Long> {

    List<ConversationMember> findByUserId(Long userId);

    List<ConversationMember> findByConversationId(Long conversationId);

    Optional<ConversationMember> findByConversationIdAndUserId(
            Long conversationId,
            Long userId
    );

    boolean existsByConversationIdAndUserId(
            Long conversationId,
            Long userId
    );
}