# Pesquisa (Fase 0): Correção da redação por competência

Decisões verificadas nos jars do Spring AI 2.0.1 em `~/.m2` (com `javap`) em 2026-10-15. Não
restam itens "NEEDS CLARIFICATION".

## 1. Como receber JSON do modelo: saída estruturada

- **Decisão**: `.call().entity(ModelEvaluation.class, spec -> spec.useProviderStructuredOutput())`.
- **Base**: `ChatClient.CallResponseSpec` tem `entity(Class<T>)` e
  `entity(Class<T>, Consumer<EntityParamSpec>)`; `EntityParamSpec` tem
  `useProviderStructuredOutput()` e `validateSchema()`. `OllamaChatOptions` implementa
  `StructuredOutputChatOptions` (`getOutputSchema()`), ou seja, o Spring AI gera o JSON Schema
  a partir do record e o envia no campo `format` do Ollama. O Ollama então **restringe a
  geração** ao schema: o modelo não consegue escrever texto solto nem esquecer um campo.
- **Por que importa**: modelos de 4B erram formato com frequência quando só "pedimos" JSON no
  prompt. Com o schema no Ollama, a falha de formato fica rara, e o que sobra (por exemplo
  competência repetida) é pego pelo `ScoreNormalizer`.
- **Alternativas**: (a) `entity(Class)` sem provider output: o Spring AI só acrescenta
  instruções de formato ao prompt; mais frágil. (b) `content()` + Jackson manual: mais código
  sem ganho.
- **Consequência para o prompt**: `evaluation.st` NÃO descreve o formato JSON (o schema faz
  isso). Ele fica só com a rubrica e as regras, e assim é idêntico nos dois modos (spec, US5).
- **Jackson 3**: o Spring AI 2.0.1 usa `tools.jackson.databind` (não `com.fasterxml`). Erros de
  conversão são `tools.jackson.core.JacksonException` (não checada). Confirmar no teste do
  serviço qual exceção chega do `entity(...)` e convertê-la em `EvaluationFormatException`.

## 2. Formato completo e formato reduzido com a mesma rubrica

- **Decisão**: o mesmo `system` (`evaluation.st`) e o mesmo `user` (`evaluation-user.st`); só
  muda a classe passada para `entity(...)`: `ModelEvaluation` (notas + justificativa + dicas) ou
  `ModelScores` (só notas). O schema muda, a rubrica não.
- **Base**: atende RF-008 e RF-004 do experimento 002 (mesmas instruções de correção).
- **API**: `POST /api/evaluations?mode=scores`; sem o parâmetro, formato completo.
- **Alternativa**: dois arquivos de prompt (completo e reduzido). Rejeitada: as rubricas
  divergiriam com o tempo.

## 3. Sinal de nota zero vindo do modelo

- **Decisão**: o JSON do modelo tem dois booleanos, `fullyOffTopic` e `notArgumentative`. Se
  algum for `true`, o serviço zera todas as notas e preenche `zeroReason`.
- **Por quê**: perguntas de sim/não são mais fáceis para um modelo pequeno do que escolher um
  valor de enum, e deixam a decisão final (zerar) no Java.
- **Regra das 7 linhas**: o serviço conta as linhas não vazias do texto antes de chamar o
  modelo; ≤ 7 → resposta com tudo zero e `zeroReason = TOO_SHORT`, sem chamada (RF-005).

## 4. Prompt com tema e texto: `system` + `user` com parâmetros

- **Decisão**: `.system(evaluationPrompt)` com a rubrica e
  `.user(u -> u.text(userPrompt).param("theme", theme).param("text", text))`.
- **Base**: `ChatClientRequestSpec.system(Resource)`, `PromptUserSpec.text(Resource)` e
  `PromptUserSpec.param(String, Object)` existem (verificado na 001). O renderizador
  StringTemplate substitui `{theme}` e `{text}`; os **valores** não são reinterpretados, então
  chaves dentro da redação não quebram nada.
- **Cuidado**: nos arquivos `.st`, a única chave permitida é a de variável (`{theme}`,
  `{text}`). Nada de JSON de exemplo no prompt.
