package com.example.PadelCaleruela.service;


import com.example.PadelCaleruela.dto.LeagueTeamRankingDTO;
import com.example.PadelCaleruela.model.League;
import com.example.PadelCaleruela.model.LeagueStatus;
import com.example.PadelCaleruela.model.LeagueMatch;
import com.example.PadelCaleruela.repository.LeagueMatchRepository;
import com.example.PadelCaleruela.repository.LeagueRepository;
import jakarta.mail.MessagingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LeagueCompletionService {

    private final LeagueRepository leagueRepository;
    private final LeagueMatchRepository matchRepository;
    private final LeagueTeamRankingService rankingService;
    private final EmailService emailService;

    public LeagueCompletionService(LeagueRepository leagueRepository,
                                   LeagueMatchRepository matchRepository,
                                   LeagueTeamRankingService rankingService,
                                   EmailService emailService) {
        this.leagueRepository = leagueRepository;
        this.matchRepository = matchRepository;
        this.rankingService = rankingService;
        this.emailService = emailService;
    }

    /** Comprueba si una liga ha finalizado y envía correo al creador si es así */
    @Transactional
    public void checkAndCompleteLeague(Long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        if (league.getStatus() == LeagueStatus.FINISHED) {
            return; // ya finalizada
        }

        // Verificar si todos los partidos están jugados
        List<LeagueMatch> matches = matchRepository.findByLeague(league);
        boolean allFinished = !matches.isEmpty() &&
                matches.stream().allMatch(m -> m.getStatus().name().equals("FINISHED"));

        if (!allFinished) return;

        // Cambiar estado a FINISHED
        league.setStatus(LeagueStatus.FINISHED);
        leagueRepository.save(league);

        // Obtener ranking final y campeón
        List<LeagueTeamRankingDTO> ranking = rankingService.getRanking(leagueId);
        LeagueTeamRankingDTO champion = ranking.isEmpty() ? null : ranking.get(0);

        // Enviar correo HTML al creador
        try {
            String subject = "🏆 Tu liga '" + league.getName() + "' ha finalizado";
            String html = buildLeagueCompletedEmail(league, champion, ranking);
            emailService.sendHtmlEmail(league.getCreator().getEmail(), subject, html);
        } catch (MessagingException e) {
            throw new RuntimeException("Error enviando email de finalización: " + e.getMessage());
        }
    }

    /** 📨 Genera el contenido HTML del correo */
    private String buildLeagueCompletedEmail(League league, LeagueTeamRankingDTO champion, List<LeagueTeamRankingDTO> ranking) {
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
        html.append("<tr><th style='border:1px solid #ddd;padding:8px;'>Posición</th>")
                .append("<th style='border:1px solid #ddd;padding:8px;'>Pareja</th>")
                .append("<th style='border:1px solid #ddd;padding:8px;'>PJ</th>")
                .append("<th style='border:1px solid #ddd;padding:8px;'>PG</th>")
                .append("<th style='border:1px solid #ddd;padding:8px;'>PP</th>")
                .append("<th style='border:1px solid #ddd;padding:8px;'>Puntos</th></tr>");

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
