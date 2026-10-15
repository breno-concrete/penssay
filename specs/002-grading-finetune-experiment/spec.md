# Especificação da Feature: Experimento de fine-tuning da correção

**Branch da Feature**: `002-grading-finetune-experiment`

**Criada em**: 2026-10-14

**Status**: Rascunho

**Entrada**: Descrição do usuário: "Experimento de fine-tuning da correção de redações (quinta-feira, 15/10/2026): medir se um modelo ajustado com LoRA em redações ENEM corrigidas dá notas C1–C5 mais próximas das notas humanas que o modelo base."

## Cenários de Usuário e Testes *(obrigatório)*

### História de Usuário 1 - Medir o modelo atual contra notas humanas (Prioridade: P1)

O autor quer saber, com números, o quanto as notas do Penssay se afastam das notas dadas por corretores humanos. Ele reúne redações já corrigidas (com as cinco notas por competência), separa uma parte que nunca será usada em treino e mede o erro de cada modelo de correção nessas redações.

**Por que esta prioridade**: É o resultado mínimo do dia. Mesmo que o treino falhe, a medição do modelo atual (Gemma, local) e do modelo base já é conteúdo verdadeiro e útil para o post, e funciona como plano de corte.

**Teste Independente**: Pode ser testada rodando a correção do modelo atual e do modelo base sobre o conjunto de teste e conferindo que `docs/evaluation.md` mostra o erro médio de cada um por competência e no total.

**Cenários de Aceitação**:

1. **Dado** o conjunto de teste com notas humanas, **Quando** o modelo de correção atual do app corrige cada redação, **Então** o erro absoluto médio por competência (C1–C5) e no total fica registrado.
2. **Dado** o mesmo conjunto de teste, **Quando** o modelo base (sem ajuste) corrige cada redação, **Então** o mesmo erro fica registrado na mesma tabela, permitindo a comparação direta.
3. **Dado** que as notas dos modelos passam pela mesma normalização do app (múltiplos de 40, 0–200, total somado), **Quando** o erro é calculado, **Então** ele usa as notas normalizadas, não o texto cru do modelo.

---

### História de Usuário 2 - Ajustar o modelo e mostrar se ele melhora (Prioridade: P2)

O autor ajusta o modelo base com as redações de treino e mede o modelo ajustado no mesmo conjunto de teste. O objetivo é responder com clareza: o ajuste aproximou as notas das notas humanas? Quanto custou? Ficou mais rápido ou mais lento?

**Por que esta prioridade**: É o coração do experimento e o que concorre à categoria "Best Use of Tinker" ("mostrar melhora clara sobre o baseline"), mas depende da medição de base (P1) para ter significado.

**Teste Independente**: Pode ser testada treinando com o conjunto de treino e conferindo que a tabela traz o terceiro ponto de comparação (modelo ajustado) com as mesmas métricas, mais o custo do treino.

**Cenários de Aceitação**:

1. **Dado** o conjunto de treino preparado, **Quando** o ajuste termina, **Então** existe um modelo ajustado identificável e o custo total do treino fica registrado.
2. **Dado** o modelo ajustado, **Quando** ele corrige o conjunto de teste, **Então** o erro absoluto médio por competência e no total fica registrado ao lado do modelo base e do modelo atual.
3. **Dado** os três resultados, **Quando** o relatório é escrito, **Então** ele diz explicitamente se houve melhora clara (pelo critério definido em CS-002), sem esconder resultado negativo.

---

### História de Usuário 3 - Proteger as redações dos amigos (Prioridade: P1)

As redações pertencem ao autor e aos amigos dele. Como o treino roda em um servidor externo, só entram redações com autorização explícita de quem escreveu, e todas são anonimizadas antes de sair da máquina.

**Por que esta prioridade**: É uma condição de entrada: sem ela, nenhuma redação de terceiro pode ser usada. Também é exigência da constituição do projeto.

