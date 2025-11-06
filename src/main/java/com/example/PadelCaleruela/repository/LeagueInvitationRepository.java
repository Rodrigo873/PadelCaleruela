package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.LeagueInvitation;
import com.example.PadelCaleruela.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LeagueInvitationRepository extends JpaRepository<LeagueInvitation, Long> {

    List<LeagueInvitation> findByReceiver(User receiver);
    List<LeagueInvitation> findBySender(User sender);
    List<LeagueInvitation> findByLeague_Id(Long leagueId);
    boolean existsByLeague_IdAndReceiver_Id(Long leagueId, Long receiverId);
}
