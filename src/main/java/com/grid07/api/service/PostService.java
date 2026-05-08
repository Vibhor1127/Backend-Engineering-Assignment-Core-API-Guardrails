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

    // ==================== CREATE POST ====================

    @Transactional
    public Post createPost(CreatePostRequest request) {
        Post post = new Post();
        post.setAuthorId(request.getAuthorId());
        post.setAuthorType(request.getAuthorType());
        post.setContent(request.getContent());
        return postRepository.save(post);
    }

    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

    // ==================== ADD COMMENT ====================

    /**
     * Adds a comment to a post.
     *
     * Before saving to the database, we check:
     * 1. Vertical cap  -> depth cannot exceed 20
     * 2. Horizontal cap -> bot replies cannot exceed 100 (using atomic Redis INCR)
     * 3. Cooldown cap  -> bot cannot interact with the same human within 10 minutes
     *
     * Only after all checks pass, we commit to PostgreSQL.
     */
    @Transactional
    public Comment addComment(Long postId, CreateCommentRequest request) {

        // Check: post must exist
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));

        boolean isBotComment = "BOT".equalsIgnoreCase(request.getAuthorType());

        // ---- Guardrail 1: Vertical Cap ----
        if (request.getDepthLevel() > 20) {
            throw new TooManyRequestsException("Comment thread is too deep. Max depth is 20 levels.");
        }

        if (isBotComment) {

            // ---- Guardrail 2: Horizontal Cap (Atomic) ----
            // We increment FIRST, then check. This is the atomic pattern.
            long newCount = viralityService.incrementBotCount(postId);

            if (newCount > 100) {
                // Went over the limit - roll back the counter and reject
                viralityService.decrementBotCount(postId);
                throw new TooManyRequestsException(
                        "This post has reached the bot reply limit of 100. Request rejected."
                );
            }

            // ---- Guardrail 3: Cooldown Cap ----
            Long postOwnerId = request.getPostOwnerId();
            if (postOwnerId != null && viralityService.isBotInCooldown(request.getAuthorId(), postOwnerId)) {
                // This bot already interacted with this human in the last 10 minutes
                viralityService.decrementBotCount(postId); // undo the increment
                throw new TooManyRequestsException(
                        "Bot is in cooldown. Cannot interact with this user again within 10 minutes."
                );
            }

            // All checks passed - start cooldown and update virality
            if (postOwnerId != null) {
                viralityService.startBotCooldown(request.getAuthorId(), postOwnerId);

                // Also handle notification for the post owner
                handleBotNotification(request.getAuthorId(), postOwnerId, postId);
            }

            // Update virality score for bot reply
            viralityService.addViralityForBotReply(postId);

        } else {
            // Human comment - update virality
            viralityService.addViralityForHumanComment(postId);
        }

        // All good - save the comment to PostgreSQL
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(request.getAuthorId());
        comment.setAuthorType(request.getAuthorType());
        comment.setContent(request.getContent());
        comment.setDepthLevel(request.getDepthLevel());

        return commentRepository.save(comment);
    }

    // ==================== LIKE POST ====================

    @Transactional
    public Post likePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));

        // Update likes in DB
        post.setLikeCount(post.getLikeCount() + 1);
        postRepository.save(post);

        // Update virality score in Redis (+20 for human like)
        viralityService.addViralityForHumanLike(postId);

        return post;
    }

    // ==================== NOTIFICATION LOGIC ====================

    /**
     * Handles notification throttling when a bot interacts with a user's post.
     * - If the user got a notification in the last 15 minutes, queue the message.
     * - Otherwise, send immediately (log it) and set a 15-minute cooldown.
     */
    private void handleBotNotification(Long botId, Long userId, Long postId) {
        String message = "Bot " + botId + " replied to your post " + postId;

        if (viralityService.userReceivedRecentNotification(userId)) {
            // User already notified recently - add to pending queue
            viralityService.addPendingNotification(userId, message);
            System.out.println("[Notification] Queued for user " + userId + ": " + message);
        } else {
            // Send right away and set cooldown
            System.out.println("[Notification] Push Notification Sent to User " + userId + ": " + message);
            viralityService.setNotificationCooldown(userId);
        }
    }
}
