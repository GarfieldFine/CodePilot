package com.codepilot.github;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.codepilot.github.exception.GitHubApiException;
import com.codepilot.github.model.CommitInfo;
import com.codepilot.github.model.PrFile;
import com.codepilot.github.model.PrInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GitHubClient {

    private final WebClient webClient;
    private final String baseUrl;
    private final String token;
    private final int maxRetries;
    private final long retryDelayMs;

    private static final Pattern PR_URL_PATTERN =
            Pattern.compile("^https?://github\\.com/([^/]+)/([^/]+)/pull/(\\d+)/?.*$");
    private static final String DIFF_HEADER = "application/vnd.github.v3.diff";

    public GitHubClient(WebClient webClient,
                        @Value("${github.api.base-url}") String baseUrl,
                        @Value("${github.token}") String token,
                        @Value("${github.rate-limit.max-retries}") int maxRetries,
                        @Value("${github.rate-limit.retry-delay-ms}") long retryDelayMs) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
        this.token = token;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }

    public static PrUrlInfo parsePrUrl(String url) {
        Matcher matcher = PR_URL_PATTERN.matcher(url.trim());
        if (!matcher.matches()) {
            throw new GitHubApiException("Invalid GitHub PR URL: " + url);
        }
        return new PrUrlInfo(matcher.group(1), matcher.group(2), Integer.parseInt(matcher.group(3)));
    }

    public Mono<PrInfo> fetchPrDetail(String prUrl) {
        PrUrlInfo urlInfo = parsePrUrl(prUrl);
        return fetchPrDetail(urlInfo.owner, urlInfo.repo, urlInfo.number);
    }

    public Mono<PrInfo> fetchPrDetail(String owner, String repo, int prNumber) {
        log.info("Fetching PR detail: {}/{}/{}", owner, repo, prNumber);

        Mono<Map<String, Object>> prMono = get("/repos/" + owner + "/" + repo + "/pulls/" + prNumber);
        Mono<String> diffMono = getDiff("/repos/" + owner + "/" + repo + "/pulls/" + prNumber);
        Mono<List<Map<String, Object>>> filesMono = getList("/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/files");
        Mono<List<Map<String, Object>>> commitsMono = getList("/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/commits");

        return Mono.zip(prMono, diffMono, filesMono, commitsMono)
                .map(tuple -> buildPrInfo(owner, repo, prNumber, tuple.getT1(), tuple.getT2(),
                        tuple.getT3(), tuple.getT4()));
    }

    public Mono<String> fetchRawFile(String owner, String repo, String ref, String path) {
        log.debug("Fetching raw file: {}/{}/{} @ {}", owner, repo, path, ref);
        return getRaw("/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + ref);
    }

    private PrInfo buildPrInfo(String owner, String repo, int number,
                                Map<String, Object> prData, String diffContent,
                                List<Map<String, Object>> filesData,
                                List<Map<String, Object>> commitsData) {
        Integer additions = 0;
        Integer deletions = 0;
        Integer changedFiles = 0;
        if (prData.containsKey("additions")) {
            Object a = prData.get("additions");
            additions = a instanceof Integer ? (Integer) a : Integer.valueOf(a.toString());
        }
        if (prData.containsKey("deletions")) {
            Object d = prData.get("deletions");
            deletions = d instanceof Integer ? (Integer) d : Integer.valueOf(d.toString());
        }
        if (prData.containsKey("changed_files")) {
            Object cf = prData.get("changed_files");
            changedFiles = cf instanceof Integer ? (Integer) cf : Integer.valueOf(cf.toString());
        }

        List<PrFile> files = buildFileList(filesData, owner, repo);
        List<CommitInfo> commits = buildCommitList(commitsData);

        return PrInfo.builder()
                .owner(owner)
                .repo(repo)
                .number(number)
                .title(getString(prData, "title"))
                .description(getString(prData, "body"))
                .author(getNestedString(prData, "user", "login"))
                .baseBranch(getNestedString(prData, "base", "ref"))
                .headBranch(getNestedString(prData, "head", "ref"))
                .state(getString(prData, "state"))
                .changedFiles(changedFiles)
                .additions(additions)
                .deletions(deletions)
                .files(files)
                .commits(commits)
                .diffContent(diffContent)
                .createdAt(parseDateTime(getString(prData, "created_at")))
                .updatedAt(parseDateTime(getString(prData, "updated_at")))
                .htmlUrl(getString(prData, "html_url"))
                .build();
    }

    private List<PrFile> buildFileList(List<Map<String, Object>> filesData, String owner, String repo) {
        List<PrFile> files = new ArrayList<>();
        for (Map<String, Object> f : filesData) {
            String filename = getString(f, "filename");
            String language = detectLanguage(filename);

            files.add(PrFile.builder()
                    .filename(filename)
                    .status(getString(f, "status"))
                    .additions(intValue(f, "additions"))
                    .deletions(intValue(f, "deletions"))
                    .changes(intValue(f, "changes"))
                    .patch(getString(f, "patch"))
                    .rawUrl(getString(f, "raw_url"))
                    .contentsUrl(getString(f, "contents_url"))
                    .language(language)
                    .build());
        }
        return files;
    }

    private List<CommitInfo> buildCommitList(List<Map<String, Object>> commitsData) {
        List<CommitInfo> commits = new ArrayList<>();
        for (Map<String, Object> c : commitsData) {
            commits.add(CommitInfo.builder()
                    .sha(getString(c, "sha"))
                    .message(getNestedString(c, "commit", "message"))
                    .author(getNestedString(c, "commit", "author", "name"))
                    .date(parseDateTime(getNestedString(c, "commit", "author", "date")))
                    .build());
        }
        return commits;
    }

    private Mono<Map<String, Object>> get(String path) {
        return webClient.get()
                .uri(baseUrl + path)
                .header("Authorization", token != null && !token.isEmpty() ? "Bearer " + token : "")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "CodePilot-AI-Reviewer")
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new GitHubApiException(
                                        "GitHub API error: " + response.statusCode() + " - " + body,
                                        response.statusCode().value()))))
                .bodyToMono(String.class)
                .map(body -> JSONUtil.toBean(body, new TypeReference<Map<String, Object>>() {}, false))
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(retryDelayMs))
                        .filter(ex -> !(ex instanceof GitHubApiException ghe
                                && (ghe.isNotFound() || ghe.isRateLimited()))));
    }

    private Mono<String> getDiff(String path) {
        return webClient.get()
                .uri(baseUrl + path)
                .header("Authorization", token != null && !token.isEmpty() ? "Bearer " + token : "")
                .header("Accept", DIFF_HEADER)
                .header("User-Agent", "CodePilot-AI-Reviewer")
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new GitHubApiException(
                                        "GitHub API error: " + response.statusCode(),
                                        response.statusCode().value()))))
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(retryDelayMs))
                        .filter(ex -> !(ex instanceof GitHubApiException ghe && ghe.isRateLimited())));
    }

    private Mono<List<Map<String, Object>>> getList(String path) {
        return webClient.get()
                .uri(baseUrl + path + "?per_page=100")
                .header("Authorization", token != null && !token.isEmpty() ? "Bearer " + token : "")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "CodePilot-AI-Reviewer")
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new GitHubApiException(
                                        "GitHub API error: " + response.statusCode(),
                                        response.statusCode().value()))))
                .bodyToMono(String.class)
                .map(body -> JSONUtil.toList(body, Map.class))
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(retryDelayMs))
                        .filter(ex -> !(ex instanceof GitHubApiException ghe
                                && (ghe.isNotFound() || ghe.isRateLimited()))));
    }

    private Mono<String> getRaw(String path) {
        return webClient.get()
                .uri(baseUrl + path)
                .header("Authorization", token != null && !token.isEmpty() ? "Bearer " + token : "")
                .header("Accept", "application/vnd.github.v3.raw")
                .header("User-Agent", "CodePilot-AI-Reviewer")
                .retrieve()
                .bodyToMono(String.class);
    }

    private String detectLanguage(String filename) {
        if (filename == null) return "Unknown";
        if (filename.endsWith(".java")) return "Java";
        if (filename.endsWith(".kt")) return "Kotlin";
        if (filename.endsWith(".py")) return "Python";
        if (filename.endsWith(".ts")) return "TypeScript";
        if (filename.endsWith(".tsx")) return "TypeScript React";
        if (filename.endsWith(".js")) return "JavaScript";
        if (filename.endsWith(".jsx")) return "React";
        if (filename.endsWith(".go")) return "Go";
        if (filename.endsWith(".rs")) return "Rust";
        if (filename.endsWith(".sql")) return "SQL";
        if (filename.endsWith(".xml")) return "XML";
        if (filename.endsWith(".yml") || filename.endsWith(".yaml")) return "YAML";
        if (filename.endsWith(".json")) return "JSON";
        if (filename.endsWith(".vue")) return "Vue";
        if (filename.endsWith(".css") || filename.endsWith(".scss")) return "CSS";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    @SuppressWarnings("unchecked")
    private String getNestedString(Map<String, Object> map, String... keys) {
        Object current = map;
        for (int i = 0; i < keys.length; i++) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(keys[i]);
            if (current == null && i < keys.length - 1) return null;
        }
        return current != null ? current.toString() : null;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private Integer intValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    public record PrUrlInfo(String owner, String repo, int prNumber) {}
}
