package com.example.PadelCaleruela.model;

public enum ReservationStatus {
    PENDING,      // Reservada pero no pagada
    CONFIRMED,    // Pagada y activa
    CANCELED      // Cancelada manual o automáticamente
}
