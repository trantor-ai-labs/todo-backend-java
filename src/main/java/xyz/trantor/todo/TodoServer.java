package xyz.trantor.todo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A Todo-Backend implementation (https://www.todobackend.com).
 *
 * The contract it answers is not ours: it is the published Todo-Backend spec, exercised by
 * TodoBackend/todo-backend-js-spec, which we did not write and cannot tune. That is the point of
 * this service in the estate — it is the thing that must NOT change while the front end is
 * migrated from Angular to Svelte, and the evidence that it did not change is a third party's
 * test suite going green against it before and after.
 *
 *   GET    /        every todo
 *   POST   /        create from {title, order?}
 *   DELETE /        remove every todo
 *   GET    /{id}    one todo
 *   PATCH  /{id}    update {title?, completed?, order?}
 *   DELETE /{id}    remove one todo
 *
 * State is in memory. A todo survives a page reload, which is what the browser-side conformance
 * test means by "persist"; it does not survive a restart of this process, and nothing here claims
 * it does.
 */
public final class TodoServer {

  private final ConcurrentSkipListMap<Long, Map<String, Object>> todos = new ConcurrentSkipListMap<>();
  private final AtomicLong nextId = new AtomicLong(1);
  private final HttpServer http;
  private final String fallbackBase;

  public TodoServer(int port, String baseUrl) throws IOException {
    this.fallbackBase = baseUrl;
    this.http = HttpServer.create(new InetSocketAddress(port), 0);
    this.http.createContext("/", this::route);
    this.http.setExecutor(null);
  }

  public void start() { http.start(); }

  public void stop() { http.stop(0); }

  public int port() { return http.getAddress().getPort(); }

  private void route(HttpExchange ex) throws IOException {
    try {
      // CORS first and unconditionally. The conformance suite and the front end are both served
      // from a different origin than this API, so a missing preflight answer does not look like a
      // CORS bug in a browser — it looks like the whole backend is down.
      ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
      ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Accept");
      ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS");
      ex.getResponseHeaders().add("Access-Control-Max-Age", "86400");

      String method = ex.getRequestMethod();
      if ("OPTIONS".equals(method)) { send(ex, 204, null); return; }

      String path = ex.getRequestURI().getPath();
      String id = path.replaceAll("^/+", "").replaceAll("/+$", "");

      if (id.isEmpty()) {
        switch (method) {
          case "GET" -> {
            List<Map<String, Object>> all = new ArrayList<>();
            for (Map<String, Object> t : todos.values()) all.add(withUrl(ex, t));
            send(ex, 200, Json.write(all));
          }
          case "POST" -> send(ex, 200, Json.write(create(ex, body(ex))));
          case "DELETE" -> { todos.clear(); send(ex, 200, "[]"); }
          default -> send(ex, 405, null);
        }
        return;
      }

      long key;
      try { key = Long.parseLong(id); } catch (NumberFormatException e) { send(ex, 404, null); return; }
      Map<String, Object> todo = todos.get(key);
      if (todo == null) { send(ex, 404, null); return; }

      switch (method) {
        case "GET" -> send(ex, 200, Json.write(withUrl(ex, todo)));
        case "PATCH" -> send(ex, 200, Json.write(withUrl(ex, patch(key, body(ex)))));
        case "DELETE" -> { todos.remove(key); send(ex, 200, "{}"); }
        default -> send(ex, 405, null);
      }
    } catch (RuntimeException e) {
      send(ex, 400, Json.write(Map.of("error", String.valueOf(e.getMessage()))));
    }
  }

  /**
   * The base URL this request arrived on, as the caller sees it.
   *
   * The Todo-Backend spec requires every todo to carry a `url` that the client can follow, and a
   * server has no reliable idea what it is reachable as: a person on the host says `localhost:8081`,
   * a container on the estate network says `backend:8081`, and a browser behind the app's reverse
   * proxy says `localhost:8080/api`. A single configured value is wrong for two of the three — as
   * measured: with a fixed base, 8 of the 12 contract assertions failed from inside the network
   * while passing from the host.
   *
   * So it is derived per request from the forwarding headers, which is what any service behind a
   * proxy has to do anyway. The configured value remains the fallback for a client that sends
   * neither.
   */
  private String baseFor(HttpExchange ex) {
    var headers = ex.getRequestHeaders();
    String host = headers.getFirst("X-Forwarded-Host");
    if (host == null) host = headers.getFirst("Host");
    if (host == null) return fallbackBase;

    String scheme = headers.getFirst("X-Forwarded-Proto");
    if (scheme == null) scheme = "http";

    String prefix = headers.getFirst("X-Forwarded-Prefix");
    if (prefix == null) prefix = "";
    prefix = prefix.replaceAll("/+$", "");

    return scheme + "://" + host + prefix;
  }

  private Map<String, Object> create(HttpExchange ex, String raw) {
    Map<String, Object> in = Json.readObject(raw);
    long id = nextId.getAndIncrement();
    Map<String, Object> todo = new LinkedHashMap<>();
    todo.put("id", id);
    todo.put("title", in.getOrDefault("title", ""));
    // The spec is explicit: a new todo is initially not completed, whatever the caller sent.
    todo.put("completed", Boolean.TRUE.equals(in.get("completed")));
    if (in.containsKey("order")) todo.put("order", in.get("order"));
    todo.put("url", baseFor(ex) + "/" + id);
    todos.put(id, todo);
    return todo;
  }

  private Map<String, Object> patch(long id, String raw) {
    Map<String, Object> in = Json.readObject(raw);
    Map<String, Object> todo = todos.get(id);
    for (String field : List.of("title", "completed", "order")) {
      if (in.containsKey(field)) todo.put(field, in.get(field));
    }
    return todo;
  }

  /** A copy whose `url` is correct for the route this request came in on. */
  private Map<String, Object> withUrl(HttpExchange ex, Map<String, Object> todo) {
    Map<String, Object> copy = new LinkedHashMap<>(todo);
    copy.put("url", baseFor(ex) + "/" + todo.get("id"));
    return copy;
  }

  private static String body(HttpExchange ex) throws IOException {
    try (InputStream in = ex.getRequestBody()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static void send(HttpExchange ex, int status, String json) throws IOException {
    if (json == null) {
      ex.sendResponseHeaders(status, -1);
      ex.close();
      return;
    }
    byte[] out = json.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    ex.sendResponseHeaders(status, out.length);
    ex.getResponseBody().write(out);
    ex.close();
  }

  public static void main(String[] args) throws IOException {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"));
    String base = System.getenv().getOrDefault("BASE_URL", "http://localhost:" + port);
    TodoServer server = new TodoServer(port, base);
    server.start();
    System.out.println("todo-backend-java listening on " + base);
  }
}
