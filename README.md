# NotaMil

**Snap a photo of your handwritten ENEM essay draft and get a score for each of the 5 competencies, plus concrete tips to improve. Everything runs on open-weight models, locally.**

> Built for the [Hacktoberfest Open-Source AI Challenge — Week 2: Side Hustle](https://dev.to/challenges/hacktoberfest-week2-2026-10-12) on DEV.
> Project started on **October 13, 2026**. Any commit after the submission deadline (October 18, 2026, 11:59 PM PDT) is listed in the [Post-deadline changes](#post-deadline-changes) section.

---

## The problem

ENEM is Brazil's national university entrance exam, taken by millions of students every year. The essay (*redação*) is worth up to 1,000 points and is one of the most decisive parts of the final score.

Students practice by writing essays **by hand, on paper**, the same way they will on exam day. Getting feedback is the bottleneck:

- Private essay correction services charge per essay, which makes weekly practice expensive.
- Teachers at prep schools (*cursinhos*) correct hundreds of essays a week and return them days later.
- Generic AI chatbots require typing the whole essay, and they don't follow the official rubric.

**Who pays:** students preparing for ENEM and prep schools that need to correct essays at scale.

## How it works

```
Photo of handwritten draft
        │
        ▼
1. Transcription  ── vision model reads the handwriting, preserving every mistake
        │
        ▼
2. Student review ── transcription shown in an editable text box; uncertain words are highlighted
        │
        ▼
3. Evaluation     ── grading model scores C1–C5 against the official rubric
        │
        ▼
Score per competency (0–200) + total (0–1000) + 2–3 improvement tips per competency
```

The student-review step is deliberate: a handwriting misread must never silently turn into a grading error.

## Why open source

| Concern | With a closed API | With NotaMil |
| --- | --- | --- |
| Cost per essay | Paid per token, twice per essay (vision + grading) | Zero API cost; runs on the user's or the school's own hardware |
| Privacy | Student photos and texts go to a third-party server | The photo never leaves the machine |
| Control | Fixed model behavior, vendor pricing | Swap models, tune the rubric prompt, and fine-tune on Brazilian essays |

## Features

### In scope (MVP)

- Upload or take a photo of a handwritten essay from a phone browser.
- Faithful transcription that keeps spelling, accent, agreement and punctuation errors.
- Words the model is unsure about are marked and highlighted for review.
- Editable transcription before grading.
- Score for each competency (0, 40, 80, 120, 160 or 200), total score, short justification quoting the essay, and 2–3 practical tips per competency.

### Out of scope for this week

- User accounts, history or a database.
- Native mobile app.
- Fine-tuned model in production (planned as an experiment, see [Roadmap](#roadmap)).
- Grading any exam other than ENEM.

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot (latest stable) |
| AI integration | Spring AI with the Ollama starter |
| Model server | Ollama |
| Transcription model | `qwen2.5vl:7b` (chosen over `gemma3:4b` by the transcription test) |
| Grading model | `gemma3:4b` (`gemma3:12b` when hardware allows) |
| Frontend | Single static `index.html` served by Spring Boot, no framework |
| Build | Maven |
| Tests | JUnit 5 + Mockito |
| License | MIT |

The transcription model is a configuration value. If the transcription test picks a different model, only `application.properties` and the table above change.

## Architecture

### Package layout

```
src/main/java/com/notamil/
├── NotaMilApplication.java
├── transcription/
│   ├── TranscriptionController.java
│   ├── TranscriptionService.java
│   └── ImageTiler.java
├── evaluation/
│   ├── EvaluationController.java
│   ├── EvaluationService.java
│   ├── ScoreNormalizer.java
│   ├── CompetencyEvaluation.java   (record)
│   └── EssayEvaluation.java        (record)
└── config/
    └── AiConfig.java

src/main/resources/
├── application.properties
├── prompts/
│   ├── transcription.st
│   └── evaluation.st
└── static/
    └── index.html
```

### API

#### `POST /api/transcriptions`

Request: `multipart/form-data` with a `photo` field (JPEG or PNG, up to 10 MB).

Response `200 OK`:

```json
{
  "text": "A democratização do acesso ao cinema no Brasil [?enfrenta] desafios..."
}
```

Words wrapped in `[?...]` are uncertain readings. `[illegible]` marks a word the model could not read.

#### `POST /api/evaluations`

Request:

```json
{
  "theme": "Desafios para a valorização de comunidades e povos tradicionais no Brasil",
  "text": "Full essay text, already reviewed by the student"
}
```

Response `200 OK`:

```json
{
  "competencies": [
    {
      "number": 1,
      "score": 160,
      "justification": "Few deviations from formal Portuguese, such as \"excessão\" in paragraph 2.",
      "tips": [
        "Review the spelling of words with the -ção / -ssão ending.",
        "Avoid starting sentences with an unstressed object pronoun."
      ]
    }
  ],
  "totalScore": 840
}
```

The `competencies` array always has 5 items, ordered from 1 to 5.

## Domain rules: ENEM essay scoring

These rules are the source of truth for the grading prompt and for `ScoreNormalizer`.

| Competency | What is assessed |
| --- | --- |
| C1 | Command of formal written Portuguese |
| C2 | Understanding the prompt and developing the theme within the argumentative essay structure, using knowledge from different areas |
| C3 | Selecting, relating, organizing and interpreting information and arguments to defend a point of view |
| C4 | Linguistic mechanisms of cohesion used to build the argument |
| C5 | An intervention proposal for the problem that respects human rights, with five elements: agent, action, means, purpose and detailing |

- Each competency scores **0, 40, 80, 120, 160 or 200**. The total ranges from 0 to 1,000.
- The whole essay scores **zero** when it fully escapes the theme, is not an argumentative essay, or has 7 lines or fewer.
- The model is never trusted with arithmetic. `ScoreNormalizer` rounds each score to the nearest multiple of 40, clamps it to 0–200 and computes `totalScore` as the sum of the five competencies.

## Transcription rules

These rules are the source of truth for `prompts/transcription.st`.

1. Transcribe word by word and line by line.
2. **Never correct** spelling, accents, agreement or punctuation. C1 depends on the student's real mistakes.
3. Mark uncertain words as `[?word]` and unreadable words as `[illegible]`.
4. Ignore crossed-out words.
5. Return only the transcribed text, with no title or comments.

`ImageTiler` optionally splits the photo into horizontal strips before transcription. Vision models downscale large images, which erases small details such as accents and the cedilla. Strips overlap by 10% of their height, and the duplicated line at each boundary is removed when the strips are joined. Tiling is off by default and is turned on with `notamil.transcription.tiles`.

## Getting started

### Prerequisites

- Java 21
- Maven 3.9+
- [Ollama](https://ollama.com)
- At least 8 GB of free RAM. An NVIDIA GPU with 8 GB+ of VRAM is recommended; without it, models run on the CPU, more slowly.

### 1. Pull the models

```bash
ollama pull qwen2.5vl:7b
ollama pull gemma3:4b
```

### 2. Run the app

```bash
git clone https://github.com/<your-user>/notamil.git
cd notamil
./mvnw spring-boot:run
```

Open `http://localhost:8080`.

### 3. Use it from your phone

1. Connect the phone to the same Wi-Fi network as the computer.
2. Find the computer's IPv4 address (`ipconfig` on Windows, `ip addr` on Linux).
3. Open `http://<computer-ip>:8080` on the phone.
4. On Windows, allow inbound connections on port 8080 for private networks in the firewall if the page does not load.

### Photo tips

- Capture the full page, flat and straight.
- Use even lighting with no shadow over the text.
- Prefer the rear camera.

## Configuration

`src/main/resources/application.properties`:

```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.temperature=0

notamil.transcription.model=qwen2.5vl:7b
notamil.transcription.tiles=1
notamil.evaluation.model=gemma3:4b

spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

| Property | Default | Description |
| --- | --- | --- |
| `notamil.transcription.model` | `qwen2.5vl:7b` | Ollama vision model used to read the photo |
| `notamil.transcription.tiles` | `1` | Number of horizontal strips; `1` disables tiling |
| `notamil.evaluation.model` | `gemma3:4b` | Ollama model used for grading |
| `spring.ai.ollama.chat.options.temperature` | `0` | Keeps scores as stable as possible between runs |

## Evaluation

Results are recorded in [`docs/evaluation.md`](docs/evaluation.md) and in the DEV post.

### Transcription

Measured against a manually transcribed reference:

- **Word error rate**: wrong words per 100 words.
- **Error preservation**: out of 5 deliberate mistakes in a handwritten sample, how many the model kept. A model that fixes them is rejected.
- **Latency**: seconds per photo on the development machine.

### Grading

- **Sanity check**: official score-1000 essays from INEP's *Cartilha do Participante* must reach a total of 880 or more. A deliberately weak essay with no intervention proposal must score 80 or less in C5.
- **Stability**: each essay is graded 3 times; the total must not vary by more than 80 points.
- **Accuracy (fine-tuning experiment)**: mean absolute error per competency on a held-out set of graded essays, baseline model vs. fine-tuned model.

## Roadmap

| Day | Goal |
| --- | --- |
| Tue, Oct 13 | Ollama setup, transcription model test, `/api/transcriptions` working |
| Wed, Oct 14 | Rubric prompt, structured output, `/api/evaluations`, phone UI, end-to-end flow |
| Thu, Oct 15 | LoRA fine-tuning experiment on graded essays; baseline vs. fine-tuned comparison |
| Fri, Oct 16 | First real user writes an essay on paper and tests the app |
| Sat, Oct 17 | Demo video, final README, evaluation results |
| Sun, Oct 18 | DEV post published |

The MVP (Tuesday and Wednesday) is complete on its own. The fine-tuning experiment is reported whether or not it beats the baseline.

## Notes for AI coding agents

This section guides any coding agent (such as Claude Code) working in this repository.

### Principles

- Every model call goes to the local Ollama server. Never add a call to a hosted or paid AI API.
- Never commit essay photos or essay texts. Samples live in `samples/`, which is git-ignored.
- Keep the scope listed in [Features](#features). Don't add a database, authentication or a frontend framework.
- Prompts live in `src/main/resources/prompts/` as `.st` files, never hardcoded in Java classes.

### Code conventions

- Java 21: use `record` for DTOs and value objects; no Lombok.
- Constructor injection only; no field injection with `@Autowired`.
- Code identifiers, comments and commit messages in English. Prompt content and user-facing text in Brazilian Portuguese.
- Arithmetic and validation of scores happen in Java (`ScoreNormalizer`), never in the prompt.
- Controllers stay thin: validate input, call the service, return the response.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org) (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`), small and frequent.

### Tests

- JUnit 5 + Mockito.
- Unit tests mock `ChatClient`; they never call a real model.
- `ScoreNormalizer` has full unit test coverage: rounding, clamping and total computation.
- Run with `./mvnw test`.

### Definition of done for any change

1. `./mvnw test` passes.
2. The end-to-end flow (photo → transcription → review → evaluation) still works from a phone.
3. The README is updated if an endpoint, property or rule changed.

## Data and credits

- Score-1000 essays used for calibration and testing come from INEP's *Cartilha do Participante*. They are not redistributed in this repository.
- Datasets used in the fine-tuning experiment are credited here, with their licenses, when added.

## Post-deadline changes

None yet.

## License

[MIT](LICENSE)
