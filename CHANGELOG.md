# Changelog

All notable changes of `multi-tour-commercial-reservation-api` (Commercial and Reservations service) are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [3.0.0] - 2026-11-14

Cut 3 release. Every entry below is a pull request merged into `qa` and re-applied to `release/3.0.0` with `git cherry-pick -x`; the heading of each group is the user story named in the pull request, and the entry is the title of the pull request without its type. The frozen head of `qa` is `3ac81b9315a1649f0be515cceff563d3c1fe65c8`.

### Added

#### HU-COM-002 - Manage associated establishments

- The contact of an associated establishment ([PR 453](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/453))
- The mandatory reason and change log of associated establishments ([PR 455](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/455))

#### HU-COM-006 - Browse the offer from the digital channel

- The effective capacity recomputation and its single repository bean ([PR 503](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/503))

#### HU-COM-013 - Cancel a reservation at the customer's request

- The cancellation penalty policy ([PR 482](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/482))

#### HU-COM-015 - Notify the Final Customer

- The notice of a rescheduling proposal ([PR 454](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/454))

#### HU-COM-016 - Authorize and execute a refund

- The authorization of a credit balance request ([PR 471](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/471))
- Serve the internal read of a refund to the execute-refund saga ([PR 517](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/517))

#### Cross-cutting: CI, test fixes and alignment promotions (no single story is named in the PR title, branch or body)

- Serve the internal read of a validated payment to the register-payment saga ([PR 525](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/525))

### Changed

#### HU-COM-001 - Manage the operational catalog

- Hu-com-001 collaborator and route prefix ([PR 25](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/25))
- The collaborator flag and the transport rate of the catalog ([PR 264](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/264))
- The refreshed stale comments ([PR 396](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/396))

#### HU-COM-002 - Manage associated establishments

- Hu-com-002 transition events test ([PR 27](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/27))
- Hu-com-002 Annex C checklist tests ([PR 48](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/48))
- The refreshed header of the contract copy ([PR 265](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/265))
- The EstablishmentChangeLogTest compile fix ([PR 466](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/466))

#### HU-COM-003 - Manage discount rules

- Hu-com-003 discount rule aggregate and audit ([PR 49](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/49))
- Hu-com-003 rule calendar validity and evaluation ([PR 50](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/50))
- Hu-com-003 discount rules over http ([PR 51](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/51))
- Hu-com-003 discount rules jdbc persistence ([PR 52](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/52))
- The stackable flag and the reactivation of the discount rule ([PR 267](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/267))
- The stackable persistence and the discount line chaining ([PR 317](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/317))
- The stackable flag and reactivation over HTTP ([PR 321](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/321))

#### HU-COM-004 - View and apply current discounts

- Hu-com-004 money core and additional discount ([PR 53](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/53))
- Hu-com-004 breakdown by priority and jdbc persistence ([PR 54](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/54))
- The additional discount preview and registration over HTTP ([PR 274](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/274))
- The current discount breakdown read ([PR 309](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/309))
- The additional discount in the payment value ([PR 331](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/331))

#### HU-COM-005 - Register the lead customer and companions

- Hu-com-005 lead customer registration ([PR 55](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/55))
- Hu-com-005 customers jdbc persistence ([PR 57](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/57))
- The companions refusals and isolation tests ([PR 275](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/275))
- The link of the account on the verified e-mail ([PR 279](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/279))
- The e-mail match of a lead and the customer search ([PR 318](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/318))

#### HU-COM-006 - Browse the offer from the digital channel

- The public tour detail, the departure mirror and the discount rule list ([PR 222](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/222))
- The public paths, the public tours and the departure events route ([PR 311](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/311))
- The public establishments and the departure event order ([PR 315](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/315))
- The public routes no-personal-data log test ([PR 322](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/322))
- The capacity row seed of another tenant with its own service ([PR 505](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/505))

#### HU-COM-007 - Create a reservation and calculate its value

- The reservation core and the departure checks ([PR 146](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/146))
- The reservation creation core ([PR 268](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/268))
- The departure and reservation reads of a tenant ([PR 269](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/269))
- The departures of a tour and the reservation quote routes ([PR 270](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/270))
- The creation and the reads of a reservation over HTTP ([PR 271](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/271))
- The JDBC storage of a reservation ([PR 272](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/272))
- The inline holder of the reservation creation ([PR 277](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/277))
- The quote refusals and the paid amount fixes of the reservation ([PR 280](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/280))
- The JDBC reads of a reservation and the transport rate reader ([PR 284](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/284))

