package com.earthscan.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Simple acknowledgement. The single {@code message} field mirrors the anonymous objects the ASP.NET
 * controllers returned, which the frontend reads as {@code response.data.message}.
 */
@Schema(name = "MessageResponse")
public record MessageResponse(String message) {

    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}
