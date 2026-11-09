# Glossary

The domain language of LearnHarness, shared by the web/desktop app and `android/`. Use these terms in code, tests, issues and docs.

## Sessions and Turns

**Session**
A durable record of one learner working toward one goal with an agent: its message history, settings, optional Plan and usage. Every Session is either a Teaching Session or a Practice Session. Upstream code stores a Session as a `ChatRecord`.

**Teaching Session**
A Session that measures, plans and teaches toward a goal the learner states. It starts with the Prober and hands off to the Teacher.

**Practice Session**
A Session in which the Practitioner poses problems that need knowledge the learner already has, without naming it. Its outcomes are Session evidence, not ratings stored in the graph.

**Turn**
One unit of agent work inside a Session. A Turn builds a fresh agent from the saved history, may make several model calls and run tools, and settles by saving its output (including partial output on abort). Learner input is saved before inference. A Session runs at most one Turn at a time. A Turn is not an HTTP request or a single model generation.

## Agents

**Prober**
The agent that opens a Teaching Session. It asks probe and recall questions to find where the learner's understanding ends on each part the goal depends on, then hands off with `probe_done`.

**Teacher**
The agent that takes over from the Prober. It builds the Plan with `plan_init` and teaches one reasoning step at a time through `explain` and `quiz`.

**Practitioner**
The agent of a Practice Session. It commits one problem at a time with `problem` and records the outcome, revealing the solution, with `conclude`.

**Plan**
The Teacher's lesson map: a validated dependency graph (DAG) from the learner's starting point to the goal. Current and visited nodes come from the Teacher's tagged calls. A new `plan_init` can replace it.

## Knowledge graph

**Proposition**
One objective claim extracted from what the learner was taught, with links back to the Sessions it came from. The graph records Propositions, not the learner's level of understanding.

**Reasoning Chain**
A set of premise Propositions that jointly entail a conclusion Proposition.

## Background work

**Job**
Background work an agent dispatches from a Turn (web research, reading a PDF, generating a visual or an interactive demo). A Job is persisted, runs independently of the Turn, and its result is delivered into the Session exactly once.

## Avoided terms

- **Chat**, **Conversation**: say **Session**. (Upstream code still has `ChatRecord` and `/api/chats`; those are identifiers, not domain language.)
