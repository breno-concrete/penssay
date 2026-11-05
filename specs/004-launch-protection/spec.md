# Especificação da Feature: Proteção para o lançamento público

**Branch da Feature**: `004-launch-protection`

**Criada em**: 2026-11-05

**Status**: Rascunho

**Entrada**: Descrição do usuário: "Proteção para o lançamento público do Penssay (dia do ENEM, 2026-11-08): o app vai ficar aberto na internet, sem login, e cada correção chama modelos pagos na OpenRouter (~US$ 0,005 por redação). Objetivo: impedir abuso de custo e quedas, e dar erros claros ao aluno. Escopo: limite de requisições por IP, limites de tamanho de texto e tema, validação da foto pelos bytes, regra das 70 palavras antes do modelo, tratamento centralizado de erros, teto diário global de correções além do teto de crédito na chave, e servidor que não trava com correções lentas. Fora de escopo: login, banco de dados, CAPTCHA, calibração de notas, mudanças nos prompts, a tela web."

## Cenários de Usuário e Testes *(obrigatório)*

### História de Usuário 1 - O gasto não foge do controle (Prioridade: P1)

O Penssay fica aberto na internet, sem login, e cada pedido custa dinheiro. Alguém pode chamar o serviço direto por script, milhares de vezes, ou mandar um "texto" gigante. O dono do projeto precisa de um teto: nenhum uso, normal ou malicioso, passa do orçamento.

**Por que esta prioridade**: Sem isso, um único abuso esvazia o crédito e o app para para todo mundo no dia do ENEM. É o único item que pode gerar prejuízo real.

**Teste Independente**: Pode ser testada disparando pedidos seguidos da mesma origem até passar do limite, enviando um texto acima do tamanho máximo, e baixando o teto diário para 2 e pedindo 3 correções; em todos os casos o pedido excedente é recusado sem chamar o modelo.

**Cenários de Aceitação**:

1. **Dado** que uma origem já fez o número máximo de correções na janela de tempo, **Quando** ela pede mais uma, **Então** o pedido é recusado com a mensagem "Você atingiu o limite de correções por agora. Tente de novo mais tarde." e o tempo de espera, sem chamar o modelo.
2. **Dado** o mesmo cenário para a leitura de fotos, **Quando** a origem envia mais uma foto, **Então** o pedido é recusado com a mensagem equivalente para fotos, sem chamar o modelo.
3. **Dado** um texto de redação acima do tamanho máximo ou um tema acima do tamanho máximo, **Quando** o pedido chega, **Então** ele é recusado como entrada inválida, com mensagem que diz o limite, sem chamar o modelo.
4. **Dado** que o total de correções do dia já atingiu o teto global, **Quando** qualquer pessoa pede uma correção, **Então** o pedido é recusado com a mensagem "O Penssay atingiu o limite de correções de hoje. Volte amanhã." até a virada do dia.
5. **Dado** que uma origem foi limitada, **Quando** outra origem faz um pedido, **Então** o pedido dela segue normalmente.

---

### História de Usuário 2 - Entrada inválida não custa nada (Prioridade: P1)

O aluno manda uma foto que não é imagem, um arquivo vazio ou um texto curto demais. O Penssay responde na hora, sem gastar uma chamada de modelo, e explica o que fazer.

**Por que esta prioridade**: Cada chamada desperdiçada custa dinheiro e tempo de fila. Validar antes também evita erros internos confusos.

**Teste Independente**: Pode ser testada enviando um PDF renomeado como `.jpg`, um arquivo vazio e um texto de 69 palavras, e conferindo que nenhum deles chega ao modelo.

**Cenários de Aceitação**:

1. **Dado** um arquivo cujo conteúdo não é JPEG, PNG nem WebP (mesmo que o nome ou o tipo declarado digam que é imagem), **Quando** o aluno envia a foto, **Então** recebe "Envie uma foto em JPEG, PNG ou WebP." sem chamada ao modelo.
2. **Dado** um arquivo vazio ou nenhum arquivo, **Quando** o aluno envia, **Então** recebe "Envie a foto da redação." sem chamada ao modelo.
3. **Dado** uma foto acima de 10 MB, **Quando** o aluno envia, **Então** recebe "A foto é muito grande. Envie uma foto de até 10 MB."
4. **Dado** um texto com menos de 70 palavras, **Quando** o aluno pede a correção, **Então** recebe as cinco competências com nota zero, motivo "texto insuficiente" explicado e total zero, sem chamada ao modelo.
5. **Dado** tema ou texto ausente, vazio ou só com espaços, **Quando** o aluno pede a correção, **Então** recebe "Informe o tema da redação." ou "Envie o texto da redação." sem chamada ao modelo.

