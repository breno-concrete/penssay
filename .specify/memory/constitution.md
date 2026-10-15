<!--
Sync Impact Report
- Version change: 1.0.0 → 1.1.0 (MINOR: exceção explícita ao princípio II)
- Princípios modificados: II. Privacidade dos Dados (nova exceção para o experimento de treino)
- Princípio I esclarecido: vale para o app; experimentos offline de treino seguem o princípio II
- Seções adicionadas/removidas: nenhuma
- Templates dependentes: nenhum alterado (lidos em tempo de execução)
- TODOs adiados: nenhum
- Histórico: 1.0.0 (2026-10-14) ratificação inicial
-->

# Constituição do Penssay

## Princípios Fundamentais

### I. IA 100% Local (INEGOCIÁVEL)
Toda chamada de modelo feita pelo app DEVE ir ao servidor Ollama local. É PROIBIDO adicionar
ao app chamada a qualquer API de IA hospedada ou paga. Fotos e textos de redações enviados
pelo estudante NUNCA saem da máquina. Scripts de experimento fora do app (em `experiments/`)
seguem a exceção do princípio II.
**Motivo**: custo zero por redação, privacidade do aluno e a premissa open source do desafio.

### II. Privacidade dos Dados
Fotos e textos de redações NUNCA são commitados, publicados no post ou registrados em
logs. Amostras ficam em `samples/`, que DEVE permanecer no `.gitignore`. Apenas métricas
agregadas podem ser divulgadas.
**Exceção (experimento de treino)**: redações PODEM ser enviadas a um serviço externo de
treino somente se (a) o autor da redação deu consentimento explícito e registrado para esse
uso, (b) o texto foi anonimizado antes do envio (sem nome, escola, cidade ou dado pessoal) e
(c) o envio é feito por script de experimento, nunca pelo app.
**Motivo**: as redações são de estudantes reais (o autor, amigos), com consentimento limitado.

### III. Escopo Enxuto
O produto NÃO DEVE incluir banco de dados, autenticação, contas, histórico nem framework de
frontend (a interface é um único `index.html` estático). Qualquer item fora do escopo do
README exige emenda desta constituição antes de ser implementado.
**Motivo**: o prazo de entrega é curto e o MVP precisa fechar sozinho.

### IV. Integridade do Domínio
Os prompts DEVEM viver em arquivos `.st` em `src/main/resources/prompts/`, nunca fixos em
classes Java. A aritmética e a validação das notas (arredondar para múltiplos de 40, limitar
a 0–200, somar o total) DEVEM acontecer em Java, em `ScoreNormalizer`; o modelo NUNCA é
confiável para fazer contas. A transcrição NUNCA corrige o texto do aluno.
**Motivo**: a Competência 1 depende dos erros reais, e modelos pequenos erram aritmética.

### V. Testes sem Modelo Real
Os testes usam JUnit 5 e Mockito. Testes unitários DEVEM mockar `ChatClient` e NUNCA chamar
um modelo real. `ScoreNormalizer` DEVE ter cobertura total (arredondamento, limites e
soma). Avaliações com modelo real (WER, estabilidade, MAE) são experimentos registrados em
`docs/evaluation.md`, não testes automatizados.
**Motivo**: testes rápidos, determinísticos e executáveis em qualquer máquina.

## Convenções e Restrições Técnicas

- Java 21, Maven, Spring Boot e Spring AI com o starter do Ollama.
- DTOs e objetos de valor DEVEM ser `record`. Lombok é PROIBIDO.
- Injeção APENAS por construtor; nada de `@Autowired` em campo.
- Controllers DEVEM ser finos: validar a entrada, chamar o serviço, devolver a resposta.
- Identificadores, comentários e mensagens de commit em inglês. Conteúdo dos prompts e textos
  voltados ao usuário em português do Brasil.
- O modelo de transcrição e o de avaliação DEVEM ser valores de configuração
  (`application.properties`), nunca constantes no código.

## Fluxo de Desenvolvimento e Aprendizado

- Commits em Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`), pequenos e
  frequentes.
- **Definition of done**: (1) `./mvnw test` passa; (2) o fluxo ponta a ponta (foto →
  transcrição → revisão → avaliação) funciona do celular; (3) o README é atualizado quando um
  endpoint, propriedade ou regra muda.
- **Build-to-learn**: o autor escreve o código central e a IA explica antes e revisa (skill
  `learn-mode`). Boilerplate pode ser gerado pela IA. Tarefas de aprendizado são registradas
  em `docs/learning-log.md`.
- Prazo de entrega: post no DEV até 18/10/2026, 23:59 PDT. Em conflito entre o MVP
  (transcrição, avaliação e interface) e experimentos, o MVP tem prioridade. Commits após o
  prazo DEVEM ser listados na seção "Post-deadline changes" do README.

## Governança

Esta constituição prevalece sobre outras práticas do projeto. Emendas exigem registro neste
arquivo, com atualização da versão e do Sync Impact Report. O versionamento segue SemVer:
MAIOR para remoção ou redefinição incompatível de princípio; MENOR para novo princípio ou
orientação materialmente ampliada; PATCH para esclarecimentos e ajustes de redação. Toda
revisão de código e todo `/speckit-plan` DEVEM verificar a conformidade com os princípios
acima; desvios exigem justificativa escrita. Orientações operacionais para agentes ficam na
seção "Notes for AI coding agents" do `README.md`.

**Version**: 1.1.0 | **Ratified**: 2026-10-14 | **Last Amended**: 2026-10-14
