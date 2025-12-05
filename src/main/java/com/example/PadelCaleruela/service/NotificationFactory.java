package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.model.NotificationType;
import org.springframework.stereotype.Component;

@Component
public class NotificationFactory {

    public String getTitle(NotificationType type) {
        return switch (type) {

            case FRIEND_REQUEST -> "Nueva solicitud de amistad";
            case FRIEND_ACCEPT -> "Solicitud de amistad aceptada";

            case LEAGUE_INVITATION -> "Invitación a una liga";
            case LEAGUE_INVITATION_ACCEPT -> "Invitación a liga aceptada";
            case LEAGUE_INVITATION_REJECT -> "Invitación a liga rechazada";

            case MATCH_INVITATION -> "Invitación a una partida";
            case MATCH_INVITATION_ACCEPT -> "Invitación a partida aceptada";
            case MATCH_INVITATION_REJECT -> "Invitación a partida rechazada";

            case RESERVATION_CANCELLED -> "Reserva cancelada";
            case RESERVATION_TIME_CANCELLED -> "Reserva cancelada";
            case PAYMENT_REMINDER -> "Tienes una reserva pendiente";

            case RESERVATION_PAYMENT_CONFIRMED -> "Pago confirmado";
            case RESERVATION_PAYMENT_CANCELLED -> "Pago cancelado";
            case RESERVATION_PLAYER_LEFT -> "Un jugador ha salido de la reserva";
            case MATCH_PLAYER_KICKED -> "Jugador expulsado de la partida";

            case LEAGUE_JOINED -> "Nuevo jugador en tu liga";
            case LEAGUE_JOINED_WITH_YOU -> "Nuevo compañero en tu liga";
            case LEAGUE_REJECTED -> "Invitación rechazada";
            case LEAGUE_DELETED -> "Una liga ha sido eliminada";
            case PLAYER_REMOVED_FROM_TEAM -> "Tu compañero ha sido expulsado";
            case PLAYER_REMOVED_FROM_LEAGUE -> "Has sido expulsado de la liga";


            case MATCH_INVITATION_ACCEPTED -> "Un jugador ha aceptado tu invitación";
            case MATCH_INVITATION_REJECTED -> "Invitación rechazada";
            case MATCH_JOINED_WITH_YOU -> "Nuevo jugador en tu partida";

            case PUBLIC_MATCH_JOINED -> "Nuevo jugador en partida pública";
            case MATCH_PLAYER_LEFT -> "Un jugador ha abandonado la partida";

            case ADMIN_USER_REGISTERED -> "Nuevo usuario registrado";
            case ADMIN_LEAGUE_CREATED -> "Nueva liga creada";
            case ADMIN_LEAGUE_DELETED -> "Liga eliminada";
            case POST_LIKED -> "Nuevo me gusta";


        };
    }


    public String getMessage(NotificationType type, String senderName) {
        return switch (type) {

            case FRIEND_REQUEST ->
                    senderName + " quiere agregarte como amigo";

            case FRIEND_ACCEPT ->
                    senderName + " ha aceptado tu solicitud de amistad";

            case LEAGUE_INVITATION ->
                    senderName + " te ha invitado a una liga";

            case LEAGUE_INVITATION_ACCEPT ->
                    senderName + " ha aceptado unirse a tu liga";

            case LEAGUE_INVITATION_REJECT ->
                    senderName + " ha rechazado unirse a tu liga";
            case PLAYER_REMOVED_FROM_TEAM ->
                    senderName + " ha expulsado a tu compañero del equipo.";
            case PLAYER_REMOVED_FROM_LEAGUE ->
                    senderName + " te ha expulsado de la liga.";


            case MATCH_INVITATION ->
                    senderName + " te ha invitado a una partida";

            case MATCH_INVITATION_ACCEPT ->
                    senderName + " ha aceptado unirse a tu partida";

            case MATCH_INVITATION_REJECT ->
                    senderName + " ha rechazado unirse a tu partida";

            case RESERVATION_CANCELLED ->
                    senderName + " ha cancelado la reserva";

            case RESERVATION_TIME_CANCELLED ->
                    "Tu reserva ha superado el tiempo de pago y ha sido cancelada";

            case PAYMENT_REMINDER  ->
                "Quedan 5 minutos para pagar tu reserva";


            case RESERVATION_PAYMENT_CONFIRMED ->
                    "Tu pago ha sido confirmado correctamente";

            case RESERVATION_PAYMENT_CANCELLED ->
                    "El pago de tu reserva ha sido cancelado";

            case RESERVATION_PLAYER_LEFT ->
                    senderName + " ha abandonado la reserva.";

            case LEAGUE_JOINED ->
                    senderName + " se ha unido a tu liga";

            case LEAGUE_JOINED_WITH_YOU ->
                    senderName + " se ha unido a la liga contigo";

            case LEAGUE_REJECTED ->
                    senderName + " ha rechazado tu invitación a la liga";

            case LEAGUE_DELETED ->
                    senderName + " ha eliminado la liga en la que participabas.";

            case MATCH_INVITATION_ACCEPTED ->
                    senderName + " se ha unido a tu reserva";

            case MATCH_INVITATION_REJECTED ->
                    senderName + " ha rechazado la invitación a la reserva";

            case MATCH_JOINED_WITH_YOU ->
                    senderName + " se ha unido a la partida";

            case PUBLIC_MATCH_JOINED ->
                    senderName + " se ha unido a tu partida pública";

            case MATCH_PLAYER_LEFT ->
                    senderName + " ha abandonado la partida";
            case MATCH_PLAYER_KICKED ->
                    senderName + " te ha expulsado de la partida";

            case ADMIN_USER_REGISTERED ->
                    senderName + " se ha registrado en tu ayuntamiento";

            case ADMIN_LEAGUE_CREATED ->
                    senderName + " ha creado una nueva liga";

            case ADMIN_LEAGUE_DELETED ->
                    senderName + " ha eliminado una liga";
            case POST_LIKED ->
                    senderName + " ha dado me gusta a tu publicación";

        };
    }

}
