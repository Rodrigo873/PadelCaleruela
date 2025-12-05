package com.example.PadelCaleruela.controller;


import com.example.PadelCaleruela.dto.LeagueMatchDTO;
import com.example.PadelCaleruela.service.LeagueMatchService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/league-matches")
@CrossOrigin(origins = "http://localhost:4200")
public class LeagueMatchController {

    private final LeagueMatchService matchService;

    public LeagueMatchController(LeagueMatchService matchService) {
        this.matchService = matchService;
    }

    @PostMapping
    public LeagueMatchDTO createMatch(@RequestBody LeagueMatchDTO dto) {
        return matchService.createMatch(dto);
    }

    @GetMapping("/league/{leagueId}")
    public List<LeagueMatchDTO> getMatchesByLeague(@PathVariable Long leagueId) {
        return matchService.getMatchesByLeague(leagueId);
    }

    @GetMapping("/league/{leagueId}/upcoming")
    public List<LeagueMatchDTO> getUpcoming(@PathVariable Long leagueId) {
        return matchService.getUpcomingMatches(leagueId);
    }

    @GetMapping("/league/{leagueId}/past")
    public List<LeagueMatchDTO> getPast(@PathVariable Long leagueId) {
        return matchService.getPastMatches(leagueId);
    }

    @PutMapping("/{matchId}/result")
    public Map<String, Object> updateResultAndRanking(
            @PathVariable Long matchId,
            @RequestBody Map<String, Object> payload
    ) {
        // 🧩 Extraer los sets del JSON
        @SuppressWarnings("unchecked")
        List<Map<String, Integer>> sets = (List<Map<String, Integer>>) payload.get("sets");
        if (sets == null || sets.isEmpty()) {
            throw new RuntimeException("Debe enviar al menos un set con los resultados.");
        }

        return matchService.updateResultAndRanking(matchId, sets);
    }

    @PutMapping("/{matchId}/reschedule")
    public void reschedule(@PathVariable Long matchId, @RequestParam String newDate) {
        matchService.rescheduleMatch(matchId, LocalDateTime.parse(newDate));
    }
}

