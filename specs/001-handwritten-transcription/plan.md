# Plano de Implementação: Transcrição de redação manuscrita

**Branch**: `001-handwritten-transcription` | **Data**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Entrada**: Especificação da feature em `/specs/001-handwritten-transcription/spec.md`

## Resumo

Expor `POST /api/transcriptions`, que recebe a foto de uma redação manuscrita e devolve
`{"text": "..."}` com a transcrição fiel (erros preservados, `[?palavra]` e `[illegible]`).
A abordagem é um `TranscriptionController` fino que valida o arquivo e delega a um
`TranscriptionService`; o serviço chama um modelo de visão do Ollama via Spring AI
(`ChatClient` + `Media`), com o prompt em `prompts/transcription.st` e o modelo escolhido por
`penssay.transcription.model`. A feature inclui ainda a comparação de modelos em
`docs/evaluation.md`, respeitando a GPU de 6 GB.

## Contexto Técnico

**Linguagem/Versão**: Java 21 (JDK Microsoft 21.0.10 instalado)

**Dependências Principais**: Spring Boot 4.1.1 (`spring-boot-starter-web`), Spring AI 2.0.1
(`spring-ai-starter-model-ollama`), já no `pom.xml`

**Armazenamento**: N/A (sem banco de dados; a foto é descartada após a transcrição)

**Testes**: JUnit 5 + Mockito (`spring-boot-starter-test`); `@WebMvcTest` exige adicionar
`spring-boot-starter-webmvc-test` (no Spring Boot 4 o suporte a MockMvc saiu do starter genérico;
ver research.md, decisão 5)

**Plataforma Alvo**: Windows 11 local (desenvolvimento) com Ollama em `localhost:11434`; GPU
RTX 3050 Laptop com 6 GB de VRAM

**Tipo de Projeto**: serviço web (API REST em uma única aplicação)

**Metas de Desempenho**: transcrição de uma foto em até 90 s na máquina de desenvolvimento (CS-003)

**Restrições**: 100% local (sem API externa); upload ≤ 10 MB; modelo configurável; `temperature=0`

**Escala/Escopo**: um usuário por vez, uma foto por requisição; sem concorrência alta

## Verificação da Constituição

*PORTÃO: deve passar antes da pesquisa (Fase 0). Reavaliado após o design (Fase 1).*

| Princípio | Situação | Observação |
|---|---|---|
| I. IA 100% Local | Passa | Só `spring.ai.ollama.base-url=http://localhost:11434`; nenhum cliente de API externa |
| II. Privacidade dos Dados | Passa | Foto só em memória; `samples/` ignorado; nada de foto/texto em log |
| III. Escopo Enxuto | Passa | Sem DB, auth, histórico nem UI nesta feature |
| IV. Integridade do Domínio | Passa | Prompt em `.st`; transcrição nunca corrige; sem notas aqui |
| V. Testes sem Modelo Real | Passa | `ChatClient` mockado nos testes; comparação de modelos é experimento, não teste |
| Convenções Técnicas | Passa | `record` nos DTOs, injeção por construtor, controller fino, modelo em propriedade |

**Reavaliação pós-design**: sem violações. Complexity Tracking não é necessário.

## Estrutura do Projeto

### Documentação (esta feature)

```text
specs/001-handwritten-transcription/
├── plan.md              # Este arquivo
├── research.md          # Fase 0
├── data-model.md        # Fase 1
├── quickstart.md        # Fase 1
├── contracts/
│   └── transcriptions-api.md   # Fase 1
└── tasks.md             # Fase 2 (/speckit-tasks, NÃO criado aqui)
```

### Código-Fonte (raiz do repositório)

```text
src/main/java/br/com/breno/penssay/
├── PenssayApplication.java                  # já existe
└── transcription/
    ├── TranscriptionController.java         # valida o arquivo, delega, devolve JSON
    ├── TranscriptionService.java            # monta a chamada ao modelo (ChatClient + Media)
    ├── TranscriptionResponse.java           # record(String text)
    ├── ErrorResponse.java                   # record(String message)
    ├── InvalidPhotoException.java           # foto inválida → 400
    ├── TranscriptionUnavailableException.java  # Ollama fora do ar → 503
    └── TranscriptionExceptionHandler.java   # @RestControllerAdvice → ErrorResponse em PT-BR

src/main/resources/
├── application.properties                   # já existe (+ limites/propriedades)
└── prompts/
    └── transcription.st                     # regras de transcrição (PT-BR)

src/test/java/br/com/breno/penssay/transcription/
├── TranscriptionServiceTest.java            # Mockito: ChatClient mockado
└── TranscriptionControllerTest.java         # @WebMvcTest: validações 400 e caminho feliz

docs/
├── evaluation.md                            # comparação de modelos (RF-013)
└── learning-log.md                          # já existe

samples/                                     # git-ignored: fotos e referências
experiments/
└── wer.py                                   # cálculo de WER (utilitário, fora do app Java)
```

**Decisão de Estrutura**: projeto único Maven, pacote por funcionalidade
(`transcriptionService`), como já descrito no README. `experiments/` guarda só um script utilitário
para medir WER, fora do app.

## Divisão Build-to-Learn

**[B]** = Breno escreve (Claude explica antes e revisa). **[C]** = boilerplate, Claude escreve.

| Peça | Quem | Por quê |
|---|---|---|
| `pom.xml`, `application.properties`, wrapper, `.gitignore` | [C] feito | Boilerplate |
| Dependência `spring-boot-starter-webmvc-test` no pom | [C] | Boilerplate |
| `TranscriptionResponse`, `ErrorResponse` (records) | [C] | Triviais |
| `prompts/transcription.st` | **[B]** | Prompt engineering é o núcleo do aprendizado |
| `TranscriptionService` (`ChatClient`, `Media`, opções por chamada) | **[B]** | Conceito central do Spring AI |
| `TranscriptionController` + validação (tipo, tamanho, vazio) | **[B]** | Controller fino e validação |
| `InvalidPhotoException` + `TranscriptionExceptionHandler` | **[B]** guiado | Tratamento de erros |
| `TranscriptionServiceTest` (Mockito) | **[B]** guiado | Breno é iniciante em Mockito |
| `TranscriptionControllerTest` (`@WebMvcTest`) | [C] com explicação | Setup de teste web novo no Boot 4 |
| `experiments/wer.py` | [C] | Utilitário; Breno só interpreta os números |
| `docs/evaluation.md` (comparação e análise) | **[B]** | Resultados e conclusões são dele |

## Complexity Tracking

Sem violações da constituição; nada a justificar.
