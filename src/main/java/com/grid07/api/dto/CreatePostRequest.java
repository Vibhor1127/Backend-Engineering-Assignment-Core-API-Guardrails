package com.grid07.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreatePostRequest {

    private Long authorId;
    private String authorType; // "USER" or "BOT"
    private String content;
}
