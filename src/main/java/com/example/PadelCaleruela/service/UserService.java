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
        // 🔹 Comprobación de username único
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("El nombre de usuario ya está en uso.");
        }

        // 🔹 Comprobación opcional de email único
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado.");
        }

        // 🔹 Encriptar la contraseña antes de guardar
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 🔹 Guardar usuario
        User saved = userRepository.save(user);

        // 🔹 Retornar el DTO
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

            // 🔹 Verificar si el nuevo username pertenece a otro usuario
            if (!user.getUsername().equals(updatedUser.getUsername()) &&
                    userRepository.findByUsername(updatedUser.getUsername()).isPresent()) {
                throw new IllegalArgumentException("El nombre de usuario ya está en uso por otro usuario.");
            }

            // 🔹 Verificar si el nuevo email pertenece a otro usuario
            if (!user.getEmail().equals(updatedUser.getEmail()) &&
                    userRepository.findByEmail(updatedUser.getEmail()).isPresent()) {
                throw new IllegalArgumentException("El correo electrónico ya está registrado por otro usuario.");
            }

            // 🔹 Actualizar los campos permitidos
            user.setUsername(updatedUser.getUsername());
            user.setEmail(updatedUser.getEmail());
            user.setFullName(updatedUser.getFullName());

            // 🔹 Solo encriptar si se envía una nueva contraseña
            if (updatedUser.getPassword() != null && !updatedUser.getPassword().isBlank()) {
                user.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
            }

            // 🔹 Guardar cambios y devolver DTO
            User savedUser = userRepository.save(user);
            return toDTO(savedUser);
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
