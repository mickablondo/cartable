Pour la fiche de mathématiques, produis le contenu structuré suivant :
- `theme` : le thème demandé, formulé fidèlement.
- `courseContent.keyConcepts` : les notions clés avec leurs `sourceIds`.
- `courseContent.definitions` : les définitions avec `term`, `definition` et `sourceIds`.
- `courseContent.properties` : les propriétés avec `name`, `statement` et `sourceIds`.
- `courseContent.theorems` : les théorèmes avec `name`, `statement` et `sourceIds`. Reprends les énoncés fidèlement au cours, sans généraliser ni compléter.
- `courseContent.formulas` : les formules avec `name`, `latex`, `explanation` et `sourceIds`. Écris chaque formule en LaTeX. Ne supprime jamais le marqueur `[formule à vérifier]` présent dans un passage cité ; signale-le dans l'explication.
- `courseContent.methods` : les méthodes avec `name`, les étapes ordonnées dans `steps`, et `sourceIds`. N'ajoute pas d'étape absente des passages.
- `courseContent.commonErrors` : les erreurs fréquentes avec `error`, `correction` et `sourceIds`. Ne les invente pas.
- `courseContent.examples` : uniquement les exemples déjà présents dans les passages de cours, recopiés exactement dans `text` avec leurs `sourceIds`. N'invente aucun exemple, nombre, calcul, résultat ni contexte.
- `generalKnowledge` : compléments séparés, sans source. Tout complément contenant une formule doit explicitement porter la mention « à vérifier ».
- `limitations` : éléments insuffisamment couverts ou divergences à signaler.

La partie `courseContent` est rédigée uniquement à partir des passages fournis. Chaque élément doit citer au moins une source exacte. N'invente aucun calcul et ne modifie pas les énoncés du cours. Les formules restent en LaTeX, sans délimiteurs Markdown ajoutés.
