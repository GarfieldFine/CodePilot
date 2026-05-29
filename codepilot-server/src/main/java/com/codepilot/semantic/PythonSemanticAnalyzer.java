package com.codepilot.semantic;

import com.codepilot.github.model.PrFile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PythonSemanticAnalyzer implements CodeSemanticAnalyzer {

    private static final Pattern BARE_EXCEPT = Pattern.compile("except\\s*:");
    private static final Pattern EXCEPT_PASS = Pattern.compile("except[^:]*:\\s*\\n\\s+pass");
    private static final Pattern MUTABLE_DEFAULT = Pattern.compile("def\\s+\\w+\\s*\\([^)]*=\\s*\\[\\s*\\]");
    private static final Pattern MUTABLE_DEFAULT_DICT = Pattern.compile("def\\s+\\w+\\s*\\([^)]*=\\s*\\{\\s*\\}");
    private static final Pattern OPEN_WITHOUT_CONTEXT = Pattern.compile("=\\s*open\\s*\\(");
    private static final Pattern WITH_OPEN = Pattern.compile("with\\s+open\\s*\\(");
    private static final Pattern REQUESTS_GET = Pattern.compile("requests\\.(get|post|put|delete|patch)\\s*\\(");
    private static final Pattern NO_TIMEOUT = Pattern.compile("requests\\.(get|post|put|delete|patch)\\s*\\([^)]*\\)");
    private static final Pattern FSTRING_SQL = Pattern.compile("f\"[^\"]*\\b(SELECT|INSERT|UPDATE|DELETE|DROP)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern STRING_FORMAT_SQL = Pattern.compile("\\.format\\([^)]*\\).*\\b(SELECT|INSERT|UPDATE|DELETE)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SLEEP_IN_ASYNC = Pattern.compile("async\\s+def.*:\\n.*time\\.sleep\\(");
    private static final Pattern PRINT_STATEMENT = Pattern.compile("^\\s*print\\s*\\(", Pattern.MULTILINE);
    private static final Pattern PICKLE_LOAD = Pattern.compile("pickle\\.(load|loads)\\s*\\(");
    private static final Pattern EXEC_EVAL = Pattern.compile("\\b(exec|eval)\\s*\\(");
    private static final Pattern OS_SYSTEM = Pattern.compile("os\\.system\\s*\\(|subprocess\\.call\\s*\\([^)]*shell\\s*=\\s*True");
    private static final Pattern HARDCODED_SECRET = Pattern.compile("(password|secret|api_key|token)\\s*=\\s*['\"][^'\"]+['\"]", Pattern.CASE_INSENSITIVE);

    @Override public String getName() { return "Python"; }
    @Override public int getPriority() { return 30; }

    @Override
    public boolean supports(String language) {
        return language != null && (language.equalsIgnoreCase("Python") || language.toLowerCase().contains("python"));
    }

    @Override
    public List<SemanticFinding> analyze(PrFile file) {
        String patch = file.getPatch();
        if (patch == null || patch.isEmpty()) return List.of();

        List<SemanticFinding> findings = new ArrayList<>();

        checkExceptionHandling(patch, file.getFilename(), findings);
        checkFunctionPatterns(patch, file.getFilename(), findings);
        checkResourceManagement(patch, file.getFilename(), findings);
        checkSecurityPatterns(patch, file.getFilename(), findings);
        checkAsyncPatterns(patch, file.getFilename(), findings);

        return findings;
    }

    private void checkExceptionHandling(String patch, String filename, List<SemanticFinding> findings) {
        if (BARE_EXCEPT.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("BARE_EXCEPT")
                    .severity("HIGH")
                    .file(filename)
                    .pattern("bare except:")
                    .description("Bare 'except:' clause detected. This catches all exceptions including SystemExit and KeyboardInterrupt, making the program hard to terminate.")
                    .suggestion("Catch specific exception types: 'except ValueError as e:' or at minimum 'except Exception as e:'.")
                    .language("Python")
                    .source(getName())
                    .build());
        }

        if (EXCEPT_PASS.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("EXCEPT_PASS")
                    .severity("HIGH")
                    .file(filename)
                    .pattern("except: pass")
                    .description("Exception handler with 'pass' body — the error is silently ignored with no logging or recovery.")
                    .suggestion("At minimum, log the exception. If truly expected, add a comment explaining why it's safe to ignore.")
                    .language("Python")
                    .source(getName())
                    .build());
        }
    }

    private void checkFunctionPatterns(String patch, String filename, List<SemanticFinding> findings) {
        if (MUTABLE_DEFAULT.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("MUTABLE_DEFAULT_ARG")
                    .severity("HIGH")
                    .file(filename)
                    .pattern("mutable default argument []")
                    .description("Mutable default argument (list) detected. This list is shared across all calls — a classic Python bug.")
                    .suggestion("Use 'def foo(x=None): if x is None: x = []' pattern instead of mutable defaults.")
                    .language("Python")
                    .source(getName())
                    .build());
        }

        if (MUTABLE_DEFAULT_DICT.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("MUTABLE_DEFAULT_ARG")
                    .severity("HIGH")
                    .file(filename)
                    .pattern("mutable default argument {}")
                    .description("Mutable default argument (dict) detected. Same bug pattern as list defaults.")
                    .suggestion("Use None as default and initialize dict inside the function body.")
                    .language("Python")
                    .source(getName())
                    .build());
        }
    }

    private void checkResourceManagement(String patch, String filename, List<SemanticFinding> findings) {
        if (OPEN_WITHOUT_CONTEXT.matcher(patch).find() && !WITH_OPEN.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("OPEN_WITHOUT_CONTEXT")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("open() without 'with'")
                    .description("File opened without 'with' statement — the file may not be properly closed, causing resource leaks.")
                    .suggestion("Use 'with open(filename) as f:' to ensure the file is automatically closed.")
                    .language("Python")
                    .source(getName())
                    .build());
        }
    }

    private void checkSecurityPatterns(String patch, String filename, List<SemanticFinding> findings) {
        if (FSTRING_SQL.matcher(patch).find() || STRING_FORMAT_SQL.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("SQL_INJECTION_PYTHON")
                    .severity("CRITICAL")
                    .file(filename)
                    .pattern("SQL with string formatting")
                    .description("SQL query built with f-string or .format() — vulnerable to SQL injection. Always use parameterized queries.")
                    .suggestion("Use parameterized queries with ? placeholders (sqlite3/psycopg2) or %s placeholders (MySQLdb).")
                    .language("Python")
                    .source(getName())
                    .build());
        }

        if (PICKLE_LOAD.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("UNSAFE_PICKLE")
                    .severity("CRITICAL")
                    .file(filename)
                    .pattern("pickle.load/loads")
                    .description("pickle.load() on untrusted data is a remote code execution vulnerability. Pickle can execute arbitrary Python code during deserialization.")
                    .suggestion("Use JSON for data serialization. If pickle is required, only load data from fully trusted sources.")
                    .language("Python")
                    .source(getName())
                    .build());
        }

        if (EXEC_EVAL.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("EXEC_EVAL_USAGE")
                    .severity("CRITICAL")
                    .file(filename)
                    .pattern("exec/eval")
                    .description("exec() or eval() on dynamic input — this allows arbitrary code execution and is a critical security risk.")
                    .suggestion("Remove exec/eval entirely. Use safe alternatives like ast.literal_eval() for data parsing, or explicit logic for dynamic behavior.")
                    .language("Python")
                    .source(getName())
                    .build());
        }

        if (HARDCODED_SECRET.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("HARDCODED_SECRET")
                    .severity("CRITICAL")
                    .file(filename)
                    .pattern("hardcoded credential")
                    .description("Hardcoded password, API key, or secret string detected. These will be committed to version control and exposed.")
                    .suggestion("Use environment variables (os.environ.get()) or a secrets manager. Never hardcode credentials.")
                    .language("Python")
                    .source(getName())
                    .build());
        }

        if (OS_SYSTEM.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("SHELL_INJECTION")
                    .severity("HIGH")
                    .file(filename)
                    .pattern("os.system / subprocess with shell=True")
                    .description("Shell command execution with potential injection. If any part of the command comes from user input, this is exploitable.")
                    .suggestion("Use subprocess.run() with shell=False and pass arguments as a list. Avoid os.system() entirely.")
                    .language("Python")
                    .source(getName())
                    .build());
        }
    }

    private void checkAsyncPatterns(String patch, String filename, List<SemanticFinding> findings) {
        if (PRINT_STATEMENT.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("PRINT_STATEMENT")
                    .severity("LOW")
                    .file(filename)
                    .pattern("print() statement")
                    .description("print() statement in production code. Use proper logging instead.")
                    .suggestion("Replace with logging.info/debug/warning. Configure logging level for different environments.")
                    .language("Python")
                    .source(getName())
                    .build());
        }

        Matcher requestMatcher = REQUESTS_GET.matcher(patch);
        if (requestMatcher.find()) {
            findings.add(SemanticFinding.builder()
                    .type("HTTP_WITHOUT_TIMEOUT")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("requests without timeout")
                    .description("HTTP request may be missing timeout parameter. Without timeout, requests can hang indefinitely.")
                    .suggestion("Always set timeout= parameter on requests calls (e.g., timeout=30).")
                    .language("Python")
                    .source(getName())
                    .build());
        }
    }
}
