package com.mealpilot.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

  @GetMapping(path = "/", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> homeJson() {
    return Map.of(
        "service", "mealpilot-api",
        "status", "ok",
        "time", Instant.now().toString(),
        "health", "/actuator/health",
        "auth", Map.of(
            "register", "/api/auth/register",
            "login", "/api/auth/login"
        ),
        "note", "Most /api endpoints require Authorization: Bearer <jwt>"
    );
  }

  @GetMapping(path = "/", produces = MediaType.TEXT_HTML_VALUE)
  public String homeHtml() {
    return """
      <!doctype html>
      <html lang=\"en\">
        <head>
          <meta charset=\"utf-8\" />
          <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
          <title>MealPilot API</title>
          <style>
            :root { color-scheme: light dark; }
            body { font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif; margin: 24px; max-width: 980px; }
            code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
            .card { border: 1px solid rgba(127,127,127,0.35); border-radius: 10px; padding: 14px; margin: 14px 0; }
            .muted { opacity: 0.75; }
            ul { line-height: 1.7; }
          </style>
        </head>
        <body>
          <h1>MealPilot API</h1>
          <div class=\"muted\">Backend is running. For the full interactive flow UI, use the React app in <code>mealpilot-web/</code>.</div>

          <div class=\"card\">
            <h3>React UI (dev)</h3>
            <ul>
              <li>Start: <code>cd mealpilot-web; npm install; npm run dev</code></li>
              <li>Open: <code>http://127.0.0.1:5173/</code></li>
            </ul>
          </div>

          <div class=\"card\">
            <h3>Useful endpoints</h3>
            <ul>
              <li><a href=\"/actuator/health\">/actuator/health</a> (public)</li>
              <li><code>POST /api/auth/register</code> (public)</li>
              <li><code>POST /api/auth/login</code> (public, returns JWT)</li>
              <li><code>/api/items</code>, <code>/api/decide</code>, <code>/api/decisions</code> (JWT required)</li>
            </ul>
          </div>

          <div class=\"card\">
            <h3>JSON info</h3>
            <div class=\"muted\">Send <code>Accept: application/json</code> to this same <code>/</code> endpoint.</div>
          </div>
        </body>
      </html>
      """;
  }
}
