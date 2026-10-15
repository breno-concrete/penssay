# Pesquisa (Fase 0): Transcrição de redação manuscrita

Todas as decisões abaixo foram verificadas nos jars do Spring AI 2.0.1 em `~/.m2` ou no Maven
Central em 2026-10-14. Não restam itens "NEEDS CLARIFICATION".

## 1. Como enviar a imagem ao modelo

- **Decisão**: usar `ChatClient` com `.user(u -> u.text(prompt).media(mimeType, resource))`.
- **Base**: `ChatClient.PromptUserSpec` tem `media(MimeType, Resource)` e `media(Media...)`;
  `Media` aceita `(MimeType, Resource)`.
- **Alternativas**: chamar `OllamaChatModel` direto (mais baixo nível, perde o fluxo fluente);
  `RestClient` manual para o Ollama (reinventa o que o Spring AI já faz).
- **Detalhe**: a foto chega como `byte[]` do `MultipartFile`; embrulhar em `ByteArrayResource`.

## 2. Como escolher o modelo por configuração

- **Decisão**: injetar `@Value("${penssay.transcription.model}")` no serviço e passar
  `.options(OllamaChatOptions.builder().model(model))` em cada chamada.
- **Base**: na versão 2.0.1, `ChatClientRequestSpec.options(B)` recebe um **builder**
  (`ChatOptions.Builder`), não um objeto `ChatOptions` pronto, e `OllamaChatOptions.builder()`
  existe. Isso muda em relação a tutoriais da série 1.x: conferir antes de copiar exemplos.
- **Alternativa**: definir o modelo só em `spring.ai.ollama.chat.options.model`. Rejeitada
  porque transcrição e avaliação usam modelos diferentes (RF-010 pede uma propriedade
  própria).
- `temperature=0` já está em `spring.ai.ollama.chat.options.temperature`.

## 3. Carregar o prompt de arquivo

- **Decisão**: `@Value("classpath:/prompts/transcription.st") Resource` e passar para
  `.user(...)`/`.system(...)` via `text(Resource)`.
- **Base**: `PromptUserSpec.text(Resource)` e `ChatClientRequestSpec.system(Resource)`
  existem. O módulo `spring-ai-template-st` já está no classpath (renderiza `.st`).
- **Cuidado**: o formato StringTemplate usa `{}` como delimitador; evitar chaves literais no
  prompt (como `{"text": ...}`), pois seriam interpretadas como variáveis.
- **Alternativa**: `String` fixa no Java. Rejeitada pela constituição (princípio IV).

## 4. Validação de upload e erros 400

- **Decisão**: o controller valida (a) arquivo ausente/vazio, (b) content type diferente de
  `image/jpeg` e `image/png`, (c) tamanho > 10 MB, lançando `InvalidPhotoException`;
  `@RestControllerAdvice` converte em 400 + `ErrorResponse(message)` em português.
- **Limite do servidor**: `spring.servlet.multipart.max-file-size=10MB` já configurado; o
  estouro gera `MaxUploadSizeExceededException`, que também deve virar 400 com a mesma
  mensagem (tratar no advice). A checagem manual de tamanho cobre o limite exato.
- **Tipo**: não confiar só no `Content-Type` declarado; conferir os bytes iniciais
  (assinatura JPEG `FF D8 FF`, PNG `89 50 4E 47`).
- **Alternativa**: Bean Validation customizada. Rejeitada por excesso para o escopo.

## 5. Testes no Spring Boot 4

- **Decisão**: unitário do serviço com Mockito puro (`ChatClient` mockado, `RETURNS_DEEP_STUBS`
  para a cadeia fluente); teste web com `@WebMvcTest` + `MockMvc`.
- **Base**: `spring-boot-starter-test` não traz mais o suporte a MockMvc no Boot 4; é preciso
  `spring-boot-starter-webmvc-test`, que existe no Maven Central na 4.1.1 (HTTP 200).
- **Alternativa**: `@SpringBootTest` completo. Rejeitada: sobe o contexto inteiro, mais lento.

## 6. Indisponibilidade do Ollama (RF-012)

- **Decisão**: capturar a falha de conexão na chamada ao modelo no serviço e lançar
  `TranscriptionUnavailableException`; o advice responde **503** com mensagem em português.
- **Alternativa**: deixar propagar 500. Rejeitada: a spec exige mensagem clara.

## 7. "Nenhum texto encontrado" (RF-011)

- **Decisão**: o prompt instrui o modelo a responder exatamente com uma sentinela fixa
  (`[no-text]`) quando não houver texto manuscrito; o serviço detecta a sentinela e o
  controller responde **422** com a mensagem "Nenhum texto manuscrito foi encontrado na foto".
- **Alternativa**: heurística de texto vazio/curto. Rejeitada: pouco confiável.
- **Risco**: modelo pequeno pode ignorar a sentinela; medir na comparação de modelos.

## 8. Escolha de modelos para uma GPU de 6 GB

- **Decisão**: candidatos `qwen2.5vl:7b` (padrão do README), `qwen2.5vl:3b` e `gemma3:4b`.
- **Base**: o 7B em quantização Q4 ocupa cerca de 6 GB e tende a vazar para a CPU numa GPU de
  6 GB; o 3B e o gemma3:4b cabem na VRAM. O resultado medido decide o padrão; trocar é só a
  propriedade (RF-010).
- **Verificar**: `ollama ps` mostra a divisão GPU/CPU durante cada teste; registrar em
  `docs/evaluation.md` (spec, história 4, cenário 3).

## 9. Medição de WER

- **Decisão**: script Python simples (`experiments/wer.py`) com distância de edição por
  palavras, comparando a transcrição do modelo com a referência feita à mão.
  Preservação de erros: conferir manualmente as 5 palavras erradas propositais.
- **Alternativa**: biblioteca `jiwer`. Aceitável se instalar for trivial; o script próprio
  também serve para o aprendizado.
