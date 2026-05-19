package es.kitti.chat.config;

import es.kitti.chat.dto.*;
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
