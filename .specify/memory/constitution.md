<!--
Sync Impact Report
- Version change: 1.1.0 → 2.0.0 (MAJOR: princípio I redefinido)
- Princípios modificados:
  - I. "IA 100% Local" → "Custo de IA Controlado": o app hospedado usa um provedor configurável
    (OpenRouter desde o commit 6e50e93), com teto de crédito e limites no app; Ollama vira perfil local
  - II. Privacidade: a redação sai da máquina só para o provedor ler e corrigir; nunca é guardada
    nem registrada em log, e o aluno é avisado na tela
- Convenções: starter do Ollama → starter OpenAI (fala com OpenRouter e com o Ollama local)
- Fluxo: prazo do post no DEV substituído pelo lançamento no dia do ENEM (2026-11-08)
- Templates dependentes: nenhum alterado (lidos em tempo de execução)
- Specs afetadas: 003 (RF-009 e RF-014 citam modelo e máquina locais; valem os termos desta versão)
- TODOs adiados: nenhum
- Histórico: 1.0.0 (2026-10-14) ratificação inicial; 1.1.0 (2026-10-14) exceção de treino no princípio II
-->

# Constituição do Penssay

## Princípios Fundamentais

### I. Custo de IA Controlado (INEGOCIÁVEL)
O app PODE chamar um provedor de modelos hospedado, escolhido por configuração (padrão:
OpenRouter), e DEVE rodar com o Ollama local pelo perfil `local`. Todo uso pago DEVE ter teto:
a chave de produção tem limite de crédito no painel do provedor, e o app aplica limite por
origem, teto diário e limite de tamanho ANTES de qualquer chamada ao modelo. Entrada inválida
NUNCA gera chamada paga.
**Motivo**: o lançamento é um site público que a GPU local não atende, e um app aberto sem
teto pode esvaziar o crédito num único abuso.

### II. Privacidade dos Dados
A foto e o texto da redação são enviados ao provedor de modelos somente para serem lidos e
corrigidos, e o aluno DEVE ser avisado disso na tela. O Penssay NUNCA guarda, commita, publica
nem registra em log fotos ou textos de redações. Amostras ficam em `samples/`, que DEVE permanecer no `.gitignore`. Apenas métricas
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

- Java 21, Maven, Spring Boot e Spring AI com o starter OpenAI (OpenRouter em produção, Ollama
  pelo endpoint compatível no perfil `local`).
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
- Prazo de entrega: lançamento público no dia da redação do ENEM, 08/11/2026. Em conflito entre
  o lançamento (transcrição, avaliação, interface e proteção de custo) e experimentos, o
  lançamento tem prioridade. Commits após o prazo do DEV (18/10/2026) DEVEM ser listados na
  seção "Post-deadline changes" do README.

## Governança

Esta constituição prevalece sobre outras práticas do projeto. Emendas exigem registro neste
arquivo, com atualização da versão e do Sync Impact Report. O versionamento segue SemVer:
MAIOR para remoção ou redefinição incompatível de princípio; MENOR para novo princípio ou
orientação materialmente ampliada; PATCH para esclarecimentos e ajustes de redação. Toda
revisão de código e todo `/speckit-plan` DEVEM verificar a conformidade com os princípios
acima; desvios exigem justificativa escrita. Orientações operacionais para agentes ficam na
seção "Notes for AI coding agents" do `README.md`.

**Version**: 2.0.0 | **Ratified**: 2026-10-14 | **Last Amended**: 2026-11-05
