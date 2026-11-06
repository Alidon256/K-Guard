# Declarations

Everything used to build K-Guard that the build plan (`plan.md`, section 2) asks us to declare.

## AI tools

| Tool | Version | Used for |
|---|---|---|
| Claude Code (Anthropic) | Claude Sonnet 5.5 | Coding agent. Wrote and reviewed code in this repository under Able's direction, including `vpn/QueryMonitor.kt`, `vpn/QueryStats.kt`, the Today card, `vpn/SafeSearch.kt`, the CNAME answer code in `vpn/DnsPacket.kt`, and their tests. |

## Libraries, models and lists

Safe-mode host mappings (`vpn/SafeSearch.kt`): Google `forcesafesearch.google.com`, YouTube `restrict.youtube.com`, Bing `strict.bing.com`, DuckDuckGo `safe.duckduckgo.com`. These are the vendors' documented DNS enforcement hosts, taken from `plan.md` section 5.2. No third-party code or library.

DNS query monitoring and safe modes added **no** new libraries or models. dnsjava, which `plan.md` allows, was not needed: the few DNS records involved are built by hand in `DnsPacket.kt`.
It uses the Kotlin standard library, kotlinx-coroutines and the AndroidX/Compose libraries the app already had.
The existing dependencies and the starter lists in `app/src/main/assets/blocklists/` still need their versions and licences listed here.
