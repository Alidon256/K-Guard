# Declarations

Everything used to build K-Guard that the build plan (`plan.md`, section 2) asks us to declare.

## AI tools

| Tool | Version | Used for |
|---|---|---|
| Claude Code (Anthropic) | Claude Sonnet 5.5 | Coding agent. Wrote and reviewed code in this repository under Able's direction, including `vpn/QueryMonitor.kt`, `vpn/QueryStats.kt`, the Today card, and their tests. |

## Libraries, models and lists

DNS query monitoring (branch `feature/dns-query-monitoring`) added **no** new libraries, models or domain lists.
It uses the Kotlin standard library, kotlinx-coroutines and the AndroidX/Compose libraries the app already had.
The existing dependencies and the starter lists in `app/src/main/assets/blocklists/` still need their versions and licences listed here.
