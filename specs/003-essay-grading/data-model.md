# Modelo de Dados: Correção da redação por competência

Não há persistência (constituição, princípio III). Tudo vive em memória durante a requisição.

## Entrada

### `record EvaluationRequest(String theme, String text)`

| Campo | Regra |
|---|---|
| `theme` | Obrigatório; não vazio nem só espaços |
| `text` | Obrigatório; não vazio nem só espaços; mantém as quebras de linha da folha |

Parâmetro de consulta `mode`: ausente = completo; `scores` = reduzido; outro valor = 400.

## O que o modelo devolve (JSON Schema gerado a partir destes records)

### `record ModelEvaluation(boolean fullyOffTopic, boolean notArgumentative, List<ModelCompetency> competencies)`

### `record ModelCompetency(int number, double score, String justification, List<String> tips)`

### `record ModelScores(boolean fullyOffTopic, boolean notArgumentative, List<ModelScore> competencies)`

### `record ModelScore(int number, double score)`

O modelo **não** devolve total: ele nem existe no schema.

## Saída da API

### `record CompetencyEvaluation(int number, int score, String justification, List<String> tips)`

| Campo | Regra |
|---|---|
| `number` | 1 a 5 |
| `score` | Um de 0, 40, 80, 120, 160, 200 (já normalizado) |
| `justification` | Curta, em PT-BR; no modo reduzido, ausente |
| `tips` | 2 a 3 itens, em PT-BR; no modo reduzido, ausente |

### `record EssayEvaluation(List<CompetencyEvaluation> competencies, int totalScore, ZeroReason zeroReason)`

| Campo | Regra |
|---|---|
| `competencies` | Sempre 5, ordenadas de 1 a 5 |
| `totalScore` | Soma das 5 notas normalizadas (0–1000) |
| `zeroReason` | `null` quando não há nota zero; senão o motivo |

### `record EssayScores(List<Integer> scores, int totalScore, ZeroReason zeroReason)` (modo reduzido)

`scores` tem 5 notas normalizadas, na ordem C1 a C5.

### `enum ZeroReason { TOO_SHORT, OFF_TOPIC, NOT_ARGUMENTATIVE }`

| Valor | Quem decide | Quando |
|---|---|---|
| `TOO_SHORT` | App, sem chamar o modelo | 7 linhas não vazias ou menos |
| `OFF_TOPIC` | Modelo (`fullyOffTopic`) | Fuga total ao tema |
| `NOT_ARGUMENTATIVE` | Modelo (`notArgumentative`) | Texto não dissertativo-argumentativo |

Com qualquer motivo, todas as notas e o total são 0. Se o modelo marcar os dois booleanos,
vale `OFF_TOPIC`.

## Regras do `ScoreNormalizer` (casos que o teste DEVE cobrir)

| Nota crua | Normalizada | Caso |
|---|---|---|
| 0 | 0 | valor permitido |
| 120 | 120 | valor permitido |
| 200 | 200 | valor permitido |
| 150 | 160 | mais perto de 160 |
| 130 | 120 | mais perto de 120 |
| 20 | 40 | empate → para cima |
| 180 | 200 | empate → para cima |
| 152.5 | 160 | casa decimal |
| -40 | 0 | abaixo do limite |
| 230 | 200 | acima do limite |
| 1000 | 200 | muito acima |

Validação da lista de competências:

| Entrada | Resultado |
|---|---|
| números 1–5, em ordem | ordenada, normalizada |
| números 1–5, fora de ordem | ordenada de 1 a 5 |
| 4 competências | `EvaluationFormatException` |
| 6 competências | `EvaluationFormatException` |
| número repetido (por exemplo dois `3` e nenhum `5`) | `EvaluationFormatException` |
| número fora de 1–5 (0 ou 6) | `EvaluationFormatException` |
| lista nula | `EvaluationFormatException` |

Total: soma das normalizadas; por exemplo, cruas 150, 130, 20, 230, -40 → 160 + 120 + 40 +
200 + 0 = **520**.

A cópia em Python usada pelo experimento 002 DEVE passar pelos mesmos casos.

## Mapeamento de falhas para status

| Situação | Exceção | Status |
|---|---|---|
| Tema ou texto ausente/vazio; `mode` desconhecido | `InvalidRequestException` | 400 |
| Ollama fora do ar ou modelo não instalado | `EvaluationUnavailableException` | 503 |
| JSON do modelo inválido ou competências erradas | `EvaluationFormatException` | 502 |

## Transições de estado

Nenhuma; cada requisição é independente.
