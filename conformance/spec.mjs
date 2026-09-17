/**
 * The Todo-Backend contract, asserted against a running server.
 *
 * These are the twelve assertions of TodoBackend/todo-backend-js-spec, transcribed to run
 * headlessly. The canonical suite is browser-hosted (todobackend.com points a Mocha page at a
 * deployed URL); this is the same contract in a form that runs in CI with no browser.
 *
 * The contract is not ours. That is the entire reason this file exists: when the front end is
 * migrated from Angular to Svelte, "the backend did not change" has to be a measurement rather
 * than an assurance, and a suite written by whoever is doing the migrating is not a measurement.
 *
 *   node conformance/spec.mjs [baseUrl]
 */

import fs from 'node:fs';
import path from 'node:path';

const BASE = (process.argv[2] || process.env.TODO_API || 'http://localhost:8081').replace(/\/$/, '');

// Where to leave a machine-readable record, if asked. Console output is for a person watching;
// a factory grading this conversion needs something it can parse, and it should not have to scrape
// prose to get it. Absent means nobody asked — the suite still runs and still prints.
const JUNIT = process.env.JUNIT_OUT || null;

let passed = 0;
const failures = [];
const cases = [];

const json = (r) => r.json();
const post = (body) =>
  fetch(BASE + '/', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(json);
const patch = (url, body) =>
  fetch(url, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(json);
const clear = () => fetch(BASE + '/', { method: 'DELETE' });

function eq(actual, expected, what) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${what}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

async function it(name, fn) {
  const began = Date.now();
  try {
    await clear();
    await fn();
    console.log(`  ok    ${name}`);
    passed++;
    cases.push({ name, ms: Date.now() - began, failure: null });
  } catch (err) {
    console.log(`  FAIL  ${name}`);
    console.log(`        ${err.message}`);
    failures.push(name);
    cases.push({ name, ms: Date.now() - began, failure: err.message });
  }
}

/**
 * JUnit XML, hand-written because this suite has no dependencies and is not about to grow one.
 *
 * Paired `<testcase>…</testcase>` tags rather than self-closing, deliberately: a self-closed tag is
 * the form Surefire uses and the form a greedy parser silently merges into its neighbour. Both
 * parse correctly now, but emitting the shape that cannot be misread costs nothing.
 */
function writeJunit(file) {
  const esc = (t) => String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const body = cases.map((c) => {
    const open = `<testcase classname="todo-backend-contract" name="${esc(c.name)}" time="${(c.ms / 1000).toFixed(3)}">`;
    const fail = c.failure ? `<failure message="${esc(c.failure)}"></failure>` : '';
    return `    ${open}${fail}</testcase>`;
  }).join('\n');
  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="todo-backend-contract" tests="${cases.length}" failures="${failures.length}">
${body}
</testsuite>
`;
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, xml);
  console.log(`\n  wrote ${file}`);
}

console.log(`todo-backend contract against ${BASE}\n`);

await it('adds a new todo to the list of todos at the root url', async () => {
  await post({ title: 'a todo' });
  const all = await fetch(BASE + '/').then(json);
  eq(all.length, 1, 'list length');
  eq(all[0].title, 'a todo', 'title');
});

await it('sets up a new todo as initially not completed', async () => {
  eq((await post({ title: 'x' })).completed, false, 'completed');
});

await it('each new todo has a url', async () => {
  const todo = await post({ title: 'x' });
  if (typeof todo.url !== 'string' || !todo.url) throw new Error('no url on the created todo');
});

await it('each new todo has a url, which returns a todo', async () => {
  const todo = await post({ title: 'y' });
  eq((await fetch(todo.url).then(json)).title, 'y', 'title at url');
});

await it('can navigate from a list of todos to an individual todo via urls', async () => {
  await post({ title: 'n1' });
  const [first] = await fetch(BASE + '/').then(json);
  eq((await fetch(first.url).then(json)).title, 'n1', 'title via list url');
});

await it('can change the todo title by PATCHing to the todo url', async () => {
  const todo = await post({ title: 'old' });
  eq((await patch(todo.url, { title: 'new' })).title, 'new', 'patched title');
});

await it('can change the todo completedness by PATCHing to the todo url', async () => {
  const todo = await post({ title: 'c' });
  eq((await patch(todo.url, { completed: true })).completed, true, 'patched completed');
});

await it('changes to a todo are persisted and show up when re-fetching', async () => {
  const todo = await post({ title: 'p' });
  await patch(todo.url, { title: 'pp', completed: true });
  const again = await fetch(todo.url).then(json);
  eq(again.title, 'pp', 're-fetched title');
  eq(again.completed, true, 're-fetched completed');
});

await it('can delete a todo making a DELETE request to the todo url', async () => {
  const todo = await post({ title: 'd' });
  await fetch(todo.url, { method: 'DELETE' });
  eq((await fetch(todo.url)).status, 404, 'status after delete');
});

await it('can create a todo with an order field', async () => {
  eq((await post({ title: 'o', order: 523 })).order, 523, 'order');
});

await it('can PATCH a todo to change its order', async () => {
  const todo = await post({ title: 'o2', order: 10 });
  eq((await patch(todo.url, { order: 95 })).order, 95, 'patched order');
});

await it('remembers changes to a todo order', async () => {
  const todo = await post({ title: 'o3', order: 10 });
  await patch(todo.url, { order: 95 });
  eq((await fetch(todo.url).then(json)).order, 95, 're-fetched order');
});

await clear();

console.log(`\n  ${passed} passed, ${failures.length} failed`);
if (JUNIT) writeJunit(JUNIT);
process.exit(failures.length ? 1 : 0);
