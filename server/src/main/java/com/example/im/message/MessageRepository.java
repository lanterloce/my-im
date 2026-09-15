package com.example.im.message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MessageRepository extends JpaRepository<Message,Long> {
  List<Message> findTop50ByConversationIdOrderByIdDesc(Long conversationId);
}
