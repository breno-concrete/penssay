# Contrato: `POST /api/evaluations`

## Requisição

`Content-Type: application/json`

```json
{
  "theme": "Desafios para a valorização de comunidades e povos tradicionais no Brasil",
  "text": "Texto completo da redação, já revisado pelo aluno,\ncom as quebras de linha da folha."
}
```

Parâmetro opcional: `?mode=scores` (formato reduzido, usado pelo experimento 002).

## Resposta `200 OK`: formato completo

```json
{
  "competencies": [
    {
      "number": 1,
      "score": 160,
      "justification": "Poucos desvios da norma culta, como \"excessão\" no 2º parágrafo.",
      "tips": [
        "Revise a grafia das palavras terminadas em -ção e -ssão.",
        "Evite começar frases com pronome oblíquo átono."
      ]
    }
  ],
  "totalScore": 840,
  "zeroReason": null
}
```

`competencies` tem sempre 5 itens, de 1 a 5 (o exemplo mostra só o primeiro).

## Resposta `200 OK`: formato reduzido (`?mode=scores`)

```json
{
  "scores": [160, 160, 120, 160, 80],
  "totalScore": 680,
  "zeroReason": null
}
```

## Resposta `200 OK`: nota zero

```json
{
  "competencies": [
    { "number": 1, "score": 0, "justification": "...", "tips": ["..."] }
  ],
  "totalScore": 0,
  "zeroReason": "TOO_SHORT"
}
```

Valores de `zeroReason`: `TOO_SHORT` (7 linhas ou menos), `OFF_TOPIC` (fuga total ao tema),
`NOT_ARGUMENTATIVE` (não é dissertativo-argumentativo). Com `TOO_SHORT`, o modelo não é
chamado, e as justificativas explicam o motivo.

## Erros

Corpo: `{"message": "..."}`, em português do Brasil.

| Status | Quando | Mensagem |
|---|---|---|
| `400` | Tema ausente ou vazio | "Informe o tema da redação." |
| `400` | Texto ausente ou vazio | "Envie o texto da redação." |
| `400` | `mode` desconhecido | "Modo de correção inválido. Use mode=scores ou não envie o parâmetro." |
| `502` | Resposta do modelo fora do formato | "Não foi possível corrigir a redação agora. Tente de novo." |
| `503` | Ollama fora do ar ou modelo não instalado | "O serviço de correção está indisponível. Verifique se o Ollama está rodando." |
