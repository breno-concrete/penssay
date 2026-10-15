# Contrato: API de Transcrição

## `POST /api/transcriptions`

**Content-Type**: `multipart/form-data`

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `photo` | arquivo | sim | JPEG ou PNG, até 10 MB |

### Resposta `200 OK`

```json
{
  "text": "A democratização do acesso ao cinema no Brasil [?enfrenta] desafios..."
}
```

- `[?palavra]`: leitura incerta. `[illegible]`: palavra ilegível. Riscadas são omitidas.
- Somente o texto, sem título nem comentários.

### Erros

Todos com corpo `{"message": "<texto em português>"}`.

| Status | Quando | Mensagem |
|---|---|---|
| 400 | Nenhuma foto ou arquivo vazio | `Nenhuma foto foi recebida. Envie uma imagem JPEG ou PNG.` |
| 400 | Tipo não suportado | `Formato não suportado. Envie uma imagem JPEG ou PNG.` |
| 400 | Acima de 10 MB | `A foto é maior que 10 MB. Envie uma imagem menor.` |
| 422 | Modelo não achou texto manuscrito | `Nenhum texto manuscrito foi encontrado na foto.` |
| 503 | Ollama fora do ar ou modelo ausente | `O serviço de transcrição está indisponível. Verifique se o Ollama está em execução.` |

### Configuração relacionada

| Propriedade | Padrão | Efeito |
|---|---|---|
| `penssay.transcription.model` | `qwen2.5vl:7b` | Modelo de visão usado |
| `spring.ai.ollama.base-url` | `http://localhost:11434` | Servidor Ollama |
| `spring.servlet.multipart.max-file-size` | `10MB` | Limite de upload |

### Testes de contrato (resumo)

- Foto JPEG válida (modelo mockado) → 200 com `text`.
- Sem `photo` → 400. Arquivo `.txt` → 400. Arquivo vazio → 400.
- Serviço sinalizando "sem texto" → 422. Serviço indisponível → 503.
