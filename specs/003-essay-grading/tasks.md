---

description: "Lista de tarefas da feature 003: correção da redação por competência (C1–C5)"
---

# Tarefas: Correção da redação por competência (C1–C5)

**Entrada**: documentos de design em `/specs/003-essay-grading/`

**Pré-requisitos**: plan.md, spec.md, research.md, data-model.md, contracts/evaluations-api.md, quickstart.md

**Testes**: pedidos pela spec (RF-004, CS-002) e pelo plano (`ScoreNormalizer` em TDD,
`EvaluationServiceTest` com Mockito, `EvaluationControllerTest` com `@WebMvcTest`). Nenhum
teste chama modelo real (constituição, princípio V).

**Build-to-Learn**: **[B]** = Breno escreve (Claude explica antes e revisa). **[C]** = Claude
escreve. A marcação vem no início da descrição de cada tarefa, conforme a tabela "Divisão
Build-to-Learn" do `plan.md`.

## Formato: `[ID] [P?] [História] Descrição`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência pendente)
- **[História]**: a história de usuário da tarefa (US1 a US5)
- Caminhos relativos à raiz do repositório; pacote base `br/com/breno/penssay`

---

## Fase 1: Setup (infraestrutura compartilhada)

**Objetivo**: preparar a configuração e o pacote `web/` que a 001 e a 003 vão dividir.

- [x] T001 [C] Adicionar `spring.ai.retry.max-attempts=1` em `src/main/resources/application.properties` (research.md, decisão 6), mantendo `penssay.evaluation.model=gemma3:4b`
- [x] T002 [C] Mover `ErrorResponse` de `src/main/java/br/com/breno/penssay/transcription/ErrorResponse.java` para `src/main/java/br/com/breno/penssay/web/ErrorResponse.java` (pacote `br.com.breno.penssay.web`) e ajustar imports
- [x] T003 [P] [C] Criar `src/main/java/br/com/breno/penssay/web/InvalidRequestException.java` (`RuntimeException` com mensagem em PT-BR, mapeada para 400)

---

## Fase 2: Fundação (bloqueia todas as histórias)

**Objetivo**: os tipos de entrada, saída e erro que todas as histórias usam.

**⚠️ CRÍTICO**: nenhuma história começa antes desta fase.

- [x] T004 [P] [C] Criar `record EvaluationRequest(String theme, String text)` em `src/main/java/br/com/breno/penssay/evaluation/EvaluationRequest.java`
- [x] T005 [P] [C] Criar `enum ZeroReason { TOO_SHORT, OFF_TOPIC, NOT_ARGUMENTATIVE }` em `src/main/java/br/com/breno/penssay/evaluation/ZeroReason.java`
- [x] T006 [P] [C] Criar `record CompetencyEvaluation(int number, int score, String justification, List<String> tips)` em `src/main/java/br/com/breno/penssay/evaluation/CompetencyEvaluation.java`
- [x] T007 [P] [C] Criar `record EssayEvaluation(List<CompetencyEvaluation> competencies, int totalScore, ZeroReason zeroReason)` em `src/main/java/br/com/breno/penssay/evaluation/EssayEvaluation.java`
- [x] T008 [P] [C] Criar `record ModelEvaluation(boolean fullyOffTopic, boolean notArgumentative, List<ModelCompetency> competencies)` e `record ModelCompetency(int number, double score, String justification, List<String> tips)` em `src/main/java/br/com/breno/penssay/evaluation/ModelEvaluation.java` e `ModelCompetency.java` (sem campo de total: ele não existe no schema)
- [x] T009 [P] [C] Criar `EvaluationFormatException` (→ 502) e `EvaluationUnavailableException` (→ 503) em `src/main/java/br/com/breno/penssay/evaluation/EvaluationFormatException.java` e `EvaluationUnavailableException.java`

**Checkpoint**: `./mvnw compile` passa; as histórias podem começar.

---

