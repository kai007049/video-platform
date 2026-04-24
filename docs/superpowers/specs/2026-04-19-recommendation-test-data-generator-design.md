# Recommendation Test Data Generator Design

## Goal

Add a low-cost, one-command test data generator inside the backend so the project can generate realistic recommendation test data without requiring real media assets. The generator must support append-only execution on top of existing data, produce behavior distributions that are close to a real video platform, and populate the recommendation-related data needed by the current system: user behavior, interest profiles, video tag features, video counters, Redis hot ranking, and optional search indexing.

## Why this exists

The current recommendation flow depends on multiple signals rather than a single ranking source:

- hot recall from Redis ZSet
- fresh recall from recent publish time
- tag-interest recall
- category-affinity recall
- author-affinity recall
- rerank diversity and recent-watch penalty

Because of that, simple random SQL inserts are not enough. They can increase row counts, but they do not create believable user interests, long-tail popularity, or stable cold-start cases. The generator should instead create structured but low-cost synthetic data that is realistic enough for:

- recommendation quality checks
- cold-start validation
- Redis/cache behavior observation
- medium-scale local performance testing
- search/recommendation integration checks

## Non-goals

This design does not try to:

- generate real playable video files or MinIO objects
- perfectly simulate production traffic
- add a permanent admin API for data generation
- automatically clean up previously generated data
- make generated rows visually obvious as test rows in product-facing fields

## Existing project context

