package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.InvitationStatus;
import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueInvitation;
import com.example.PadelCaleruela.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LeagueInvitationRepository extends JpaRepository<LeagueInvitation, Long> {
    void deleteAllByLeague(League league);

    Optional<LeagueInvitation> findByLeague_IdAndReceiver_IdAndStatus(
            Long leagueId,
            Long receiverId,
            InvitationStatus status
    );
    List<LeagueInvitation> findByReceiverAndStatus(User receiver, InvitationStatus status);
    List<LeagueInvitation> findBySender(User sender);
    List<LeagueInvitation> findByLeague_Id(Long leagueId);
    boolean existsByLeague_IdAndReceiver_Id(Long leagueId, Long receiverId);
}