## Fase 3: História 1 - Receber a nota de cada competência (Prioridade: P1) 🎯 MVP

**Objetivo**: `POST /api/evaluations` devolve 5 competências em ordem, notas em múltiplos de
40 e o total calculado pelo app.

**Teste independente**: enviar tema e texto de uma redação (mais de 7 linhas) e conferir 5
competências de 1 a 5, notas em {0, 40, 80, 120, 160, 200} e `totalScore` igual à soma.

### Testes da História 1 (TDD: escrever primeiro e ver falhar)

- [x] T010 [US1] [B] Escrever `ScoreNormalizerTest` em `src/test/java/br/com/breno/penssay/evaluation/ScoreNormalizerTest.java` com os casos de nota da tabela do `data-model.md` (0, 120, 200, 150→160, 130→120, 20→40, 180→200, 152.5→160, -40→0, 230→200, 1000→200) e o total do exemplo (150, 130, 20, 230, -40 → 520); rodar e ver vermelho
- [x] T011 [US1] [B] Acrescentar ao `ScoreNormalizerTest` os casos de validação da lista: fora de ordem → ordenada; 4 ou 6 competências, número repetido, número 0 ou 6 e lista nula → `EvaluationFormatException`

### Implementação da História 1

- [x] T012 [US1] [B] Implementar `ScoreNormalizer` em `src/main/java/br/com/breno/penssay/evaluation/ScoreNormalizer.java` até T010 e T011 ficarem verdes: `Math.floor(raw / 40.0 + 0.5) * 40` limitado a 0–200, validação de exatamente 1–5 uma vez cada, ordenação e soma; depois refatorar
- [ ] T013 [P] [US1] [B] Escrever a rubrica C1–C5 em `src/main/resources/prompts/evaluation.st` (PT-BR, sem JSON de exemplo e sem chaves além de variáveis; research.md, decisões 1 e 4)
- [ ] T014 [P] [US1] [B] Escrever `src/main/resources/prompts/evaluation-user.st` com as variáveis `{theme}` e `{text}`
- [ ] T015 [US1] [B] Implementar `EvaluationService.evaluate(String theme, String text)` em `src/main/java/br/com/breno/penssay/evaluation/EvaluationService.java`: `ChatClient` com `.system(evaluation.st)`, `.user(u -> u.text(...).param("theme", ...).param("text", ...))`, modelo de `penssay.evaluation.model` via `OllamaChatOptions`, `.call().entity(ModelEvaluation.class, spec -> spec.useProviderStructuredOutput())`, e montagem do `EssayEvaluation` com o `ScoreNormalizer` (depende de T012–T014)
- [ ] T016 [US1] [B] Implementar `EvaluationController` em `src/main/java/br/com/breno/penssay/evaluation/EvaluationController.java`: `@RestController`, `@RequestMapping("/api/evaluations")`, `@PostMapping` recebendo `@RequestBody EvaluationRequest` e delegando ao serviço (mesmo padrão do `TranscriptionController`)
- [ ] T017 [US1] [B] Validar à mão o passo 2 do `quickstart.md` (caminho feliz com `samples/cartilha1.json`)

**Checkpoint**: a História 1 funciona sozinha; MVP pronto para demo.

---

## Fase 4: História 2 - Aplicar as regras de nota zero (Prioridade: P1)

**Objetivo**: texto com 7 linhas não vazias ou menos zera tudo sem chamar o modelo; fuga
total ao tema ou texto não dissertativo zera tudo pela indicação do modelo.

**Teste independente**: enviar um texto de 5 linhas e outro fora do tema; ambos voltam com
todas as notas e o total em zero e com `zeroReason` preenchido.

### Testes da História 2

