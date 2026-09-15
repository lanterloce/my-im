package com.example.im.conversation;

import com.example.im.user.User;
import com.example.im.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@CrossOrigin(
        origins = {
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        }
)
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;

    public ConversationController(
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            UserRepository userRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    /**
     * 获取某个用户加入的所有群聊
     */
    @GetMapping("/user/{userId}")
    public List<Conversation> getUserConversations(
            @PathVariable Long userId
    ) {
        List<ConversationMember> members =
                memberRepository.findByUserId(userId);

        return members.stream()
                .map(member ->
                        conversationRepository
                                .findById(member.getConversationId())
                                .orElse(null)
                )
                .filter(conversation -> conversation != null)
                .toList();
    }

    /**
     * 搜索群聊
     */
    @GetMapping("/search")
    public List<Conversation> searchConversations(
            @RequestParam String keyword
    ) {
        return conversationRepository
                .findByNameContainingIgnoreCase(keyword);
    }

    /**
     * 创建群聊
     *
     * 请求示例：
     * {
     *   "name": "项目讨论群",
     *   "ownerId": 1
     * }
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Conversation createConversation(
            @RequestBody CreateConversationRequest request
    ) {
        if (request.getName() == null
                || request.getName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "群聊名称不能为空"
            );
        }

        if (request.getOwnerId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "创建者不能为空"
            );
        }

        User owner = userRepository
                .findById(request.getOwnerId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "创建者不存在"
                        )
                );

        Conversation conversation = new Conversation(
                request.getName().trim(),
                owner.getId()
        );

        Conversation savedConversation =
                conversationRepository.save(conversation);

        // 创建者自动加入群聊
        ConversationMember ownerMember =
                new ConversationMember(
                        savedConversation.getId(),
                        owner.getId()
                );

        memberRepository.save(ownerMember);

        return savedConversation;
    }

    /**
     * 获取群成员
     */
    @GetMapping("/{conversationId}/members")
    public List<ConversationMember> getMembers(
            @PathVariable Long conversationId
    ) {
        return memberRepository
                .findByConversationId(conversationId);
    }

    /**
     * 邀请用户加入群聊
     *
     * 请求示例：
     * {
     *   "userId": 2
     * }
     */
    @PostMapping("/{conversationId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationMember addMember(
            @PathVariable Long conversationId,
            @RequestBody AddMemberRequest request
    ) {
        conversationRepository
                .findById(conversationId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "群聊不存在"
                        )
                );

        userRepository
                .findById(request.getUserId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "用户不存在"
                        )
                );

        boolean exists =
                memberRepository
                        .existsByConversationIdAndUserId(
                                conversationId,
                                request.getUserId()
                        );

        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "用户已经在群聊中"
            );
        }

        ConversationMember member =
                new ConversationMember(
                        conversationId,
                        request.getUserId()
                );

        return memberRepository.save(member);
    }

    /**
     * 创建群聊请求对象
     */
    public static class CreateConversationRequest {

        private String name;
        private Long ownerId;

        public CreateConversationRequest() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getOwnerId() {
            return ownerId;
        }

        public void setOwnerId(Long ownerId) {
            this.ownerId = ownerId;
        }
    }

    /**
     * 邀请成员请求对象
     */
    public static class AddMemberRequest {

        private Long userId;

        public AddMemberRequest() {
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }
    }
}