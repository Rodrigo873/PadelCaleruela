package com.example.PadelCaleruela.controller;


import com.example.PadelCaleruela.dto.UserDTO;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:4200")
public class UserController {

    private final UserService userService;
    public UserController(UserService service) { this.userService = service; }

    @GetMapping
    public List<UserDTO> getAll() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public UserDTO getById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @PostMapping
    public UserDTO create(@RequestBody User user) {
        return userService.saveUser(user);
    }

    // Actualizar usuario
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody User updatedUser) {
        return userService.updateUser(id, updatedUser)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Eliminar usuario
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        boolean deleted = userService.deleteUser(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
