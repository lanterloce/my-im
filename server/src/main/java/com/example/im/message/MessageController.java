package com.example.im.message;

import com.example.im.user.User;
import com.example.im.user.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageRepository repo;
    private final UserRepository userRepo;
    private final SimpMessagingTemplate ws;

    public MessageController(
            MessageRepository repo,
            UserRepository userRepo,
            SimpMessagingTemplate ws
    ) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.ws = ws;
    }

    @GetMapping("/{conversationId}")
    public List<MessageResponse> history(
            @PathVariable Long conversationId
    ) {
        return repo
                .findTop50ByConversationIdOrderByIdDesc(conversationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public MessageResponse send(
            @RequestBody Message message
    ) {
        Message saved = repo.save(message);

        MessageResponse response = toResponse(saved);

        ws.convertAndSend(
                "/topic/conversation/" + saved.getConversationId(),
                response
        );

        return response;
    }

    private MessageResponse toResponse(Message message) {
        User user = userRepo
                .findById(message.getSenderId())
                .orElse(null);

        String senderName = "用户" + message.getSenderId();

        if (user != null) {
            if (user.getDisplayName() != null
                    && !user.getDisplayName().isBlank()) {
                senderName = user.getDisplayName();
            } else {
                senderName = user.getUsername();
            }
        }

        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                senderName,
                message.getContent()
        );
    }
}