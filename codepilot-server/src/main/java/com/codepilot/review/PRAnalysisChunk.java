package com.codepilot.review;

import com.codepilot.github.model.PrFile;

import java.util.List;

public record PRAnalysisChunk(List<PrFile> files, String diff) {}
