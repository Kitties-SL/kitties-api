package es.kitti.chat.service;

import es.kitti.chat.dto.*;
import es.kitti.chat.entity.BlockedParticipant;
import es.kitti.chat.entity.Conversation;
import es.kitti.chat.entity.Message;
import es.kitti.chat.entity.SenderType;
import es.kitti.chat.mapper.ChatMapper;
import es.kitti.chat.repository.BlockedParticipantRepository;
import es.kitti.chat.repository.ConversationRepository;
import es.kitti.chat.repository.MessageRepository;
import es.kitti.mon.either.Either;
import es.kitti.mon.either.Unit;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class ChatService {

    @Inject ConversationRepository conversationRepository;
    @Inject MessageRepository messageRepository;
    @Inject BlockedParticipantRepository blockedRepository;
    @Inject ChatMapper mapper;

    @WithTransaction
    public Uni<Either<DomainError, ConversationResponse>> createConversation(CreateConversationRequest request) {
        return conversationRepository.findByIntakeRequestId(request.intakeRequestId())
                .onItem().transformToUni(existing -> {
                    if (existing != null)
                        return Uni.createFrom().item(Either.left(
                                new ConflictError("CONVERSATION_ALREADY_EXISTS")));
                    Conversation c = new Conversation();
                    c.intakeRequestId = request.intakeRequestId();
                    c.userId = request.userId();
                    c.organizationId = request.organizationId();
                    return conversationRepository.persist(c)
                            .onItem().transform(saved ->
                                    Either.<DomainError, ConversationResponse>right(mapper.toResponse(saved)));
                });
    }

    @WithSession
    public Uni<List<ConversationResponse>> findMineAsUser(Long userId) {
        return conversationRepository.findByUserId(userId)
                .onItem().transform(list -> list.stream().map(mapper::toResponse).toList());
    }

    @WithSession
    public Uni<List<ConversationResponse>> findMineAsOrganization(Long organizationId) {
        return conversationRepository.findByOrganizationId(organizationId)
                .onItem().transform(list -> list.stream().map(mapper::toResponse).toList());
    }

    @WithSession
    public Uni<Either<DomainError, List<MessageResponse>>> listMessages(Long conversationId, Long callerId, SenderType callerType) {
        return loadAndAuthorize(conversationId, callerId, callerType)
                .onItem().transformToUni(either -> either.fold(
                        error -> Uni.createFrom().item(Either.left(error)),
                        c -> messageRepository.findByConversationId(conversationId)
                                .onItem().transform(list ->
                                        Either.<DomainError, List<MessageResponse>>right(
                                                list.stream().map(mapper::toResponse).toList()))
                ));
    }

    @WithTransaction
    public Uni<Either<DomainError, MessageResponse>> sendMessage(Long conversationId, SendMessageRequest request,
                                                                  Long callerId, SenderType callerType) {
        return loadAndAuthorize(conversationId, callerId, callerType)
                .onItem().transformToUni(either -> either.fold(
                        error -> Uni.createFrom().item(Either.left(error)),
                        c -> rejectIfUserBlocked(c, callerId, callerType)
                                .onItem().transformToUni(blockEither -> blockEither.fold(
                                        error -> Uni.createFrom().item(Either.<DomainError, MessageResponse>left(error)),
                                        __ -> {
                                            Message m = new Message();
                                            m.conversationId = c.id;
                                            m.senderId = callerId;
                                            m.senderType = callerType;
                                            m.content = request.content();
                                            return messageRepository.<Message>persist(m)
                                                    .onItem().call(saved -> {
                                                        c.lastMessageAt = LocalDateTime.now();
                                                        return conversationRepository.persist(c);
                                                    })
                                                    .onItem().transform(saved ->
                                                            Either.<DomainError, MessageResponse>right(mapper.toResponse(saved)));
                                        }
                                ))
                ));
    }

    @WithTransaction
    public Uni<Either<DomainError, Unit>> blockUser(Long conversationId, Long callerOrgId, BlockUserRequest request) {
        return loadAndAuthorize(conversationId, callerOrgId, SenderType.Organization)
                .onItem().transformToUni(either -> either.fold(
                        error -> Uni.createFrom().item(Either.left(error)),
                        c -> blockedRepository.findByOrgAndUser(c.organizationId, c.userId)
                                .onItem().transformToUni(existing -> {
                                    if (existing != null) {
                                        if (request != null && request.reason() != null) {
                                            existing.reason = request.reason();
                                            return blockedRepository.<BlockedParticipant>persist(existing)
                                                    .onItem().transform(v -> Either.unit());
                                        }
                                        return Uni.createFrom().item(Either.unit());
                                    }
                                    BlockedParticipant b = new BlockedParticipant();
                                    b.organizationId = c.organizationId;
                                    b.userId = c.userId;
                                    b.reason = request != null ? request.reason() : null;
                                    return blockedRepository.persist(b)
                                            .onItem().transform(v -> Either.unit());
                                })
                ));
    }

    @WithSession
    public Uni<ChatDataExport> exportByUserId(Long userId) {
        return conversationRepository.findByUserId(userId)
                .onItem().transformToUni(convs -> {
                    if (convs.isEmpty()) return Uni.createFrom().item(new ChatDataExport(List.of()));
                    List<Uni<ConversationExportEntry>> entries = convs.stream()
                            .map(c -> messageRepository.findByConversationId(c.id)
                                    .onItem().transform(msgs -> new ConversationExportEntry(
                                            mapper.toResponse(c),
                                            msgs.stream().map(mapper::toResponse).toList())))
                            .toList();
                    return Uni.join().all(entries).andFailFast()
                            .onItem().transform(ChatDataExport::new);
                });
    }

    @WithTransaction
    public Uni<Void> anonymizeUser(Long userId) {
        return conversationRepository.anonymizeUser(userId)
                .chain(() -> messageRepository.anonymizeSender(userId))
                .chain(() -> blockedRepository.deleteByUserId(userId))
                .replaceWithVoid();
    }

    @WithTransaction
    public Uni<Either<DomainError, Unit>> unblockUser(Long conversationId, Long callerOrgId) {
        return loadAndAuthorize(conversationId, callerOrgId, SenderType.Organization)
                .onItem().transformToUni(either -> either.fold(
                        error -> Uni.createFrom().item(Either.left(error)),
                        c -> blockedRepository.findByOrgAndUser(c.organizationId, c.userId)
                                .onItem().transformToUni(existing -> {
                                    if (existing == null)
                                        return Uni.createFrom().item(Either.unit());
                                    return blockedRepository.delete(existing)
                                            .onItem().transform(v -> Either.unit());
                                })
                ));
    }

    private Uni<Either<DomainError, Unit>> rejectIfUserBlocked(Conversation c, Long callerId, SenderType callerType) {
        if (callerType != SenderType.User)
            return Uni.createFrom().item(Either.unit());
        return blockedRepository.existsByOrgAndUser(c.organizationId, callerId)
                .onItem().transform(blocked ->
                        blocked
                                ? Either.<DomainError, Unit>left(new ForbiddenError("USER_BLOCKED"))
                                : Either.unit()
                );
    }

    private Uni<Either<DomainError, Conversation>> loadAndAuthorize(Long conversationId, Long callerId, SenderType callerType) {
        return conversationRepository.findById(conversationId)
                .onItem().transform(c -> {
                    if (c == null)
                        return Either.<DomainError, Conversation>left(new NotFoundError("CONVERSATION_NOT_FOUND"));
                    boolean ok = switch (callerType) {
                        case User         -> c.userId.equals(callerId);
                        case Organization -> c.organizationId.equals(callerId);
                    };
                    return ok
                            ? Either.<DomainError, Conversation>right(c)
                            : Either.<DomainError, Conversation>left(new ForbiddenError("ACCESS_DENIED"));
                });
    }
}
