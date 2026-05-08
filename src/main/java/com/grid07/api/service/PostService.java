package com.grid07.api.service;

import com.grid07.api.dto.CreateCommentRequest;
import com.grid07.api.dto.CreatePostRequest;
import com.grid07.api.entity.Comment;
import com.grid07.api.entity.Post;
import com.grid07.api.exception.TooManyRequestsException;
import com.grid07.api.repository.CommentRepository;
import com.grid07.api.repository.PostRepository;
import com.grid07.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Handles creating posts, adding comments with bot guardrails, and liking posts
@Service
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ViralityService viralityService;

    public PostService(PostRepository postRepository,
                       CommentRepository commentRepository,
                       UserRepository userRepository,
                       ViralityService viralityService) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.viralityService = viralityService;
    }

    // Creates a new post and saves it to the database
    @Transactional
    public Post createPost(CreatePostRequest request) {
        Post post = new Post();
        post.setAuthorId(request.getAuthorId());
        post.setAuthorType(request.getAuthorType());
        post.setContent(request.getContent());
        return postRepository.save(post);
    }

    // Returns all posts from the database
    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

    // Adds a comment to a post after enforcing depth limit, bot reply cap, and cooldown rules
    @Transactional
    public Comment addComment(Long postId, CreateCommentRequest request) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));

        boolean isBotComment = "BOT".equalsIgnoreCase(request.getAuthorType());

        // Reject if the comment thread is deeper than 20 levels
        if (request.getDepthLevel() > 20) {
            throw new TooManyRequestsException("Comment thread is too deep. Max depth is 20 levels.");
        }

        if (isBotComment) {

            // Atomically increment bot reply count and reject if it exceeds 100
            long newCount = viralityService.incrementBotCount(postId);

            if (newCount > 100) {
                viralityService.decrementBotCount(postId);
                throw new TooManyRequestsException(
                        "This post has reached the bot reply limit of 100. Request rejected."
                );
            }

            // Reject if this bot already interacted with this human in the last 10 minutes
            Long postOwnerId = request.getPostOwnerId();
            if (postOwnerId != null && viralityService.isBotInCooldown(request.getAuthorId(), postOwnerId)) {
                viralityService.decrementBotCount(postId);
                throw new TooManyRequestsException(
                        "Bot is in cooldown. Cannot interact with this user again within 10 minutes."
                );
            }

            // Start cooldown timer and queue a notification for the post owner
            if (postOwnerId != null) {
                viralityService.startBotCooldown(request.getAuthorId(), postOwnerId);
                handleBotNotification(request.getAuthorId(), postOwnerId, postId);
            }

            // Bump the virality score for a bot reply
            viralityService.addViralityForBotReply(postId);

        } else {
            // Bump the virality score for a human comment
            viralityService.addViralityForHumanComment(postId);
        }

        // Save the comment to the database
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(request.getAuthorId());
        comment.setAuthorType(request.getAuthorType());
        comment.setContent(request.getContent());
        comment.setDepthLevel(request.getDepthLevel());

        return commentRepository.save(comment);
    }

    // Increments the like count on a post and updates its virality score
    @Transactional
    public Post likePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));

        post.setLikeCount(post.getLikeCount() + 1);
        postRepository.save(post);

        viralityService.addViralityForHumanLike(postId);

        return post;
    }

    // Sends the notification immediately or queues it if the user was notified recently
    private void handleBotNotification(Long botId, Long userId, Long postId) {
        String message = "Bot " + botId + " replied to your post " + postId;

        if (viralityService.userReceivedRecentNotification(userId)) {
            viralityService.addPendingNotification(userId, message);
            System.out.println("[Notification] Queued for user " + userId + ": " + message);
        } else {
            System.out.println("[Notification] Push Notification Sent to User " + userId + ": " + message);
            viralityService.setNotificationCooldown(userId);
        }
    }
}