- [ ] T018 [US2] [B guiado] Criar `EvaluationServiceTest` em `src/test/java/br/com/breno/penssay/evaluation/EvaluationServiceTest.java` com `ChatClient` mockado: texto com ≤ 7 linhas não vazias (linhas em branco não contam) → tudo zero, `TOO_SHORT` e `verifyNoInteractions` no cliente; texto com 8 linhas → o modelo é chamado
- [ ] T019 [US2] [B guiado] Acrescentar ao `EvaluationServiceTest`: modelo com `fullyOffTopic=true` e notas altas → tudo zero e `OFF_TOPIC`; `notArgumentative=true` → `NOT_ARGUMENTATIVE`; os dois `true` → `OFF_TOPIC`

### Implementação da História 2

- [ ] T020 [US2] [B] Implementar a regra das 7 linhas no início de `EvaluationService.evaluate` em `src/main/java/br/com/breno/penssay/evaluation/EvaluationService.java`, devolvendo 5 competências com nota 0, justificativa explicando o motivo e `zeroReason = TOO_SHORT`, sem chamar o modelo
- [ ] T021 [US2] [B] Implementar em `EvaluationService` a leitura de `fullyOffTopic` e `notArgumentative`: zerar todas as notas e o total e preencher `zeroReason` (`OFF_TOPIC` tem prioridade)
- [ ] T022 [US2] [B] Acrescentar a `src/main/resources/prompts/evaluation.st` as regras de fuga total (marcar `fullyOffTopic`), texto não dissertativo (marcar `notArgumentative`) e fuga parcial (NÃO zera; penaliza C2 e C3)

**Checkpoint**: Histórias 1 e 2 funcionam; os testes passam com o Ollama desligado.

---

## Fase 5: História 3 - Entender a nota e saber como melhorar (Prioridade: P2)

**Objetivo**: cada competência traz justificativa curta que cita a redação e 2 a 3 dicas.

**Teste independente**: corrigir uma redação com erro de ortografia conhecido e outra sem
proposta de intervenção; conferir que C1 cita o trecho, que C5 aponta a falta da proposta e
que cada competência tem de 2 a 3 dicas.

- [ ] T023 [US3] [B] Acrescentar a `src/main/resources/prompts/evaluation.st` as instruções de justificativa (curta, PT-BR, citando trechos reais da redação entre aspas) e de dicas (2 a 3, práticas; em C5, citar agente, ação, meio, finalidade e detalhamento quando faltarem)
- [ ] T024 [US3] [B] Validar à mão com uma redação com desvio de norma culta e uma sem proposta de intervenção (cenários 2 e 3 da História 3) e anotar o resultado em `docs/evaluation.md`

**Checkpoint**: as respostas completas explicam a nota.

---

## Fase 6: História 4 - Falhas claras, nunca nota inventada (Prioridade: P2)

**Objetivo**: 400 para entrada inválida, 503 para Ollama fora ou modelo ausente, 502 para
resposta fora do formato; mensagens em PT-BR e nenhuma nota parcial.

**Teste independente**: enviar pedido sem texto, desligar o Ollama e simular resposta sem uma
competência; conferir status e mensagem de cada caso (contrato, tabela "Erros").

### Testes da História 4

- [ ] T025 [US4] [B guiado] Acrescentar ao `EvaluationServiceTest` em `src/test/java/br/com/breno/penssay/evaluation/EvaluationServiceTest.java`: `ResourceAccessException` e `NonTransientAiException` vindas do cliente → `EvaluationUnavailableException`; exceção de conversão do Jackson 3 (`tools.jackson.core.JacksonException`, confirmar qual chega do `entity(...)`) → `EvaluationFormatException`
- [ ] T026 [US4] [C, com explicação] Criar `EvaluationControllerTest` em `src/test/java/br/com/breno/penssay/evaluation/EvaluationControllerTest.java` com `@WebMvcTest` e serviço mockado: caminho feliz 200; tema vazio → 400 "Informe o tema da redação."; texto só com espaços → 400 "Envie o texto da redação." (serviço não chamado); `EvaluationFormatException` → 502; `EvaluationUnavailableException` → 503

