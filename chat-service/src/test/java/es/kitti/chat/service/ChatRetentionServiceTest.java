package es.kitti.chat.service;

import es.kitti.chat.entity.Conversation;
import es.kitti.chat.repository.ConversationRepository;
import es.kitti.chat.repository.MessageRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRetentionServiceTest {

    @Mock ConversationRepository conversationRepository;
    @Mock MessageRepository      messageRepository;
    @InjectMocks ChatRetentionService service;

    @Test
    void purgeInactiveConversations_noItems_skipsDelete() {
        when(conversationRepository.findInactiveBefore(any(LocalDateTime.class)))
                .thenReturn(Uni.createFrom().item(List.of()));

        service.purgeInactiveConversations().await().indefinitely();

        verify(messageRepository, never()).deleteByConversationIds(any());
        verify(conversationRepository, never()).deleteByIds(any());
    }

    @Test
    void purgeInactiveConversations_withItems_deletesMessagesBeforeConversations() {
        Conversation conv = new Conversation();
        conv.id = 3L;
        when(conversationRepository.findInactiveBefore(any(LocalDateTime.class)))
                .thenReturn(Uni.createFrom().item(List.of(conv)));
        when(messageRepository.deleteByConversationIds(List.of(3L)))
                .thenReturn(Uni.createFrom().item(1L));
        when(conversationRepository.deleteByIds(List.of(3L)))
                .thenReturn(Uni.createFrom().item(1L));

        service.purgeInactiveConversations().await().indefinitely();

        InOrder order = inOrder(messageRepository, conversationRepository);
        order.verify(messageRepository).deleteByConversationIds(List.of(3L));
        order.verify(conversationRepository).deleteByIds(List.of(3L));
    }
}
