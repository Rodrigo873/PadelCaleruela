package com.example.PadelCaleruela.controller;

import com.example.PadelCaleruela.dto.FriendshipDTO;
import com.example.PadelCaleruela.dto.UserDTO;
import com.example.PadelCaleruela.service.FriendshipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friendships")
@CrossOrigin(origins = "http://localhost:4200")
public class FriendshipController {

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService){
        this.friendshipService=friendshipService;
    }

    // Enviar solicitud
    @PostMapping("/request")
    public ResponseEntity<?> sendRequest(@RequestParam Long fromUserId, @RequestParam Long toUserId) {
        friendshipService.sendFriendRequest(fromUserId, toUserId);
        return ResponseEntity.ok("Solicitud enviada");
    }

    // Aceptar solicitud
    @PostMapping("/accept")
    public ResponseEntity<?> acceptRequest(@RequestParam Long user,@RequestParam Long friend) {
        friendshipService.acceptFriendRequest(user,friend);
        return ResponseEntity.ok("Solicitud aceptada");
    }

    //Rechazar solicitud
    @PostMapping("/reject")
    public ResponseEntity<?> rejectRequest(@RequestParam Long user,@RequestParam Long friend) {
        friendshipService.rejectFriendRequest(user,friend);
        return ResponseEntity.ok("Solicitud rechazada");
    }


    // Eliminar amigo
    @DeleteMapping("/{friendshipId}")
    public ResponseEntity<?> deleteFriendship(@RequestParam Long user,@RequestParam Long friend) {
        friendshipService.removeFriendship(user,friend);
        return ResponseEntity.ok("Amigo eliminado");
    }

    //Listar solicitudes pendientes
    @GetMapping("/pending/{userId}")
    public List<FriendshipDTO> getPendingRequests(@PathVariable Long userId) {
        return friendshipService.getPendingRequests(userId);
    }

    // Listar amigos de un usuario
    @GetMapping("/user/{userId}")
    public List<UserDTO> getFriends(@PathVariable Long userId) {
        return friendshipService.getFriendsOfUser(userId);
    }
}

