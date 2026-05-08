package es.kitti.chat.config;

import es.kitti.chat.dto.BlockUserRequest;
import es.kitti.chat.dto.ChatDataExport;
import es.kitti.chat.dto.ConversationExportEntry;
import es.kitti.chat.dto.ConversationResponse;
import es.kitti.chat.dto.CreateConversationRequest;
import es.kitti.chat.dto.MessageResponse;
import es.kitti.chat.dto.SendMessageRequest;
import es.kitti.chat.entity.SenderType;
import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        BlockUserRequest.class,
        ChatDataExport.class,
        ConversationExportEntry.class,
        ConversationResponse.class,
        CreateConversationRequest.class,
        MessageResponse.class,
        SendMessageRequest.class,
        SenderType.class,
        ErrorResponse.class,
        FieldViolation.class,
        ValidationError.class
})
public class NativeConfig {}
