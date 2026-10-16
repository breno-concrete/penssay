# Plano de Implementação: Correção da redação por competência (C1–C5)

**Branch**: `003-essay-grading` | **Data**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Entrada**: Especificação da feature em `/specs/003-essay-grading/spec.md`

## Resumo

Expor `POST /api/evaluations`, que recebe `{"theme", "text"}` e devolve as 5 competências em
ordem (nota, justificativa, dicas), o total e o motivo de nota zero, quando houver. Um
`EvaluationController` fino valida a entrada e delega ao `EvaluationService`. O serviço aplica
a regra das 7 linhas sem chamar o modelo, chama o modelo de correção do Ollama com a rubrica
em `prompts/evaluation.st` e pede **saída estruturada** (JSON Schema enviado ao Ollama, que
restringe a resposta do modelo ao formato). O `ScoreNormalizer`, escrito com TDD, valida as 5
competências, arredonda para múltiplos de 40, limita a 0–200 e soma o total. O modo reduzido
(`?mode=scores`) usa a mesma rubrica com um formato de saída só de notas, para o
experimento 002.

## Contexto Técnico

**Linguagem/Versão**: Java 21 (JDK Microsoft 21.0.10)

**Dependências Principais**: Spring Boot 4.1.1 (`spring-boot-starter-webmvc`), Spring AI 2.0.1
(`spring-ai-starter-model-ollama`), Jackson 3 (`tools.jackson`, já transitivo). Nenhuma
dependência nova.

**Armazenamento**: N/A (sem banco; o texto só existe durante a requisição)

**Testes**: JUnit 5 + Mockito; `ScoreNormalizer` com teste unitário puro (TDD, sem mocks);
`EvaluationService` com `ChatClient` mockado; `EvaluationController` com `@WebMvcTest`
(`spring-boot-starter-webmvc-test`, já no pom)

**Plataforma Alvo**: Windows 11 local, Ollama em `localhost:11434`, RTX 3050 Laptop 6 GB
(`gemma3:4b` coube 100% na GPU no teste da 001)

**Tipo de Projeto**: serviço web (API REST em uma única aplicação)

**Metas de Desempenho**: correção completa em até 60 s com o modelo carregado (CS-006, a medir)

**Restrições**: 100% local; `temperature=0`; modelo em `penssay.evaluation.model`; texto nunca
em log; retry do Spring AI reduzido para o 503 não demorar minutos (research.md, decisão 6)

**Escala/Escopo**: um usuário por vez; uma redação por requisição

## Verificação da Constituição

*PORTÃO: deve passar antes da pesquisa (Fase 0). Reavaliado após o design (Fase 1).*

| Princípio | Situação | Observação |
|---|---|---|
| I. IA 100% Local | Passa | Só Ollama local; o modo reduzido serve o experimento sem sair da máquina |
| II. Privacidade dos Dados | Passa | Texto só em memória; nada em log; amostras e Cartilha em `samples/` |
| III. Escopo Enxuto | Passa | Sem UI, DB, histórico ou auth nesta feature |
| IV. Integridade do Domínio | Passa | Rubrica em `.st`; arredondar, limitar, somar e validar ficam no `ScoreNormalizer`; o total do modelo nem existe no formato pedido |
| V. Testes sem Modelo Real | Passa | `ScoreNormalizer` com cobertura total; serviço com `ChatClient` mockado; estabilidade e sanity check são experimentos em `docs/evaluation.md` |
| Convenções Técnicas | Passa | `record` nos DTOs, injeção por construtor, controller fino, modelo em propriedade |

**Reavaliação pós-design**: sem violações. Uma mudança afeta a 001: `ErrorResponse` e o
handler de erros passam a ser compartilhados no pacote `web` (ver Estrutura). Isso não fere a
constituição; só antecipa o handler que a 001 ainda não escreveu.

## Estrutura do Projeto

### Documentação (esta feature)

```text
specs/003-essay-grading/
├── plan.md              # Este arquivo
├── research.md          # Fase 0
├── data-model.md        # Fase 1
├── quickstart.md        # Fase 1
├── contracts/
│   └── evaluations-api.md   # Fase 1
└── tasks.md             # Fase 2 (/speckit-tasks, NÃO criado aqui)
```

### Código-Fonte (raiz do repositório)

