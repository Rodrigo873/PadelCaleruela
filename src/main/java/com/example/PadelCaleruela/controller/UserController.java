package com.example.PadelCaleruela.controller;


import com.example.PadelCaleruela.dto.*;
import com.example.PadelCaleruela.model.Role;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.service.AuthService;
import com.example.PadelCaleruela.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:4200")
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    public UserController(UserService service,AuthService authService) { this.userService = service;
        this.authService=authService;
    }

    @GetMapping
    public List<UserDTO> getAll() {
        return userService.getAllUsers();
    }

    @GetMapping("/infoUser")
    public List<InfoUserDTO> getAllInfo() {
        return userService.getAllInfoUsers();
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<Map<String, String>> updateUserRole(
            @PathVariable Long userId,
            @RequestParam String newRole) {

        userService.updateUserRole(userId, newRole);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Rol actualizado correctamente");
        response.put("newRole", newRole);
        return ResponseEntity.ok(response);
    }

    // ⬇️ Añadir dentro de UserController
    @PutMapping("/{id}/change-ayuntamiento")
    public ResponseEntity<?> changeAyuntamiento(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {

        Long newAytoId = body.get("ayuntamientoId");

        User currentUser = authService.getCurrentUser();

        // ❌ ADMIN no puede cambiar ayuntamiento (ni propio ni ajeno)
        if (currentUser.getRole() == Role.ADMIN) {
            return ResponseEntity.status(403)
                    .body("Los administradores no pueden cambiar de ayuntamiento.");
        }

        // ❌ Usuario normal solo puede cambiar su propio ayuntamiento
        if (!currentUser.getId().equals(id) &&
                currentUser.getRole() != Role.SUPERADMIN) {
            return ResponseEntity.status(403)
                    .body("No tienes permiso para cambiar el ayuntamiento de este usuario.");
        }

        UserDTO updated = userService.updateUserAyuntamiento(id, newAytoId);

        return ResponseEntity.ok(updated);
    }



    @GetMapping("/{id}")
    public UserDTO getById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @GetMapping("/public/{id}")
    public PlayerInfoDTO getPublicProfile(@PathVariable Long id) {
        return userService.getPublicPlayerProfile(id);
    }


    @GetMapping("/search")
    public ResponseEntity<List<UserDTO>> searchUsers(@RequestParam("username") String username) {
        return ResponseEntity.ok(userService.searchUsersByUsername(username));
    }

    @GetMapping("/available")
    public ResponseEntity<List<UserDTO>> getAvailablePlayers() {
        List<UserDTO> users = userService.findAvailablePlayers();
        return ResponseEntity.ok(users);
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

    @PutMapping("/{id}/profile-image")
    public ResponseEntity<?> updateProfileImage(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        String imageUrl = request.get("imageUrl");
        User updatedUser = userService.updateProfileImage(id, imageUrl);
        return ResponseEntity.ok(updatedUser);
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String password,
            @RequestPart(required = false) MultipartFile profileImage
    ) {
        try {
            UserDTO updatedUser = userService.updateUserProfile(id,fullName, username, email, password, profileImage);
            return ResponseEntity.ok(updatedUser);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error al guardar la imagen");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ✅ Actualizar estado (llamando al service)
    @PutMapping("/{id}/status")
    public ResponseEntity<String> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String newStatus = body.get("status");
        userService.updateUserStatus(id, newStatus);

        return ResponseEntity.ok("Estado de usuario actualizado correctamente.");
    }

    /**
     * 🧠 Jugadores sugeridos (amigos de mis amigos)
     */
    @GetMapping("/suggested/{userId}")
    public List<UserDTO> getSuggestedPlayers(@PathVariable Long userId) {
        return userService.getSuggestedPlayers(userId);
    }

    @GetMapping("/availableLeagues")
    public ResponseEntity<List<PlayerInfoDTO>> getAvailableUsers(@RequestParam Long leagueId) {
        List<PlayerInfoDTO> users = userService.getAvailableUsersForLeague(leagueId);
        return ResponseEntity.ok(users);
    }

    /** 🔹 Obtener lista de amigos mutuos */
    @GetMapping("/{userId}/friends")
    public ResponseEntity<List<UserDTO>> getFriends(@PathVariable Long userId) {
        List<UserDTO> amigos = userService.getFriends(userId);
        return ResponseEntity.ok(amigos);
    }

    @GetMapping("/available-for-reservation/{reservationId}")
    public ResponseEntity<List<PlayerInfoDTO>> getAvailablePlayersForReservation(
            @PathVariable Long reservationId,
            @RequestParam Long requesterId) {
        List<PlayerInfoDTO> players = userService.getAvailablePlayersForReservation(reservationId, requesterId);
        return ResponseEntity.ok(players);
    }


    // Eliminar usuario
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        boolean deleted = userService.deleteUser(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/following")
    public ResponseEntity<List<PlayerSimpleDTO>> getUsersIFollow() {
        return ResponseEntity.ok(userService.getUsersIFollow());
    }

    @GetMapping("/followers")
    public ResponseEntity<List<FollowerStatusDTO>> getMyFollowers() {
        return ResponseEntity.ok(userService.getMyFollowers());
    }

    @GetMapping("/blocked")
    public List<PlayerInfoDTO> getMisUsuariosBloqueados() {
        return userService.getUsuariosQueYoHeBloqueado();
    }

    @GetMapping("/ayuntamiento/blocked")
    public List<InfoUserDTO> getUsuariosBloqueadosPorAyuntamiento() {
        return userService.getUsuariosBloqueadosPorMiAyuntamiento();
    }


}
