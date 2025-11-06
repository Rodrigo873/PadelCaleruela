package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeagueRepository extends JpaRepository<League, Long> {
    List<League> findByIsPublicTrue();
    List<League> findByCreator(User creator);
}
