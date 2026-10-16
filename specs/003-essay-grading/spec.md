# Especificação da Feature: Correção da redação por competência (C1–C5)

**Branch da Feature**: `003-essay-grading`

**Criada em**: 2026-10-15

**Status**: Rascunho

**Entrada**: Descrição do usuário: "Correção da redação ENEM por competência (C1–C5), base do experimento 002. Recebe tema e texto já revisado pelo aluno e devolve as 5 competências em ordem (nota 0/40/80/120/160/200, justificativa curta citando a redação, 2–3 dicas) e o total (0–1000). Rubrica oficial em arquivo de prompt, aritmética sempre no app, modelo configurável, falhas claras em PT-BR, reutilizável pelo experimento 002, cujas notas humanas têm só os 5 números."

## Cenários de Usuário e Testes *(obrigatório)*

### História de Usuário 1 - Receber a nota de cada competência (Prioridade: P1)

O aluno já revisou a transcrição da redação. Ele informa o tema e envia o texto. O Penssay devolve uma nota para cada uma das cinco competências do ENEM e o total, seguindo a rubrica oficial.

**Por que esta prioridade**: É o produto. Sem a nota, o Penssay é só um leitor de letra. Também é a base que o experimento 002 mede contra as notas humanas.

**Teste Independente**: Pode ser testada enviando o tema e o texto de uma redação e conferindo que a resposta traz exatamente 5 competências, na ordem de 1 a 5, cada uma com nota permitida, e um total igual à soma delas.

**Cenários de Aceitação**:

1. **Dado** um tema e o texto de uma redação, **Quando** o aluno pede a correção, **Então** recebe 5 competências numeradas de 1 a 5, cada uma com nota em {0, 40, 80, 120, 160, 200}, e o total.
2. **Dado** que o modelo sugere uma nota fora dos valores permitidos (por exemplo 150 ou 230), **Quando** a nota é processada, **Então** o app a ajusta para o múltiplo de 40 mais próximo e a limita a 0–200, sem chamar o modelo de novo.
3. **Dado** que o modelo informa um total, **Quando** a resposta é montada, **Então** o total devolvido é sempre a soma das cinco notas ajustadas, e o total do modelo é ignorado.

---

### História de Usuário 2 - Aplicar as regras de nota zero (Prioridade: P1)

No ENEM, algumas situações zeram a redação inteira, não só uma competência. O Penssay precisa aplicá-las do mesmo jeito.

**Por que esta prioridade**: Uma redação que deveria tirar zero e recebe 600 engana o aluno no ponto mais grave. Também distorce a medição do experimento.

**Teste Independente**: Pode ser testada enviando um texto de 7 linhas ou menos e, separadamente, um texto fora do tema, e conferindo que ambos recebem zero em todas as competências.

**Cenários de Aceitação**:

1. **Dado** um texto com 7 linhas ou menos, **Quando** o aluno pede a correção, **Então** todas as competências e o total são zero, com o motivo explicado, e o app decide isso sem depender do modelo.
2. **Dado** um texto que foge totalmente ao tema ou que não é dissertativo-argumentativo, **Quando** o modelo indica essa situação, **Então** todas as competências e o total são zero e o motivo aparece na resposta.
3. **Dado** um texto que só tangencia o tema (fuga parcial), **Quando** é corrigido, **Então** ele NÃO é zerado; a penalidade aparece nas notas das competências, como no ENEM.

---

### História de Usuário 3 - Entender a nota e saber como melhorar (Prioridade: P2)

Para cada competência, o aluno recebe uma justificativa curta que cita trechos da própria redação e de 2 a 3 dicas práticas.

**Por que esta prioridade**: É o que transforma a nota em aprendizado, e o que diferencia o Penssay de uma nota seca. Depende da nota existir (P1).

**Teste Independente**: Pode ser testada corrigindo uma redação com um erro de ortografia conhecido e conferindo que a justificativa de C1 cita esse trecho e que cada competência tem de 2 a 3 dicas.

**Cenários de Aceitação**:

1. **Dado** uma redação corrigida, **Quando** o aluno lê a resposta, **Então** cada competência tem uma justificativa curta em português e de 2 a 3 dicas práticas.
2. **Dado** uma redação com desvios de norma culta, **Quando** é corrigida, **Então** a justificativa de C1 cita ao menos um trecho real da redação.
3. **Dado** uma redação sem proposta de intervenção, **Quando** é corrigida, **Então** a justificativa de C5 diz que faltou a proposta e as dicas explicam os cinco elementos (agente, ação, meio, finalidade e detalhamento).

---

### História de Usuário 4 - Falhas claras, nunca nota inventada (Prioridade: P2)

