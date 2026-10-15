# Especificação da Feature: Transcrição de redação manuscrita

**Branch da Feature**: `001-handwritten-transcription`

**Criada em**: 2026-10-14

**Status**: Rascunho

**Entrada**: Descrição do usuário: "Transcrição de redação manuscrita do ENEM: o estudante envia a foto de uma redação escrita à mão e recebe o texto transcrito fielmente, sem corrigir erros, com marcação de palavras incertas e ilegíveis."

## Cenários de Usuário e Testes *(obrigatório)*

### História de Usuário 1 - Transcrever a foto de uma redação preservando os erros (Prioridade: P1)

O estudante tira uma foto da redação que escreveu à mão e a envia. Em troca, recebe o texto da redação digitado, palavra por palavra e linha por linha, exatamente como foi escrito, inclusive com os erros de ortografia, acentuação, concordância e pontuação. O texto servirá de base para a correção posterior; por isso, qualquer "correção automática" da transcrição prejudicaria a avaliação da Competência 1 (domínio da norma culta), que depende dos erros reais do aluno.

**Por que esta prioridade**: É o primeiro passo do fluxo do produto e o único que transforma o papel em texto. Sem ele, não há como corrigir redações escritas à mão. Sozinha, já entrega valor: o aluno obtém uma versão digital fiel da redação.

**Teste Independente**: Pode ser testada integralmente enviando a foto de uma redação manuscrita que contém erros propositais conhecidos e conferindo que o texto devolvido contém as mesmas palavras e os mesmos erros, sem nenhuma correção.

**Cenários de Aceitação**:

1. **Dado** a foto nítida de uma redação manuscrita legível, **Quando** o estudante a envia, **Então** recebe o texto transcrito na mesma ordem de palavras e linhas da redação original.
2. **Dado** uma redação com erros de ortografia, acentuação, concordância e pontuação, **Quando** ela é transcrita, **Então** todos esses erros aparecem no texto devolvido exatamente como foram escritos (nada é corrigido, completado ou reescrito).
3. **Dado** uma redação com palavras riscadas pelo aluno, **Quando** ela é transcrita, **Então** as palavras riscadas não aparecem no texto devolvido.
4. **Dado** qualquer foto aceita, **Quando** a transcrição é devolvida, **Então** a resposta contém apenas o texto da redação, sem título, comentários, explicações ou introduções.

---

### História de Usuário 2 - Sinalizar palavras incertas e ilegíveis (Prioridade: P2)

Quando o sistema não tem certeza de como uma palavra foi escrita, ele não escolhe em silêncio: marca a palavra como incerta ou ilegível, para que o estudante possa revisá-la antes da correção. Assim, uma leitura errada da letra não vira, sem ninguém perceber, um erro de nota.

**Por que esta prioridade**: Protege a confiabilidade do fluxo (uma má leitura da caligrafia nunca deve virar um erro de avaliação), mas depende de a transcrição básica (P1) já existir.

**Teste Independente**: Pode ser testada enviando uma foto com uma palavra deliberadamente mal escrita e outra totalmente ilegível, e conferindo que cada uma recebe a marcação correspondente.

**Cenários de Aceitação**:

1. **Dado** uma palavra cuja leitura é duvidosa, **Quando** a redação é transcrita, **Então** a palavra aparece no formato `[?palavra]`, com a melhor leitura possível entre os marcadores.
2. **Dado** uma palavra que não pode ser lida de forma alguma, **Quando** a redação é transcrita, **Então** ela é substituída pela marcação `[illegible]`.
3. **Dado** uma palavra com leitura clara, **Quando** a redação é transcrita, **Então** ela aparece sem nenhuma marcação.

---

### História de Usuário 3 - Rejeitar arquivos inválidos com mensagem clara (Prioridade: P2)

Se o estudante envia algo que não pode ser transcrito (um arquivo de tipo não suportado, um arquivo vazio ou um arquivo grande demais), o sistema recusa o envio e explica o motivo em linguagem simples, para que ele saiba o que corrigir e tentar de novo.

**Por que esta prioridade**: Evita esperas inúteis e erros confusos, mas só importa depois que o caminho feliz funciona.

