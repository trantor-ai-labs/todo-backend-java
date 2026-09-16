# todo-backend-java

A [Todo-Backend](https://www.todobackend.com) implementation in Java, with **no dependencies**.

This service exists to be the part of a migration that **does not move**.

## Its job in the case study

`trantor-ai-labs/todo-angular` is an Angular front end that persists here. That application is
being migrated to Svelte. "Everything continues to work" is only a claim worth making if something
holds still and something independent measures it — so:

- **This service does not change during the migration.** Not a line. If it did, the experiment
  would have two variables.
- **Its contract is not ours.** The Todo-Backend API is a published spec with a published test
  suite ([todo-backend-js-spec](https://github.com/TodoBackend/todo-backend-js-spec)), which we
  did not write and cannot quietly adjust when something goes red.

## Running it

```bash
./run.sh
```

A JDK is all it needs — `javac` compiles it, there is nothing to resolve. `PORT` (default `8081`)
and `BASE_URL` (default `http://localhost:$PORT`) are the only knobs. `BASE_URL` matters because
the spec requires every todo to carry a `url` that actually resolves, so behind a proxy it has to
be told what it is reachable as.

Maven works too (`mvn package`), and `pom.xml` declares `maven.compiler.release=21` — verified to
produce major-version-65 bytecode on a JDK 26 host. The declaration is there because our engine
reads what a repository declares in order to supply its toolchain, so it has to be true.

## Verifying it

```bash
node conformance/spec.mjs [baseUrl]
```

The twelve assertions of `todo-backend-js-spec`, transcribed to run headlessly. The canonical
suite is browser-hosted; this is the same contract in a form that runs in CI. **12 passed, 0
failed** against this implementation on 2026-09-16, JDK 26.

## The API

| | |
|---|---|
| `GET /` | every todo |
| `POST /` | create from `{title, order?}` |
| `DELETE /` | remove every todo |
| `GET /{id}` | one todo |
| `PATCH /{id}` | update `{title?, completed?, order?}` |
| `DELETE /{id}` | remove one todo |

CORS is open (`*`) and `OPTIONS` is answered, because the front end and the conformance suites are
all served from a different origin. A missing preflight answer does not look like a CORS problem
in a browser — it looks like the whole backend is down.

## What it does not do

State is in memory. A todo survives a page reload, which is what the browser-side conformance test
means by "persist". It does not survive a restart of this process, and nothing here claims it
does. Adding a database would add a second thing that can fail during a demo, for no gain in what
the case study is trying to show.
