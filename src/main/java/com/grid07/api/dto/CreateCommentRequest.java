package com.grid07.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateCommentRequest {

    private Long authorId;
    private String authorType; // "USER" or "BOT"
    private String content;
    private int depthLevel;
    private Long postOwnerId; // the human user who owns the post (for cooldown checks)
}