**Teste Independente**: Pode ser testada enviando, um a um, um arquivo de outro tipo, um arquivo vazio e um arquivo maior que o limite, e conferindo que cada envio é recusado com uma mensagem que diz o que está errado.

**Cenários de Aceitação**:

1. **Dado** um arquivo que não é JPEG nem PNG, **Quando** o estudante o envia, **Então** o envio é recusado com uma mensagem informando que apenas imagens JPEG ou PNG são aceitas.
2. **Dado** um arquivo vazio ou nenhuma foto enviada, **Quando** o estudante tenta transcrever, **Então** o envio é recusado com uma mensagem informando que nenhuma foto foi recebida.
3. **Dado** uma foto maior que 10 MB, **Quando** o estudante a envia, **Então** o envio é recusado com uma mensagem informando o limite de 10 MB.

---

### História de Usuário 4 - Escolher o modelo de transcrição e comparar alternativas (Prioridade: P3)

O desenvolvedor precisa trocar o modelo usado na transcrição sem alterar o código, e registrar uma comparação objetiva entre modelos candidatos (taxa de erro de palavras, quantos dos 5 erros propositais foram preservados e tempo de resposta), levando em conta que a máquina de desenvolvimento tem uma placa de vídeo de 6 GB de memória. O resultado fica registrado em `docs/evaluation.md` e embasa a escolha do modelo padrão e o conteúdo do post de entrega.

**Por que esta prioridade**: Não altera o que o estudante vê, mas justifica a escolha do modelo e é um dos resultados esperados da entrega.

**Teste Independente**: Pode ser testada trocando o modelo apenas por configuração, transcrevendo as mesmas amostras com cada candidato e conferindo que as três medidas de cada modelo aparecem na tabela de `docs/evaluation.md`.

**Cenários de Aceitação**:

1. **Dado** a configuração do modelo de transcrição alterada, **Quando** o sistema é reiniciado e uma foto é enviada, **Então** a transcrição é feita pelo modelo configurado, sem nenhuma outra mudança.
2. **Dado** um conjunto de amostras com transcrição de referência feita à mão, **Quando** cada modelo candidato transcreve as amostras, **Então** `docs/evaluation.md` registra, por modelo, a taxa de erro de palavras, quantos dos 5 erros propositais foram mantidos e o tempo médio por foto.
3. **Dado** um modelo que não cabe na memória da placa de vídeo e fica visivelmente mais lento, **Quando** a comparação é feita, **Então** esse fato é registrado junto com o resultado do modelo.

---

### Casos de Borda

- Foto desfocada, escura ou com sombra sobre o texto: o sistema deve transcrever o que for legível e marcar o restante como incerto ou ilegível, sem inventar texto.
- Foto que não contém texto manuscrito (por exemplo, paisagem, página em branco): a resposta não deve inventar uma redação; deve indicar que nenhum texto foi encontrado.
- Foto cortada, torta ou com apenas parte da página: transcrever apenas o que aparece na imagem.
- Redação com letra de forma, letra cursiva ou mistura das duas: o comportamento esperado é o mesmo.
- Palavra riscada e reescrita ao lado: aparece apenas a palavra final, sem a riscada.
- Erro proposital que a leitura "natural" tenderia a corrigir (por exemplo, "excessão" no lugar de "exceção"): deve permanecer como foi escrito.
- Serviço de transcrição fora do ar ou modelo não disponível: o estudante recebe uma mensagem clara de indisponibilidade, e não uma resposta vazia ou técnica.
- Foto muito pesada dentro do limite (próxima de 10 MB): é aceita normalmente.

## Requisitos *(obrigatório)*

### Requisitos Funcionais

