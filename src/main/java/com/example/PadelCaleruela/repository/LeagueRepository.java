package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LeagueRepository extends JpaRepository<League, Long> {
    List<League> findByIsPublicTrue();
    List<League> findByCreator(User creator);

    @Query("SELECT l FROM League l WHERE l.status = 'ACTIVE' AND l.isPublic = true")
    List<League> findAllActivePublicLeagues();

    @Query("SELECT l FROM League l WHERE l.isPublic = true AND l.status = 'PENDING'")
    List<League> findAllPublicPendingLeagues();


}