#### HU-COM-008 - Reserve from the digital channel

- The own scope of the Final Customer over the reservation routes ([PR 276](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/276))
- The reservation refusals, the customer race and the isolation tests ([PR 293](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/293))
- The own reservation quote and the full public departure ([PR 314](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/314))
- The payment deadline of the reservation ([PR 334](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/334))
- The omission of the frozen deadline ([PR 335](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/335))

#### HU-COM-009 - Register a payment or a partial payment

- The payment core of the reservation ([PR 281](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/281))
- The transfer registration with its proof ([PR 282](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/282))
- The cash registration, the payment quote and the parameters ([PR 283](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/283))
- The transfer and cash registration routes ([PR 286](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/286))
- The payment history, quote and parameters routes ([PR 288](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/288))
- The JDBC storage of the payments of a reservation ([PR 289](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/289))
- The JDBC capacity, proofs and payment conditions ([PR 290](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/290))
- The payment parameters idempotency and the payment isolation tests ([PR 292](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/292))
- The payment row lock and the effective deadline on payment ([PR 302](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/302))
- The race tests and the effective deadline in the quote ([PR 305](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/305))
- The seat hold only on a scheduled departure ([PR 333](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/333))
- The payment parameters read and the attempt upsert guard ([PR 359](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/359))
- The cash session status read from the cash api ([PR 389](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/389))
- The registration of a deposit below the minimum ([PR 391](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/391))
- The below-minimum deposit HTTP and JDBC test ([PR 399](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/399))
- The payment parameters and no-personal-data tests ([PR 413](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/413))
- The default cash reporting url of the compose ([PR 420](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/420))
- The cash income port with reversal ([PR 433](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/433))
- The cash income adapter and its wiring ([PR 440](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/440))
- The PaymentParametersChanged event of the payment parameters ([PR 442](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/442))

#### HU-COM-010 - Validate or reject a payment proof

- The payment proof decision core ([PR 285](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/285))
- The proof list and the validated payment record ([PR 287](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/287))
- The proof read and decision routes ([PR 291](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/291))
- The proof file read of the owner and the effective deadline decision ([PR 296](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/296))
- The reservation lock of the proof decision ([PR 298](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/298))
- The batch read of the proof list ([PR 301](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/301))
- The proof isolation test and the decision controller refactor ([PR 306](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/306))
- The idempotent decision in the core and the proof row fields ([PR 308](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/308))
- The customer name in the proof reads ([PR 312](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/312))
- The idempotency port of the writes ([PR 316](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/316))
- The idempotent payment of a reservation ([PR 320](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/320))
- The read-only platform support actor ([PR 362](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/362))
- The attempt id of the payment review events ([PR 374](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/374))
- The idempotent rescheduling writes ([PR 384](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/384))
- The authorizable reasons refactor and reconcile the architecture list ([PR 385](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/385))
- The idempotent expiry, modification and cancellation writes and reconcile the import ([PR 386](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/386))
- The idempotent refund, deadline and discount writes and reconcile the architecture list ([PR 390](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/390))
- The suspension-aware review clock and the late approval cause ([PR 412](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/412))
- The proof reads restricted to the administrator and the collaborator ([PR 435](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/435))
- The refund execution test fixes ([PR 469](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/469))
- The removal of the direct approval of a proof ([PR 475](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/475))

#### HU-COM-011 - Manage the reservations pending payment

- The effective payment deadline core ([PR 294](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/294))
- The payment follow-up core ([PR 295](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/295))
- The JDBC adapters of the payment follow-up ([PR 297](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/297))
- The public routes of the payment deadline ([PR 300](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/300))
- The internal routes of the payment deadline ([PR 303](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/303))
- The initial payment deadline of the reservation ([PR 304](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/304))
- The follow-up race tests and the log note fix ([PR 338](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/338))
- The hidden deadline grantor ([PR 340](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/340))
- The cancellation columns of the cancelled test reservations ([PR 393](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/393))
- The expiry job test of HU-COM-011 ([PR 472](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/472))

#### HU-COM-012 - Modify a reservation before execution