```text
src/main/java/br/com/breno/penssay/
├── evaluation/
│   ├── EvaluationController.java      # valida tema/texto, lê ?mode, delega
│   ├── EvaluationService.java         # regra das 7 linhas, chamada ao modelo, monta a resposta
│   ├── ScoreNormalizer.java           # valida 5 competências, arredonda, limita, soma (TDD)
│   ├── EvaluationRequest.java         # record(theme, text)
│   ├── EssayEvaluation.java           # record da resposta completa
│   ├── CompetencyEvaluation.java      # record(number, score, justification, tips)
│   ├── EssayScores.java               # record da resposta reduzida (só notas)
│   ├── ZeroReason.java                # enum TOO_SHORT, OFF_TOPIC, NOT_ARGUMENTATIVE (só na API)
│   ├── ModelEvaluation.java           # record do JSON que o modelo devolve (formato completo)
│   ├── ModelScores.java               # record do JSON que o modelo devolve (formato reduzido)
│   ├── EvaluationFormatException.java       # resposta do modelo inválida → 502
│   └── EvaluationUnavailableException.java  # Ollama fora / modelo ausente → 503
└── web/
    ├── ErrorResponse.java             # movido de transcription/ (compartilhado)
    ├── InvalidRequestException.java   # entrada inválida → 400 (001 e 003)
    └── ApiExceptionHandler.java       # @RestControllerAdvice único → ErrorResponse em PT-BR

src/main/resources/
├── application.properties             # + spring.ai.retry.max-attempts
└── prompts/
    ├── evaluation.st                  # system: rubrica C1–C5 + regras de zero (PT-BR)
    └── evaluation-user.st             # user: tema e redação via {theme} e {text}

src/test/java/br/com/breno/penssay/evaluation/
├── ScoreNormalizerTest.java           # TDD, cobertura total
├── EvaluationServiceTest.java         # Mockito: ChatClient mockado; 7 linhas não chama o modelo
└── EvaluationControllerTest.java      # @WebMvcTest: 400, 502, 503, caminho feliz

docs/evaluation.md                     # estabilidade (3x) e sanity check (Cartilha INEP)
```

**Decisão de Estrutura**: pacote por funcionalidade (`evaluation/`), como no README. O pacote
`web/` concentra o que é comum às duas APIs (erro e handler), em vez de duplicar
`ErrorResponse` e um handler por feature. Os records `Model*` separam "o que o modelo
responde" de "o que a API devolve": o modelo nunca manda total, e a API nunca repassa nota
crua.

## Divisão Build-to-Learn

**[B]** = Breno escreve (Claude explica antes e revisa). **[C]** = boilerplate, Claude escreve.

| Peça | Quem | Por quê |
|---|---|---|
| `ScoreNormalizerTest` → `ScoreNormalizer` (TDD) | **[B]** | Núcleo do domínio e primeira experiência de TDD |
| `prompts/evaluation.st` (rubrica) | **[B]** | Prompt engineering e conhecimento do ENEM |
| `prompts/evaluation-user.st` (`{theme}`, `{text}`) | **[B]** | Aprender `.param(...)` do template |
| `EvaluationService` (`.system`, `.param`, `.entity`, saída estruturada) | **[B]** | Conceito central novo do Spring AI |
| `EvaluationController` | **[B]** | Repete o padrão da 001; fixa o aprendizado |
| `EvaluationServiceTest` (mock + `verifyNoInteractions`) | **[B]** guiado | Mockito, nível 2 |
| `ApiExceptionHandler` | **[B]** guiado | Tratamento de erros; serve à 001 também |
| Records, enum, exceções, mover `ErrorResponse` | [C] | Triviais |
| `EvaluationControllerTest` (`@WebMvcTest`) | [C] com explicação | Setup de teste web |
| `application.properties` (retry) e README | [C] | Boilerplate e documentação |
| Estabilidade e sanity check em `docs/evaluation.md` | **[B]** | Resultados e conclusões são dele |

**Ordem sugerida**: (1) `ScoreNormalizer` com TDD, que não depende de modelo nenhum; (2)
records [C]; (3) prompts; (4) serviço; (5) controller + handler; (6) testes do serviço e do
controller; (7) medições.

## Complexity Tracking

Sem violações da constituição; nada a justificar.
