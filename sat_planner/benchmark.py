#!/usr/bin/env python3
"""
Benchmark script: compare SatPlanner vs HSP on 4 PDDL domains.
Metrics: total runtime and makespan (plan length).
Produces 8 figures saved as PNG and combined in results.pdf.
"""

import subprocess
import re
import os
import time
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages

# ── Configuration ────────────────────────────────────────────────────────────

BASE = os.path.dirname(os.path.abspath(__file__))
BENCHMARK_BASE = os.path.join(BASE, "test", "benchmarks", "pddl")

DOMAINS = {
    "blocks": {
        "path": os.path.join(BENCHMARK_BASE, "ipc2000", "blocks", "strips-typed"),
        "problems": [f"p{i:03d}.pddl" for i in range(1, 11)],
    },
    "gripper": {
        "path": os.path.join(BENCHMARK_BASE, "ipc1998", "gripper", "strips"),
        "problems": [f"p{i:02d}.pddl" for i in range(1, 11)],
    },
    "depot": {
        "path": os.path.join(BENCHMARK_BASE, "ipc2002", "depots", "strips-automatic"),
        "problems": [f"p{i:02d}.pddl" for i in range(1, 11)],
    },
    "logistics": {
        "path": os.path.join(BENCHMARK_BASE, "ipc2000", "logistics", "strips-typed"),
        "problems": [f"p{i:02d}.pddl" for i in range(1, 11)],
    },
}

TIMEOUT = 300  # seconds per run
CLASSPATH_HSP = os.path.join(BASE, "lib", "pddl4j-4.0.0.jar")
CLASSPATH_SAT = f"{os.path.join(BASE, 'classes')}:{os.path.join(BASE, 'lib', '*')}"

# ── Compile SatPlanner ────────────────────────────────────────────────────────

def compile_sat_planner():
    src = os.path.join(BASE, "src", "fr", "uga", "pddl4j", "examples", "satplanner", "SatPlanner.java")
    cp  = os.path.join(BASE, "lib", "*")
    out = os.path.join(BASE, "classes")
    print("Compiling SatPlanner...")
    r = subprocess.run(["javac", "-cp", cp, "-d", out, src], capture_output=True, text=True)
    if r.returncode != 0:
        print("Compilation failed:\n", r.stderr)
        raise SystemExit(1)
    print("Compilation OK.")

# ── Run a planner ─────────────────────────────────────────────────────────────

def run_planner(cmd, timeout=TIMEOUT):
    """Return (runtime_seconds, makespan) or (None, None) on timeout/failure."""
    try:
        start = time.time()
        result = subprocess.run(
            cmd, capture_output=True, text=True, timeout=timeout,
            cwd=BASE
        )
        elapsed = time.time() - start
        output = result.stdout + result.stderr

        # Parse runtime from PDDL4J output
        m = re.search(r"(\d+[,\.]\d+) seconds total time", output)
        runtime = float(m.group(1).replace(",", ".")) if m else elapsed

        # Parse makespan: count plan action lines  "N: (action ...)"
        makespan = len(re.findall(r"^\d+: \(", output, re.MULTILINE))

        if makespan == 0:
            return (runtime, None)  # no plan found
        return (runtime, makespan)

    except subprocess.TimeoutExpired:
        return (None, None)
    except Exception as e:
        print(f"  Error: {e}")
        return (None, None)

def run_hsp(domain_file, problem_file):
    cmd = [
        "java", "-cp", CLASSPATH_HSP,
        "fr.uga.pddl4j.planners.statespace.HSP",
        domain_file, problem_file
    ]
    return run_planner(cmd)

def run_sat(domain_file, problem_file):
    cmd = [
        "java", "-cp", CLASSPATH_SAT,
        "fr.uga.pddl4j.examples.satplanner.SatPlanner",
        domain_file, problem_file
    ]
    return run_planner(cmd)

# ── Benchmark one domain ──────────────────────────────────────────────────────

