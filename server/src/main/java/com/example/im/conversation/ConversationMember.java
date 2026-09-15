package com.example.im.conversation;

import jakarta.persistence.*;

@Entity
@Table(
    name = "conversation_members",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {"conversation_id", "user_id"}
        )
    }
)
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public ConversationMember() {
    }

    public ConversationMember(Long conversationId, Long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}