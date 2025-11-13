package com.example.PadelCaleruela.repository;

import com.example.PadelCaleruela.model.LeagueMatch;
import com.example.PadelCaleruela.model.LeagueMatchSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeagueMatchSetRepository extends JpaRepository<LeagueMatchSet, Long> {
    List<LeagueMatchSet> findByMatch(LeagueMatch match);
}