def benchmark_domain(name, config):
    path     = config["path"]
    problems = config["problems"]
    domain   = os.path.join(path, "domain.pddl")

    print(f"\n=== {name.upper()} ===")
    hsp_results = []
    sat_results = []

    for pb in problems:
        pfile = os.path.join(path, pb)
        if not os.path.exists(pfile):
            print(f"  {pb} not found, skipping")
            hsp_results.append((None, None))
            sat_results.append((None, None))
            continue

        print(f"  {pb} — HSP...", end=" ", flush=True)
        h = run_hsp(domain, pfile)
        print(f"runtime={h[0]}, makespan={h[1]}  |  SAT...", end=" ", flush=True)
        s = run_sat(domain, pfile)
        print(f"runtime={s[0]}, makespan={s[1]}")

        hsp_results.append(h)
        sat_results.append(s)

    return problems, hsp_results, sat_results

# ── Plot ──────────────────────────────────────────────────────────────────────

def plot_metric(ax, labels, hsp_vals, sat_vals, title, ylabel):
    x = list(range(len(labels)))

    def split(vals):
        xs, ys = [], []
        for i, v in enumerate(vals):
            if v is not None:
                xs.append(i)
                ys.append(v)
        return xs, ys

    hx, hy = split(hsp_vals)
    sx, sy = split(sat_vals)

    ax.plot(hx, hy, "o-", color="#1f77b4", label="HSP", linewidth=2, markersize=6)
    ax.plot(sx, sy, "s--", color="#ff7f0e", label="SatPlanner", linewidth=2, markersize=6)

    # Mark timeouts
    for i, v in enumerate(hsp_vals):
        if v is None:
            ax.axvline(x=i, color="#1f77b4", linestyle=":", alpha=0.4)
    for i, v in enumerate(sat_vals):
        if v is None:
            ax.axvline(x=i, color="#ff7f0e", linestyle=":", alpha=0.4)

    ax.set_xticks(x)
    ax.set_xticklabels(labels, rotation=45, ha="right", fontsize=8)
    ax.set_xlabel("Problem (ordered by HSP difficulty)")
    ax.set_ylabel(ylabel)
    ax.set_title(title)
    ax.legend()
    ax.grid(True, alpha=0.3)

# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    compile_sat_planner()

    figures = []  # (fig, filename)

    for domain_name, config in DOMAINS.items():
        problems, hsp_res, sat_res = benchmark_domain(domain_name, config)

        # Sort problems by HSP runtime (None → infinity)
        order = sorted(range(len(problems)), key=lambda i: hsp_res[i][0] if hsp_res[i][0] is not None else float("inf"))

        labels_sorted   = [problems[i] for i in order]
        hsp_rt_sorted   = [hsp_res[i][0] for i in order]
        sat_rt_sorted   = [sat_res[i][0] for i in order]
        hsp_mk_sorted   = [hsp_res[i][1] for i in order]
        sat_mk_sorted   = [sat_res[i][1] for i in order]

        # Figure 1: Runtime
        fig, ax = plt.subplots(figsize=(10, 5))
        plot_metric(ax, labels_sorted, hsp_rt_sorted, sat_rt_sorted,
                    f"{domain_name.capitalize()} — Runtime",
                    "Time (s)")
        plt.tight_layout()
        fname = f"results_{domain_name}_runtime.png"
        fig.savefig(os.path.join(BASE, fname), dpi=150)
        figures.append((fig, fname))
        print(f"  Saved {fname}")

        # Figure 2: Makespan
        fig, ax = plt.subplots(figsize=(10, 5))
        plot_metric(ax, labels_sorted, hsp_mk_sorted, sat_mk_sorted,
                    f"{domain_name.capitalize()} — Makespan",
                    "Plan length (actions)")
        plt.tight_layout()
        fname = f"results_{domain_name}_makespan.png"
        fig.savefig(os.path.join(BASE, fname), dpi=150)
        figures.append((fig, fname))
        print(f"  Saved {fname}")

    # Combine into PDF
    pdf_path = os.path.join(BASE, "results.pdf")
    with PdfPages(pdf_path) as pdf:
        # Title page
        fig_title, ax_title = plt.subplots(figsize=(10, 5))
        ax_title.axis("off")
        ax_title.text(0.5, 0.5, "SAT Planner vs HSP — Benchmark Results",
                      ha="center", va="center", fontsize=18, fontweight="bold")
        pdf.savefig(fig_title)
        plt.close(fig_title)

        for fig, _ in figures:
            pdf.savefig(fig)
            plt.close(fig)

    print(f"\nAll figures saved to {pdf_path}")

if __name__ == "__main__":
    main()
