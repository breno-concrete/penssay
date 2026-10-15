# Quickstart: validar a transcrição ponta a ponta

Contrato e regras: [contracts/transcriptions-api.md](contracts/transcriptions-api.md) e
[data-model.md](data-model.md).

## Pré-requisitos

- Java 21 e Maven (o wrapper `./mvnw` já está no repositório).
- Ollama em execução com os modelos baixados:
  `ollama pull qwen2.5vl:7b`, `ollama pull qwen2.5vl:3b`, `ollama pull gemma3:4b`.
- Em `samples/` (git-ignored): 3 a 5 fotos manuscritas, cada uma com `<nome>.ref.txt`; uma
  delas com 5 erros propositais (`<nome>.erros.txt`).

## 1. Testes automatizados

```bash
./mvnw test
```

Esperado: tudo verde, sem nenhuma chamada a modelo real (`ChatClient` mockado).

## 2. Subir a aplicação

```bash
./mvnw spring-boot:run
```

## 3. Caminho feliz

```bash
curl -F "photo=@samples/redacao1.jpg" http://localhost:8080/api/transcriptions
```

Esperado: `200` com `{"text": "..."}`; erros preservados; só o texto.

## 4. Entradas inválidas

```bash
curl -i -F "photo=@README.md" http://localhost:8080/api/transcriptions   # 400, formato
curl -i -X POST http://localhost:8080/api/transcriptions                  # 400, sem foto
```

Esperado: 400 com mensagem em português. Para o limite, enviar um arquivo > 10 MB.

## 5. Ollama fora do ar

Encerrar o Ollama e repetir o passo 3. Esperado: `503` com mensagem de indisponibilidade.

## 6. Comparação de modelos

Para cada modelo (`qwen2.5vl:7b`, `qwen2.5vl:3b`, `gemma3:4b`):

1. Definir `penssay.transcription.model` e reiniciar a aplicação.
2. Transcrever cada amostra, anotando o tempo; rodar `ollama ps` durante a chamada (GPU/CPU).
3. Calcular WER com `python experiments/wer.py samples/x.ref.txt saida.txt`.
4. Conferir à mão quantos dos 5 erros propositais foram preservados.
5. Registrar a linha na tabela de `docs/evaluation.md`.

## Critérios de aceite (da spec)

- CS-001: ≥ 4 dos 5 erros propositais preservados.
- CS-002: WER < 15 por 100 palavras nas amostras legíveis.
- CS-003: ≤ 90 s por foto.
- CS-004: 100% dos envios inválidos recusados com mensagem.
- CS-005: funciona sem internet.
- CS-006: ≥ 2 modelos na tabela, três medidas cada.
- CS-007: trocar o modelo só por configuração.