- The reservation modification and the service change policy ([PR 326](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/326))
- The modification settlement with the seat move ([PR 329](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/329))
- The next modification step of a reservation ([PR 332](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/332))
- Reconcile the outbox event type with develop and promote the modification adapters ([PR 363](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/363))
- The stored modification adapters ([PR 365](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/365))
- The modification routes ([PR 367](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/367))
- The capacity ceiling and the modification quote value ([PR 373](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/373))
- The default change policy fallback ([PR 432](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/432))
- The modification excess test of HU-COM-012 ([PR 479](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/479))
- Hold the seats of each stored reservation in the concurrent modification test ([PR 515](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/515))

#### HU-COM-013 - Cancel a reservation at the customer's request

- The cancellation at the customer request core ([PR 323](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/323))
- The cancellation adapters ([PR 327](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/327))
- The reprogramming mark of the cancellation ([PR 357](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/357))

#### HU-COM-014 - Consult my reservations and my supports

- The next customer action and the consultation reads ([PR 324](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/324))
- The next action fields of the reservation reads ([PR 328](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/328))
- The receipts and the cause of the reservation reads ([PR 330](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/330))
- The open proposal reader ([PR 358](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/358))

#### HU-COM-015 - Notify the Final Customer

- The client notice policy and writing ([PR 325](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/325))
- The client notice content route ([PR 356](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/356))
- The outbox delivery routes ([PR 366](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/366))
- The outbox audit of the exceptional access ([PR 376](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/376))
- The log scan of the client notice internal routes ([PR 392](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/392))
- The refund processed and credit recorded notices ([PR 426](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/426))
- The refund and credit notice persistence test and the reader with amount ([PR 429](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/429))

#### HU-COM-016 - Authorize and execute a refund

- The decision and refund domain ([PR 336](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/336))
- The refund use cases ([PR 337](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/337))
- The refund persistence ([PR 339](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/339))
- The saga routes of the refund ([PR 342](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/342))
- The refund reads ([PR 344](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/344))
- The refund writes ([PR 347](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/347))
- The five business days count of the refund ([PR 434](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/434))
- The closed refund decisions of an inactive tenant and the decision as authorization ([PR 445](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/445))
- Remove dead DeferredFinancialDecisionOpener and refresh stale comments ([PR 521](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/521))

#### HU-COM-017 - Record and apply a credit balance

- The credit balance, its recording and its application ([PR 341](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/341))
- The credit balance adapters and routes ([PR 345](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/345))
- The credit decision and application routes ([PR 368](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/368))
- The credit balance reads and reconcile the imports with develop ([PR 369](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/369))
- The platform support actor on the credit routes ([PR 371](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/371))
- The credit events and the refund request ([PR 372](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/372))
- The credit balance ownership test ([PR 377](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/377))

#### HU-COM-018 - Reschedule an affected reservation

- The rescheduling episode and its opening ([PR 343](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/343))
- The answer to the rescheduling proposal ([PR 346](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/346))
- The cancellations of the rescheduling episode ([PR 348](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/348))
- The voiding of rescheduling proposals ([PR 352](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/352))
- The adapters of the rescheduling routes ([PR 375](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/375))
- The internal routes to open and void a rescheduling episode ([PR 378](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/378))
- The public writes of the rescheduling ([PR 379](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/379))
- The public reads of the rescheduling ([PR 380](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/380))
- The rescheduling evidence out of DEBUG logs ([PR 418](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/418))
- The rescheduling race tests and guards ([PR 439](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/439))

#### HU-COM-019 - Delete sensitive data one year after the service

- The anchor and facts of the sensitive data deletion ([PR 350](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/350))
- The sensitive data deletion ([PR 354](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/354))
- The internal deletion routes and the deletion event ([PR 370](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/370))
- The AC-19.15 deletion test ([PR 398](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/398))
- The real reservation closing reader of the deletion ([PR 411](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/411))
- The AC-19.14 read-after-deletion test ([PR 414](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/414))
- The sensitive data deletion test of HU-COM-019 ([PR 480](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/480))

#### HU-COM-020 - Handle the obligations of an inactive tenant

- The tenant inactivity hook and the suspension adapters ([PR 353](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/353))
- The inactive tenant cancellation and the decision resume ([PR 355](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/355))
- The rescheduling episodes of an inactive tenant ([PR 383](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/383))
- The suspension open since ([PR 410](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/410))
- The not-resumed decision with a reached departure ([PR 415](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/415))
- The restricted proof decision of an inactive tenant ([PR 416](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/416))
- The single suspension bean fix ([PR 422](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/422))
- The restricted access marker of the outbox envelope ([PR 449](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/449))