**Teste Independente**: Pode ser testada conferindo o registro de consentimento contra a lista de redações enviadas e inspecionando o arquivo enviado em busca de nomes ou dados pessoais.

**Cenários de Aceitação**:

1. **Dado** uma redação sem consentimento registrado, **Quando** o dataset é montado, **Então** ela é excluída.
2. **Dado** uma redação com nome, escola, cidade ou outro dado pessoal no texto ou no cabeçalho, **Quando** ela é preparada, **Então** esses dados são removidos ou substituídos antes do envio.
3. **Dado** qualquer arquivo do experimento (redações, notas, saídas dos modelos), **Quando** o autor faz commit, **Então** nenhum deles entra no repositório; só métricas agregadas são publicadas.

---

### História de Usuário 4 - Rodar o modelo ajustado localmente no app (Prioridade: P3, opcional)

Se o modelo ajustado vencer, o autor gostaria de usá-lo localmente no app, como já faz com o modelo atual.

**Por que esta prioridade**: É um extra; não é critério de entrega. A conversão do modelo ajustado para execução local não é garantida.

**Teste Independente**: Pode ser testada trocando o modelo de correção do app por configuração para o modelo ajustado e corrigindo uma redação de teste.

**Cenários de Aceitação**:

1. **Dado** o modelo ajustado convertido para execução local, **Quando** ele é configurado no app, **Então** o app corrige uma redação sem nenhum serviço externo.
2. **Dado** que a conversão falhou, **Quando** o relatório é escrito, **Então** a falha e o motivo aparecem como "próximo passo", sem afetar o restante do experimento.

---

### Casos de Borda

- Menos de 30 redações reunidas: o experimento segue como prova de conceito, e o relatório deixa isso explícito.
- Redação com nota incompleta (falta alguma competência): fica fora do dataset.
- Redação com nota zero total (fuga ao tema, texto curto): pode entrar, mas é sinalizada; o relatório informa quantas há.
- Notas de corretores diferentes para a mesma redação: usar a média ou a de um único corretor, registrando qual.
- Modelo devolve resposta fora do formato esperado para uma redação: a redação conta como falha de formato, é reportada à parte e não some silenciosamente do cálculo.
- Treino não converge ou serviço de treino indisponível: aplicar o plano de corte (decisão às 13:00).
- Créditos de treino insuficientes: reduzir épocas ou tamanho do dataset e registrar o ajuste.
- Desbalanceamento de notas (quase todas altas, por exemplo): o relatório mostra a distribuição das notas do conjunto de teste.

## Requisitos *(obrigatório)*

### Requisitos Funcionais

- **RF-001**: O dataset DEVE conter apenas redações com as cinco notas (C1–C5) atribuídas por corretor humano e com consentimento explícito do autor da redação para uso em servidor externo de treino.
- **RF-002**: Antes de sair da máquina, cada redação DEVE ser anonimizada (sem nome, escola, cidade ou outro dado pessoal).
- **RF-003**: A divisão treino/teste DEVE ser definida e salva antes de qualquer treino, com cerca de 75% treino e 25% teste e no mínimo 8 redações de teste; o conjunto de teste NUNCA é usado no treino.
- **RF-004**: Os três pontos de comparação (modelo base, modelo ajustado e modelo atual do app) DEVEM ser avaliados no mesmo conjunto de teste, com as mesmas instruções de correção (rubrica) e a mesma normalização de notas.
- **RF-005**: Para cada ponto de comparação, o experimento DEVE registrar o erro absoluto médio por competência e no total, o tempo médio por redação e o número de respostas fora do formato.
- **RF-006**: O experimento DEVE registrar o custo total do treino.
- **RF-007**: O relatório em `docs/evaluation.md` DEVE declarar explicitamente se houve melhora clara (CS-002) e DEVE ser publicado mesmo se o resultado for negativo.
- **RF-008**: Nenhuma redação, nota individual, saída de modelo ou registro de consentimento DEVE ser versionado ou publicado; apenas métricas agregadas.
- **RF-009**: O experimento DEVE ser reproduzível: a divisão de dados, as configurações do treino e as instruções de correção usadas ficam registradas (sem os textos das redações).
- **RF-010**: Se às 13:00 de 15/10 o treino não tiver produzido um modelo ajustado avaliável, o experimento DEVE ser encerrado e o relatório DEVE trazer a medição de base e a descrição da tentativa.
- **RF-011**: O app em uso pelo estudante NÃO DEVE depender de serviço externo; executar o modelo ajustado no app é opcional e só vale se ele rodar localmente.

