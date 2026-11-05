"""Merge every public graded-essay dataset we found into one local corpus, then pick a stratified benchmark.

Sources (downloaded on each run, cached by huggingface_hub; nothing is committed):
    lais      laisnuto/self-collected-ENEM-dataset-with-prompts-and-text-support (HF, no license declared)
              157 essays written in the real ENEM, official scores declared by the authors (PROPOR 2026)
    kamelA    kamel-usp/aes_enem_dataset, sourceAWithGraders (HF, Apache-2.0)
              395 essays with the site's score plus two hired graders; the reference is the graders' mean
    kamelB    kamel-usp/aes_enem_dataset, sourceB (HF, Apache-2.0): 3219 essays, site score
    kamel1000 kamel-usp/aes_enem_dataset, gradesThousand (HF, Apache-2.0): 179 nota-1000 essays
    essaybr   lplnufpi/essay-br extended corpus (GitHub, MIT): 6577 essays from Banco de Redações UOL

The other kamel-usp configs (PROPOR2024, JBCS2025, sourceAOnly) repeat the sourceA essays and are skipped.

Output (samples/ is git-ignored):
    samples/corpus/corpus.jsonl   one essay per line: id, source, grade_type, theme, year, text, human, total,
                                  graders (kamelA only), flags
    samples/benchmark-ext/*.json  with --benchmark N: a stratified pick in the format of experiments/benchmark.py

Usage:
    python experiments/build_corpus.py                  # corpus + stats
    python experiments/build_corpus.py --benchmark 100  # also writes samples/benchmark-ext/
    python experiments/benchmark.py --dir samples/benchmark-ext --label gemma-4-31b-ext
"""

from __future__ import annotations

import argparse
import ast
import json
import random
import re
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

import pandas as pd
from huggingface_hub import hf_hub_download

ROOT = Path(__file__).resolve().parent.parent
CORPUS_DIR = ROOT / "samples" / "corpus"
BENCHMARK_EXT_DIR = ROOT / "samples" / "benchmark-ext"
EXISTING_BENCHMARK = ROOT / "samples" / "benchmark"

LAIS = ("laisnuto/self-collected-ENEM-dataset-with-prompts-and-text-support",
        "self_collected_enem_with_support_and_prompt.csv")
KAMEL = "kamel-usp/aes_enem_dataset"
ESSAYBR = "https://raw.githubusercontent.com/lplnufpi/essay-br/main/extended-corpus/"

MIN_WORDS = 70  # below this Penssay zeroes the essay without grading, so it says nothing about the model
THEME_IN_PROMPT = re.compile(r"tema\s*[“\"]([^”\"]+)[”\"]", re.IGNORECASE)
# Essay-BR keeps the site's inline corrections next to the original word ("ex presidente Lula ex-presidente Lula")
REPEATED_WORDS = re.compile(r"\b(\w{4,}(?:\s+\w+){0,2})[\s-]+\1\b", re.IGNORECASE)


def slugify(text: str) -> str:
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def fingerprint(text: str) -> str:
    return slugify(text)[:400]


def split_paragraphs(raw: str) -> list[str]:
    return [p.strip() for p in re.split(r"\n+", raw) if p.strip()]


def drop_title(pars: list[str]) -> list[str]:
    if len(pars) > 1 and len(pars[0]) <= 80 and not pars[0].endswith((".", "!", "?", ":", ";")):
        return pars[1:]
    return pars


def valid_grades(grades: list[int], total: int | None = None) -> bool:
    if len(grades) != 5 or any(g < 0 or g > 200 or g % 20 for g in grades):
        return False
    return total is None or sum(grades) == total


def theme_from_slug(slug: str, titles_by_slug: dict[str, str]) -> str:
    if slug in titles_by_slug:
        return titles_by_slug[slug]
    words = slug.replace("-", " ").strip()
    return words[:1].upper() + words[1:]


def record(source, n, grade_type, theme, year, pars, human, graders=None):
    text = "\n\n".join(pars)
    flags = []
    if len(pars) == 1:
        flags.append("one_paragraph")
    if REPEATED_WORDS.search(text):
        flags.append("repeated_words")
    if sum(human) == 0:
        flags.append("zero")
    words = len(text.split())
    if words < MIN_WORDS:
        flags.append("too_short")
    out = {"id": f"{source}-{n:04d}", "source": source, "grade_type": grade_type, "theme": theme,
           "year": year, "words": words, "text": text, "human": human, "total": sum(human), "flags": flags}
    if graders:
        out["graders"] = graders
    return out


def kamel_split(config: str, splits=("train", "validation", "test")) -> pd.DataFrame:
    return pd.concat([pd.read_parquet(hf_hub_download(KAMEL, f"{config}/{s}-00000-of-00001.parquet",
                                                      repo_type="dataset")) for s in splits])


