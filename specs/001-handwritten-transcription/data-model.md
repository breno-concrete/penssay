# Modelo de Dados: Transcrição de redação manuscrita

Não há persistência (constituição, princípio III). Tudo vive em memória durante a requisição.

## Entidades

### Foto da redação (entrada, não persistida)

| Campo | Tipo | Regra |
|---|---|---|
| `photo` | arquivo multipart | Obrigatório; não vazio |
| formato | `image/jpeg` ou `image/png` | Conferido pelo content type **e** pela assinatura dos bytes |
| tamanho | bytes | ≤ 10 MB (10 485 760 bytes) |

Descartada ao fim da requisição; nunca logada nem gravada em disco.

### Transcrição (saída): `record TranscriptionResponse(String text)`

| Campo | Tipo | Regra |
|---|---|---|
| `text` | `String` | Texto fiel da redação; sem título nem comentários; pode conter `[?palavra]` e `[illegible]` |

### Erro (saída): `record ErrorResponse(String message)`

| Campo | Tipo | Regra |
|---|---|---|
| `message` | `String` | Mensagem em português do Brasil, em linguagem simples |

### Amostra de avaliação (arquivos, fora do app)

Em `samples/` (git-ignored): `<nome>.jpg` + `<nome>.ref.txt` (transcrição de referência feita à
mão). Uma das amostras contém 5 erros propositais, listados em `<nome>.erros.txt`.

### Resultado de comparação (documentação)

Linha da tabela em `docs/evaluation.md`: modelo, WER (palavras erradas por 100), erros
propositais preservados (x/5), latência média (s), uso de GPU/CPU (`ollama ps`).

## Transições de estado

Nenhuma; cada requisição é independente (sem sessão nem histórico).

## Mapeamento de falhas para status

| Situação | Exceção | Status |
|---|---|---|
| Foto ausente/vazia | `InvalidPhotoException` | 400 |
| Tipo diferente de JPEG/PNG | `InvalidPhotoException` | 400 |
| Tamanho > 10 MB | `InvalidPhotoException` / `MaxUploadSizeExceededException` | 400 |
| Modelo devolve `[no-text]` | (tratado no controller) | 422 |
| Ollama indisponível | `TranscriptionUnavailableException` | 503 |
