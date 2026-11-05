# Pesquisa: Proteção para o lançamento público

Decisões técnicas da feature 004. Cada uma traz o que foi escolhido, o porquê e o que foi descartado.

## 1. Limite por origem: contador próprio na memória

**Decisão**: Um componente `UsageLimiter` com `ConcurrentHashMap<String, Window>` por tipo de pedido (correção, foto), em janela fixa de 1 hora por origem. O relógio é um `java.time.Clock` injetado, para os testes avançarem o tempo sem `sleep`. Quando o mapa passa de 10.000 origens, as janelas vencidas são removidas, o que limita a memória.

**Motivo**: São umas 60 linhas sem dependência nova, fáceis de testar e de explicar. Há uma instância só (premissa da spec), então não é preciso estado compartilhado. A janela fixa permite até 2× o limite na virada da hora, o que é aceitável: o teto diário e o teto de crédito seguram o custo.

**Alternativas descartadas**:
- **Bucket4j**: dependência nova e mais conceito (token bucket) para o mesmo resultado neste tamanho.
- **Limite no proxy da hospedagem**: depende do provedor escolhido e não é testável por `./mvnw test`.
- **Filtro HTTP antes do controller**: contaria pedidos inválidos e textos curtos, que não custam nada. A spec diz que eles não contam.

## 2. Ordem das verificações

**Decisão**: (1) validar entrada (vazio, tamanho, tipo da foto) → (2) regra das 70 palavras (devolve zeros, sem custo) → (3) reservar vaga no limite da origem → (4) reservar vaga no teto do dia → (5) chamar o modelo. A reserva acontece antes da chamada e não é devolvida se o provedor falhar.

**Motivo**: Só pedidos que vão custar dinheiro consomem limite. Reservar antes da chamada impede que 50 pedidos simultâneos passem juntos por um teto que tinha 1 vaga. Não devolver em falha é mais simples, e uma falha pode ter sido cobrada mesmo assim.

**Consequência no código**: a origem do cliente vira um parâmetro de `EvaluationService.evaluate` e de `TranscriptionService.transcribe`, que chamam o `UsageLimiter` no ponto (3).

## 3. Origem real do cliente atrás do proxy

**Decisão**: `server.forward-headers-strategy=native`. O Tomcat (`RemoteIpValve`) só aceita `X-Forwarded-For` quando o pedido vem de um proxy interno (faixas de IP privado, o padrão do Tomcat) e usa o endereço mais à direita que não é proxy interno. Com isso, `request.getRemoteAddr()` passa a ser o IP do aluno.

**Motivo**: Um cliente que manda `X-Forwarded-For: 1.2.3.4` não escapa do limite. O proxy da hospedagem acrescenta o IP real à direita, e é esse que vale. Chamadas diretas de fora não vêm de IP privado, então o cabeçalho delas é ignorado.

**Risco**: Isso depende de o proxy da hospedagem usar IP privado. O passo 6 do `quickstart.md` confere no deploy: dois pedidos com `X-Forwarded-For` diferentes, a partir da mesma máquina, têm que cair no mesmo contador.

**Alternativa descartada**: Ler `X-Forwarded-For` à mão, pegando o primeiro valor. Esse valor é exatamente o que o cliente controla.

## 4. Teto do dia

**Decisão**: O mesmo `UsageLimiter` guarda um contador por tipo (correção, foto) e a data em `America/Sao_Paulo`. Quando a data muda, o contador volta a zero. A reserva é atômica (`synchronized` num método curto).

**Motivo**: É o disjuntor antes do teto de crédito da chave. Na memória basta: ao reiniciar, o contador zera, e o teto de crédito continua valendo (premissa da spec).

## 5. Validação da foto pelos bytes

**Decisão**: `PhotoValidator` lê os primeiros 12 bytes:
- JPEG: `FF D8 FF`
- PNG: `89 50 4E 47 0D 0A 1A 0A`
- WebP: `RIFF` nos bytes 0–3 e `WEBP` nos bytes 8–11

O validador devolve o `MimeType` detectado, e o serviço de transcrição usa esse tipo, nunca `photo.getContentType()`. Arquivo vazio ou ausente gera a mensagem "Envie a foto da redação.".

**Motivo**: O tipo declarado vem do cliente. Hoje, um envio sem tipo causa erro 500 (`parseMimeType(null)`).

**HEIC (iPhone)**: Fica fora. O Safari no iOS converte para JPEG ao enviar por `<input type="file" accept="image/*">`, e a tela vai redimensionar a foto por canvas e gerar JPEG de qualquer forma.

