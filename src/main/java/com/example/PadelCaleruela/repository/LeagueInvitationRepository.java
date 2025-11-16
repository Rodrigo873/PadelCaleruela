package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.InvitationStatus;
import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueInvitation;
import com.example.PadelCaleruela.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LeagueInvitationRepository extends JpaRepository<LeagueInvitation, Long> {
    void deleteAllByLeague(League league);

    Optional<LeagueInvitation> findByLeague_IdAndReceiver_IdAndStatus(
            Long leagueId,
            Long receiverId,
            InvitationStatus status
    );


    // Todas las invitaciones PENDIENTES de un usuario
    List<LeagueInvitation> findByReceiverIdAndStatus(Long receiverId, InvitationStatus status);

    // Número de invitaciones PENDIENTES de un usuario
    long countByReceiverIdAndStatus(Long receiverId, InvitationStatus status);


    boolean existsByLeague_IdAndReceiver_Id(Long leagueId, Long receiverId);
}