def load_lais() -> list[dict]:
    df = pd.read_csv(hf_hub_download(*LAIS, repo_type="dataset"))
    out = []
    for i, row in df.iterrows():
        grades = [int(float(g)) for g in ast.literal_eval(str(row["notas"]))]
        if not valid_grades(grades, int(float(row["nota_final"]))):
            continue
        m = THEME_IN_PROMPT.search(str(row["prompt"]))
        if not m:
            continue
        out.append(record("lais", i, "oficial", m.group(1).strip(), int(row["ano"]),
                          drop_title(split_paragraphs(str(row["texto"]))), grades))
    return out


def load_kamel(titles_by_slug: dict[str, str]) -> list[dict]:
    out = []

    a = kamel_split("sourceAWithGraders")
    for n, ((essay_id, slug), group) in enumerate(a.groupby(["id", "id_prompt"], sort=True)):
        by_ref = {r["reference"]: [int(g) for g in r["grades"]] for _, r in group.iterrows()}
        site = by_ref.get("crawled_from_web")
        if not site or not valid_grades(site[:5], site[5]):
            continue
        graders = {"site": site[:5]}
        for ref, key in (("grader_a", "a"), ("grader_b", "b")):
            if ref in by_ref and valid_grades(by_ref[ref][:5], by_ref[ref][5]):
                graders[key] = by_ref[ref][:5]
        first = group.iloc[0]
        year = int(first["essay_year"]) if int(first["essay_year"]) > 0 else None
        if "a" in graders and "b" in graders:
            # the site's score is ~200 points away from either grader, while the graders differ by ~96:
            # use their mean, which is also how ENEM combines its two graders (always a multiple of 20)
            human = [(x + y) // 2 for x, y in zip(graders["a"], graders["b"])]
            grade_type = "corretores"
        else:
            human, grade_type = site[:5], "plataforma"
        out.append(record("kamelA", n, grade_type, theme_from_slug(slug, titles_by_slug), year,
                          split_paragraphs(str(first["essay_text"])), human, graders))

    b = kamel_split("sourceB", ("full",))
    for n, row in enumerate(b.itertuples()):
        g = [int(x) for x in row.grades]
        if not valid_grades(g[:5], g[5]):
            continue
        year = int(row.essay_year) if int(row.essay_year) > 0 else None
        out.append(record("kamelB", n, "plataforma", theme_from_slug(row.id_prompt, titles_by_slug), year,
                          split_paragraphs(str(row.essay_text)), g[:5]))

    t = kamel_split("gradesThousand")
    for n, row in enumerate(t.itertuples()):
        text = str(row.essay_text).strip().strip('"')
        out.append(record("kamel1000", n, "nota1000", str(row.id_prompt).strip(),
                          int(row.essay_year) if int(row.essay_year) > 0 else None,
                          drop_title(split_paragraphs(text)), [200] * 5))
    return out


def load_essaybr() -> tuple[list[dict], dict[str, str]]:
    prompts = pd.read_csv(ESSAYBR + "prompts.csv")
    titles = {int(r.id): str(r.title).strip() for r in prompts.itertuples()}
    df = pd.read_csv(ESSAYBR + "extended_essay-br.csv")
    out = []
    for n, row in enumerate(df.itertuples()):
        grades = [int(row.c1), int(row.c2), int(row.c3), int(row.c4), int(row.c5)]
        if not valid_grades(grades, int(row.score)) or int(row.prompt) not in titles:
            continue
        pars = [p.strip() for p in ast.literal_eval(row.essay) if p.strip()]
        out.append(record("essaybr", n, "plataforma", titles[int(row.prompt)], None, pars, grades))
    return out, {slugify(t): t for t in titles.values()}


def existing_benchmark_fingerprints() -> set[str]:
    if not EXISTING_BENCHMARK.exists():
        return set()
    return {fingerprint(json.loads(p.read_text(encoding="utf-8"))["text"]) for p in EXISTING_BENCHMARK.glob("*.json")}


def dedupe(essays: list[dict]) -> tuple[list[dict], int]:
    # earlier sources win: official scores first, then the essays that also have hired graders
    priority = {"lais": 0, "kamel1000": 1, "kamelA": 2, "kamelB": 3, "essaybr": 4}
    seen, kept = set(), []
    for e in sorted(essays, key=lambda e: priority[e["source"]]):
        fp = fingerprint(e["text"])
        if fp in seen:
            continue
        seen.add(fp)
        kept.append(e)
    return kept, len(essays) - len(kept)


def band(total: int) -> str:
    return "0-399" if total < 400 else "400-599" if total < 600 else "600-799" if total < 800 else "800+"


def mae(pairs):
    return sum(abs(x - y) for x, y in pairs) / len(pairs) if pairs else float("nan")


def print_stats(essays: list[dict], duplicates: int) -> None:
    print(f"\n{len(essays)} essays after removing {duplicates} duplicates")
    by_source = defaultdict(Counter)
    for e in essays:
        by_source[e["source"]][band(e["total"])] += 1
    print(f"{'source':<11}{'total':>7}" + "".join(f"{b:>9}" for b in ("0-399", "400-599", "600-799", "800+")))
    for s, c in by_source.items():
        print(f"{s:<11}{sum(c.values()):>7}" + "".join(f"{c[b]:>9}" for b in ("0-399", "400-599", "600-799", "800+")))
    flags = Counter(f for e in essays for f in e["flags"])
    print("flags:", dict(flags))
    print("themes:", len({e["theme"] for e in essays}))

    # how much two humans disagree on the same essay: the floor for any automatic grader
    both = [e["graders"] for e in essays if "a" in e.get("graders", {}) and "b" in e["graders"]]
    if both:
        print(f"\nhuman vs human on {len(both)} kamelA essays (mean absolute difference):")
        for x, y in (("a", "b"), ("site", "a"), ("site", "b")):
            per_c = [mae([(g[x][c], g[y][c]) for g in both]) for c in range(5)]
            total = mae([(sum(g[x]), sum(g[y])) for g in both])
            print(f"  {x:>4} vs {y:<4}" + "".join(f"  C{c + 1} {v:5.1f}" for c, v in enumerate(per_c))
                  + f"   total {total:6.1f}")


def pick_benchmark(essays: list[dict], n: int, seed: int) -> list[dict]:
    """Stratify by source and score band so low and mid scores, which the old benchmark lacks, are well covered."""
    taken = existing_benchmark_fingerprints()
    usable = [e for e in essays if not e["flags"] and fingerprint(e["text"]) not in taken
              and e["grade_type"] in ("oficial", "corretores", "plataforma")]
    # site-only scores are too noisy to be a reference (see the human vs human stats), so they stay a small
    # group reported apart; official scores and the mean of two hired graders carry the benchmark
    shares = {"lais": 0.45, "kamelA": 0.45, "kamelB": 0.05, "essaybr": 0.05}
    rng = random.Random(seed)
    picked = []
    for source, share in shares.items():
        pool = defaultdict(list)
        for e in usable:
            if e["source"] == source and (source != "kamelA" or e["grade_type"] == "corretores"):
                pool[band(e["total"])].append(e)
        for b in pool.values():
            rng.shuffle(b)
        quota = round(n * share)
        bands = [b for b in ("0-399", "400-599", "600-799", "800+") if pool[b]]
        while quota > 0 and any(pool[b] for b in bands):
            for b in bands:  # round-robin over bands keeps the pick spread across the score range
                if quota and pool[b]:
                    picked.append(pool[b].pop())
                    quota -= 1
    return picked


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--benchmark", type=int, default=0, help="also write N stratified essays to benchmark-ext")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    essaybr, titles_by_slug = load_essaybr()
    essays = load_lais() + load_kamel(titles_by_slug) + essaybr
    essays, duplicates = dedupe(essays)

    CORPUS_DIR.mkdir(parents=True, exist_ok=True)
    with (CORPUS_DIR / "corpus.jsonl").open("w", encoding="utf-8") as f:
        for e in essays:
            f.write(json.dumps(e, ensure_ascii=False) + "\n")
    print_stats(essays, duplicates)
    print(f"\nsaved {(CORPUS_DIR / 'corpus.jsonl').relative_to(ROOT)}")

    if args.benchmark:
        BENCHMARK_EXT_DIR.mkdir(parents=True, exist_ok=True)
        for old in BENCHMARK_EXT_DIR.glob("*.json"):
            old.unlink()
        picked = pick_benchmark(essays, args.benchmark, args.seed)
        for e in picked:
            out = {"theme": e["theme"], "text": e["text"], "human": e["human"],
                   "source": e["source"], "grade_type": e["grade_type"]}
            if "graders" in e:
                out["graders"] = e["graders"]
            (BENCHMARK_EXT_DIR / f"{e['id']}.json").write_text(json.dumps(out, ensure_ascii=False, indent=2),
                                                               encoding="utf-8")
        bands = Counter(band(e["total"]) for e in picked)
        sources = Counter(e["source"] for e in picked)
        print(f"benchmark-ext: {len(picked)} essays, by source {dict(sources)}, by band {dict(sorted(bands.items()))}")


if __name__ == "__main__":
    main()
