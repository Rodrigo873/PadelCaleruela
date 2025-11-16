package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.dto.LeagueTeamRankingDTO;
import com.example.PadelCaleruela.dto.LeagueTeamRankingViewDTO;
import com.example.PadelCaleruela.model.*;
import com.example.PadelCaleruela.repository.LeagueMatchRepository;
import com.example.PadelCaleruela.repository.LeagueRepository;
import jakarta.mail.MessagingException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LeagueCompletionService {

    private final LeagueRepository leagueRepository;
    private final LeagueMatchRepository matchRepository;
    private final LeagueTeamRankingService rankingService;
    private final EmailService emailService;
    private final AuthService authService;

    public LeagueCompletionService(
            LeagueRepository leagueRepository,
            LeagueMatchRepository matchRepository,
            LeagueTeamRankingService rankingService,
            EmailService emailService,
            AuthService authService
    ) {
        this.leagueRepository = leagueRepository;
        this.matchRepository = matchRepository;
        this.rankingService = rankingService;
        this.emailService = emailService;
        this.authService = authService;
    }

    // ============================================================
    // 🔐 SEGURIDAD
    // ============================================================

    private void ensureCanCompleteLeague(League league) {
        var current = authService.getCurrentUser();

        if (authService.isSuperAdmin()) return;

        if (authService.isAdmin()) {
            authService.ensureSameAyuntamiento(league.getAyuntamiento());
            return;
        }

        // USER → solo el creador
        if (authService.isUser()) {
            if (league.getCreator() == null ||
                    !league.getCreator().getId().equals(current.getId())) {
                throw new AccessDeniedException("No puedes completar esta liga.");
            }
            return;
        }

        throw new AccessDeniedException("No tienes permiso para completar esta liga.");
    }

    // ============================================================
    // 🏁 COMPLETAR LIGA
    // ============================================================

    /**
     * Comprueba si todos los partidos han acabado.
     * Si es así → marca la liga como FINISHED y envía un email al creador.
     */
    @Transactional
    public void checkAndCompleteLeague(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        // 🔐 Seguridad
        ensureCanCompleteLeague(league);

        if (league.getStatus() == LeagueStatus.FINISHED) return;

        List<LeagueMatch> matches = matchRepository.findByLeague(league);
        if (matches.isEmpty()) return;

        boolean allFinished = matches.stream()
                .allMatch(m -> m.getStatus() == MatchStatus.FINISHED);

        if (!allFinished) return;

        // 🏁 Marcar liga como finalizada
        league.setStatus(LeagueStatus.FINISHED);
        leagueRepository.save(league);

        // Obtener ranking final
        List<LeagueTeamRankingViewDTO> viewRanking = rankingService.getRanking(leagueId);
        LeagueTeamRankingViewDTO viewChampion = viewRanking.isEmpty() ? null : viewRanking.get(0);

        // Convertir a DTOs antiguos para email
        List<LeagueTeamRankingDTO> ranking = viewRanking.stream()
                .map(v -> new LeagueTeamRankingDTO(
                        v.getTeamId(),
                        v.getPlayers().stream().map(p -> p.getUsername()).toList(),
                        v.getMatchesPlayed(),
                        v.getMatchesWon(),
                        v.getMatchesLost(),
                        v.getPoints()
                ))
                .toList();

        LeagueTeamRankingDTO champion = viewChampion == null ? null :
                new LeagueTeamRankingDTO(
                        viewChampion.getTeamId(),
                        viewChampion.getPlayers().stream().map(p -> p.getUsername()).toList(),
                        viewChampion.getMatchesPlayed(),
                        viewChampion.getMatchesWon(),
                        viewChampion.getMatchesLost(),
                        viewChampion.getPoints()
                );

        // 📩 Enviar correo al creador
        String subject = "🏆 Tu liga '" + league.getName() + "' ha finalizado";
        String html = buildLeagueCompletedEmail(league, champion, ranking);
        emailService.sendHtmlEmail(league.getCreator().getEmail(), subject, html);
    }

    // ============================================================
    // 📧 GENERADOR DE EMAIL
    // ============================================================

    private String buildLeagueCompletedEmail(
            League league,
            LeagueTeamRankingDTO champion,
            List<LeagueTeamRankingDTO> ranking
    ) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,sans-serif;color:#333;'>");
        html.append("<h2>🏁 ¡La liga <strong>").append(league.getName()).append("</strong> ha finalizado!</h2>");
        html.append("<p>Te informamos que todos los partidos se han completado.</p>");

        if (champion != null) {
            html.append("<h3>🏆 Campeones:</h3>");
            html.append("<p><strong>")
                    .append(String.join(" & ", champion.getPlayerNames()))
                    .append("</strong> con ")
                    .append(champion.getPoints())
                    .append(" puntos.</p>");
        }

        html.append("<hr><h4>Clasificación final:</h4>");
        html.append("<table style='border-collapse:collapse;width:100%;'>");
        html.append("<tr>")
                .append("<th style='border:1px solid #ddd;padding:8px;'>Posición</th>")
                .append("<th style='border:1px solid #ddd;padding:8px;'>Pareja</th>")
                .append("<th style='border:1px solid #ddd;padding:8px;'>PJ</th>")
                .append("<th style='border:1px solid #ddd;padding:8px;'>PG</th>")
                .append("<th style='border:1px solid #ddd;padding:8px;'>PP</th>")
                .append("<th style='border:1px solid #ddd;padding:8px;'>Puntos</th>")
                .append("</tr>");

        for (int i = 0; i < ranking.size(); i++) {
            LeagueTeamRankingDTO r = ranking.get(i);
            html.append("<tr>")
                    .append("<td style='border:1px solid #ddd;padding:8px;text-align:center;'>").append(i + 1).append("</td>")
                    .append("<td style='border:1px solid #ddd;padding:8px;'>").append(String.join(" & ", r.getPlayerNames())).append("</td>")
                    .append("<td style='border:1px solid #ddd;padding:8px;text-align:center;'>").append(r.getMatchesPlayed()).append("</td>")
                    .append("<td style='border:1px solid #ddd;padding:8px;text-align:center;'>").append(r.getMatchesWon()).append("</td>")
                    .append("<td style='border:1px solid #ddd;padding:8px;text-align:center;'>").append(r.getMatchesLost()).append("</td>")
                    .append("<td style='border:1px solid #ddd;padding:8px;text-align:center;font-weight:bold;'>").append(r.getPoints()).append("</td>")
                    .append("</tr>");
        }

        html.append("</table>");
        html.append("<p style='margin-top:20px;'>¡Gracias por usar <strong>PadelApp</strong>! 🎾</p>");
        html.append("</body></html>");

        return html.toString();
    }
}