#### HU-CSH-003 - Consult the monthly report and the cash consolidation

- The commercial value in ReservationConfirmed ([PR 401](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/401))

#### HU-SEC-005 - Manage the tenant's internal users

- The account session cutoff filter and the internal events route ([PR 447](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/447))

#### HU-WRK-001 - Expire unpaid reservations and balances

- The internal list of the active tenants ([PR 388](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/388))

#### HU-WRK-007 - Relay events between domains

- The tenant status copy core, the discount quote and the high-risk answer ([PR 143](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/143))
- The tenant event route and the inactive tenant refusal ([PR 145](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/145))
- The tenant status copy in PostgreSQL ([PR 266](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/266))

#### Cross-cutting: CI, test fixes and alignment promotions (no single story is named in the PR title, branch or body)

- Seed the departure state in the capacity integration test ([PR 64](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/64))
- Expose the customers over HTTP ([PR 65](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/65))
- Serve the public tour list and establishments from the core ([PR 66](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/66))
- Mirror the departure events into the capacity rows in the core ([PR 67](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/67))
- Persist the terms acceptance of the customer ([PR 68](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/68))
- The used-at of the exceptional access ([PR 381](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/381))
- The Dockerfile and the compose of the api ([PR 382](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/382))
- The open point comments that reference the spec decision ([PR 492](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/492))
- The PostgreSQL 18 service of the persistence tests ([PR 490](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/490))
- Say in the filter that a tenant with no status copy is not refused by decision ([PR 499](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/499))
- Open the transaction of the http adapters through the port RequestTransaction ([PR 500](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/500))
- Validate every HTTP response against the full contract and cover its operations ([PR 501](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/501))
- The checks on pull requests to main ([PR 507](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/507))
- Keep the catalog HTTP tests off the database and follow DP-05 in the discount payment test ([PR 511](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/511))
- Run the test workflow only on pull requests to develop and qa ([PR 523](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/523))

### Fixed

#### HU-COM-002 - Manage associated establishments

- The change log wiring of the JDBC context ([PR 468](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/468))
- The valid contact of EstablishmentChangeLogTest ([PR 491](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/491))

#### HU-COM-009 - Register a payment or a partial payment

- The payment condition aggregate id ([PR 470](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/470))

#### HU-COM-010 - Validate or reject a payment proof

- The closed approve route of payment proofs ([PR 467](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/467))

#### HU-COM-013 - Cancel a reservation at the customer's request

- Answer the deletion with its reservation id and sync the contract copy ([PR 509](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/509))

#### HU-COM-016 - Authorize and execute a refund

- The refund tie-break and the inactive tenant test ([PR 458](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/458))
- Keep credit-balance refund requests out of the 24-hour default ([PR 519](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/519))

#### Cross-cutting: CI, test fixes and alignment promotions (no single story is named in the PR title, branch or body)

- The delivery payloads of catalog, establishment and discount events ([PR 478](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/478))
- The in-memory HTTP test fixes ([PR 489](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/489))
- Order pending outbox events by a unique key so pages never skip or repeat ([PR 513](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/513))

### Security

#### HU-COM-001 - Manage the operational catalog

- Hu-com-001 catalog read for Manager and Analyst and dev token script ([PR 47](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/47))

#### HU-COM-005 - Register the lead customer and companions

- Hu-com-005 sensitive data read and field cipher ([PR 56](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/56))

#### HU-COM-010 - Validate or reject a payment proof

- The platform support role tests ([PR 364](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/364))

#### HU-COM-017 - Record and apply a credit balance

- The exceptional access token and its outbox ([PR 360](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/360))

#### Cross-cutting: CI, test fixes and alignment promotions (no single story is named in the PR title, branch or body)

- The fictitious identity number in the cipher test ([PR 397](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/397))
- Every writing route refuses a request without a token and a role that may not write ([PR 497](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/497))
- Log only the reasons the verifier writes when it refuses a token ([PR 498](https://github.com/code-corhuila/multi-tour-commercial-reservation-api/pull/498))

[Unreleased]: https://github.com/code-corhuila/multi-tour-commercial-reservation-api/compare/v3.0.0...HEAD
[3.0.0]: https://github.com/code-corhuila/multi-tour-commercial-reservation-api/releases/tag/v3.0.0