Quando algo dá errado, o aluno recebe uma mensagem clara em português, e nunca uma nota falsa.

**Por que esta prioridade**: Uma nota inventada a partir de uma resposta quebrada é pior que um erro. Mas o caminho feliz (P1) vem primeiro.

**Teste Independente**: Pode ser testada enviando um pedido sem texto, desligando o servidor de modelos e simulando uma resposta do modelo sem uma das competências, e conferindo a mensagem de cada caso.

**Cenários de Aceitação**:

1. **Dado** um pedido sem tema ou sem texto (vazio ou só espaços), **Quando** é enviado, **Então** o aluno recebe um erro de entrada inválida com mensagem em português, e o modelo não é chamado.
2. **Dado** que o servidor de modelos está fora do ar ou o modelo não está instalado, **Quando** o aluno pede a correção, **Então** recebe um erro de serviço indisponível com mensagem em português.
3. **Dado** que o modelo responde fora do formato esperado (faltou competência, nota que não é número, texto solto), **Quando** a resposta é processada, **Então** o aluno recebe um erro claro de falha na correção, e nenhuma nota é devolvida.

---

### História de Usuário 5 - Reutilizar a correção no experimento (Prioridade: P2)

O autor usa a mesma rubrica e o mesmo ajuste de notas para medir três modelos (Gemma, Qwen base e Qwen ajustado) contra notas humanas que têm só os cinco números, sem justificativa.

**Por que esta prioridade**: É a dependência direta do experimento 002. Sem ela, cada modelo seria medido com regras diferentes e a comparação não valeria.

**Teste Independente**: Pode ser testada trocando o modelo de correção só por configuração e pedindo uma correção no formato reduzido, conferindo que voltam só as cinco notas ajustadas e o total.

**Cenários de Aceitação**:

1. **Dado** outro modelo configurado, **Quando** uma redação é corrigida, **Então** a correção usa o novo modelo sem nenhuma mudança no código.
2. **Dado** o formato reduzido (só notas), **Quando** uma redação é corrigida, **Então** a resposta traz as cinco notas e o total, ajustados pela mesma regra do app, sem justificativas nem dicas.
3. **Dado** o formato completo e o reduzido, **Quando** ambos corrigem a mesma redação, **Então** os dois usam a mesma descrição das competências (rubrica); só muda o que o modelo precisa escrever na resposta.

---

### Casos de Borda

- Nota exatamente no meio de dois valores permitidos (por exemplo 20, 60 ou 180): arredonda para cima (20 → 40, 180 → 200).
- Nota negativa ou acima de 200: limitada a 0 ou 200.
- Nota com casas decimais (por exemplo 152,5): arredonda para o múltiplo de 40 mais próximo, como qualquer outro número.
- Competência repetida ou fora de ordem na resposta do modelo: o app reorganiza de 1 a 5 se as cinco estiverem presentes uma vez cada; se alguma faltar ou repetir com valores diferentes, é falha de formato.
- Texto com linhas em branco: só linhas com algum texto contam para a regra das 7 linhas.
- Texto muito longo (muito acima das 30 linhas da folha oficial): é corrigido normalmente; o comprimento não zera a redação.
- Modelo marca zero por fuga ao tema mas devolve notas diferentes de zero: o app zera tudo, porque a regra de zero prevalece.
- Tema enviado não corresponde ao texto: o app não julga isso sozinho; é tarefa do modelo pela rubrica (fuga total ou parcial).
- Justificativa que "cita" um trecho que não está na redação: é registrado como limitação conhecida e medido no teste de estabilidade, não bloqueia a resposta.

## Requisitos *(obrigatório)*

### Requisitos Funcionais