### Entidades Principais

- **Redação corrigida**: texto da redação (já transcrito), tema, cinco notas humanas (C1–C5), total, identificação do corretor (anonimizada), status de consentimento.
- **Registro de consentimento**: quem autorizou, quando e para qual uso; guardado só localmente.
- **Divisão de dados**: lista de identificadores anônimos de treino e de teste, fixada antes do treino.
- **Ponto de comparação**: um modelo avaliado (base, ajustado ou atual do app) com suas métricas.
- **Relatório do experimento**: tabela comparativa, custo, distribuição das notas do teste, conclusão (melhorou ou não) e limitações.

## Critérios de Sucesso *(obrigatório)*

### Resultados Mensuráveis

- **CS-001**: `docs/evaluation.md` traz a tabela com os três pontos de comparação (ou, no plano de corte, pelo menos dois), cada um com erro médio em C1–C5 e no total, tempo médio por redação e falhas de formato.
- **CS-002**: "Melhora clara" é definida como o modelo ajustado reduzir o erro absoluto médio do **total** em pelo menos 20% em relação ao modelo base **e** não piorar mais de 10 pontos em nenhuma competência. O relatório diz se esse critério foi atingido.
- **CS-003**: 100% das redações usadas têm consentimento registrado, e uma inspeção do arquivo enviado não encontra nenhum nome ou dado pessoal.
- **CS-004**: O custo do treino fica registrado e não passa dos créditos disponíveis do participante.
- **CS-005**: O conjunto de teste tem no mínimo 8 redações e nenhuma delas aparece no conjunto de treino.
- **CS-006**: O experimento termina (com resultado ou com plano de corte) até o fim de 15/10, sem consumir o dia 16/10, que é do primeiro usuário.
- **CS-007**: O resultado é publicado no post do DEV com a mesma conclusão de `docs/evaluation.md`, seja positiva ou negativa.

## Premissas

- O autor terá entre 30 e 80 redações corrigidas com notas C1–C5 até a manhã de 15/10; abaixo de 30, o experimento vira prova de conceito declarada.
- As redações estão disponíveis como texto (digitadas ou transcritas pelo próprio Penssay e revisadas); o experimento não mede transcrição.
- As instruções de correção (rubrica) e a normalização de notas vêm da feature de avaliação do app (correção C1–C5), que DEVE estar pronta antes deste experimento; se não estiver, o plano usa uma versão provisória da rubrica e registra isso.
- O modelo atual de correção do app é o Gemma (`gemma3:4b`) executado localmente; ele entra como terceiro ponto de comparação e sustenta a categoria "Best Use of Gemma".
- O serviço de treino externo não oferece Gemma; o modelo base do ajuste é outro modelo aberto de porte semelhante (decisão do autor: família Qwen de ~4B), definido no plano.
- O treino em servidor externo exige emenda na constituição (princípio II, Privacidade), liberando o envio de redações anonimizadas e consentidas **apenas** para o experimento de treino; o app continua 100% local.
- Os créditos de treino oferecidos aos participantes do Hacktoberfest cobrem o experimento.
- Fora do escopo: corpus público de redações, ajuste do modelo de transcrição, comparação com corretores profissionais além das notas já existentes e ajuste de hiperparâmetros em várias rodadas.
