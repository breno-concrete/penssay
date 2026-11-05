# Quickstart: validar a proteção para o lançamento

## Pré-requisitos

- Java 21. `OPENROUTER_API_KEY` só é necessária no passo 4.
- App rodando: `./mvnw spring-boot:run` (porta 8080).

## 1. Testes automatizados (sem modelo e sem rede)

```bash
./mvnw test
```

Esperado: todos passam (CS-006). Cobrem o limite por origem e o teto do dia (com relógio fixo), a validação da foto pelos bytes, a regra das 70 palavras sem chamada ao modelo, o mapeamento de erros do [contrato](contracts/api-errors.md) e o caso de quem troca o próprio `X-Forwarded-For` (CS-003).

## 2. Entrada inválida não chama o modelo

Com o app rodando, sem chave (`OPENROUTER_API_KEY=x`). Conferido em 2026-11-05: todos os casos abaixo deram o status e a mensagem do contrato.

| Pedido | Esperado |
|---|---|
| `curl -X POST localhost:8080/api/evaluations -H "Content-Type: application/json" -d '{"theme":"","text":"a"}'` | 400 "Informe o tema da redação." |
| texto de 6.001 caracteres | 400 com o limite |
| texto de 69 palavras | 200, tudo zero, `TOO_SHORT`, responde na hora |
| `curl -F photo=@algum.pdf localhost:8080/api/transcriptions` | 400 "Envie uma foto em JPEG, PNG ou WebP." |
| foto de 11 MB | 413 |
| `-d 'isto não é json'` | 400 sem detalhes internos |

## 3. Limites

Rode com limites baixos: `./mvnw spring-boot:run -Dspring-boot.run.arguments="--penssay.limits.evaluations-per-origin=2 --penssay.limits.evaluations-per-day=3"`.

- A 3ª correção válida do mesmo IP → 429 com `Retry-After`.
- Para testar o teto do dia, abaixe `evaluations-per-origin` só no teste automatizado, já que daqui só há um IP.

## 4. Provedor indisponível e chave recusada

- `OPENROUTER_API_KEY=invalida` → correção válida retorna 503 com a mensagem do contrato.
- No log, aparece só o tipo do erro, sem texto da redação (RF-011).

## 5. Carga (CS-005)

```bash
python experiments/load_test.py
```

O script sobe um provedor falso que demora 12 s, inicia o app apontando para ele com limites desligados, dispara 300 correções e mede um `GET /` durante a espera. Esperado: `GET /` em até 2 s.

Resultado em 2026-11-05, na máquina de desenvolvimento (rodar `./mvnw -q package -DskipTests` antes):

| Configuração | `GET /` mais lento durante a carga | Correção mais lenta | 300 correções |
|---|---|---|---|
| virtual threads ligadas (padrão) | 1,78 s ✅ | 17,2 s | todas 200 |
| `SPRING_THREADS_VIRTUAL_ENABLED=false` | 13,08 s ❌ | 27,1 s | todas 200 |

Sem virtual threads, o pedido leve fica na fila atrás das 200 threads do Tomcat ocupadas esperando o provedor.

## 6. No deploy (origem real)

- `curl -H "X-Forwarded-For: 1.1.1.1"` e `curl -H "X-Forwarded-For: 2.2.2.2"`, da mesma máquina, várias vezes: os dois têm que cair no **mesmo** contador e chegar juntos ao 429.
- Se cada cabeçalho ganhar seu próprio contador, o proxy da hospedagem não usa IP privado. Ajuste `server.tomcat.remoteip.internal-proxies` para a faixa do provedor antes de divulgar.

## 7. Benchmark local

```bash
PENSSAY_LIMITS_ENABLED=false ./mvnw spring-boot:run
python experiments/benchmark.py --dir samples/benchmark-ext --label ...
```
