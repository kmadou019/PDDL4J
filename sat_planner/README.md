# SAT Planner

A SAT-based planner built on top of [PDDL4J](https://github.com/pellierd/pddl4j) that encodes planning problems as satisfiability problems and solves them using the [SAT4J](https://www.sat4j.org) library.

## Overview

The planner encodes a bounded planning problem of length `n` into a propositional formula in CNF and delegates resolution to SAT4J. It uses **iterative deepening** — it tries step bounds from 1 upward until a plan is found or the maximum bound is reached.

### Encoding

For a horizon of `n` steps, the encoding includes:

- **Initial state** (step 0): unit clauses forcing each fluent to its initial truth value
- **Goal state** (step n): unit clauses for each goal fluent
- **Action implications**: for each action `a` at step `t`, clauses encoding preconditions and effects
- **At-most-one**: mutex clauses preventing two actions from being executed at the same step
- **Frame axioms**: explanatory frame axioms ensuring fluent changes are justified by an action

### Variable numbering

| Variable type | Formula |
|---|---|
| Fluent `f` at step `t` | `t * nbFluents + f + 1` |
| Action `a` at step `t` | `(n+1) * nbFluents + t * nbActions + a + 1` |

## Project structure

```
sat_planner/
├── src/           — Java source (SatPlanner.java)
├── lib/           — Dependencies (pddl4j-4.0.0.jar, sat4j jars)
├── classes/       — Compiled output
├── test/          — PDDL benchmark problems (IPC 1998–2014)
├── benchmark.py   — Benchmark script (SAT vs HSP comparison)
└── results.pdf    — Benchmark results figures
```

## Dependencies

- Java 11+
- [PDDL4J 4.0.0](https://github.com/pellierd/pddl4j)
- [SAT4J Core 3.0.0](https://gitlab.ow2.org/sat4j/sat4j)

## Build

```bash
javac -cp "lib/*" -d classes src/fr/uga/pddl4j/examples/satplanner/SatPlanner.java
```

## Usage

```bash
java -cp "classes:lib/*" fr.uga.pddl4j.examples.satplanner.SatPlanner <domain.pddl> <problem.pddl>
```

Example:

```bash
java -cp "classes:lib/*" fr.uga.pddl4j.examples.satplanner.SatPlanner \
  test/benchmarks/pddl/ipc1998/gripper/strips/domain.pddl \
  test/benchmarks/pddl/ipc1998/gripper/strips/p01.pddl
```

## Benchmark

The script `benchmark.py` compares the SAT planner against HSP (PDDL4J's A* planner) on four domains: **blocks**, **gripper**, **depot**, and **logistics**.

Two metrics are measured: **total runtime** and **makespan** (plan length). Problems are ordered on the X axis from simplest to hardest for HSP.

### Requirements

```bash
sudo apt-get install python3-matplotlib
```

### Run

```bash
python3 benchmark.py
```

Outputs 8 PNG figures and a `results.pdf` combining all of them.

### Results

See `results.pdf` for the full comparison figures.
