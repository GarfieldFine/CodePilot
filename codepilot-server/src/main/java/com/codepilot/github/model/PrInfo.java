package com.codepilot.github.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrInfo {
    private String owner;
    private String repo;
    private Integer number;
    private String title;
    private String description;
    private String author;
    private String baseBranch;
    private String headBranch;
    private String state;
    private Integer changedFiles;
    private Integer additions;
    private Integer deletions;
    private List<PrFile> files;
    private List<CommitInfo> commits;
    private String diffContent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String htmlUrl;
}
