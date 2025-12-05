package com.example.PadelCaleruela.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LockSimpleDTO {

    private Long id;
    private String name;
    private String pistaNombre;
    private String ayuntamientoNombre;
}
