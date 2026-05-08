package com.grid07.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Holds the data needed to create a new post
@Getter
@Setter
@NoArgsConstructor
public class CreatePostRequest {

    private Long authorId;
    private String authorType;
    private String content;
}
