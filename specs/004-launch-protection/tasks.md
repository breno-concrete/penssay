# Tasks: Proteção para o lançamento público

**Input**: Design documents from `specs/004-launch-protection/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api-errors.md, quickstart.md

**Tests**: Incluídos. A spec exige verificação automatizada (CS-001, CS-003, CS-004, CS-006), e a constituição (princípio V) exige `ChatClient` mockado.

**Modo**: Prazo do ENEM. Claude implementa (feedback "ENEM deadline mode"). Breno faz só a tela, à parte.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [X] T001 Emendar a constituição para 2.0.0 conforme research.md, 10, em `.specify/memory/constitution.md`:
  - **Princípio I** passa a ser "Custo de IA controlado": provedor hospedado configurável, teto de crédito e limites no app, Ollama como perfil local.
  - **Princípio II** passa a dizer: a redação vai ao provedor só para ser lida e corrigida e nunca é guardada nem registrada em log, e o aluno é avisado.
  - **Prazo** passa a ser o lançamento do ENEM, 2026-11-08.
  - **Sync Impact Report** atualizado.
- [X] T002 Adicionar a `src/main/resources/application.properties`: `spring.threads.virtual.enabled=true`, `server.forward-headers-strategy=native`, `penssay.limits.enabled=${PENSSAY_LIMITS_ENABLED:true}` e os padrões de `penssay.limits.*` do data-model.md.

## Phase 2: Foundational (bloqueia todas as histórias)

- [X] T003 [P] Criar `record LimitsProperties` com `@ConfigurationProperties("penssay.limits")` (enabled, window como `Duration`, evaluationsPerOrigin, transcriptionsPerOrigin, evaluationsPerDay, transcriptionsPerDay, maxTextChars, maxThemeChars) e registrá-lo com `@ConfigurationPropertiesScan` em `src/main/java/br/com/breno/penssay/PenssayApplication.java`, criando o record em `src/main/java/br/com/breno/penssay/web/LimitsProperties.java`.
- [X] T004 [P] Criar `enum UsageKind { EVALUATION, TRANSCRIPTION }` em `src/main/java/br/com/breno/penssay/web/UsageKind.java`.
- [X] T005 [P] Criar `UsageLimitException` (kind, scope ORIGIN/DAILY, retryAfterSeconds) em `src/main/java/br/com/breno/penssay/web/UsageLimitException.java`.
- [X] T006 Criar o bean `Clock` (`Clock.systemUTC()`) em `src/main/java/br/com/breno/penssay/web/ClockConfig.java`, para o `UsageLimiter` ser testável com relógio fixo.
- [X] T007 Criar `ApiExceptionHandler` (`@RestControllerAdvice`) com os mapeamentos de 400 (`InvalidRequestException`) e 429 (`UsageLimitException` com cabeçalho `Retry-After` e mensagens do contrato) em `src/main/java/br/com/breno/penssay/web/ApiExceptionHandler.java`. Os demais mapeamentos ficam na US3.

**Checkpoint**: a base compila (`./mvnw -q compile`).

---

## Phase 3: User Story 1 - O gasto não foge do controle (P1) 🎯 MVP

**Goal**: Nenhum uso passa dos limites por IP e do teto do dia, e textos gigantes são recusados antes do modelo.

**Independent Test**: `UsageLimiterTest` e `EvaluationControllerTest` passam. No quickstart, passo 3, a 3ª correção com limite 2 dá 429.

### Tests

- [X] T008 [P] [US1] Criar `UsageLimiterTest` em `src/test/java/br/com/breno/penssay/web/UsageLimiterTest.java`, com `Clock` mutável. Deve cobrir:
  - limite por origem e recusa com `retryAfterSeconds` correto;
  - origens independentes;
  - nova janela depois de `window`;
  - teto do dia e recusa até a meia-noite de Brasília;
  - virada do dia zerando o contador;
  - recusa no teto do dia sem consumir a vaga da origem;
  - `enabled=false` nunca recusa;
  - limpeza das janelas vencidas acima de 10.000 origens.
- [X] T009 [P] [US1] Criar `EvaluationControllerTest` (`@WebMvcTest`, `EvaluationService` mockado) em `src/test/java/br/com/breno/penssay/evaluation/EvaluationControllerTest.java`. Deve cobrir:
  - texto com 6.001 caracteres e tema com 301 dão 400 com a mensagem do contrato, sem chamar o serviço;
  - 6.000 e 300 exatos são aceitos;
  - `UsageLimitException` vinda do serviço dá 429 com `Retry-After`;
  - o IP passado ao serviço é o `remoteAddr`, e não um `X-Forwarded-For` enviado pelo cliente (CS-003).

### Implementation

- [X] T010 [US1] Implementar `UsageLimiter` em `src/main/java/br/com/breno/penssay/web/UsageLimiter.java`, conforme data-model.md:
  - `acquire(UsageKind kind, String origin)` reserva de forma atômica a vaga da origem e a do dia, ou lança `UsageLimitException`;
  - quando `enabled=false`, não faz nada.
- [X] T011 [US1] Validar tema e texto em `EvaluationController` (`src/main/java/br/com/breno/penssay/evaluation/EvaluationController.java`): nulos, vazios ou só com espaços, e tamanhos de `LimitsProperties`, lançando `InvalidRequestException` com as mensagens do contrato. Ler `HttpServletRequest.getRemoteAddr()` e passar o IP a `EvaluationService.evaluate(theme, text, origin)`.
- [X] T012 [US1] Em `EvaluationService` (`src/main/java/br/com/breno/penssay/evaluation/EvaluationService.java`), receber `UsageLimiter` pelo construtor e chamar `acquire(EVALUATION, origin)` antes das 5 chamadas ao modelo.
- [X] T013 [US1] Em `TranscriptionService` (`src/main/java/br/com/breno/penssay/transcription/TranscriptionService.java`), receber `UsageLimiter` e chamar `acquire(TRANSCRIPTION, origin)` antes da chamada ao modelo. A assinatura passa a ser `transcribe(byte[] photo, MimeType type, String origin)`.

**Checkpoint**: US1 testável sozinha. Com limites baixos, os pedidos excedentes dão 429 sem custo.

---

## Phase 4: User Story 2 - Entrada inválida não custa nada (P1)

**Goal**: Foto inválida e texto curto são recusados ou zerados sem chamar o modelo.

**Independent Test**: `PhotoValidatorTest`, `TranscriptionControllerTest` e `EvaluationServiceTest` passam, com `verifyNoInteractions` no `ChatClient`.

### Tests

- [X] T014 [P] [US2] Criar `PhotoValidatorTest` em `src/test/java/br/com/breno/penssay/transcription/PhotoValidatorTest.java`. Deve cobrir:
  - bytes de JPEG, PNG e WebP são reconhecidos e o tipo correto é devolvido;
  - PDF (`%PDF`) declarado como `image/jpeg` é recusado;
  - arquivo vazio, `null` e arquivo com menos de 12 bytes são recusados com as mensagens do contrato.
- [X] T015 [P] [US2] Criar `TranscriptionControllerTest` (`@WebMvcTest`, serviço mockado) em `src/test/java/br/com/breno/penssay/transcription/TranscriptionControllerTest.java`. Deve cobrir:
  - sem o campo `photo` → 400 "Envie a foto da redação.";
  - PDF → 400, sem chamar o serviço;
  - JPEG válido → 200 e o serviço recebe `image/jpeg` mesmo que o cliente declare outro tipo.
- [X] T016 [P] [US2] Criar `EvaluationServiceTest` em `src/test/java/br/com/breno/penssay/evaluation/EvaluationServiceTest.java`, com `ChatClient.Builder` e `ChatClient` mockados e `UsageLimiter` mockado. Deve cobrir:
  - 69 palavras espalhadas em linhas e linhas em branco → 5 notas 0, `TOO_SHORT`, total 0, `verifyNoInteractions` no cliente e no limiter;
  - 70 palavras → `acquire(EVALUATION, origin)` é chamado.

  Cobre também T018 da spec 003.

### Implementation

- [X] T017 [P] [US2] Implementar o componente `PhotoValidator` em `src/main/java/br/com/breno/penssay/transcription/PhotoValidator.java` (research.md, 5): `MimeType detect(MultipartFile photo)`.
- [X] T018 [US2] Em `TranscriptionController` (`src/main/java/br/com/breno/penssay/transcription/TranscriptionController.java`), receber `@RequestParam(value = "photo", required = false)`, validar com `PhotoValidator` e passar bytes, tipo detectado e `remoteAddr` ao serviço.
- [X] T019 [US2] Implementar a regra das 70 palavras no início de `EvaluationService.evaluate`, antes do `UsageLimiter`, conforme data-model.md. Cobre T020 da spec 003.

**Checkpoint**: US1 e US2 juntas: nenhum pedido barato ou abusivo chega ao modelo.

---

## Phase 5: User Story 3 - Toda falha tem uma mensagem clara (P2)

**Goal**: Todos os erros do contrato, com mensagem em PT-BR, sem detalhes internos e sem texto de redação em log.

**Independent Test**: os novos casos de `EvaluationControllerTest` e `TranscriptionControllerTest` passam. O passo 4 do quickstart, com chave inválida, dá 503.

### Tests

- [X] T020 [P] [US3] Acrescentar ao `EvaluationControllerTest`:
  - corpo não JSON → 400 "Pedido inválido. Envie o tema e o texto da redação.";
  - `EvaluationFormatException` e `JacksonException` → 502;
  - `OpenAIIoException` e `UnauthorizedException` (`OpenAIServiceException`) → 503;
  - `RuntimeException` → 500 genérico;
  - em todos os casos, o corpo tem só `message`, e o log capturado (`OutputCaptureExtension`) não contém o texto enviado (CS-004).
- [X] T021 [P] [US3] Acrescentar ao `TranscriptionControllerTest`:
  - `MaxUploadSizeExceededException` → 413;
  - falhas do provedor → 502 e 503, com as mensagens próprias de foto do contrato.

### Implementation

- [X] T022 [US3] Completar `ApiExceptionHandler` com todos os mapeamentos de research.md, 7 e as mensagens de `contracts/api-errors.md`. As mensagens de 502 e 503 dependem do endpoint (correção ou foto), decidido pelo caminho do pedido. O log registra só a classe da exceção e o status. Arquivo: `src/main/java/br/com/breno/penssay/web/ApiExceptionHandler.java`.

**Checkpoint**: qualquer falha vira uma mensagem que a tela pode mostrar diretamente.

---

## Phase 6: User Story 4 - Correções lentas não derrubam o serviço (P2)

**Goal**: Pedidos leves continuam rápidos com 300 correções esperando.

**Independent Test**: `python experiments/load_test.py` mostra `GET /` em até 2 s.

- [X] T023 [US4] Criar `experiments/load_test.py`:
  1. sobe um provedor falso compatível com OpenAI em `localhost:8099`, que responde `/v1/chat/completions` depois de 12 s com um JSON válido de `ModelCompetencyAnswer`;
  2. inicia o app com `--spring.ai.openai.base-url=http://localhost:8099/v1` e `PENSSAY_LIMITS_ENABLED=false`;
  3. dispara 300 correções de 80 palavras em threads;
  4. mede 5 `GET /` durante a espera;
  5. imprime os tempos e encerra o app.

  O script nunca usa texto de redação real.