- **RF-001**: O sistema DEVE receber o tema e o texto da redação e devolver exatamente cinco competências, numeradas de 1 a 5 e em ordem, mais o total.
- **RF-002**: A descrição das competências (rubrica oficial do ENEM: C1 norma culta; C2 compreensão do tema e tipo dissertativo-argumentativo, com repertório; C3 seleção, relação e organização de argumentos; C4 mecanismos de coesão; C5 proposta de intervenção com agente, ação, meio, finalidade e detalhamento, respeitando os direitos humanos) DEVE ficar em arquivo de instruções do modelo, nunca escrita no código.
- **RF-003**: Cada nota DEVE ser ajustada pelo app para o múltiplo de 40 mais próximo (empate arredonda para cima) e limitada a 0–200; o total DEVE ser a soma das cinco notas ajustadas. Nenhum total ou conta vinda do modelo é usado.
- **RF-004**: O ajuste de notas DEVE ter testes automatizados cobrindo arredondamento, empate, limites (negativo, acima de 200) e soma do total.
- **RF-005**: Um texto com 7 linhas não vazias ou menos DEVE receber zero em todas as competências, decidido pelo app sem chamar o modelo.
- **RF-006**: Quando o modelo indicar fuga total ao tema ou texto não dissertativo-argumentativo, o app DEVE zerar todas as competências e o total, mesmo que o modelo tenha dado outras notas, e informar o motivo.
- **RF-007**: No formato completo, cada competência DEVE trazer uma justificativa curta em português e de 2 a 3 dicas práticas.
- **RF-008**: O sistema DEVE oferecer um formato reduzido que devolve só as cinco notas ajustadas e o total, usando a mesma rubrica e o mesmo ajuste do formato completo.
- **RF-009**: O modelo de correção DEVE ser escolhido por configuração (padrão: Gemma de ~4B, local), sem mudança de código para trocá-lo.
- **RF-010**: Tema ou texto ausente, vazio ou só com espaços DEVE resultar em erro de entrada inválida, sem chamar o modelo.
- **RF-011**: Servidor de modelos fora do ar ou modelo não instalado DEVE resultar em erro de serviço indisponível.
- **RF-012**: Resposta do modelo fora do formato esperado DEVE resultar em erro de falha na correção; o sistema NUNCA devolve nota parcial ou inventada.
- **RF-013**: Todas as mensagens de erro e todos os textos voltados ao aluno DEVEM estar em português do Brasil, em linguagem simples.
- **RF-014**: Os textos das redações NUNCA são gravados, registrados em log nem enviados para fora da máquina.

### Entidades Principais

- **Pedido de correção**: tema e texto da redação (já revisado pelo aluno); formato desejado (completo ou reduzido).
- **Avaliação de competência**: número (1–5), nota ajustada, justificativa e dicas (estas duas só no formato completo).
- **Correção da redação**: as cinco avaliações de competência em ordem, o total e, quando houver, o motivo de nota zero.
- **Regra de ajuste de notas**: arredondamento para múltiplo de 40, limites 0–200 e soma do total; a mesma para o app e para o experimento 002.

## Critérios de Sucesso *(obrigatório)*

### Resultados Mensuráveis

- **CS-001**: 100% das respostas bem-sucedidas têm 5 competências em ordem, notas só em {0, 40, 80, 120, 160, 200} e total igual à soma.
- **CS-002**: Os testes do ajuste de notas cobrem 100% dos casos de arredondamento, empate, limites e soma, e passam sem nenhum modelo real.
- **CS-003**: Estabilidade: a mesma redação corrigida 3 vezes tem variação do total de no máximo 80 pontos.
- **CS-004**: Sanity check: as redações nota 1000 da Cartilha do Participante do INEP recebem total ≥ 880, e uma redação fraca sem proposta de intervenção recebe ≤ 80 em C5.
- **CS-005**: 100% dos textos com 7 linhas ou menos e dos pedidos sem tema ou sem texto são tratados conforme as regras (zero ou erro), sem nota inventada.
- **CS-006**: Uma correção completa termina em até 60 segundos na máquina de desenvolvimento, com o modelo já carregado (meta inicial, a confirmar na primeira medição).
- **CS-007**: Trocar o modelo de correção exige só mudar a configuração; nenhum arquivo de código é alterado.
- **CS-008**: O experimento 002 consegue medir pelo menos dois modelos usando o formato reduzido, com a mesma rubrica e o mesmo ajuste de notas.

## Premissas

- O texto recebido já foi revisado pelo aluno após a transcrição e mantém as quebras de linha da folha (a transcrição é feita linha por linha), o que permite aplicar a regra das 7 linhas.
- O empate no arredondamento (nota no meio de dois múltiplos de 40) vai para cima; o ENEM não define essa regra porque o corretor humano só atribui valores permitidos.
- A rubrica é uma versão resumida dos critérios oficiais do INEP; a Cartilha do Participante é a referência, mas seus textos e redações não são copiados para o repositório.
- A justificativa e as dicas são geradas pelo modelo e não passam por verificação automática; a qualidade delas é avaliada por amostragem, não por teste.
- As notas humanas do experimento 002 vêm de prints de uma plataforma de correção, só com os cinco números por competência; por isso o formato reduzido é suficiente para o experimento.
- A meta de tempo (CS-006) é uma estimativa inicial para uma GPU de 6 GB e pode ser revista após a primeira medição.
- Fora do escopo: interface (vem em outra feature), histórico, banco de dados, contas, correção de outros vestibulares e verificação automática de que os trechos citados existem na redação.
