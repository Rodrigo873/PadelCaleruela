package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.Ayuntamiento;
import com.example.PadelCaleruela.model.Block;
import com.example.PadelCaleruela.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlockedUserAndBlockedByAyuntamiento(User user, Ayuntamiento ayuntamiento);

    boolean existsByBlockedUserAndBlockedByUser(User blockedUser, User blockingUser);

    void deleteByBlockedByUser_IdAndBlockedUser_Id(Long blockerId, Long blockedId);

    void deleteByBlockedByAyuntamiento_IdAndBlockedUser_Id(Long ayuntamientoId, Long blockedId);

    List<Block> findByBlockedByUser(User user);

    List<Block> findByBlockedByAyuntamiento(Ayuntamiento ayuntamiento);

    List<Block> findByBlockedUser(User blockedUser);

    boolean existsByBlockedByUserAndBlockedUser(User blocker, User blocked);

    boolean existsByBlockedByAyuntamientoAndBlockedUser(Ayuntamiento ayto, User blocked);


}