- [X] T024 [US4] Rodar `experiments/load_test.py` e registrar o resultado (CS-005) em `specs/004-launch-protection/quickstart.md`, passo 5.

---

## Phase 7: Polish & Cross-Cutting

- [X] T025 [P] Documentar no `README.md`:
  - os limites e as propriedades `penssay.limits.*`;
  - a tabela de erros (link para o contrato);
  - `PENSSAY_LIMITS_ENABLED=false` para benchmarks;
  - o passo manual da chave de produção (chave só para produção, teto de crédito de US$ 10 no painel da OpenRouter, chave como segredo da hospedagem) (RF-014).
- [X] T026 [P] Marcar em `specs/003-essay-grading/tasks.md` as tarefas T018, T020, T027, T028, T029 e T038 como cobertas pela 004, com referência às tarefas desta lista.
- [X] T027 Atualizar o `--url`/uso do benchmark no docstring de `experiments/benchmark.py` com a nota de `PENSSAY_LIMITS_ENABLED=false`.
- [X] T028 Rodar `./mvnw test` (CS-006) e os passos 2 a 4 do `quickstart.md`.

---

## Dependencies & Execution Order

- **Setup (T001–T002)** → **Foundational (T003–T007)** → histórias.
- **US1 (T008–T013)** é a base do controle de custo. A **US2 (T014–T019)** mexe nos mesmos serviços (`EvaluationService`, `TranscriptionService`), então vem depois da US1, em sequência.
- **US3 (T020–T022)** depende do handler da fase 2 e das exceções das US1 e US2.
- **US4 (T023–T024)** depende só de T002 (virtual threads) e pode ser medida a qualquer momento depois dela.
- **Polish** por último.

## Parallel Opportunities

- T003, T004 e T005 são arquivos diferentes.
- T008 e T009 (testes da US1).
- T014, T015, T016 e T017 (testes da US2 e o validador).
- T020 e T021.
- T025 e T026.

## Implementation Strategy

1. **MVP = US1 + US2** (as duas P1). Juntas, garantem que o gasto tem teto e que entrada inválida não custa nada. Sem elas, não há lançamento.
2. **US3** antes do deploy, porque a tela do Breno depende das mensagens do contrato.
3. **US4**: a configuração (T002) entra com o setup, e a medição pode ficar para depois do deploy, se faltar tempo.
4. Um commit por fase, em Conventional Commits, sem a linha de co-autoria (feedback do Breno).
