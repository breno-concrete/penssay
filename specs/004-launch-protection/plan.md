# Implementation Plan: Proteção para o lançamento público

**Branch**: `004-launch-protection` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-launch-protection/spec.md`

## Summary

O Penssay vai para a internet aberto e sem login no dia do ENEM, e cada pedido custa dinheiro na OpenRouter. Esta feature coloca, antes de qualquer chamada ao modelo:
- validação de entrada: vazio, tamanho do texto e do tema, foto conferida pelos bytes;
- a regra das 70 palavras, sem custo;
- um `UsageLimiter` na memória, com limite por IP por hora e teto global por dia;
- tratamento central de erros, com mensagens em PT-BR e sem vazar detalhes;
- virtual threads, para que as esperas de ~12 s não travem o servidor.

Tudo é configurável por `application.properties` e testado sem modelo real.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1 (webmvc, Tomcat), Spring AI 2.0.1 (starter OpenAI, SDK `com.openai` 4.x), Jackson 3 (`tools.jackson`). Nenhuma dependência nova.

**Storage**: Nenhum. Contadores na memória (constituição, princípio III).

**Testing**: JUnit 5 + Mockito, `@WebMvcTest` para os controllers e o handler. `Clock` fixo para as janelas de tempo. `ChatClient` sempre mockado (princípio V).

**Target Platform**: Container Linux numa hospedagem com proxy HTTPS (Render, Railway ou Fly), instância única.

**Project Type**: Web service (API REST + `index.html` estático servido pelo mesmo app)

**Performance Goals**: Pedido leve em até 2 s com 300 correções esperando o provedor (CS-005).

**Constraints**: Nenhuma chamada ao modelo para entrada inválida, texto curto ou limite atingido (CS-001). Nenhum texto de redação em log (RF-011). Gasto diário limitado ao teto configurado (CS-002).

**Scale/Scope**: Lançamento: dezenas a poucas centenas de correções por dia; teto de 1.500 correções e 3.000 fotos por dia.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Status | Nota |
|---|---|---|
| I. IA 100% local | ❌ **Violado, mas não por esta feature** | O app usa a OpenRouter desde `6e50e93`. Esta feature só protege esse uso. A emenda 2.0.0 é a tarefa T001 (research.md, 10) |
| II. Privacidade | ⚠️ Texto desatualizado | A redação sai da máquina para o provedor. Nada é guardado nem registrado em log (RF-011 reforça). A emenda atualiza o texto, e a tela precisa avisar o aluno |
| III. Escopo enxuto | ✅ | Sem banco, login ou framework de front. Contadores na memória |
| IV. Integridade do domínio | ✅ | Prompts intactos. A regra das 70 palavras e as notas zero ficam em Java |
| V. Testes sem modelo real | ✅ | Todos os testes mockam `ChatClient`. Carga e benchmark são experimentos |
| Convenções | ✅ | `record` para configuração e DTOs, injeção por construtor, limites em `application.properties`, mensagens em PT-BR e código em inglês |

**Gate**: passa com a justificativa abaixo. **Re-check pós-design**: o design (research, data-model e contrato) não adiciona nenhuma violação nova.

## Project Structure

### Documentation (this feature)

```text
specs/004-launch-protection/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/api-errors.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
src/main/java/br/com/breno/penssay/
├── evaluation/
│   ├── EvaluationController.java     # MUDA: valida tema/texto e tamanhos; passa o IP ao serviço
│   └── EvaluationService.java        # MUDA: regra das 70 palavras; reserva no UsageLimiter antes do modelo
├── transcription/
│   ├── TranscriptionController.java  # MUDA: usa PhotoValidator; passa o IP e o tipo detectado
│   ├── TranscriptionService.java     # MUDA: recebe o MimeType detectado; reserva no UsageLimiter
│   └── PhotoValidator.java           # NOVO: confere os bytes iniciais
└── web/
    ├── ApiExceptionHandler.java      # NOVO: @RestControllerAdvice (contrato)
    ├── LimitsProperties.java         # NOVO: record @ConfigurationProperties("penssay.limits")
    ├── UsageKind.java                # NOVO: EVALUATION, TRANSCRIPTION
    ├── UsageLimiter.java             # NOVO: janela por IP + teto do dia, com Clock
    └── UsageLimitException.java      # NOVO

src/main/resources/application.properties   # MUDA: penssay.limits.*, virtual threads, forward headers

src/test/java/br/com/breno/penssay/
├── evaluation/EvaluationServiceTest.java     # NOVO: 70 palavras sem modelo; limiter chamado só quando vai ao modelo
├── evaluation/EvaluationControllerTest.java  # NOVO: validações e mapeamento de erros
├── transcription/PhotoValidatorTest.java     # NOVO
├── transcription/TranscriptionControllerTest.java  # NOVO
└── web/UsageLimiterTest.java                 # NOVO: janela, virada de hora, teto do dia, virada do dia, limpeza

experiments/load_test.py                      # NOVO: provedor falso lento + 300 pedidos (CS-005)
.specify/memory/constitution.md               # MUDA: emenda 2.0.0
README.md                                     # MUDA: limites, erros, passo da chave de produção (RF-014)
specs/003-essay-grading/tasks.md              # MUDA: T020, T027–T029 e T038 marcadas como cobertas pela 004
```

**Structure Decision**: Mantém o projeto Spring único e os pacotes atuais. O que é comum aos dois endpoints fica em `web/`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Princípio I (modelo hospedado) | O lançamento é um site público: o Ollama numa GPU de 6 GB não atende vários alunos ao mesmo tempo, e os modelos locais erravam demais (transcrição com 6% ou mais de erro de caractere, notas comprimidas) | Rodar o Ollama num servidor com GPU custa mais por mês do que o uso previsto na OpenRouter, que fica abaixo de US$ 10 por dia com teto |
