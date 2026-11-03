"""Grade every essay in samples/benchmark/ through the Penssay API and compare with human scores.

Each input file is JSON: {"theme": "...", "text": "...", "human": [c1, c2, c3, c4, c5]}.
Prints only numbers (never essay text) and saves the per-essay scores under samples/, which is git-ignored.

Usage (with the app running on localhost:8080):
    python experiments/benchmark.py [--runs 1] [--label gemma3-4b]
"""

import argparse
import json
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BENCHMARK_DIR = ROOT / "samples" / "benchmark"
RESULTS_DIR = ROOT / "samples" / "benchmark-results"
API_URL = "http://localhost:8080/api/evaluations"
TIMEOUT_SECONDS = 600


def grade(theme, text):
    body = json.dumps({"theme": theme, "text": text}).encode("utf-8")
    request = urllib.request.Request(API_URL, data=body, headers={"Content-Type": "application/json; charset=utf-8"})
    with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
        result = json.load(response)
    return [c["score"] for c in result["competencies"]]


def mean(values):
    return sum(values) / len(values) if values else float("nan")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", type=int, default=1, help="times each essay is graded")
    parser.add_argument("--label", default="run", help="name saved with the results, e.g. the model")
    args = parser.parse_args()

    files = sorted(BENCHMARK_DIR.glob("*.json"))
    if not files:
        raise SystemExit(f"No essays in {BENCHMARK_DIR}")

    rows = []  # one per graded essay: id, human, model, seconds
    failures = 0
    for path in files:
        essay_id = path.stem  # neutral code such as r01 or m03, never a name
        essay = json.loads(path.read_text(encoding="utf-8"))
        human = essay["human"]
        for run in range(1, args.runs + 1):
            started = time.time()
            try:
                model = grade(essay["theme"], essay["text"])
            except (urllib.error.URLError, TimeoutError, KeyError) as e:
                failures += 1
                print(f"{essay_id} run {run}: FAILED ({type(e).__name__})")
                continue
            seconds = time.time() - started
            rows.append({"id": essay_id, "run": run, "human": human, "model": model, "seconds": round(seconds, 1)})
            print(f"{essay_id} run {run}: human {sum(human):4d}  model {sum(model):4d}  "
                  f"{model}  ({seconds:.0f} s)")

    if not rows:
        raise SystemExit("Every essay failed; is the app running?")

    # mean absolute error per competency and for the total; bias > 0 means the model grades higher than humans
    summary = {"label": args.label, "essays": len(files), "graded": len(rows), "failures": failures}
    for c in range(5):
        errors = [r["model"][c] - r["human"][c] for r in rows]
        summary[f"c{c + 1}_mae"] = round(mean([abs(e) for e in errors]), 1)
        summary[f"c{c + 1}_bias"] = round(mean(errors), 1)
    total_errors = [sum(r["model"]) - sum(r["human"]) for r in rows]
    summary["total_mae"] = round(mean([abs(e) for e in total_errors]), 1)
    summary["total_bias"] = round(mean(total_errors), 1)
    summary["exact_competency_rate"] = round(
        mean([r["model"][c] == r["human"][c] for r in rows for c in range(5)]), 3)
    summary["within_40_rate"] = round(
        mean([abs(r["model"][c] - r["human"][c]) <= 40 for r in rows for c in range(5)]), 3)
    summary["mean_seconds"] = round(mean([r["seconds"] for r in rows]), 1)

    print()
    print(f"{'':10}{'C1':>8}{'C2':>8}{'C3':>8}{'C4':>8}{'C5':>8}{'Total':>9}")
    print(f"{'MAE':10}" + "".join(f"{summary[f'c{c}_mae']:>8}" for c in range(1, 6)) + f"{summary['total_mae']:>9}")
    print(f"{'bias':10}" + "".join(f"{summary[f'c{c}_bias']:>8}" for c in range(1, 6)) + f"{summary['total_bias']:>9}")
    print(f"exact per competency: {summary['exact_competency_rate']:.0%}   within 40 points: "
          f"{summary['within_40_rate']:.0%}   failures: {failures}   mean time: {summary['mean_seconds']} s")

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    out = RESULTS_DIR / f"{datetime.now():%Y%m%d-%H%M%S}-{args.label}.json"
    out.write_text(json.dumps({"summary": summary, "rows": rows}, indent=2), encoding="utf-8")
    print(f"saved {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
