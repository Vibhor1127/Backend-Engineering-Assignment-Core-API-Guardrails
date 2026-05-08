package com.grid07.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Holds the data needed to create a comment on a post
@Getter
@Setter
@NoArgsConstructor
public class CreateCommentRequest {

    private Long authorId;
    private String authorType;
    private String content;
    private int depthLevel;
    private Long postOwnerId;
}
