"""Transcribe every benchmark photo through the Penssay API and compare with the hand-made transcription.

Inputs, under samples/benchmark/fotos/ (git-ignored):
    rNN.<jpg|jpeg|png>   photo of the handwritten essay (files ending in -c are the official score sheet, skipped)
    rNN.txt              correct transcription of that photo

Metrics (lower is better), computed after collapsing whitespace:
    CER  character edits needed to turn the model's text into the reference, divided by the reference length
    WER  the same with words; a single wrong accent already costs a whole word

Prints only numbers (never essay text) and saves the per-photo results under samples/benchmark-results/.

Usage (with the app running on localhost:8080):
    python experiments/transcription_benchmark.py [--label gemma-3-27b] [--url http://localhost:8081/api/transcriptions]
"""

import argparse
import json
import mimetypes
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PHOTOS_DIR = ROOT / "samples" / "benchmark" / "fotos"
RESULTS_DIR = ROOT / "samples" / "benchmark-results"
API_URL = "http://localhost:8080/api/transcriptions"
TIMEOUT_SECONDS = 600
PHOTO_EXTENSIONS = {".jpg", ".jpeg", ".png"}


def transcribe(photo, api_url):
    boundary = uuid.uuid4().hex
    content_type = mimetypes.guess_type(photo.name)[0] or "image/jpeg"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="photo"; filename="{photo.name}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode("utf-8") + photo.read_bytes() + f"\r\n--{boundary}--\r\n".encode("utf-8")
    request = urllib.request.Request(
        api_url, data=body, headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
        return json.load(response)["text"]


def edit_distance(a, b):
    """Levenshtein distance between two sequences (strings or word lists), one row at a time."""
    previous = list(range(len(b) + 1))
    for i, item_a in enumerate(a, start=1):
        current = [i]
        for j, item_b in enumerate(b, start=1):
            current.append(min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (item_a != item_b)))
        previous = current
    return previous[-1]


def normalize(text):
    return " ".join(text.split())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", default="run", help="name saved with the results, e.g. the model")
    parser.add_argument("--url", default=API_URL, help="transcription endpoint, to run a second app on another port")
    args = parser.parse_args()

    photos = sorted(p for p in PHOTOS_DIR.iterdir()
                    if p.suffix.lower() in PHOTO_EXTENSIONS and not p.stem.endswith("-c")
                    and (PHOTOS_DIR / f"{p.stem}.txt").exists())
    if not photos:
        raise SystemExit(f"No photo with a matching .txt in {PHOTOS_DIR}")

    rows = []
    failures = 0
    for photo in photos:
        reference = normalize((PHOTOS_DIR / f"{photo.stem}.txt").read_text(encoding="utf-8"))
        started = time.time()
        try:
            hypothesis = normalize(transcribe(photo, args.url))
        except (urllib.error.URLError, TimeoutError, KeyError) as e:
            failures += 1
            print(f"{photo.stem}: FAILED ({type(e).__name__})")
            continue
        seconds = time.time() - started
        cer = edit_distance(hypothesis, reference) / len(reference)
        wer = edit_distance(hypothesis.split(), reference.split()) / len(reference.split())
        rows.append({"id": photo.stem, "cer": round(cer, 4), "wer": round(wer, 4),
                     "reference_words": len(reference.split()), "model_words": len(hypothesis.split()),
                     "seconds": round(seconds, 1)})
        print(f"{photo.stem}: CER {cer:6.1%}  WER {wer:6.1%}  words {len(hypothesis.split())}/{len(reference.split())}"
              f"  ({seconds:.0f} s)")

    if not rows:
        raise SystemExit("Every photo failed; is the app running?")

    summary = {
        "label": args.label,
        "photos": len(photos),
        "transcribed": len(rows),
        "failures": failures,
        "mean_cer": round(sum(r["cer"] for r in rows) / len(rows), 4),
        "mean_wer": round(sum(r["wer"] for r in rows) / len(rows), 4),
        "mean_seconds": round(sum(r["seconds"] for r in rows) / len(rows), 1),
    }
    print(f"\nmean CER {summary['mean_cer']:.1%}  mean WER {summary['mean_wer']:.1%}  "
          f"failures {failures}  mean {summary['mean_seconds']} s")

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    out = RESULTS_DIR / f"{stamp}-transcription-{args.label}.json"
    out.write_text(json.dumps({"summary": summary, "rows": rows}, indent=2), encoding="utf-8")
    print(f"saved {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
