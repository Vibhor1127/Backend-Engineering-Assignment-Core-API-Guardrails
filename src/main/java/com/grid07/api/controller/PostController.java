package com.grid07.api.controller;

import com.grid07.api.dto.CreateCommentRequest;
import com.grid07.api.dto.CreatePostRequest;
import com.grid07.api.dto.LikePostRequest;
import com.grid07.api.entity.Comment;
import com.grid07.api.entity.Post;
import com.grid07.api.service.PostService;
import com.grid07.api.service.ViralityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PostController {

    private final PostService postService;
    private final ViralityService viralityService;

    public PostController(PostService postService, ViralityService viralityService) {
        this.postService = postService;
        this.viralityService = viralityService;
    }

    // ==================== POST ENDPOINTS ====================

    // Create a new post
    @PostMapping("/posts")
    public ResponseEntity<Post> createPost(@RequestBody CreatePostRequest request) {
        Post post = postService.createPost(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    // Get all posts (bonus - helpful for testing)
    @GetMapping("/posts")
    public ResponseEntity<List<Post>> getAllPosts() {
        return ResponseEntity.ok(postService.getAllPosts());
    }

    // Add a comment to a post
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<Comment> addComment(
            @PathVariable Long postId,
            @RequestBody CreateCommentRequest request) {

        Comment comment = postService.addComment(postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    // Like a post
    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<Post> likePost(
            @PathVariable Long postId,
            @RequestBody LikePostRequest request) {

        Post post = postService.likePost(postId, request.getUserId());
        return ResponseEntity.ok(post);
    }

    // ==================== BONUS ENDPOINTS ====================

    // Get virality score of a post from Redis
    @GetMapping("/posts/{postId}/virality")
    public ResponseEntity<Map<String, Object>> getViralityScore(@PathVariable Long postId) {
        long score = viralityService.getViralityScore(postId);
        long botCount = viralityService.getBotCount(postId);

        Map<String, Object> result = new HashMap<>();
        result.put("postId", postId);
        result.put("viralityScore", score);
        result.put("botReplyCount", botCount);
        return ResponseEntity.ok(result);
    }
}
