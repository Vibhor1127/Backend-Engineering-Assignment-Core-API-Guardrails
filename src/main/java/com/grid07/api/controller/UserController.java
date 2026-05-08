package com.grid07.api.controller;

import com.grid07.api.entity.Bot;
import com.grid07.api.entity.User;
import com.grid07.api.repository.BotRepository;
import com.grid07.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserRepository userRepository;
    private final BotRepository botRepository;

    public UserController(UserRepository userRepository, BotRepository botRepository) {
        this.userRepository = userRepository;
        this.botRepository = botRepository;
    }

    // Saves a new user to the database
    @PostMapping("/users")
    public ResponseEntity<User> createUser(@RequestBody User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userRepository.save(user));
    }

    // Returns all registered users
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    // Saves a new bot to the database
    @PostMapping("/bots")
    public ResponseEntity<Bot> createBot(@RequestBody Bot bot) {
        return ResponseEntity.status(HttpStatus.CREATED).body(botRepository.save(bot));
    }

    // Returns all registered bots
    @GetMapping("/bots")
    public ResponseEntity<List<Bot>> getAllBots() {
        return ResponseEntity.ok(botRepository.findAll());
    }
}
