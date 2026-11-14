# OblivionDuels (development foundation)

Paper 1.21.11 / Java 21 Maven project.

## Build
Run `mvn clean package`. The JAR is created in `target/`.

## Setup
1. `/duels create arena1`
2. Stand at Team A spawn: `/duels setspawn arena1 a`
3. Stand at Team B spawn: `/duels setspawn arena1 b`
4. Set two opposite arena corners: `/duels pos1 arena1` and `/duels pos2 arena1`
5. Players queue with `/duels join 1 arena1` through `/duels join 5 arena1`.

Admin commands require `oblivionduels.admin` (default OP).

## Current development scope / limitations
This is an early source foundation, not a production-ready release. It provides arena setup, solo queues, 1v1–5v5 team assignment, countdown, basic elimination, disconnect elimination, and basic session stats. The configured arena bounds are saved but boundary enforcement is not yet implemented. Inventory/death loot ownership, reconnect handling, durable statistics/leaderboards, full match recovery after restart, and GUI are not yet implemented. Test on a separate server; do not use on a live SMP yet.
