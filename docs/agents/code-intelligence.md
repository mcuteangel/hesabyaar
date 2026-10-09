# Code Intelligence Tools (agent guide)

> Part of the agent guide. Start at `AGENTS.md`; it says when to read this file.

## Code Intelligence: Graphify

Graphify builds a persistent knowledge graph of the codebase. It maps code structure (AST), documents, and images into a queryable graph with community detection.

### When to use it

- Always use Graphify when finding connections, references, callers, or dependencies between components.
- Before answering "why does X connect to Y?", "what calls Z?", or "trace the data flow".
- When exploring cross-cutting concerns across multiple modules.
- When a question requires understanding relationships that span Rust, Kotlin, FFI, and UI layers.

### Quick reference

```bash
# Ask a question about the codebase
graphify query "Why does AccountEntity connect 40+ communities?"

# Find shortest path between two concepts
graphify path "AccountEntity" "RustBridge" --undirected

# Plain-language explanation of a node
graphify explain "AccountEntity"

# Incremental update (after code changes)
graphify update .
```

### Setup

- **Install once (fresh clone):**
  ```bash
  python -m pip install graphifyy  # install CLI (or: uv tool install graphifyy)
  graphify hook install            # post-commit + post-checkout hooks
  graphify .                       # build initial graph (takes ~30s)
  ```
- **On code changes:** hooks run `graphify update .` automatically.
- **On doc/image changes:** run `graphify update .` manually.

### Rules

- Use `graphify query` to find connections, references, callers, dependencies, and cross-cutting relationships. Use Serena `find_symbol`/`find_referencing_symbols` for live symbol lookup. Neither replaces reading code.
- Treat community boundaries as hypotheses — verify against actual code dependencies before acting on them.
- God nodes (highest degree) are central abstractions. Changes to them affect many modules.

## Code Intelligence: Serena

Serena is the symbol-level code-intelligence backend for this repo. It provides live AST symbol lookups, call hierarchy tracing, and declaration finding without rebuilding any index. Use Graphify for broad architectural and cross-community graph queries, and Serena for concrete symbol and reference lookups. Do not add another indexing server.

### Project setup

- The serena MCP server is active on this machine. The project is registered and activated (read `mem:core` first; it links to the others).
- Project memories (5): `mem:core` (map + invariants), `mem:tech_stack`, `mem:suggested_commands`, `mem:conventions`, `mem:task_completion`.
- Author new project knowledge with `write_memory`. Follow the rules in the memory `memory_maintenance`.
- If a memory mention seems stale, run `serena memories check` from the project root.

### Usage pointers

- Use `get_symbols_overview` to orient on a file, `find_symbol` to locate a symbol, `find_referencing_symbols` to trace callers, `find_declaration` for a definition.
- Line numbers returned by serena are 0-based.
- Do not bypass serena with bulk reads when a symbolic tool answers the question.
