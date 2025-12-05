package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.AyuntamientoRepository;
import com.example.PadelCaleruela.repository.BlockRepository;
import com.example.PadelCaleruela.repository.FriendshipRepository;
import com.example.PadelCaleruela.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;
    private final AyuntamientoRepository ayuntamientoRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    @Transactional
    public void bloquearUsuarioDesdeAyuntamiento(Ayuntamiento ayuntamiento, User usuario) {

        // 1. Verificar si ya está bloqueado por este ayuntamiento
        if (blockRepository.existsByBlockedUserAndBlockedByAyuntamiento(usuario, ayuntamiento)) {
            return;
        }

        // 2. Crear el bloqueo
        Block block = new Block();
        block.setBlockedUser(usuario);
        block.setBlockedByAyuntamiento(ayuntamiento);
        blockRepository.save(block);

        // 3. Asignar al usuario al ayuntamiento con ID 99999
        Ayuntamiento fakeAyto = ayuntamientoRepository.findByCodigoPostal("99999")
                .orElseThrow(() -> new RuntimeException("El ayuntamiento 99999 no existe"));

        usuario.setAyuntamiento(fakeAyto);

        // 4. Guardar usuario actualizado
        userRepository.save(usuario);
    }


    @Transactional
    public void bloquearUsuarioDesdeUsuario(User bloqueante, User bloqueado) {

        // --------------------------------------------------------
        // 1️⃣ Si ya está bloqueado, salimos
        // --------------------------------------------------------
        if (blockRepository.existsByBlockedUserAndBlockedByUser(bloqueado, bloqueante)) {
            return;
        }

        // --------------------------------------------------------
        // 2️⃣ Si son amigos → eliminar amistad automáticamente
        // --------------------------------------------------------
        Optional<Friendship> f1 = friendshipRepository.findByUserAndFriend(bloqueante, bloqueado);
        Optional<Friendship> f2 = friendshipRepository.findByUserAndFriend(bloqueado, bloqueante);

        Optional<Friendship> friendshipOpt = f1.isPresent() ? f1 : f2;

        if (friendshipOpt.isPresent()) {
            Friendship friendship = friendshipOpt.get();

            if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                friendshipRepository.delete(friendship);
            }
        }

        // --------------------------------------------------------
        // 3️⃣ Crear el bloqueo
        // --------------------------------------------------------
        Block block = new Block();
        block.setBlockedUser(bloqueado);
        block.setBlockedByUser(bloqueante);
        blockRepository.save(block);
    }


    // =====================================================
    // DESBLOQUEAR — USUARIO → USUARIO
    // =====================================================
    @Transactional
    public void desbloquearUsuarioDesdeUsuario(Long bloqueanteId, Long bloqueadoId) {
        blockRepository.deleteByBlockedByUser_IdAndBlockedUser_Id(bloqueanteId, bloqueadoId);
    }


    // =====================================================
    // DESBLOQUEAR — AYUNTAMIENTO → USUARIO
    // =====================================================
    @Transactional
    public void desbloquearUsuarioDesdeAyuntamiento(Long ayuntamientoId, Long bloqueadoId) {
        blockRepository.deleteByBlockedByAyuntamiento_IdAndBlockedUser_Id(ayuntamientoId, bloqueadoId);
    }
}
