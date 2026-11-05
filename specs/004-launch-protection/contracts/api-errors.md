# Contrato: erros da API

Vale para `POST /api/transcriptions` e `POST /api/evaluations`. Todo erro tem corpo JSON `{"message": "<texto em português>"}` e nada mais: sem `timestamp`, `path`, `trace` nem nome de classe.

## POST /api/evaluations

| Situação | Status | `message` |
|---|---|---|
| Tema ausente, vazio ou só espaços | 400 | Informe o tema da redação. |
| Texto ausente, vazio ou só espaços | 400 | Envie o texto da redação. |
| Tema com mais de 300 caracteres | 400 | O tema pode ter no máximo 300 caracteres. |
| Texto com mais de 6.000 caracteres | 400 | A redação pode ter no máximo 6.000 caracteres. |
| Corpo que não é JSON válido | 400 | Pedido inválido. Envie o tema e o texto da redação. |
| Menos de 70 palavras | **200** | (resposta normal: 5 competências com nota 0, `zeroReason: "TOO_SHORT"`) |
| Limite da origem atingido | 429 + `Retry-After: <s>` | Você atingiu o limite de correções por agora. Tente de novo mais tarde. |
| Teto do dia atingido | 429 + `Retry-After: <s>` | O Penssay atingiu o limite de correções de hoje. Volte amanhã. |
| Resposta do modelo fora do formato (após a 2ª tentativa) | 502 | Não conseguimos concluir a correção. Tente de novo. |
| Provedor fora do ar, chave recusada ou limite do provedor | 503 | O serviço de correção está indisponível no momento. Tente de novo em alguns minutos. |
| Erro inesperado | 500 | Algo deu errado do nosso lado. Tente de novo. |

## POST /api/transcriptions (multipart, campo `photo`)

| Situação | Status | `message` |
|---|---|---|
| Campo `photo` ausente ou arquivo vazio | 400 | Envie a foto da redação. |
| Conteúdo que não é JPEG, PNG nem WebP | 400 | Envie uma foto em JPEG, PNG ou WebP. |
| Arquivo acima de 10 MB | 413 | A foto é muito grande. Envie uma foto de até 10 MB. |
| Limite da origem atingido | 429 + `Retry-After: <s>` | Você atingiu o limite de fotos por agora. Tente de novo mais tarde. |
| Teto do dia atingido | 429 + `Retry-After: <s>` | O Penssay atingiu o limite de leituras de foto de hoje. Volte amanhã. |
| Resposta do provedor quebrada (após a 2ª tentativa) | 502 | Não conseguimos ler a foto. Tente de novo. |
| Provedor fora do ar, chave recusada ou limite do provedor | 503 | O serviço de leitura está indisponível no momento. Tente de novo em alguns minutos. |
| Erro inesperado | 500 | Algo deu errado do nosso lado. Tente de novo. |

## Outros

| Situação | Status | `message` |
|---|---|---|
| Rota inexistente | 404 | Página não encontrada. |
| Método errado (por exemplo, GET na API) | 405 | Método não permitido. |

**Para a tela**: no 429, mostre a `message`. `Retry-After` dá os segundos, se quiser mostrar "tente em X minutos". Nos 502 e 503, ofereça "tentar de novo".
