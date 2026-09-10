# FC27 API Reference

Every backend endpoint is documented here as it's added, alongside its SpringDoc/OpenAPI annotations in code. See `feedback_api_documentation` project convention: this file must stay in sync — an endpoint isn't considered done until it has an entry here.

Roles: `PUBLIC` (no auth) · `GUEST` (unauthenticated app session) · `USER` (authenticated) · `ADMIN` (authenticated + admin role)

## Legend

| Field | Meaning |
|---|---|
| Method / Path | HTTP verb and route |
| Auth | Required auth header / token type, or `none` |
| Role | Who may call it |
| Params | Path / query parameters |
| Request Body | DTO fields |
| Response | DTO fields + status codes |

---

## Auth (`/api/auth`)

SpringDoc/Swagger is now live at `/swagger-ui.html` (raw spec at `/v3/api-docs`) — both public, no auth needed to view.

> **UI note:** Google and SMS endpoints below are fully implemented and tested but are deprioritized for now — the Web/Mobile login screens should only expose Guest and Email OTP. Keep the endpoints as-is (don't remove).

### POST /api/auth/google
- **Auth:** none · **Role:** PUBLIC (creates or logs in a `USER`)
- **Request body:** `{ idToken: string, rememberMe: boolean }` — `idToken` is the Google Sign-In ID token from the client SDK.
- **Response:** `200 AuthResponse` — see shared response shape below. `503` if `GOOGLE_CLIENT_ID` isn't configured server-side; `401` if the token is invalid/expired or its audience doesn't match.
- **Behavior:** finds an existing user by Google id, else by email, else creates one. Links the Google id to the account if it wasn't set yet.

### POST /api/auth/otp/email/send
- **Auth:** none · **Role:** PUBLIC
- **Request body:** `{ email: string }`
- **Response:** `202 Accepted`, empty body. `429` if this email has requested more than `app.otp.rate-limit.max-requests` (default 3) codes in the last `window-minutes` (default 10).
- **Behavior:** generates a 6-digit code (5 min TTL), logs it via the mock `OtpSender` (see [[project-fc27-suggestions]] — no real email provider wired up yet).

### POST /api/auth/otp/email/verify
- **Auth:** none · **Role:** PUBLIC (creates or logs in a `USER`)
- **Request body:** `{ email: string, code: string (6 digits), rememberMe: boolean }`
- **Response:** `200 AuthResponse`. `400` if no code was requested, the code is wrong, expired, or has exceeded 5 attempts.
- **Behavior:** finds-or-creates a `User` by email once the code is verified.

### POST /api/auth/otp/sms/send
- **Auth:** none · **Role:** PUBLIC
- **Request body:** `{ phone: string }` (E.164-ish, 7–15 digits, optional leading `+`)
- **Response:** same shape/rules as the email send endpoint, keyed by phone instead.

### POST /api/auth/otp/sms/verify
- **Auth:** none · **Role:** PUBLIC (creates or logs in a `USER`)
- **Request body:** `{ phone: string, code: string (6 digits), rememberMe: boolean }`
- **Response:** same shape/rules as the email verify endpoint, keyed by phone instead.

### POST /api/auth/refresh
- **Auth:** refresh token, via **either** JSON body `{ refreshToken: string }` **or** the `refresh_token` HTTP-only cookie (body wins if both present) · **Role:** PUBLIC (no access token required — this is how you get a new one)
- **Response:** `200 AuthResponse` with a freshly rotated access + refresh token. `401` if the refresh token is missing, unknown, revoked, or expired.
- **Behavior:** rotates on every use — the old refresh token is revoked and a new one issued. If a cookie was used to authenticate the call, the response re-sets it.

### POST /api/auth/logout
- **Auth:** `Authorization: Bearer <accessToken>` **required** · **Role:** USER/ADMIN (any authenticated user)
- **Request body (optional):** `{ refreshToken: string }` — falls back to the `refresh_token` cookie if omitted.
- **Response:** `204 No Content`, and always clears the `refresh_token` cookie. `401` without a valid access token.
- **Behavior:** revokes the given refresh token server-side, but only if it belongs to the caller (a stolen/guessed token for someone else's session is rejected, not revoked).

### Shared `AuthResponse` shape
```json
{
  "accessToken": "string (JWT, 15 min TTL)",
  "refreshToken": "string (opaque, 30 day TTL — mobile clients store this in SecureStore)",
  "user": { "id": 1, "displayName": "string", "email": "string|null", "phone": "string|null", "role": "USER|ADMIN" }
}
```
When `rememberMe: true` was sent (web), the same refresh token is *also* set as an HTTP-only, `SameSite=Lax` cookie named `refresh_token` scoped to `/api/auth` — web clients should rely on the cookie and ignore the `refreshToken` field in the body.

### Notes for later steps
- Guest mode needs no endpoint at all — it's a client-only state with no access token.
- There is still no endpoint to promote a `User` to `ADMIN`. For local testing, the `dev` profile seeds an admin account at `admin@fc27.app` (`DevDataSeeder`) — sign in via the email OTP endpoints above and read the code from the backend console log.

---

## League Types (`/api/league-types`)

A small lookup table of team categories (e.g. "Premier League", "Ember Circuit") — **not** the same as a friend-group `League` (see the `Leagues` section below). Introduced instead of a free-text field on `Team` or a fixed enum: free text let two admins create "Premier League" and "Premierlig" as silently different categories via a typo; an enum can't grow without a redeploy. This table gets picked from a dropdown, never typed by hand, and enforces case-insensitive uniqueness server-side.

### GET /api/league-types
- **Auth:** none · **Role:** PUBLIC (used to populate the team form's dropdown, and the wheel's league filter — guests need this too)
- **Response:** `200`, array of `{ id, name }`, alphabetical.

### POST /api/league-types
- **Auth:** `Authorization: Bearer <accessToken>` · **Role:** ADMIN only
- **Request body:** `{ name: string }`
- **Response:** `201 { id, name }`. `400` if a league type with that name already exists (case-insensitive — "Serie A" and "serie a" collide).

---

## Teams (`/api/teams`)

The pool of FC27 teams the wheel draws from.

### GET /api/teams
- **Auth:** none · **Role:** PUBLIC (works for guests — the wheel needs this without login)
- **Query params (all optional):** `stars` — comma-separated star levels, e.g. `?stars=2,3,4`; `leagueTypeId` — id from `GET /api/league-types`, e.g. `?leagueTypeId=3`; `includeInactive` — `true` to also return deactivated teams (default `false`)
- **Response:** `200`, array of `TeamResponse`. Only `active` (non-deleted) teams are returned unless `includeInactive=true`. The admin Teams page uses `includeInactive=true` for its own listing — otherwise a team it just deactivated would vanish from admin's view too, with no way to confirm the action worked.

### GET /api/teams/{id}
- **Auth:** none · **Role:** PUBLIC
- **Response:** `200 TeamResponse`, or `404` if no team with that id exists (active or not — lets an admin re-check a deactivated team).

### POST /api/teams
- **Auth:** `Authorization: Bearer <accessToken>` · **Role:** ADMIN only (403 for a logged-in non-admin, 401 with no/invalid token)
- **Request body:** `{ name: string, code: string (1-5 letters/digits, upper-cased server-side), crestUrl: string|null (must be a URL if present), colorHex: string|null (e.g. "#a1b2c3"), starLevel: int (2-5), leagueTypeId: number }`
- **Response:** `201 TeamResponse`. `400` on validation failure, `404` if `leagueTypeId` doesn't exist.

### PUT /api/teams/{id}
- **Auth/Role:** ADMIN only
- **Request body:** same shape as POST — replaces every field.
- **Response:** `200 TeamResponse`, `404` if the team or the given `leagueTypeId` is missing, `400` on validation failure.

### PATCH /api/teams/{id}
- **Auth/Role:** ADMIN only
- **Request body:** any subset of the POST fields — `null`/omitted fields are left unchanged, e.g. `{ "starLevel": 4 }`.
- **Response:** `200 TeamResponse`, `404` if missing.

### DELETE /api/teams/{id}
- **Auth/Role:** ADMIN only
- **Response:** `204 No Content`.
- **Behavior:** **soft delete** — sets `active=false`, does not remove the row. `Match` rows reference teams with a required, non-cascading foreign key, so a real delete on a team that's ever played a match would either fail or corrupt league/match history. A deactivated team just stops appearing in `GET /api/teams`; `GET /api/teams/{id}` can still fetch it directly.

### `TeamResponse` shape
```json
{
  "id": 1, "name": "string", "code": "string", "crestUrl": "string|null", "colorHex": "string|null",
  "starLevel": 2, "leagueTypeId": 3, "leagueTypeName": "string", "active": true, "createdAt": "instant", "updatedAt": "instant"
}
```

---

## Leagues (`/api/leagues`)

A friend group's league — a container for `Player`s and recorded `Match`es. **Requires authentication; guests cannot create or view leagues.** There is no membership/collaborator concept — a league has exactly one owner (whoever created it), and every league-scoped endpoint below 404s if the league doesn't exist and 403s if the caller isn't its owner (so a non-owner can't even tell whether the id exists). Standings and stats are always computed live from recorded matches, never stored — see the "league standings are never manually editable" rule.

### POST /api/leagues
- **Auth:** required · **Role:** any authenticated user
- **Request body:** `{ name: string }`
- **Response:** `201 LeagueResponse`.

### GET /api/leagues
- **Auth:** required
- **Response:** `200`, array of `LeagueResponse` — leagues owned by the caller only.

### POST /api/leagues/{id}/reset
- **Auth:** required · owner only
- **Response:** `204 No Content`. Deletes every recorded match in the league (standings/stats go back to zero); the league and its player roster are untouched. The prototype's "danger zone: reset league" action.

### `LeagueResponse` shape
```json
{ "id": 1, "name": "string", "ownerId": 5, "createdAt": "instant" }
```

### POST /api/leagues/{leagueId}/players
- **Auth:** required · owner only
- **Request body:** `{ displayName: string, userId: number|null }` — `userId` optionally links the player to a registered account; omit it for "add by name only".
- **Response:** `201 PlayerResponse`. `400` if `displayName` is already used in this league (case-sensitive exact match), `404` if `userId` is given but doesn't exist.

### GET /api/leagues/{leagueId}/players
- **Auth:** required · owner only
- **Query params:** `includeInactive` — `true` to also return removed players (default `false`).
- **Response:** `200`, array of `PlayerResponse`.

### DELETE /api/leagues/{leagueId}/players/{playerId}
- **Auth:** required · owner only
- **Response:** `204 No Content`. **Soft-remove** — sets `active=false`; a player's past matches (non-nullable FK) stay valid and keep contributing to historical standings. `404` if the player doesn't exist or belongs to a different league.

### `PlayerResponse` shape
```json
{ "id": 1, "leagueId": 5, "displayName": "string", "userId": null, "active": true }
```

### POST /api/leagues/{leagueId}/matches
- **Auth:** required · owner only
- **Request body:** `{ player1Id: number, player2Id: number, team1Id: number, team2Id: number, score1: number (>=0), score2: number (>=0) }` — this is the "Live Match Registration" submit, called once the wheel has drawn `team1`/`team2` for `player1`/`player2` and a final score is known.
- **Response:** `201 MatchResponse`. `400` if `player1Id == player2Id`, `404` if either player doesn't belong to this league or either team id doesn't exist.

### GET /api/leagues/{leagueId}/matches
- **Auth:** required · owner only
- **Response:** `200`, array of `MatchResponse`, most recent first.

### `MatchResponse` shape
```json
{
  "id": 1, "leagueId": 5, "player1Id": 2, "player1Name": "string", "player2Id": 3, "player2Name": "string",
  "team1Id": 10, "team1Name": "string", "team2Id": 11, "team2Name": "string", "score1": 3, "score2": 1, "recordedAt": "instant"
}
```

### GET /api/leagues/{leagueId}/standings
- **Auth:** required · owner only
- **Response:** `200`, array of `StandingRow`, one per player who has ever been in the league (including soft-removed ones, so their history isn't lost), sorted by points desc → goal difference desc → goals for desc → name. Points: win = 3, draw = 1, loss = 0.
- **`StandingRow` shape:** `{ playerId, displayName, played, won, drawn, lost, goalsFor, goalsAgainst, goalDifference, points }`

### GET /api/leagues/{leagueId}/stats
- **Auth:** required · owner only
- **Response:** `200 LeagueStatsResponse` — `topScorer`/`bestDefense` are `null` if no matches have been recorded yet (a player with zero matches can't legitimately "win" Best Defense with a trivial 0 conceded).
```json
{
  "topScorer": { "playerId": 2, "displayName": "string", "value": 7 },
  "bestDefense": { "playerId": 3, "displayName": "string", "value": 1 },
  "headToHead": [
    { "player1Id": 2, "player1Name": "string", "player2Id": 3, "player2Name": "string", "player1Wins": 2, "player2Wins": 1, "draws": 0 }
  ]
}
```
`headToHead` has one row per unordered pair of players who have played each other at least once (not every possible pair) — `player1`/`player2` here is just the pair's canonical ordering (lower player id first), unrelated to who was "player1" in any specific match.