### Implementação da História 4

- [ ] T027 [US4] [B] Validar tema e texto (nulo, vazio ou só espaços) em `EvaluationController`, lançando `InvalidRequestException` com as mensagens do contrato antes de chamar o serviço
- [ ] T028 [US4] [B] Converter em `EvaluationService` as exceções do Spring AI: `ResourceAccessException` e `NonTransientAiException` → `EvaluationUnavailableException`; erro de conversão do JSON → `EvaluationFormatException` (o `ScoreNormalizer` já lança a dela para competências erradas)
- [ ] T029 [US4] [B guiado] Implementar `ApiExceptionHandler` (`@RestControllerAdvice`) em `src/main/java/br/com/breno/penssay/web/ApiExceptionHandler.java`: `InvalidRequestException` → 400, `EvaluationFormatException` → 502, `EvaluationUnavailableException` → 503, sempre com `ErrorResponse` e as mensagens do contrato; nunca registrar o texto da redação em log (RF-014)
- [ ] T030 [US4] [B] Validar à mão a tabela do passo 4 do `quickstart.md` (sem tema → 400; Ollama fechado → 503 em poucos segundos)

**Checkpoint**: todos os erros do contrato respondem como especificado.

---

## Fase 7: História 5 - Reutilizar a correção no experimento (Prioridade: P2)

**Objetivo**: `?mode=scores` devolve só as cinco notas e o total, com a mesma rubrica e o
mesmo `ScoreNormalizer`; o modelo muda só por configuração.

**Teste independente**: trocar `penssay.evaluation.model` e pedir `?mode=scores`; conferir
`scores` com 5 notas normalizadas e `totalScore`, sem justificativas.

### Testes da História 5

- [ ] T031 [US5] [C, com explicação] Acrescentar ao `EvaluationControllerTest`: `?mode=scores` → 200 com `scores` e `totalScore`; `?mode=xyz` → 400 "Modo de correção inválido. Use mode=scores ou não envie o parâmetro."
- [ ] T032 [US5] [B guiado] Acrescentar ao `EvaluationServiceTest`: o modo reduzido usa os mesmos `evaluation.st` e `evaluation-user.st` e normaliza as notas pela mesma regra; com ≤ 7 linhas também não chama o modelo

### Implementação da História 5

- [ ] T033 [P] [US5] [C] Criar `record ModelScores(boolean fullyOffTopic, boolean notArgumentative, List<ModelScore> competencies)` e `record ModelScore(int number, double score)` em `src/main/java/br/com/breno/penssay/evaluation/ModelScores.java` e `ModelScore.java`
- [ ] T034 [P] [US5] [C] Criar `record EssayScores(List<Integer> scores, int totalScore, ZeroReason zeroReason)` em `src/main/java/br/com/breno/penssay/evaluation/EssayScores.java`
- [ ] T035 [US5] [B] Implementar `EvaluationService.evaluateScores(String theme, String text)` reaproveitando os mesmos prompts, a regra das 7 linhas, a regra de zero e o `ScoreNormalizer`; só muda a classe passada a `entity(...)` (`ModelScores`)
- [ ] T036 [US5] [B] Ler `@RequestParam(required = false) String mode` em `EvaluationController`: ausente → completo; `scores` → reduzido; outro valor → `InvalidRequestException`
- [ ] T037 [US5] [B] Validar à mão o passo 3 do `quickstart.md` e a troca de modelo só por `application.properties` (CS-007)

**Checkpoint**: todas as histórias funcionam; o experimento 002 já pode usar a API.

---

## Fase 8: Acabamento e pontos transversais

