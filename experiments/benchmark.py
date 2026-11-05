"""Grade every essay in samples/benchmark/ through the Penssay API and compare with human scores.

Each input file is JSON: {"theme": "...", "text": "...", "human": [c1, c2, c3, c4, c5]}.
Prints only numbers (never essay text) and saves the per-essay scores under samples/, which is git-ignored.

Usage (with the app running on localhost:8080 and its limits off, or the per-client limit stops the run:
PENSSAY_LIMITS_ENABLED=false ./mvnw spring-boot:run):
    python experiments/benchmark.py [--runs 1] [--label gemma3-4b] [--url http://localhost:8081/api/evaluations]
                                    [--dir samples/benchmark-ext]

Essays may carry a "grade_type" (oficial, corretores, plataforma; see build_corpus.py): the total error is then
also printed per type, because a site's score is a much noisier reference than an official one.
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


def grade(theme, text, api_url=API_URL):
    body = json.dumps({"theme": theme, "text": text}).encode("utf-8")
    request = urllib.request.Request(api_url, data=body, headers={"Content-Type": "application/json; charset=utf-8"})
    with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
        result = json.load(response)
    return [c["score"] for c in result["competencies"]]


def mean(values):
    return sum(values) / len(values) if values else float("nan")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", type=int, default=1, help="times each essay is graded")
    parser.add_argument("--label", default="run", help="name saved with the results, e.g. the model")
    parser.add_argument("--url", default=API_URL, help="evaluation endpoint, to run several apps on other ports")
    parser.add_argument("--dir", type=Path, default=BENCHMARK_DIR, help="folder with the essay JSON files")
    args = parser.parse_args()

    essays_dir = args.dir if args.dir.is_absolute() else ROOT / args.dir
    files = sorted(essays_dir.glob("*.json"))
    if not files:
        raise SystemExit(f"No essays in {essays_dir}")

    rows = []  # one per graded essay: id, human, model, seconds
    failures = 0
    for path in files:
        essay_id = path.stem  # neutral code such as r01 or m03, never a name
        essay = json.loads(path.read_text(encoding="utf-8"))
        human = essay["human"]
        for run in range(1, args.runs + 1):
            started = time.time()
            try:
                model = grade(essay["theme"], essay["text"], args.url)
            except (urllib.error.URLError, TimeoutError, KeyError) as e:
                failures += 1
                print(f"{essay_id} run {run}: FAILED ({type(e).__name__})")
                continue
            seconds = time.time() - started
            rows.append({"id": essay_id, "run": run, "human": human, "model": model, "seconds": round(seconds, 1),
                         "grade_type": essay.get("grade_type")})
            print(f"{essay_id} run {run}: human {sum(human):4d}  model {sum(model):4d}  "
                  f"{model}  ({seconds:.0f} s)")

    if not rows:
        raise SystemExit("Every essay failed; is the app running?")

    # mean absolute error per competency and for the total; bias > 0 means the model grades higher than humans
    summary = {"label": args.label, "dir": essays_dir.relative_to(ROOT).as_posix(), "essays": len(files),
               "graded": len(rows), "failures": failures}
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
    by_type = {}
    for grade_type in sorted({r["grade_type"] for r in rows if r["grade_type"]}):
        group = [sum(r["model"]) - sum(r["human"]) for r in rows if r["grade_type"] == grade_type]
        by_type[grade_type] = {"graded": len(group), "total_mae": round(mean([abs(e) for e in group]), 1),
                               "total_bias": round(mean(group), 1)}
    if by_type:
        summary["by_grade_type"] = by_type

    print()
    print(f"{'':10}{'C1':>8}{'C2':>8}{'C3':>8}{'C4':>8}{'C5':>8}{'Total':>9}")
    print(f"{'MAE':10}" + "".join(f"{summary[f'c{c}_mae']:>8}" for c in range(1, 6)) + f"{summary['total_mae']:>9}")
    print(f"{'bias':10}" + "".join(f"{summary[f'c{c}_bias']:>8}" for c in range(1, 6)) + f"{summary['total_bias']:>9}")
    print(f"exact per competency: {summary['exact_competency_rate']:.0%}   within 40 points: "
          f"{summary['within_40_rate']:.0%}   failures: {failures}   mean time: {summary['mean_seconds']} s")
    for grade_type, g in by_type.items():
        print(f"  {grade_type:<12} {g['graded']:3d} essays   total MAE {g['total_mae']:6.1f}   bias {g['total_bias']:6.1f}")

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    out = RESULTS_DIR / f"{datetime.now():%Y%m%d-%H%M%S}-{args.label}.json"
    out.write_text(json.dumps({"summary": summary, "rows": rows}, indent=2), encoding="utf-8")
    print(f"saved {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
