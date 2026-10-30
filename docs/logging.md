# Logging Guidelines

Goals:
- Consistent log formatting and categories.
- Useful errors with stack traces.
- Low noise for optional configuration.

Rules:
1. Use SLF4J parameterized logging with `{}` placeholders.
2. Avoid `FmtLog` except when logging to `Fuseki.configLog`.
3. Always include exceptions as the last argument to log the stack trace.
4. Prefer class-based loggers (`LoggerFactory.getLogger(MyClass.class)`).
5. Missing or invalid configuration should generally log at `WARN`; normal startup state should log at `INFO`.
6. Client input errors (4xx) should generally log at `WARN`, not `ERROR`.
7. Stack trace depth is capped at 12 frames in logging patterns for consistency across apps.

Examples:
```java
private static final Logger LOG = LoggerFactory.getLogger(MyClass.class);

LOG.info("Started module {}", moduleName);
LOG.warn("Missing ENV_FOO, using default {}", defaultValue);
LOG.error("Failed to load configuration", exception);
```

Stack trace pattern guidance:
- Logback: use `%ex{12}` in the pattern.
- Log4j2: use `%xEx{12}` in the pattern.

Request logging:
- The default application and Docker configurations emit one INFO access-log summary per HTTP request through `org.apache.jena.fuseki.Request`.
- `org.apache.jena.fuseki.Fuseki` defaults to WARN to suppress per-request action, query text and completion messages. Warnings and errors remain visible.
- To diagnose queries, temporarily set `org.apache.jena.fuseki.Fuseki` to DEBUG. GraphQL query text requires the corresponding graphql-jena logging change.
- The Search API URL is resolved on first use and cached for the process lifetime; restart after changing its configuration. Its configuration message is logged at DEBUG.