- [ ] T038 [B guiado] Adaptar a 001 ao handler compartilhado: validar a foto no `TranscriptionController` com `InvalidRequestException` e mapear a indisponibilidade da transcrição no `ApiExceptionHandler` (research.md, decisão 8) em `src/main/java/br/com/breno/penssay/transcription/` e `src/main/java/br/com/breno/penssay/web/ApiExceptionHandler.java`
- [ ] T039 [P] [B] Medir estabilidade (3 correções da mesma redação, variação do total ≤ 80), sanity check (Cartilha ≥ 880; redação sem proposta com C5 ≤ 80) e tempo da 2ª chamada (≤ 60 s) e registrar em `docs/evaluation.md` (CS-003, CS-004, CS-006)
- [ ] T040 [P] [C] Documentar `POST /api/evaluations`, `?mode=scores` e os erros no `README.md`
- [ ] T041 [C] Rodar `./mvnw test` com o Ollama desligado e o `quickstart.md` inteiro; conferir que nenhum log contém texto de redação (RF-014)

---

## Dependências e ordem de execução

### Dependências entre fases

- **Setup (Fase 1)**: sem dependências
- **Fundação (Fase 2)**: depende do Setup; bloqueia todas as histórias
- **US1 (Fase 3)**: depende da Fundação; é a base das outras
- **US2 (Fase 4)**: depende de T015 (serviço) da US1
- **US3 (Fase 5)**: depende de T013 (rubrica) e T015; só mexe no prompt
- **US4 (Fase 6)**: depende de T015 e T016; T029 também usa T002 e T003
- **US5 (Fase 7)**: depende de T012, T015 e T016; usa as regras da US2 se ela já estiver pronta
- **Acabamento (Fase 8)**: T038 depende de T029; T039 depende das Histórias 1 a 3

### Dentro de cada história

- Testes antes da implementação, vendo o vermelho (T010–T011 antes de T012; T018–T019 antes de T020–T021)
- Records antes do serviço; serviço antes do controller
- Validação manual por último

### Oportunidades de paralelismo

- Fase 1: T003 junto com T001 e T002
- Fase 2: T004 a T009 todos juntos ([C], arquivos diferentes)
- US1: T013 e T014 (prompts) enquanto T010–T012 (TDD) estão em andamento
- US5: T033 e T034 juntos
- Acabamento: T039 e T040 juntos

---

## Exemplo de paralelismo: História 1

```text
# Claude, de uma vez (Fase 2):
T004 EvaluationRequest, T005 ZeroReason, T006 CompetencyEvaluation,
T007 EssayEvaluation, T008 ModelEvaluation/ModelCompetency, T009 exceções

# Breno, em sequência (US1):
T010 → T011 → T012 (ciclo TDD do ScoreNormalizer)
T013 + T014 (prompts) → T015 (serviço) → T016 (controller) → T017 (validação)
```

---

## Estratégia de implementação

### MVP primeiro (só a História 1)

1. Fases 1 e 2 (Claude, rápido)
2. Fase 3: `ScoreNormalizer` com TDD, prompts, serviço e controller
3. **Parar e validar**: caminho feliz do `quickstart.md`
4. Já dá para demonstrar a nota C1–C5

### Entrega incremental (prazo: 2026-10-18)

1. US1 → MVP com nota
2. US2 → regras de zero (P1, fecha o núcleo do ENEM)
3. US4 → erros claros (o handler também resolve a validação pendente da 001)
4. US3 → justificativas e dicas melhores (só prompt)
5. US5 → modo reduzido para o experimento 002
6. Acabamento: medições em `docs/evaluation.md` e README

Se o tempo apertar, US3 e US5 podem ficar mínimas: US3 é só ajuste de prompt, e US5 só é
necessária se o experimento 002 rodar antes da entrega.

---

## Notas

- [P] = arquivos diferentes, sem dependência pendente
- O rótulo [US#] liga a tarefa à história, para rastrear
- A cópia em Python do `ScoreNormalizer` para o experimento 002 fica nas tarefas da 002; ela
  DEVE passar pelos mesmos casos de T010 e T011 (research.md, decisão 9)
- Commit ao fim de cada tarefa ou grupo lógico, sem a linha `Co-Authored-By`
