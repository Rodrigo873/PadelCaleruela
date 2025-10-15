package com.example.PadelCaleruela.service;


import com.example.PadelCaleruela.dto.UserDTO;
import com.example.PadelCaleruela.model.User;
import com.example.PadelCaleruela.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository repo,BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = repo;
        this.passwordEncoder=passwordEncoder;
    }

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public UserDTO saveUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User saved = userRepository.save(user);
        return toDTO(saved);
    }

    public UserDTO getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    // 🔹 Actualizar usuario
    public Optional<UserDTO> updateUser(Long id, User updatedUser) {
        return userRepository.findById(id).map(user -> {
            user.setUsername(updatedUser.getUsername());
            user.setEmail(updatedUser.getEmail());
            if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
                user.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
            }
            user.setFullName(updatedUser.getFullName());
            return toDTO(userRepository.save(user));
        });
    }

    // 🔹 Eliminar usuario
    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }


    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }



}
