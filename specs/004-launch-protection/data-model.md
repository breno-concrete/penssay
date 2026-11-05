# Modelo de dados: Proteção para o lançamento público

Não há banco de dados (constituição, princípio III). Todo estado fica na memória do processo e se perde ao reiniciar.

## UsageKind (enum)

`EVALUATION`, `TRANSCRIPTION`. Cada tipo tem seus próprios limites.

## Janela por origem (estado interno do `UsageLimiter`)

| Campo | Tipo | Regra |
|---|---|---|
| origem | `String` | IP do cliente real (`request.getRemoteAddr()` com `forward-headers-strategy=native`) |
| início da janela | `Instant` | Quando passa de `início + window`, a janela é reiniciada com contagem 0 |
| contagem | `int` | Reservar só se `contagem < limite por origem`; senão `UsageLimitException` com os segundos até `início + window` |

Limpeza: quando o mapa de um tipo passa de 10.000 origens, as janelas vencidas são removidas.

## Uso do dia (estado interno do `UsageLimiter`)

| Campo | Tipo | Regra |
|---|---|---|
| data | `LocalDate` em `America/Sao_Paulo` | Se a data atual for outra, a contagem volta a 0 |
| contagem | `int` por `UsageKind` | Reservar só se `contagem < teto diário`; senão `UsageLimitException` com os segundos até a meia-noite de Brasília |

A reserva da origem e a do dia acontecem juntas e de forma atômica. Se o teto do dia recusar, a vaga da origem não é consumida.

## UsageLimitException

| Campo | Tipo | Uso |
|---|---|---|
| kind | `UsageKind` | escolhe a mensagem |
| scope | `ORIGIN` ou `DAILY` | escolhe a mensagem |
| retryAfterSeconds | `long` | cabeçalho `Retry-After` |

## Configuração (`application.properties`, prefixo `penssay.limits`)

| Propriedade | Padrão | Requisito |
|---|---|---|
| `enabled` | `true` | research.md, 9 |
| `window` | `1h` | RF-001 |
| `evaluations-per-origin` | `10` | RF-001 |
| `transcriptions-per-origin` | `20` | RF-001 |
| `evaluations-per-day` | `1500` | RF-004 |
| `transcriptions-per-day` | `3000` | RF-004 |
| `max-text-chars` | `6000` | RF-005 |
| `max-theme-chars` | `300` | RF-005 |

As propriedades são lidas por um `record LimitsProperties` com `@ConfigurationProperties`.

## PhotoValidator

Entrada: `MultipartFile`. Saída: `MimeType` detectado (`image/jpeg`, `image/png`, `image/webp`). Recusa com `InvalidRequestException` quando o arquivo é nulo, vazio ou tem bytes iniciais desconhecidos.

## Regra das 70 palavras (`EvaluationService`)

Palavras = partes de `text.trim().split("\\s+")`. Com menos de 70, o serviço devolve `EssayEvaluation` com 5 competências nota 0, a justificativa "Texto insuficiente: a redação tem menos de 70 palavras (cerca de 7 linhas), e o ENEM atribui nota zero a textos com até 7 linhas.", sem dicas, `totalScore = 0` e `zeroReason = TOO_SHORT`. Não chama o modelo nem o `UsageLimiter`.
