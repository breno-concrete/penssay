"""Check that slow corrections do not block the server (specs/004-launch-protection, CS-005).

Starts a fake OpenAI-compatible provider that takes 12 s to answer, starts the packaged app pointing at it with the
limits off, fires 300 corrections at once and times light requests while they wait. No real essay and no paid call:
the corrections send placeholder words and the fake provider answers a fixed grade.

Usage (from the repository root):
    ./mvnw -q package -DskipTests
    python experiments/load_test.py [--corrections 300] [--delay 12]
"""

import argparse
import json
import os
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP_PORT = 8097
PROVIDER_PORT = 8099
LIGHT_REQUEST_LIMIT_SECONDS = 2.0
ANSWER = {"justification": "Resposta fixa do provedor falso.", "score": 120, "tips": ["dica 1", "dica 2"]}


def fake_provider(delay):
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            self.rfile.read(int(self.headers.get("Content-Length", 0)))
            time.sleep(delay)
            body = json.dumps({
                "id": "fake", "object": "chat.completion", "created": int(time.time()), "model": "fake",
                "choices": [{"index": 0, "finish_reason": "stop",
                             "message": {"role": "assistant", "content": json.dumps(ANSWER)}}],
                "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
            }).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    ThreadingHTTPServer.request_queue_size = 4096  # 300 corrections make 1,500 provider calls at once
    server = ThreadingHTTPServer(("127.0.0.1", PROVIDER_PORT), Handler)
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def start_app():
    jars = sorted((ROOT / "target").glob("penssay-*.jar"))
    jars = [j for j in jars if not j.name.endswith(".original")]
    if not jars:
        raise SystemExit("No jar in target/; run ./mvnw -q package -DskipTests first")
    env = dict(os.environ, OPENROUTER_API_KEY="fake", PENSSAY_LIMITS_ENABLED="false")
    app = subprocess.Popen(
        ["java", "-jar", str(jars[-1]), f"--server.port={APP_PORT}",
         f"--spring.ai.openai.base-url=http://127.0.0.1:{PROVIDER_PORT}/v1"],
        env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(120):
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{APP_PORT}/", timeout=1)
            return app
        except urllib.error.HTTPError:
            return app  # any HTTP answer (a 404 before the screen exists) means the app is up
        except OSError:
            time.sleep(0.5)
    app.kill()
    raise SystemExit("The app did not start")


def correction():
    body = json.dumps({"theme": "Tema de teste", "text": " ".join(["palavra"] * 80)}).encode()
    request = urllib.request.Request(f"http://127.0.0.1:{APP_PORT}/api/evaluations", data=body,
                                     headers={"Content-Type": "application/json"})
    started = time.time()
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return response.status, time.time() - started
    except urllib.error.HTTPError as e:
        return e.code, time.time() - started
    except OSError as e:
        return type(e).__name__, time.time() - started


def light_request():
    started = time.time()
    try:
        urllib.request.urlopen(f"http://127.0.0.1:{APP_PORT}/", timeout=30)
    except urllib.error.HTTPError:
        pass  # 404 until the screen exists; what matters is how fast the server answers
    return time.time() - started


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--corrections", type=int, default=300)
    parser.add_argument("--delay", type=float, default=12, help="seconds the fake provider takes per call")
    args = parser.parse_args()

    provider = fake_provider(args.delay)
    app = start_app()
    try:
        with ThreadPoolExecutor(max_workers=args.corrections) as pool:
            pending = [pool.submit(correction) for _ in range(args.corrections)]
            time.sleep(2)  # let every correction reach the provider and start waiting
            light = []
            for _ in range(5):
                light.append(light_request())
                time.sleep(1)
            results = [p.result() for p in pending]

        statuses = {}
        for status, _ in results:
            statuses[status] = statuses.get(status, 0) + 1
        slowest = max(seconds for _, seconds in results)
        print(f"{args.corrections} corrections, provider delay {args.delay} s: statuses {statuses}, "
              f"slowest {slowest:.1f} s")
        print("light requests during the wait: " + ", ".join(f"{s:.2f} s" for s in light))
        ok = max(light) <= LIGHT_REQUEST_LIMIT_SECONDS and statuses.get(200, 0) == args.corrections
        print("PASS" if ok else "FAIL", f"(light request limit {LIGHT_REQUEST_LIMIT_SECONDS} s, all corrections 200)")
        sys.exit(0 if ok else 1)
    finally:
        app.terminate()
        provider.shutdown()


if __name__ == "__main__":
    main()
