"""Build the benchmark essays (samples/benchmark/*.json) from the source folders.

Sources, all under samples/benchmark/ (git-ignored):
    fotos/rNN.txt       transcription of the photo rNN.<ext>, paragraphs separated by a blank line
    fotos/notas.csv     arquivo;tema;c1;c2;c3;c4;c5 with the official ENEM scores of each photo
    nota1000/mNN.txt    first line "TEMA: <theme>", then the essay; every competency is 200

Output: one {"theme", "text", "human"} JSON per essay, read by experiments/benchmark.py.

Usage:
    python experiments/build_benchmark.py
"""

import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BENCHMARK_DIR = ROOT / "samples" / "benchmark"
PHOTOS_DIR = BENCHMARK_DIR / "fotos"
NOTA1000_DIR = BENCHMARK_DIR / "nota1000"
THEME_PREFIX = "TEMA:"


def write_essay(essay_id, theme, text, human):
    essay = {"theme": theme, "text": text.strip(), "human": human}
    (BENCHMARK_DIR / f"{essay_id}.json").write_text(json.dumps(essay, ensure_ascii=False, indent=2), encoding="utf-8")


def build_photos():
    count = 0
    with open(PHOTOS_DIR / "notas.csv", encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f, delimiter=";"):
            essay_id = row["arquivo"]
            transcription = PHOTOS_DIR / f"{essay_id}.txt"
            if not transcription.exists():
                print(f"{essay_id}: no transcription yet, skipped")
                continue
            human = [int(row[f"c{c}"]) for c in range(1, 6)]
            write_essay(essay_id, row["tema"], transcription.read_text(encoding="utf-8"), human)
            count += 1
    return count


def build_nota1000():
    count = 0
    for path in sorted(NOTA1000_DIR.glob("*.txt")):
        first_line, _, text = path.read_text(encoding="utf-8").partition("\n")
        if not first_line.startswith(THEME_PREFIX) or not text.strip():
            print(f"{path.stem}: missing '{THEME_PREFIX}' line or text, skipped")
            continue
        write_essay(path.stem, first_line[len(THEME_PREFIX):].strip(), text, [200] * 5)
        count += 1
    return count


def main():
    photos = build_photos()
    nota1000 = build_nota1000()
    print(f"built {photos} graded photos + {nota1000} nota-1000 essays in {BENCHMARK_DIR.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
