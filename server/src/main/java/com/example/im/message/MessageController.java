package com.example.im.message;

import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
  private final MessageRepository repo; private final SimpMessagingTemplate ws;
  public MessageController(MessageRepository repo,SimpMessagingTemplate ws){this.repo=repo;this.ws=ws;}
  @GetMapping("/{conversationId}") public List<Message> history(@PathVariable Long conversationId){return repo.findTop50ByConversationIdOrderByIdDesc(conversationId);}
  @PostMapping public Message send(@RequestBody Message message){Message saved=repo.save(message);ws.convertAndSend("/topic/conversation/"+saved.getConversationId(),saved);return saved;}
}
