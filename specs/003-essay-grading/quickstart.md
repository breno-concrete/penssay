# Quickstart: validar a correção C1–C5

## Pré-requisitos

- Ollama rodando, com `gemma3:4b` baixado.
- `penssay.evaluation.model=gemma3:4b` e `spring.ai.retry.max-attempts=1` no
  `application.properties`.
- Em `samples/` (git-ignored): pelo menos 1 redação nota 1000 da Cartilha do Participante
  (INEP) e 1 redação fraca sem proposta de intervenção, como texto.

## 1. Testes automatizados (sem modelo)

```bash
./mvnw test
```

Esperado: `ScoreNormalizerTest`, `EvaluationServiceTest` e `EvaluationControllerTest` passam,
com o Ollama desligado inclusive.

## 2. Caminho feliz

```bash
./mvnw spring-boot:run
curl -s -H "Content-Type: application/json" \
  -d @samples/cartilha1.json localhost:8080/api/evaluations
```

`samples/cartilha1.json` contém `{"theme": "...", "text": "..."}`. Esperado: 5 competências de
1 a 5, notas em múltiplos de 40, `totalScore` igual à soma, justificativas e dicas em PT-BR.
Ver [contrato](contracts/evaluations-api.md).

## 3. Formato reduzido

```bash
curl -s -H "Content-Type: application/json" \
  -d @samples/cartilha1.json "localhost:8080/api/evaluations?mode=scores"
```

Esperado: `scores` com 5 números e `totalScore`, sem justificativas.

## 4. Regras de zero e erros

| Cenário | Como provocar | Esperado |
|---|---|---|
| Texto curto | `text` com 5 linhas | 200, tudo zero, `zeroReason: "TOO_SHORT"`, resposta imediata |
| Sem tema | `theme: ""` | 400, "Informe o tema da redação." |
| Ollama fora | Fechar o Ollama e repetir o passo 2 | 503 em poucos segundos (não minutos) |

## 5. Medições (registrar em `docs/evaluation.md`)

- **Estabilidade (CS-003)**: corrigir a mesma redação 3 vezes; anotar os 3 totais; a diferença
  entre o maior e o menor deve ser ≤ 80.
- **Sanity check (CS-004)**: redação da Cartilha → total ≥ 880; redação fraca sem proposta →
  C5 ≤ 80.
- **Tempo (CS-006)**: tempo da 2ª chamada (modelo já carregado) ≤ 60 s.