- **Alternativa**: montar a string do usuário no Java. Rejeitada pelo princípio IV.

## 5. `ScoreNormalizer`: arredondamento e validação

- **Decisão**: `Math.floor(raw / 40.0 + 0.5) * 40`, depois limitar a 0–200. Isso arredonda
  para o múltiplo de 40 mais próximo com empate para cima (20 → 40, 180 → 200, 152,5 → 160).
  A nota crua do modelo é `double`, para aceitar casas decimais.
- **Validação**: exatamente 5 competências com números 1, 2, 3, 4 e 5, uma vez cada; ordem
  livre na entrada, ordenada na saída. Qualquer outra coisa → `EvaluationFormatException`.
- **Total**: soma das 5 notas já normalizadas.
- **TDD**: classe pura (sem Spring, sem mocks), ideal para o primeiro ciclo vermelho → verde →
  refatorar.

## 6. Retry do Spring AI e o erro 503

- **Achado**: o `spring-ai-autoconfigure-retry` vem com `spring.ai.retry.max-attempts=10`,
  `backoff.initial-interval=2000ms` e `backoff.multiplier=5`. O `RetryUtils` repete
  `TransientAiException` **e** `ResourceAccessException` (conexão recusada). Com o Ollama
  desligado, o pedido ficaria minutos esperando antes de falhar.
- **Decisão**: `spring.ai.retry.max-attempts=1` no `application.properties`. Vale para a 001
  também (o 503 dela tem o mesmo problema).
- **Mapeamento**: `ResourceAccessException` (Ollama fora) e `NonTransientAiException` (modelo não
  instalado, HTTP 404 do Ollama) → `EvaluationUnavailableException` → **503**.
- **Alternativa**: manter o retry com 2 tentativas. Aceitável, mas o modelo local não tem falha
  "passageira" que justifique esperar.

## 7. Status HTTP para resposta inválida do modelo

- **Decisão**: **502 Bad Gateway** com "Não foi possível corrigir a redação agora. Tente de
  novo." O erro é do serviço de que o app depende, não do aluno (400) nem do app (500).
- **Alternativa**: 500. Rejeitada: esconde a causa e confunde com bug.

## 8. Handler de erros compartilhado

- **Decisão**: mover `ErrorResponse` para `br.com.breno.penssay.web` e criar um único
  `ApiExceptionHandler` (`@RestControllerAdvice`) para a 001 e a 003, com
  `InvalidRequestException` (400) compartilhada.
- **Alternativa**: um handler por feature (plano original da 001). Rejeitada: duplicaria o
  mapeamento 400/503 e o `ErrorResponse`.
- **Impacto na 001**: `InvalidPhotoException` vira `InvalidRequestException`, e
  `TranscriptionUnavailableException` continua dela. Registrar no `tasks.md` da 001.

## 9. Experimento 002: como reutilizar

- **Gemma (local)**: medido pela própria API, `POST /api/evaluations?mode=scores`.
- **Qwen base e Qwen ajustado (Tinker)**: o script do experimento lê os mesmos
  `evaluation.st` e `evaluation-user.st`, envia ao Tinker com o JSON Schema de `ModelScores` e
  normaliza com uma cópia em Python da regra do `ScoreNormalizer`. A cópia é validada contra os
  mesmos casos do `ScoreNormalizerTest`, listados no `data-model.md`.
- **Por quê**: o Tinker não roda no Ollama, então o modelo ajustado não passa pelo app Java.
- **Alternativa**: endpoint que só normaliza notas cruas. Rejeitada: API extra só para o
  experimento (princípio III).

## 10. Sanity check e estabilidade

- **Estabilidade**: 3 chamadas por redação com `temperature=0`; o total não pode variar mais de
  80 (CS-003). Registrar em `docs/evaluation.md`.
- **Sanity check**: redações nota 1000 da Cartilha do Participante (INEP), copiadas à mão para
  `samples/` (git-ignored, não redistribuídas): total ≥ 880. Uma redação fraca sem proposta de
  intervenção: C5 ≤ 80 (CS-004).
