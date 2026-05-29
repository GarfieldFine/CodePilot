package com.codepilot.github.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitInfo {
    private String sha;
    private String message;
    private String author;
    private LocalDateTime date;
}
