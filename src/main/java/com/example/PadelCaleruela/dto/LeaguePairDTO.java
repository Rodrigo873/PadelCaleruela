package com.example.PadelCaleruela.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeaguePairDTO {
    private List<Long> playerIds;
    private List<String> playerNames;
    private List<String> profileImageUrl;
}
