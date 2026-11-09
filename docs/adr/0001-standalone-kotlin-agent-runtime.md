# 0001. Standalone on-device Kotlin agent runtime for Android

- Status: Proposed
- Date: 2026-11-09
- Context issue: lucaskfp/learnharness#1 (spec), lucaskfp/learnharness#2

## Context

LearnHarness runs as an Electron desktop app backed by a local Node server that owns Sessions end to end: Turns, agents (Prober, Teacher, Practitioner), tools, Jobs, the knowledge graph and persistence. Learners want to continue Teaching Sessions, practise and review the graph on an Android phone or tablet, away from their computer.

Three ways to get the agent behaviour onto Android were considered:

1. **Thin client to the existing server.** The Android app is a UI over the desktop server's HTTP and stream APIs.
2. **Embedded Node runtime.** Ship a Node.js runtime inside the APK and run the upstream server code on the device.
3. **Standalone Kotlin runtime.** Reimplement the Session engine, agents, tools, provider and search ports, Jobs, graph and persistence in Kotlin on the device.

## Decision

Option 3: a standalone on-device agent runtime in Kotlin. The Android app has no dependency on the desktop app or its server. The learner's own provider keys are used from the device. Upstream stays the source of truth for prompts and tool contracts, which are imported rather than retyped.

## Consequences

- Works anywhere, with no desktop running, port forwarding or network reachability to a home machine (the main weakness of option 1).
- Native Android integration: WorkManager for Jobs that survive process death and reboot, Room for storage, Keystore-encrypted keys, platform speech recognition and PDF rendering. Option 2 would fight the platform for all of these, and a Node runtime adds tens of megabytes, memory pressure and an unusual build.
- The cost is a second implementation of the agent runtime. Behaviour can drift from upstream, which evolves quickly. Mitigations: import prompts and tool contracts from the repo, mirror upstream Turn semantics, and reuse upstream fixtures (scripted provider streams, JSON records) in the Android test suite.
- There is no automatic sync. Sessions and the graph move between devices only through export/import in the desktop JSON format.
- Features that depend on the desktop (Claude Code CLI, subscription logins through an embedded browser) are out of scope on Android.
- Hard to reverse: once the Kotlin engine exists, switching to a thin client would discard it. Revisit only if a hosted LearnHarness server appears.