**Tamanho**: O limite de 10 MB já existe (`spring.servlet.multipart.max-file-size`). A exceção `MaxUploadSizeExceededException` passa a virar 413 com mensagem em português.

## 6. Tamanho máximo do texto e do tema

**Decisão**: 6.000 caracteres para o texto e 300 para o tema, conferidos no controller (`EvaluationController`) com `InvalidRequestException`.

**Motivo**: A folha do ENEM tem 30 linhas, cerca de 420 palavras e 2.700 caracteres. 6.000 é folga para quem digita. Sem esse limite, 1 MB de texto custa cerca de US$ 0,30 por pedido em vez de US$ 0,003.

**Defesa extra**: `server.tomcat.max-swallow-size` e o limite de corpo JSON ficam como estão. O limite em caracteres basta, porque o modelo só é chamado depois dessa verificação.

## 7. Tratamento central de erros

**Decisão**: `ApiExceptionHandler` (`@RestControllerAdvice`) em `web/`, sempre devolvendo `ErrorResponse(message)`:

| Exceção | Status |
|---|---|
| `InvalidRequestException`, `HttpMessageNotReadableException`, `MissingServletRequestPartException`, `MultipartException` | 400 |
| `MaxUploadSizeExceededException` | 413 |
| `UsageLimitException` (nova; carrega os segundos de espera e vira o cabeçalho `Retry-After`) | 429 |
| `EvaluationFormatException`, `JacksonException`, `OpenAIInvalidDataException` (falha após a 2ª tentativa) | 502 |
| `EvaluationUnavailableException`, `OpenAIIoException`, `OpenAIServiceException` (401 chave recusada, 429 do provedor, 5xx) | 503 |
| `NoResourceFoundException` | 404 |
| `HttpRequestMethodNotSupportedException` | 405 |
| `Exception` | 500, mensagem genérica |

Os logs registram só o nome da classe da exceção e o status, sem `getMessage()`. A mensagem do provedor pode ecoar parte do pedido.

**Motivo**: As exceções do SDK da OpenAI já são específicas. Mapeá-las no handler evita repetir `try/catch` nos dois serviços. Os serviços continuam fazendo só a 2ª tentativa que já existe.

**Absorve**: T027, T028, T029 e T038 da spec 003.

## 8. Servidor sob carga

**Decisão**: `spring.threads.virtual.enabled=true`. Cada pedido roda numa virtual thread, e a espera de ~12 s pelo provedor não ocupa uma thread do sistema. As 5 chamadas por correção já usam virtual threads (`EvaluationService`).

**Motivo**: São 200 threads de plataforma no Tomcat por padrão. Com 300 correções esperando, a fila parava tudo. Com virtual threads, o limite passa a ser `server.tomcat.max-connections` (8.192 por padrão).

**Medição**: `experiments/load_test.py` sobe um provedor falso que demora 12 s, dispara 300 correções e mede o tempo de um `GET /` durante a carga (CS-005).

## 9. Desligar limites para benchmark e testes locais

**Decisão**: `penssay.limits.enabled` (padrão `true`). O benchmark roda com `PENSSAY_LIMITS_ENABLED=false`.

**Motivo**: O benchmark faz 100 correções do mesmo IP, e com o limite de 10 por hora ele travaria. Liberar `localhost` automaticamente foi descartado: atrás de um proxy mal configurado, todo pedido pareceria vir de `localhost`.

## 10. Constituição desatualizada

A constituição v1.1.0 ainda diz:
- **Princípio I:** "IA 100% local; proibido API hospedada";
- **Princípio II:** "redações nunca saem da máquina";
- **Fluxo de desenvolvimento:** o prazo do post no DEV.

O app já usa a OpenRouter desde o commit `6e50e93`, decisão tomada em 2026-11-03 para o lançamento hospedado. Esta feature não cria a violação, mas depende dela.

**Decisão**: Emenda **2.0.0** (MAIOR, porque o princípio I é redefinido):
- **Princípio I** passa a ser "Custo de IA controlado". São permitidos modelos hospedados via provedor configurável, com teto de crédito e limites no app. O modelo continua sendo escolhido por configuração. Ollama local continua como perfil de desenvolvimento.
- **Princípio II** passa a dizer: a redação é enviada ao provedor só para ser lida e corrigida, e nunca é guardada, registrada em log nem commitada pelo Penssay. O aluno é avisado na tela.
- **Prazo:** sai o do DEV e entra o do ENEM (2026-11-08).

Ela é tarefa desta feature (T001), feita antes do código.
