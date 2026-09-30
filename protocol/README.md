# @feedoback/protocol

The one thing the server and every SDK agree on.

Four SDKs in four languages cannot import a Zod schema, so the contract lives
here as fixtures instead: real requests and real responses, checked into the
repository.

- The **server** has a test that parses every request fixture with the schema
  the routes actually use, and builds every response fixture from a project.
- Each **SDK** has two tests: decode every response fixture, and encode a
  request and compare it to the request fixture.

Drift then fails a build instead of failing a customer, and adding a field is a
single reviewable diff across five packages.

## Files

| | |
|---|---|
| `fixtures/config.*.json` | what `GET /api/widget/{key}/config` answers |
| `fixtures/thread.*.json` | what `POST /api/widget/{key}/threads` takes |
| `fixtures/headers.json` | the headers an app sends, and what each is for |

Nothing here is generated at build time. A fixture changes because someone
changed the contract, which is the point.
