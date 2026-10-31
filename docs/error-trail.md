# Error trail (`util/trail/`)

A client-only diagnostic: SkyBlock Apex keeps a short trail of recent events in memory and writes it to a file only
when something goes wrong. Nothing is sent anywhere and nothing in the game changes.

## What is kept (memory only)
- Apex's own loggers (`Apex`, `Apex.*`, `Apex/*`, `dev.ashtonite365.apex.*`, `SkyBlock Apex textures`) at every level,
  including DEBUG lines that the console and latest.log never show. What those outputs show is unchanged.
- WARN and above from every other logger.
- Breadcrumbs: texture engine selected (each resource-manager setup), resource reload started and applied, texture
  pack files skipped after an error, screen changes (screen class), server connected, server joined, world ready.

## When a file is written
- Any ERROR or FATAL log line from any logger (via a log4j appender; log4j already ships with Minecraft).
- An uncaught exception on any thread (the JVM default handler; the previous handler still runs afterwards).
- Minecraft saving a crash report (`CrashReport.saveToFile`).
Without one of these, no file is ever written.

## Where
`<game dir>/logs/apex-trail/<yyyy-MM-dd_HH-mm-ss-SSS>.log` (local time): the trigger, the error with its stack trace,
then the trail, oldest first.

## Limits
| Limit | Value |
|---|---|
| Events kept | 2,000 (oldest dropped first) |
| Event age | 5 minutes |
| Files from ERROR lines | at most 1 per 60 s (crash reports are always written) |
| Files per rolling hour | 10 (crashes included) |
| Files kept | 20; the oldest trail file is deleted when a new one is written (other files are never touched) |
Triggers held back by the rate limits are counted and the count goes into the next file. ERROR-line files are written
on a background thread; crash and uncaught-exception files are written immediately. `-Dapex.trail=off` disables it.

## Overhead (B-TRAIL, `benchTest --tests '*TrailBench'`, box, JDK 25, 27 Oct 2026 ~00:30 AEDT)
| Case | ns/op | B/op |
|---|---:|---:|
| crumb, trail off | 7.0 | 0 |
| crumb, trail on | 53.4 | 0 |
| Apex info line, without / with trail | 524.8 / 674.1 | 24 / 128 |
| Apex debug line (hidden), without / with trail | 8.7 / 404.3 | 0 / 104 |
| other logger info line, without / with trail | 174.3 / 255.4 | 24 / 24 |
| other logger debug line (off), without / with trail | 10.5 / 5.3 | 0 / 0 |
Breadcrumbs allocate nothing (preallocated ring; text is built only when a file is written). Hidden Apex DEBUG lines
now cost ~0.4 µs each because they are kept; Apex has two debug calls today, neither per frame.