The design targets the current recommendation implementation in [RecommendationServiceImpl.java](backend/src/main/java/com/bilibili/video/service/impl/RecommendationServiceImpl.java#L58-L237), which builds a recommendation window by combining hot, fresh, editorial, tag-interest, category-affinity, and author-affinity signals.

Relevant existing pieces:

- recommendation reads hot recall from Redis key family defined in [Constants.java](backend/src/main/java/com/bilibili/video/common/Constants.java)
- recommendation uses recent watch history and profile summaries from user behavior tables
- search already supports full reindex via [AdminController.java](backend/src/main/java/com/bilibili/video/controller/AdminController.java#L93-L102)
- schema and base category/tag seed data already exist in [schema.sql](backend/src/main/resources/db/schema.sql#L143-L434)

## User-approved constraints

- data should be as realistic as practical, not obviously fake
- generation should stay low-cost
- execution should be a single command
- generated data should be appended onto existing data, not replace it
- generated rows do not need explicit visible markers in username/title fields
- default execution should prefer a medium dataset rather than immediately generating the largest possible dataset
- real media assets are out of scope for the first version

## Recommended approach

Implement an internal backend seed generator that runs only when explicitly activated by startup arguments, ideally behind a dedicated `seed` profile or an equally explicit activation mechanism chosen during planning.

Recommended invocation shape:

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--seed.enabled=true --seed.mode=medium --seed.append=true --seed.search-reindex=true"
```

Equivalent packaged-jar invocation should also work:

```bash
java -jar app.jar --seed.enabled=true --seed.mode=medium --seed.append=true --seed.search-reindex=true
```

This should be implemented as an explicit command-line execution path rather than an HTTP endpoint. Normal application startup must remain unchanged unless the seed flags are present.

## Alternatives considered

### Option A: SQL script / stored procedure

**Pros**
- simple to execute once
- easy to understand at first glance

**Cons**
- poor at expressing realistic distributions
- awkward to maintain when recommendation rules evolve
- hard to coordinate DB state, Redis hot ranking, and optional search reindex in one coherent flow
- weak fit for append-only repeated execution

### Option B: external Python/Node script

**Pros**
- flexible data generation logic
- fast to prototype

**Cons**
- adds a second implementation surface outside the backend
- can drift from the project’s entity/model assumptions
- less convenient for future contributors who expect backend-owned tooling

### Option C: internal backend command-style generator (recommended)

**Pros**
- best fit for one-command execution
- easiest place to reuse entities, mappers, config, and Redis/search integration
- simplest way to keep generation logic aligned with evolving recommendation behavior
- easiest way to support small/medium/large profiles later

**Cons**
- requires initial engineering work inside backend

## High-level architecture

The generator should be implemented as a dedicated startup workflow that only becomes active when explicit seed flags are provided, with a `seed` profile as the preferred but not mandatory wiring choice.

### Entry point

A startup component should run only when all of these are true:

- seeding is explicitly enabled
- append mode is explicitly allowed
- required infrastructure dependencies are available

The component should:

1. validate configuration
2. load domain inputs already present in DB
3. generate users/authors/videos/behaviors in dependency order
4. backfill derived recommendation data
5. warm Redis hot ranking
6. optionally rebuild search index
7. print a compact generation summary
8. exit cleanly

This is intentionally command-like behavior, not a background service.

### Modules

The implementation should be split into small focused services.

#### 1. SeedProperties
Defines external configuration such as:

- `seed.enabled`
- `seed.mode=small|medium|large`
- `seed.append=true`
- `seed.search-reindex=true|false`
- optional overrides for user/video/behavior counts
- optional random seed value for reproducibility

#### 2. SeedProfileCatalog
Maps `small`, `medium`, `large` to default counts and probability settings.

It should include:

- number of authors
- number of users
- number of videos
- behavior volume targets
- activity-level proportions
- cross-interest probability
- popularity distribution parameters
- publish-time window

#### 3. SeedDomainCatalog
Loads existing categories and tags from DB and groups them into domain clusters used for realistic generation.

Example clusters:

- animation / ACG
- gaming
- technology
- music
- sports
- lifestyle
- film / entertainment
- knowledge / education

Each cluster should define:

- primary categories
- typical tags
- cross-over tags
- title keyword pools

This lets generation stay aligned with data that already exists in the project instead of inventing a disconnected taxonomy.

#### 4. SeedUserGenerator
Generates users and author accounts.

Users should be distributed across behavior personas:

- heavy users
- medium users
- light users
- cold-start users
- cross-interest users

Each user gets:

- one primary interest cluster
- zero or one secondary cluster
- an activity level
- optional follow tendency
- optional exploration tendency

Authors should be distributed across tiers:

- head authors
- mid-tier authors
- long-tail authors

#### 5. SeedVideoGenerator
Generates videos authored by generated authors.

Each video should have:

- one primary category
- two to four tags
- a realistic publish time inside a recent window
- a plausible title assembled from domain templates and keywords
- optional editorial-recommend flag for a small minority
- an implicit potential score used internally to shape later behavior generation

The publish-time distribution should bias toward recent content while still leaving older content present.

#### 6. SeedBehaviorGenerator
Generates watch history, likes, favorites, and follows.

Behavior generation must be rule-driven, not uniform random.

Selection principles:

- users mostly consume videos from their primary cluster
- some consumption comes from secondary or exploratory clusters
- heavy users consume more content and convert more often
- head authors and high-potential videos receive more exposure, but long-tail content remains common
- newer videos get more opportunity in fresh-oriented sampling
- follow actions are rarer than likes, and favorites are rarer than views

The generator should create timestamps with realistic recency patterns so recent-history-based logic remains meaningful.

#### 7. SeedRecommendationProjection
After raw behaviors are written, compute or backfill the derived data that recommendation relies on.

Responsibilities:

- populate `user_interest_tag` for generated users
- populate `video_tag_feature` for generated videos
- backfill video counters such as play/like/favorite counts if current schema/service expects them
- ensure data needed by category- and author-affinity logic is present

#### 8. SeedRedisWarmup
Populate the hot ranking Redis ZSet used by hot recall.

This stage should compute hot score using the same project constants or an equivalent compatible formula so the recommendation hot-recall path behaves naturally.

#### 9. SeedSearchWarmup
If `seed.search-reindex=true`, invoke the existing search reindex service after generation completes.

This is optional because local search infrastructure may not always be available.

## Data realism strategy

The generator should aim for plausible distributions rather than pure randomness.

### User realism

Use an activity split such as:

- heavy users: small minority
- medium users: meaningful middle layer
- light users: largest active segment
- cold-start users: visible but limited share

Users should not be perfectly pure in taste. A typical split should look like:

- ~70% primary-interest consumption
- ~20% secondary-interest consumption
- ~10% exploratory/random consumption

### Author realism

Author productivity and audience attraction should follow a long-tail shape:

- small head group with more videos and more exposure
- medium middle group
- large long-tail group with fewer uploads and less interaction

### Video realism

Video popularity should also be long-tail:

- very few breakout videos
- some mid-popularity videos
- many ordinary videos

Publish times should not be uniform. Most content should be recent enough to participate in fresh recall, but some older content should remain so hot vs fresh behaviors can be observed separately.

### Behavior realism

Conversion rates should be constrained:

- views are common
- likes are much less frequent than views
- favorites are less frequent than likes
- follows are rare and usually associated with repeated exposure to the same author/cluster

This keeps interaction tables from looking artificially dense.

## Default scale

The default `medium` profile should be large enough to meaningfully test recommendation behavior without making local execution expensive.

Initial target range:

- authors: 80–150
- users: 800–1500
- videos: 4000–8000
- watch history rows: 50k–120k
- likes: 6k–15k
- favorites: 2k–6k
- follows: 1k–3k

The exact defaults can be finalized during implementation, but they should stay in this range unless local performance testing shows the need to reduce them.

## Generation order

Execution order should be deterministic at the stage level:

1. validate config and load categories/tags
2. generate authors
3. generate users
4. generate videos
5. generate follow relationships
6. generate watch history
7. generate likes and favorites
8. backfill derived recommendation data
9. warm Redis hot ranking
10. optionally rebuild search index
11. print summary and exit

This order ensures dependencies exist before derived data is computed.

## Repeated append behavior

The generator is append-only in the first version.

That means:

- existing business data remains untouched
- each run creates additional users/videos/behaviors
- generated content should look normal enough to coexist with existing records
- the generator should not assume the database is empty

To avoid obviously artificial growth patterns, the generator should use varied timestamps and domain distributions rather than inserting everything at one instant with uniform density.

This version does not provide automatic cleanup because the user explicitly prioritized append behavior and low-cost execution over reversible tagging/cleanup mechanics.

## Safety and activation rules

Because this tool mutates shared local state, it must be hard to trigger accidentally.

Required protections:

- seeding never runs during normal startup without `seed.enabled=true`
- append mode must be explicit
- the startup log should print the selected mode and expected counts before generation begins
- failures should stop the run instead of partially continuing through later warmup steps when foundational writes fail

## Error handling

The generator should fail fast on structural problems such as:

- missing categories or tags required for domain clustering
- DB write failure in a foundational stage
- Redis unavailable when hot-rank warmup is mandatory for the selected mode

Optional steps should be isolated:

- search reindex can be skipped when disabled
- search reindex failures should be clearly reported and should not invalidate already-generated DB data

The final summary should make it clear which stages succeeded and which optional stages were skipped or failed.

## Verification strategy

The design should be verified at two levels.

### Functional verification

After generation, check that:

- generated users receive personalized recommendation differences
- cold-start users mostly receive hot/fresh/editorial content
- recent-watch penalty can be observed for recently watched videos
- hot recall returns populated results from Redis
- rerank diversity prevents excessive same-author or same-category clustering

### Structural verification

After generation, check that:

- counts roughly match the selected profile
- behavior distributions are non-uniform and plausible
- generated video publish times span the intended window
- search reindex completes when enabled

## Testing strategy

Implementation should include tests for:

- profile-to-count mapping logic
- domain clustering fallback/validation logic
- probability-driven selection helpers where deterministic seeds are used
- derived projection generation for tag interest and video tag features
- hot-score warmup compatibility with current recommendation hot recall

At the integration level, a profile-based local verification run should confirm that the generator can execute end-to-end against the project’s actual DB/Redis stack.

## Open implementation decisions intentionally deferred to planning

These are implementation details, not design blockers:

- exact class names
- whether startup wiring uses `ApplicationRunner` or `CommandLineRunner`
- exact seed-mode counts inside the approved ranges
- whether optional reproducibility uses one global seed or per-stage derived seeds

The design direction is fixed even though these code-level choices remain for the implementation plan.

## Recommended next step

After this design is approved, the next step is to write an implementation plan that:

- maps the generator to concrete backend files and services
- defines the configuration surface
- identifies which existing entities/mappers/services should be reused
- sequences implementation and verification work in small steps