---

### História de Usuário 3 - Toda falha tem uma mensagem clara (Prioridade: P2)

Quando algo dá errado (provedor fora do ar, resposta quebrada do modelo, pedido malformado), o aluno vê uma mensagem curta em português que diz o que aconteceu e se vale tentar de novo. Nada de erro técnico, nome de classe ou trecho da redação na resposta ou no log.

**Por que esta prioridade**: Sem isso, o app funciona, mas qualquer falha vira uma tela confusa. Depende das validações (P1) para ter o que mostrar.

**Teste Independente**: Pode ser testada simulando o provedor fora do ar e uma resposta quebrada, e conferindo o código e a mensagem de cada erro e que o log não contém o texto enviado.

**Cenários de Aceitação**:

1. **Dado** que o provedor de modelos está fora do ar ou recusou a chave, **Quando** o aluno pede transcrição ou correção, **Então** recebe "O serviço de correção está indisponível no momento. Tente de novo em alguns minutos." e um código de serviço indisponível.
2. **Dado** que o modelo devolveu uma resposta fora do formato duas vezes seguidas, **Quando** a correção termina, **Então** o aluno recebe "Não conseguimos concluir a correção. Tente de novo." e nunca uma nota parcial ou inventada.
3. **Dado** um pedido malformado (corpo que não é JSON, campo com tipo errado), **Quando** chega, **Então** a resposta é um erro de entrada inválida com mensagem em português, sem detalhes internos.
4. **Dado** qualquer erro inesperado, **Quando** ele acontece, **Então** o aluno recebe uma mensagem genérica em português, e o log registra o tipo do erro sem o texto da redação nem a foto.

---

### História de Usuário 4 - Correções lentas não derrubam o serviço (Prioridade: P2)

No dia do ENEM, muitos alunos corrigem ao mesmo tempo, e cada correção espera o modelo por cerca de 12 segundos. O serviço continua aceitando pedidos enquanto essas esperas acontecem.

**Por que esta prioridade**: Uma correção que espera não pode impedir outras de começarem. Sem isso, algumas centenas de pedidos simultâneos travam o app para todos.

**Teste Independente**: Pode ser testada com o provedor simulado respondendo devagar e 300 pedidos simultâneos, conferindo que um pedido leve (por exemplo, abrir a página) responde rápido durante a carga.

**Cenários de Aceitação**:

1. **Dado** 300 correções em espera ao mesmo tempo, **Quando** alguém abre a página ou faz um pedido inválido, **Então** recebe resposta em até 2 segundos.

---

### Casos de Borda

- O app roda atrás do proxy do serviço de hospedagem: a origem do pedido DEVE ser a do aluno, não a do proxy, e o aluno NÃO pode trocar a própria origem editando um cabeçalho para escapar do limite.
- Vários alunos na mesma rede (escola, cursinho) compartilham a mesma origem: o limite por origem precisa permitir uma turma pequena, e o teto global continua protegendo o orçamento.
- O app reinicia no meio do dia: os contadores voltam a zero. É aceito, porque o teto de crédito na chave do provedor continua valendo como última barreira.
- Pedido recusado por limite não conta como uso; pedido recusado por entrada inválida também não.
- Todo pedido que chega a chamar o modelo conta para o limite da origem e para o teto do dia, mesmo que o provedor falhe depois: a vaga é reservada antes da chamada, para que pedidos simultâneos não passem do teto.
- Texto com exatamente 70 palavras é corrigido normalmente; com 69, é zerado.
- Texto no tamanho máximo exato é aceito; um caractere a mais é recusado.

## Requisitos *(obrigatório)*

### Requisitos Funcionais

