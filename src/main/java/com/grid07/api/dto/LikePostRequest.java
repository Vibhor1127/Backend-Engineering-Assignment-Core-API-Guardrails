package com.grid07.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Holds the user ID for liking a post
@Getter
@Setter
@NoArgsConstructor
public class LikePostRequest {

    private Long userId;
}
