package com.codepilot.semantic;

import com.codepilot.github.model.PrFile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GoSemanticAnalyzer implements CodeSemanticAnalyzer {

    private static final Pattern GO_ROUTINE = Pattern.compile("\\bgo\\s+func\\s*\\(");
    private static final Pattern GO_ROUTINE_CALL = Pattern.compile("\\bgo\\s+\\w+\\s*\\(");
    private static final Pattern CHAN_MAKE = Pattern.compile("make\\s*\\(\\s*chan\\b");
    private static final Pattern CHAN_SEND = Pattern.compile("<-\\s*chan|chan\\s*<-");
    private static final Pattern DEFER_IN_LOOP = Pattern.compile("for\\s+[^{]*\\{[^}]*\\bdefer\\b");
    private static final Pattern SELECT_BLOCK = Pattern.compile("\\bselect\\s*\\{");
    private static final Pattern CONTEXT_PROPAGATION = Pattern.compile("context\\.(Background|TODO)\\s*\\(\\s*\\)");
    private static final Pattern ERROR_NOT_CHECKED = Pattern.compile("(\\w+),\\s*_\\s*:=\\s*[^,]+Error");
    private static final Pattern ERROR_BLANK_ID = Pattern.compile("_, err :=|err :=");
    private static final Pattern MUTEX_LOCK = Pattern.compile("sync\\.(Mutex|RWMutex)");
    private static final Pattern MUTEX_DEFER_UNLOCK = Pattern.compile("\\.(Lock|RLock)\\s*\\(\\s*\\)\\s*\\n\\s+defer\\s+\\w+\\.(Unlock|RUnlock)");
    private static final Pattern SLICE_APPEND_LOOP = Pattern.compile("for\\s+[^{]*\\{[^}]*\\bappend\\s*\\(");
    private static final Pattern PANIC_STATEMENT = Pattern.compile("\\bpanic\\s*\\(");
    private static final Pattern RECOVER_STATEMENT = Pattern.compile("\\brecover\\s*\\(\\s*\\)");
    private static final Pattern CLOSE_CHAN = Pattern.compile("close\\s*\\(\\s*\\w+\\s*\\)");

    @Override public String getName() { return "Go"; }
    @Override public int getPriority() { return 40; }

    @Override
    public boolean supports(String language) {
        return language != null && (language.equalsIgnoreCase("Go") || language.equalsIgnoreCase("Golang"));
    }

    @Override
    public List<SemanticFinding> analyze(PrFile file) {
        String patch = file.getPatch();
        if (patch == null || patch.isEmpty()) return List.of();

        List<SemanticFinding> findings = new ArrayList<>();

        checkGoroutinePatterns(patch, file.getFilename(), findings);
        checkChannelPatterns(patch, file.getFilename(), findings);
        checkDeferPatterns(patch, file.getFilename(), findings);
        checkErrorHandling(patch, file.getFilename(), findings);
        checkConcurrencyPatterns(patch, file.getFilename(), findings);

        return findings;
    }

    private void checkGoroutinePatterns(String patch, String filename, List<SemanticFinding> findings) {
        boolean hasGoRoutine = GO_ROUTINE.matcher(patch).find() || GO_ROUTINE_CALL.matcher(patch).find();
        boolean hasContext = CONTEXT_PROPAGATION.matcher(patch).find();

        if (hasGoRoutine && hasContext) {
            findings.add(SemanticFinding.builder()
                    .type("GOROUTINE_CONTEXT")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("goroutine with context.Background()")
                    .description("New goroutine using context.Background() instead of a derived context. This prevents proper cancellation propagation.")
                    .suggestion("Use context.WithCancel/WithTimeout/WithDeadline derived from the parent context. Pass ctx to the goroutine.")
                    .language("Go")
                    .source(getName())
                    .build());
        }

        if (hasGoRoutine && !hasContext) {
            findings.add(SemanticFinding.builder()
                    .type("GOROUTINE_NO_CONTEXT")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("goroutine without context")
                    .description("Goroutine launched without a cancellation mechanism. If the parent exits, this goroutine may leak.")
                    .suggestion("Pass a context.Context to the goroutine and check ctx.Done() for cancellation. Use errgroup for coordinated goroutines.")
                    .language("Go")
                    .source(getName())
                    .build());
        }
    }

    private void checkChannelPatterns(String patch, String filename, List<SemanticFinding> findings) {
        boolean hasChan = CHAN_MAKE.matcher(patch).find() || CHAN_SEND.matcher(patch).find();
        boolean hasSelect = SELECT_BLOCK.matcher(patch).find();
        boolean hasClose = CLOSE_CHAN.matcher(patch).find();

        if (hasChan && !hasClose) {
            findings.add(SemanticFinding.builder()
                    .type("CHANNEL_NOT_CLOSED")
                    .severity("LOW")
                    .file(filename)
                    .pattern("channel without close()")
                    .description("Channel created but close() not found in the diff. Unclosed channels can cause goroutine leaks if receivers are using range.")
                    .suggestion("Ensure the sender calls close(ch) when done. Consider using defer close(ch) after creating the channel.")
                    .language("Go")
                    .source(getName())
                    .build());
        }

        if (hasChan && !hasSelect) {
            findings.add(SemanticFinding.builder()
                    .type("CHANNEL_NO_SELECT")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("channel operation without select")
                    .description("Channel send/receive without select block. This can block indefinitely if the other side isn't ready.")
                    .suggestion("Use select with a default case or timeout for non-blocking channel operations.")
                    .language("Go")
                    .source(getName())
                    .build());
        }
    }

    private void checkDeferPatterns(String patch, String filename, List<SemanticFinding> findings) {
        if (DEFER_IN_LOOP.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("DEFER_IN_LOOP")
                    .severity("HIGH")
                    .file(filename)
                    .pattern("defer in loop")
                    .description("defer in a loop body — defers accumulate and only execute when the function returns. For large loops, this causes resource buildup (file handles, connections).")
                    .suggestion("Extract the loop body into a separate function so defers execute each iteration. Or use explicit cleanup instead of defer.")
                    .language("Go")
                    .source(getName())
                    .build());
        }
    }

    private void checkErrorHandling(String patch, String filename, List<SemanticFinding> findings) {
        if (ERROR_NOT_CHECKED.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("ERROR_IGNORED")
                    .severity("HIGH")
                    .file(filename)
                    .pattern("error assigned to _")
                    .description("Error value explicitly ignored with blank identifier '_'. This silently discards errors that should at minimum be logged.")
                    .suggestion("Handle the error explicitly or log it. If truly ignorable, add a comment explaining why.")
                    .language("Go")
                    .source(getName())
                    .build());
        }

        if (PANIC_STATEMENT.matcher(patch).find() && !RECOVER_STATEMENT.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("PANIC_WITHOUT_RECOVER")
                    .severity("HIGH")
                    .file(filename)
                    .pattern("panic without recover")
                    .description("panic() used without a recover() mechanism. panics crash the entire goroutine and should be reserved for truly unrecoverable states.")
                    .suggestion("Use error returns instead of panic for expected error conditions. If panic is needed, ensure a deferred recover() handles it gracefully.")
                    .language("Go")
                    .source(getName())
                    .build());
        }
    }

    private void checkConcurrencyPatterns(String patch, String filename, List<SemanticFinding> findings) {
        if (MUTEX_LOCK.matcher(patch).find()) {
            boolean hasDeferUnlock = MUTEX_DEFER_UNLOCK.matcher(patch).find();
            if (!hasDeferUnlock) {
                findings.add(SemanticFinding.builder()
                        .type("MUTEX_WITHOUT_DEFER")
                        .severity("HIGH")
                        .file(filename)
                        .pattern("Mutex without defer Unlock")
                        .description("Mutex lock without defer unlock pattern. If a panic or early return occurs, the lock is never released, causing a deadlock.")
                        .suggestion("Always follow mu.Lock() immediately with 'defer mu.Unlock()' to guarantee release.")
                        .language("Go")
                        .source(getName())
                        .build());
            }
        }
    }
}