- **RF-001**: O sistema DEVE limitar o número de pedidos de correção e de leitura de foto por origem em uma janela de tempo. Padrão: 10 correções e 20 leituras de foto por hora por origem.
- **RF-002**: Um pedido acima do limite DEVE ser recusado com código de "muitos pedidos", mensagem em português e o tempo, em segundos, até poder tentar de novo, sem chamar o modelo.
- **RF-003**: A origem DEVE ser a do cliente real quando o app roda atrás do proxy da hospedagem, e um cliente NÃO DEVE conseguir mudá-la enviando cabeçalhos próprios.
- **RF-004**: O sistema DEVE manter um teto global por dia para correções (padrão: 1.500, cerca de US$ 4,50) e para leituras de foto (padrão: 3.000, cerca de US$ 4,50), que zeram à meia-noite no horário de Brasília. Ao atingir um teto, todo pedido novo daquele tipo é recusado com mensagem própria até a virada do dia.
- **RF-005**: O texto da redação DEVE ter no máximo 6.000 caracteres e o tema no máximo 300; acima disso, o pedido é recusado como entrada inválida, informando o limite, sem chamar o modelo.
- **RF-006**: A foto DEVE ser aceita só se o conteúdo for JPEG, PNG ou WebP, conferido pelos primeiros bytes do arquivo, ignorando o nome e o tipo declarado pelo cliente. O tipo enviado ao modelo DEVE ser o detectado.
- **RF-007**: Foto ausente, vazia ou acima de 10 MB DEVE ser recusada com mensagem própria, sem chamar o modelo.
- **RF-008**: Um texto com menos de 70 palavras DEVE receber zero em todas as competências, motivo "texto insuficiente", sem chamar o modelo (regra RF-005 da spec 003).
- **RF-009**: Tema ou texto ausente, vazio ou só com espaços DEVE ser recusado como entrada inválida, sem chamar o modelo (RF-010 da spec 003).
- **RF-010**: Todos os erros da API DEVEM ser tratados num único ponto e devolvidos no mesmo formato (uma mensagem em português), com estes códigos: entrada inválida → 400; foto grande demais → 413; limite atingido → 429; resposta do modelo fora do formato → 502; provedor indisponível ou chave recusada → 503; erro inesperado → 500.
- **RF-011**: Nenhuma resposta de erro DEVE conter detalhes internos (nome de classe, rastro de pilha, mensagem do provedor). Nenhum log DEVE conter o texto da redação, o tema ou a foto (RF-014 da spec 003).
- **RF-012**: Uma correção esperando o modelo NÃO DEVE impedir que o servidor aceite e responda outros pedidos.
- **RF-013**: Todos os limites (pedidos por origem, janela, teto diário, tamanhos) DEVEM ser valores de configuração, trocáveis sem mudar código.
- **RF-014**: O README DEVE documentar o passo manual de criar uma chave só para produção, com teto de crédito no painel do provedor, e de guardar a chave como segredo do serviço de hospedagem.

### Entidades Principais

- **Origem**: quem faz o pedido, identificado pelo endereço do cliente real. Tem um contador de correções e um de leituras de foto, cada um com sua janela de tempo.
- **Uso do dia**: contador global de correções na data atual (horário de Brasília), comparado ao teto diário.
- **Erro da API**: código e mensagem em português devolvidos ao aluno.

## Critérios de Sucesso *(obrigatório)*

### Resultados Mensuráveis

- **CS-001**: 100% dos pedidos acima do limite por origem, acima do teto diário ou com entrada inválida são recusados sem nenhuma chamada ao modelo (verificado por testes automatizados que contam as chamadas).
- **CS-002**: O gasto máximo de um dia fica limitado ao teto diário configurado: com o teto em N correções, a correção N+1 do dia é recusada.
- **CS-003**: Um cliente que troca o próprio cabeçalho de origem continua sendo limitado (verificado por teste).
- **CS-004**: 100% das respostas de erro têm uma mensagem em português e nenhum detalhe interno; nenhum log gerado nos testes contém o texto da redação enviado.
- **CS-005**: Com 300 correções simultâneas esperando um provedor lento, um pedido leve responde em até 2 segundos.
- **CS-006**: `./mvnw test` passa sem nenhum modelo real e sem rede.

## Premissas

- O app roda numa única instância (um processo). Os contadores ficam na memória; com várias instâncias, cada uma teria seus próprios contadores, o que fica fora de escopo.
- O teto de crédito na chave do provedor é a última barreira contra gasto e não depende do app; o teto diário do app é um disjuntor antes dele.
- O serviço de hospedagem coloca a origem real do cliente num cabeçalho de proxy padrão e sobrescreve o que o cliente enviar nesse cabeçalho.
- Os padrões de limite (10 correções e 20 fotos por hora por origem; 1.500 correções e 3.000 fotos por dia, juntos perto de US$ 9, abaixo de um teto de crédito de US$ 10 na chave) são estimativas para o lançamento e podem ser ajustados por configuração no dia, sem deploy de código.
- O texto voltado ao aluno sobre privacidade passa a dizer a verdade do app hospedado: a redação é enviada a um provedor de IA para ser lida e corrigida, e o Penssay não a guarda. A mudança da tela fica com o Breno.
- A spec 003 ainda tem tarefas abertas (T020, T027–T029, T038) que esta feature absorve; elas serão marcadas na 003 como cobertas pela 004.
- Esta feature depende da troca de provedor já feita (commit `6e50e93`), que conflita com o princípio I da constituição (IA 100% local). A emenda da constituição é tratada no plano.