- **RF-001**: O sistema DEVE aceitar o envio de uma foto de redação nos formatos JPEG ou PNG, com até 10 MB.
- **RF-002**: O sistema DEVE devolver o texto da redação transcrito palavra por palavra e linha por linha, na ordem original.
- **RF-003**: O sistema NÃO DEVE corrigir ortografia, acentuação, concordância nem pontuação; todos os erros do aluno DEVEM ser preservados.
- **RF-004**: O sistema DEVE marcar palavras de leitura incerta no formato `[?palavra]`.
- **RF-005**: O sistema DEVE marcar palavras ilegíveis como `[illegible]`.
- **RF-006**: O sistema DEVE omitir do texto devolvido as palavras riscadas.
- **RF-007**: A resposta DEVE conter somente o texto transcrito, sem título, comentários ou explicações.
- **RF-008**: O sistema DEVE recusar arquivos de tipo não suportado, arquivos vazios, envios sem foto e arquivos acima de 10 MB, com uma mensagem clara que diga o motivo, e sem iniciar a transcrição.
- **RF-009**: O sistema DEVE processar a foto inteiramente na máquina do usuário ou da escola; a foto e o texto NÃO DEVEM ser enviados a nenhum serviço externo.
- **RF-010**: O modelo usado na transcrição DEVE ser escolhido por configuração, sem alteração de código.
- **RF-011**: Quando a foto não contiver texto manuscrito legível, o sistema DEVE informar que nenhum texto foi encontrado, em vez de inventar conteúdo.
- **RF-012**: Quando o serviço de transcrição estiver indisponível, o sistema DEVE informar a indisponibilidade em linguagem simples.
- **RF-013**: O projeto DEVE manter em `docs/evaluation.md` uma comparação dos modelos candidatos com, para cada um: taxa de erro de palavras, número de erros propositais preservados (de 5) e tempo médio por foto.
- **RF-014**: As fotos e os textos de amostra usados na comparação NÃO DEVEM ser versionados nem publicados.

### Entidades Principais

- **Foto da redação**: a imagem enviada pelo estudante. Atributos: formato (JPEG ou PNG), tamanho (até 10 MB). Não é guardada após a transcrição.
- **Transcrição**: o texto devolvido ao estudante. Contém o texto fiel da redação, com as marcações `[?palavra]` e `[illegible]` quando aplicável.
- **Amostra de avaliação**: uma foto manuscrita acompanhada da sua transcrição de referência feita à mão; uma delas contém 5 erros propositais conhecidos.
- **Resultado de comparação**: as medidas de um modelo candidato sobre as amostras (taxa de erro de palavras, erros propositais preservados, tempo médio por foto).

## Critérios de Sucesso *(obrigatório)*

### Resultados Mensuráveis

- **CS-001**: Na amostra com 5 erros propositais, pelo menos 4 dos 5 erros aparecem no texto devolvido exatamente como foram escritos.
- **CS-002**: Em amostras legíveis e bem fotografadas, a taxa de erro de palavras do modelo escolhido fica abaixo de 15 palavras erradas a cada 100.
- **CS-003**: O estudante recebe a transcrição de uma foto em até 90 segundos nas condições da máquina de desenvolvimento.
- **CS-004**: 100% dos envios inválidos (tipo errado, vazio, acima de 10 MB) são recusados com uma mensagem que explica o motivo, sem iniciar a transcrição.
- **CS-005**: Nenhuma foto nem texto de redação sai da máquina durante o uso (verificável observando que o uso funciona sem conexão com a internet).
- **CS-006**: A comparação em `docs/evaluation.md` cobre pelo menos 2 modelos candidatos, com as três medidas preenchidas para cada um.
- **CS-007**: Trocar o modelo de transcrição exige alterar somente a configuração, sem recompilar nem editar código.

## Premissas

- O público são estudantes que praticam a redação do ENEM em papel e fotografam o rascunho com o celular; o uso ocorre em português do Brasil.
- Cada envio contém a foto de uma única página; redações de várias páginas são enviadas e transcritas página por página (a junção fica fora desta feature).
- As metas de CS-002 e CS-003 são de referência para a máquina de desenvolvimento (placa de vídeo com 6 GB de memória); caso um modelo maior não as atinja por falta de memória, o resultado é registrado e um modelo menor pode ser adotado como padrão.
- O mínimo de 3 a 5 fotos reais de redações manuscritas, uma delas com 5 erros propositais, será produzido pelo próprio autor do projeto, com transcrições de referência feitas à mão.
- O serviço local que executa os modelos já está instalado e com os modelos baixados antes do uso.
- A foto enviada é descartada depois da transcrição; não há histórico, conta de usuário nem banco de dados.
- A interface web, a avaliação por competências (notas e dicas) e a divisão da imagem em faixas ("tiling") estão **fora do escopo** desta feature e serão tratadas depois, se necessário.
