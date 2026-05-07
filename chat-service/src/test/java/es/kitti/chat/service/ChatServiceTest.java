package es.kitti.chat.service;

import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import io.smallrye.mutiny.Uni;
import es.kitti.chat.dto.BlockUserRequest;
import es.kitti.chat.dto.ConversationResponse;
import es.kitti.chat.dto.CreateConversationRequest;
import es.kitti.chat.dto.MessageResponse;
import es.kitti.chat.dto.SendMessageRequest;
import es.kitti.chat.entity.BlockedParticipant;
import es.kitti.chat.entity.Conversation;
import es.kitti.chat.entity.Message;
import es.kitti.chat.entity.SenderType;
import es.kitti.chat.mapper.ChatMapper;
import es.kitti.chat.repository.BlockedParticipantRepository;
import es.kitti.chat.repository.ConversationRepository;
import es.kitti.chat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ConversationRepository conversationRepository;
    @Mock MessageRepository messageRepository;
    @Mock BlockedParticipantRepository blockedRepository;
    @Mock ChatMapper mapper;

    @InjectMocks ChatService service;

    private Conversation conversation;
    private ConversationResponse conversationResponse;

    @BeforeEach
    void setUp() {
        conversation = new Conversation();
        conversation.id = 1L;
        conversation.intakeRequestId = 10L;
        conversation.userId = 100L;
        conversation.organizationId = 200L;
        conversation.createdAt = LocalDateTime.now();

        conversationResponse = new ConversationResponse(
                1L, 10L, 100L, 200L, conversation.createdAt, null
        );
    }

    // --- createConversation ---

    @Test
    void createConversation_success_returnsRight() {
        when(conversationRepository.findByIntakeRequestId(10L))
                .thenReturn(Uni.createFrom().nullItem());
        when(conversationRepository.persist(any(Conversation.class)))
                .thenReturn(Uni.createFrom().item(conversation));
        when(mapper.toResponse(any(Conversation.class)))
                .thenReturn(conversationResponse);

        var result = service.createConversation(new CreateConversationRequest(10L, 100L, 200L))
                .await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(10L, result.getOrElse(null).intakeRequestId());
    }

    @Test
    void createConversation_alreadyExists_returnsLeft409() {
        when(conversationRepository.findByIntakeRequestId(10L))
                .thenReturn(Uni.createFrom().item(conversation));

        var result = service.createConversation(new CreateConversationRequest(10L, 100L, 200L))
                .await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ConflictError.class, result.fold(e -> e, __ -> null));
        assertEquals(409, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- findMineAsUser / findMineAsOrganization (no Either) ---

    @Test
    void findMineAsUser_returnsList() {
        when(conversationRepository.findByUserId(100L))
                .thenReturn(Uni.createFrom().item(List.of(conversation)));
        when(mapper.toResponse(conversation)).thenReturn(conversationResponse);

        var result = service.findMineAsUser(100L).await().indefinitely();

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).userId());
    }

    @Test
    void findMineAsOrganization_returnsList() {
        when(conversationRepository.findByOrganizationId(200L))
                .thenReturn(Uni.createFrom().item(List.of(conversation)));
        when(mapper.toResponse(conversation)).thenReturn(conversationResponse);

        var result = service.findMineAsOrganization(200L).await().indefinitely();

        assertEquals(1, result.size());
        assertEquals(200L, result.get(0).organizationId());
    }

    // --- listMessages ---

    @Test
    void listMessages_asParticipant_returnsRight() {
        var msg = new Message();
        msg.id = 1L; msg.conversationId = 1L; msg.senderId = 100L;
        msg.senderType = SenderType.User; msg.content = "hi"; msg.createdAt = LocalDateTime.now();
        var msgResponse = new MessageResponse(1L, 1L, 100L, SenderType.User, "hi", msg.createdAt);

        when(conversationRepository.findById(1L)).thenReturn(Uni.createFrom().item(conversation));
        when(messageRepository.findByConversationId(1L)).thenReturn(Uni.createFrom().item(List.of(msg)));
        when(mapper.toResponse(msg)).thenReturn(msgResponse);

        var result = service.listMessages(1L, 100L, SenderType.User).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(1, result.getOrElse(null).size());
    }

    @Test
    void listMessages_notParticipant_returnsLeft403() {
        when(conversationRepository.findById(1L)).thenReturn(Uni.createFrom().item(conversation));

        var result = service.listMessages(1L, 999L, SenderType.User).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, result.fold(e -> e, __ -> null));
        assertEquals(403, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void listMessages_conversationNotFound_returnsLeft404() {
        when(conversationRepository.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        var result = service.listMessages(99L, 100L, SenderType.User).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, result.fold(e -> e, __ -> null));
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- sendMessage ---

    @Test
    void sendMessage_asUser_returnsRight() {
        var saved = new Message();
        saved.id = 5L; saved.conversationId = 1L; saved.senderId = 100L;
        saved.senderType = SenderType.User; saved.content = "hello"; saved.createdAt = LocalDateTime.now();
        var msgResponse = new MessageResponse(5L, 1L, 100L, SenderType.User, "hello", saved.createdAt);

        when(conversationRepository.findById(1L)).thenReturn(Uni.createFrom().item(conversation));
        when(blockedRepository.existsByOrgAndUser(200L, 100L)).thenReturn(Uni.createFrom().item(false));
        when(messageRepository.<Message>persist(any(Message.class))).thenReturn(Uni.createFrom().item(saved));
        when(conversationRepository.<Conversation>persist(any(Conversation.class)))
                .thenReturn(Uni.createFrom().item(conversation));
        when(mapper.toResponse(any(Message.class))).thenReturn(msgResponse);

        var result = service.sendMessage(1L, new SendMessageRequest("hello"), 100L, SenderType.User)
                .await().indefinitely();

        assertTrue(result.isRight());
        assertEquals("hello", result.getOrElse(null).content());
        assertEquals(SenderType.User, result.getOrElse(null).senderType());
        assertNotNull(conversation.lastMessageAt);
    }

    @Test
    void sendMessage_userBlocked_returnsLeft403() {
        when(conversationRepository.findById(1L)).thenReturn(Uni.createFrom().item(conversation));
        when(blockedRepository.existsByOrgAndUser(200L, 100L)).thenReturn(Uni.createFrom().item(true));

        var result = service.sendMessage(1L, new SendMessageRequest("hi"), 100L, SenderType.User)
                .await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, result.fold(e -> e, __ -> null));
        assertEquals(403, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void sendMessage_orgWhenUserBlocked_stillReturnsRight() {
        var saved = new Message();
        saved.id = 7L; saved.conversationId = 1L; saved.senderId = 200L;
        saved.senderType = SenderType.Organization; saved.content = "from org";
        saved.createdAt = LocalDateTime.now();
        var msgResponse = new MessageResponse(7L, 1L, 200L, SenderType.Organization, "from org", saved.createdAt);

        when(conversationRepository.findById(1L)).thenReturn(Uni.createFrom().item(conversation));
        when(messageRepository.<Message>persist(any(Message.class))).thenReturn(Uni.createFrom().item(saved));
        when(conversationRepository.<Conversation>persist(any(Conversation.class)))
                .thenReturn(Uni.createFrom().item(conversation));
        when(mapper.toResponse(any(Message.class))).thenReturn(msgResponse);

        var result = service.sendMessage(1L, new SendMessageRequest("from org"), 200L, SenderType.Organization)
                .await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(SenderType.Organization, result.getOrElse(null).senderType());
    }

    @Test
    void sendMessage_asOrganization_returnsRight() {
        var saved = new Message();
        saved.id = 6L; saved.conversationId = 1L; saved.senderId = 200L;
        saved.senderType = SenderType.Organization; saved.content = "from org";
        saved.createdAt = LocalDateTime.now();
        var msgResponse = new MessageResponse(6L, 1L, 200L, SenderType.Organization, "from org", saved.createdAt);

        when(conversationRepository.findById(1L)).thenReturn(Uni.createFrom().item(conversation));
        when(messageRepository.<Message>persist(any(Message.class))).thenReturn(Uni.createFrom().item(saved));
        when(conversationRepository.<Conversation>persist(any(Conversation.class)))
                .thenReturn(Uni.createFrom().item(conversation));
        when(mapper.toResponse(any(Message.class))).thenReturn(msgResponse);

        var result = service.sendMessage(1L, new SendMessageRequest("from org"), 200L, SenderType.Organization)
                .await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(SenderType.Organization, result.getOrElse(null).senderType());
    }

    @Test
    void sendMessage_notParticipant_returnsLeft403() {
        when(conversationRepository.findById(1L)).thenReturn(Uni.createFrom().item(conversation));

        var result = service.sendMessage(1L, new SendMessageRequest("nope"), 999L, SenderType.User)
                .await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(403, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- blockUser ---

    @Test
    void blockUser_asOrganization_persistsBlock() {
        when(conversationRepository.findById(1L)).thenReturn(Uni.createFrom().item(conversation));
        when(blockedRepository.findByOrgAndUser(200L, 100L)).thenReturn(Uni.createFrom().nullItem());
        when(blockedRepository.persist(any(BlockedParticipant.class)))
                .thenReturn(Uni.createFrom().item(new BlockedParticipant()));

        var result = service.blockUser(1L, 200L, new BlockUserRequest("spam")).await().indefinitely();

        assertTrue(result.isRight());
    }

    @Test
    void blockUser_alreadyBlocked_isIdempotent() {
        var existing = new BlockedParticipant();
        existing.id = 99L; existing.organizationId = 200L; existing.userId = 100L;

        when(conversationRepository.findById(1L)).thenReturn(Uni.createFrom().item(conversation));
        when(blockedRepository.findByOrgAndUser(200L, 100L)).thenReturn(Uni.createFrom().item(existing));

        var result = service.blockUser(1L, 200L, null).await().indefinitely();

        assertTrue(result.isRight());
    }

    @Test
    void blockUser_notOwnerOrg_returnsLeft403() {
        when(conversationRepository.findById(1L)).thenReturn(Uni.createFrom().item(conversation));

        var result = service.blockUser(1L, 999L, new BlockUserRequest("nope")).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(403, result.fold(DomainError::httpStatus, __ -> 0));
    }

    // --- unblockUser ---

    @Test
    void unblockUser_existing_deletes() {
        var existing = new BlockedParticipant();
        existing.id = 99L; existing.organizationId = 200L; existing.userId = 100L;

        when(conversationRepository.findById(1L)).thenReturn(Uni.createFrom().item(conversation));
        when(blockedRepository.findByOrgAndUser(200L, 100L)).thenReturn(Uni.createFrom().item(existing));
        when(blockedRepository.delete(existing)).thenReturn(Uni.createFrom().voidItem());

        var result = service.unblockUser(1L, 200L).await().indefinitely();

        assertTrue(result.isRight());
    }

    @Test
    void unblockUser_notExisting_isIdempotent() {
        when(conversationRepository.findById(1L)).thenReturn(Uni.createFrom().item(conversation));
        when(blockedRepository.findByOrgAndUser(200L, 100L)).thenReturn(Uni.createFrom().nullItem());

        var result = service.unblockUser(1L, 200L).await().indefinitely();

        assertTrue(result.isRight());
    }
}
