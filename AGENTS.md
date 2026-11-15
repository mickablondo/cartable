# AGENTS.md — Cartable

Ce fichier guide les agents IA (et les humains) qui travaillent sur ce dépôt.
Lis-le en entier avant toute tâche. Mets-le à jour quand une décision change.

**Convention de ce document :**
- Ce qui est écrit sans mention est **décidé** : applique-le.
- `(proposé)` = proposition non encore validée : applique-la, mais signale-le dans ta réponse.
- `À DÉCIDER` = rien n'est tranché : **ne choisis pas à ma place, pose la question** avant d'écrire du code qui en dépend.

---

## 1. Objectif du projet

Cartable est une application personnelle (usage familial, sur PC) qui génère des **fiches de révision pour le brevet (3ème)** à partir :
1. des **cours de l'élève** (PDF, Word, photos et scans),
2. de **connaissances générales** du modèle, en complément et clairement étiquetées ;
3. optionnellement, d'un **référentiel officiel** (programmes du cycle 4, attendus du brevet), non indispensable au départ.

Le projet sert aussi d'exercice pour pratiquer Java 25, Spring Modulith et Spring AI. La qualité pédagogique et la lisibilité du code priment sur la rapidité de développement.

Public : un collégien de 3ème. Langue de l'interface et des fiches : **français**.

## 2. Principes non négociables

1. **Toute affirmation issue du cours ou du référentiel cite sa source** : extrait du cours de l'élève (matière, chapitre, fichier) ou document du référentiel (titre, date, URL). Un élément présenté comme tiré du cours sans source valide est refusé.
2. **Le cours de l'élève est la base de la fiche ; les connaissances générales du modèle peuvent la compléter, mais jamais en se mélangeant au cours.** La fiche comporte deux parties distinctes : (a) le contenu **issu du cours** (et du référentiel s'il existe), avec sources ; (b) un **complément de connaissances générales**, sans source, clairement étiqueté et destiné à être vérifié. Le modèle ne doit jamais présenter une connaissance générale comme venant du cours, ni modifier ce que dit le cours. Si le cours et le modèle divergent, le cours prime et la divergence est signalée. Le modèle local étant petit, ses connaissances générales sont moins fiables que celles d'un grand modèle : le complément reste toujours à vérifier.
3. **Statut de validation.** Toute fiche créée est `TO_VERIFY`. Elle ne devient `VERIFIED` que par action explicite de l'élève ou d'un parent.
4. **Les quiz ne portent que sur des fiches `VERIFIED`.**
5. **Pas de calculs inventés.** En mathématiques et sciences, ne jamais générer d'exemples numériques non vérifiés. Reprendre fidèlement le cours ; signaler « formules à vérifier » quand l'extraction est incertaine.
6. **Données personnelles.** Ne jamais envoyer de nom, d'école, de bulletin ou autre donnée personnelle à un service cloud. Voir section 9.
7. **La matière est toujours indiquée par l'utilisateur.** À l'import d'un cours (fichier ou scan), l'utilisateur choisit la matière : c'est un champ **obligatoire**. L'IA ne la devine, ne la déduit pas du contenu, ne la modifie pas et ne la « corrige » jamais, même si le contenu semble relever d'une autre matière (au plus, elle peut signaler une incohérence apparente à l'utilisateur, qui décide).
8. **Droit d'auteur.** Ne pas reproduire de manuels, d'œuvres ou de contenus protégés. Pas de longs extraits : reformuler et renvoyer à la source.

## 3. Stack technique

| Domaine | Choix | Remarques |
|---|---|---|
| Langage | Java 25 (LTS) | Records, sealed types, pattern matching, threads virtuels |
| Framework | Spring Boot **4.1.2** | Version stable (pas de release candidate). Spring AI 2.x est conçu pour Spring Boot 4.0 / 4.1 |
| Build | **Maven** | Maven Wrapper (`mvnw`) présent : l'utiliser plutôt qu'un Maven installé sur la machine |
| Architecture | Spring Modulith **2.1.x** | Version 2.1.0 vérifiée ; prendre le dernier correctif 2.1.x disponible. Un seul projet Spring Boot, un seul JAR |
| IA | Spring AI **2.0.2** | Version stable la plus récente (la 2.1.0-RC1 est une pré-version : ne pas l'utiliser). `ChatClient`, sorties structurées, `VectorStore`, pipeline de lecture et de découpage |
| Extraction texte | Apache Tika | PDF et Word |
| Images et scans | Modèle multimodal **ou** Tesseract | À DÉCIDER (V2) |
| Base de données | PostgreSQL + pgvector | Migrations Flyway |
| Interface | Thymeleaf (+ HTMX) | « À tester » : peut changer, donc isolée dans un module dédié (section 5) |
| Modèles locaux | Ollama | Lancé par Docker Compose. L'URL d'Ollama est une propriété de configuration, pour pouvoir basculer vers une installation directe sur le PC si le GPU n'est pas utilisable depuis Docker |
| Modèles cloud | — | **Reporté et conditionnel.** Pas avant la fin de la V1, et seulement s'il est **100 % gratuit** (voir section 8). Ne pas l'implémenter sans demande explicite |
| Modèle de génération | `qwen3:8b` (provisoire) | Candidat de départ, à comparer avec `gemma3:12b` sur de vrais cours avant de le figer (voir section 8) |
| Modèle d'embedding | `bge-m3` | Multilingue (bon en français), **1024 dimensions**, contexte de 8192 tokens, licence MIT |
| Conteneurs | Docker Compose | Au minimum PostgreSQL + pgvector |
| Tests | JUnit 5, Testcontainers, tests Modulith | |

**Versions :** vérifiées le 14 novembre 2026. Utiliser les BOM (Bill of Materials) de Spring Boot, Spring AI et Spring Modulith pour fixer les versions, sans mélanger de versions entre eux. En cas d'erreur de compatibilité, ne pas bricoler : demander.

**Pièges connus de cette génération (à vérifier dans la documentation de la version utilisée) :**
- Spring Boot 4 utilise Jackson 3 : les imports passent de `com.fasterxml.jackson` à `tools.jackson`.
- Spring AI 2.x : les options des modèles se configurent par **builders** ; les setters ont disparu.
- Spring AI 2.x ne fonctionne pas avec Spring Boot 3.x.

**Interdictions de stack :**
- Pas de `--enable-preview` ni d'API en preview (dont `StructuredTaskScope`) sans décision explicite. Les threads virtuels (stables) sont autorisés.
- Pas de nouvelle dépendance sans me demander (voir section 11).

## 4. Langue et conventions de code

- **Code en anglais** : classes, méthodes, variables, noms d'événements, noms de tables et de colonnes.
- **Documentation et commentaires en français** : README, Javadoc, commentaires, messages d'erreur destinés à l'utilisateur, textes de l'interface.
- Prompts envoyés au modèle : en français, stockés dans `src/main/resources/prompts/` (proposé), jamais écrits en dur dans le code Java.
- Modéliser avec des **records** et des **sealed interfaces** (ex. : variantes de fiche par matière). Pas de Lombok (proposé).
- Pas de classes utilitaires fourre-tout. Pas de code mort, pas de TODO sans explication.
- Un commentaire explique le **pourquoi**, pas le quoi.

### Vocabulaire (proposé, à valider)

| Français (README) | Anglais (code) |
|---|---|
| cours | `course` |
| référentiel | `reference` |
| fiche | `sheet` |
| révision | `revision` |
| matière | `subject` |

- **groupId** : `dev.mickablondo.cartable`
- **Package racine** : `dev.mickablondo.cartable`
- `artifactId` : `cartable` (proposé) ; classe principale : `CartableApplication` (proposé)
- Les modules sont les sous-packages directs du package racine, ex. : `dev.mickablondo.cartable.course`.

## 5. Architecture (Spring Modulith)

Chaque **sous-package direct** du package racine est un module. Modules prévus :

| Module | Rôle | Événements émis |
|---|---|---|
| `course` | Import des fichiers, extraction du texte, découpage par chapitre, indexation | `CourseImported`, `CourseIndexed` |
| `reference` | Import et indexation des documents officiels (programmes, attendus du brevet) | `ReferenceIndexed` |
| `sheet` | Recherche dans `course` et `reference`, génération de la fiche, validation, export | `SheetGenerated`, `SheetVerified` |
| `revision` | Quiz à partir des fiches validées | (V4) |
| `web` | Interface (contrôleurs, templates) | — |

(Noms de modules : voir vocabulaire proposé en section 4.)

**Règles :**
1. Seul le **package racine d'un module** est son API publique. Tout le reste est interne (sous-package `internal` par convention).
2. Un module ne dépend **jamais** des classes internes ni des tables d'un autre module. Il passe par son API publique ou par des événements.
3. **Pas de dépendance cyclique** entre modules.
4. Communication entre modules : appels à l'API publique pour les lectures ; **événements** (`@ApplicationModuleListener`) pour les réactions asynchrones.
5. `web` ne contient **aucune logique métier** : il appelle les API publiques des modules et affiche le résultat.
6. Un module possède ses propres tables. Pas de jointure SQL entre tables de modules différents.
7. Les rôles des modules sont définis par le domaine (cours, référentiel, fiches), pas par une étape technique.

Les relations entre modules sont vérifiées par un test (`ApplicationModules.of(<classe racine>).verify()`). **Ce test doit toujours passer.** Si une tâche semble exiger de le contourner, arrête-toi et demande.

## 6. Données et modèle de fiche

- La **matière** (`subject`) est une liste fermée (enum) saisie par l'utilisateur à l'import (principe 7). Valeurs de départ : mathématiques, français, histoire-géographie, sciences physiques, SVT, technologie. Priorité de traitement : mathématiques, français, histoire-géographie d'abord, les trois autres ensuite. D'autres matières pourront être ajoutées plus tard, à la demande de l'utilisateur.
- Tout passage indexé porte les **métadonnées** : `origin` (`COURSE` ou `OFFICIAL_REFERENCE`), `subject`, `level`, `chapter`, `sourceFile` ou `sourceUrl`, `publicationDate` (référentiel).
- **Référentiel : l'utilisateur n'écrit jamais de YAML par document.** Les documents officiels sont décrits dans **un seul fichier de configuration versionné** (liste : identifiant stable, titre, matière, lien direct du PDF, page officielle de référence, date de publication, licence, classe et années scolaires concernées). L'application télécharge elle-même ces PDF à la demande (action « Mettre à jour le référentiel »), génère les métadonnées (dont la date de récupération) et indexe sans doublons. Aucun scraping de pages HTML, aucune IA dans cette étape : uniquement des téléchargements d'URL directes listées.
- Les sites officiels (education.gouv.fr, eduscol) peuvent **bloquer les téléchargements automatiques**. Ne jamais contourner une protection anti-robot ; en cas d'échec, continuer avec les autres documents, afficher un rapport clair (document, URL, raison) et accepter un PDF déposé à la main dans `data/reference/<identifiant>.pdf`.
- Le référentiel porte une **date de version** et indique la classe et les années scolaires concernées, car les programmes changent par niveau et par année.
- Sortie du modèle : **sortie structurée** mappée sur un record Java. Ne jamais parser du texte libre à la main.
- Modèle de fiche **générique** (V1), au minimum : matière, thème, notions clés, définitions, repères, sources, statut.
- Variantes par matière (chronologie, formules…) : V3 uniquement. Ne pas les anticiper.
- Toutes les tables, y compris la table de vecteurs, sont créées par **Flyway** (proposé), pas par l'initialisation automatique de Spring AI.
- Migrations : Flyway, `src/main/resources/db/migration`, jamais de modification d'une migration déjà appliquée.

## 7. Lots (versions)

Ne travaille **que sur le lot en cours**. Ne pas anticiper les lots suivants.

- **V0 — Fondations** : projet Spring Boot, Spring Modulith, `docker-compose.yml` (PostgreSQL + pgvector, et Ollama avec accès au GPU NVIDIA), vérification que le GPU est bien visible depuis le conteneur Ollama, test `verify()` des modules, page « Hello MiK@ ».
- **V1 — Mode fichiers numériques** : import PDF et Word (Tika), découpage par chapitre, indexation, référentiel officiel **optionnel** (mécanisme prêt, manifeste vide au départ ; à remplir plus tard), génération d'une **fiche générique** avec sources et statut `TO_VERIFY`, export Markdown, interface minimale avec validation.
- **V2 — Mode scans et images** : lecture des photos et scans par OCR ou modèle multimodal.
- **V3 — Fiches spécialisées** : chronologie en histoire, formules en maths, règles en grammaire, etc.
- **V4 — Révision** : quiz tirés des fiches `VERIFIED`.

Critère de fin d'un lot : fonctionnalités en place, tests verts, `verify()` vert, README à jour.

## 8. Modèles IA et profils Spring

- Un seul profil pour l'instant : `local` (Ollama). Un profil `cloud` pourra être ajouté plus tard (comparaison de la qualité des fiches), à ces conditions : il n'est **pas** prévu dans V0 ni V1, il n'est utilisé **que si l'offre est 100 % gratuite**, sans carte bancaire enregistrée ni dépassement payant possible. Si un fournisseur exige un moyen de paiement ou bascule en payant, l'agent s'arrête et demande. Les règles de confidentialité de la section 9 s'appliquent (certaines offres gratuites utilisent les requêtes pour entraîner leurs modèles).
- Le code métier ne dépend jamais d'un fournisseur : il utilise les abstractions Spring AI (`ChatClient`, `EmbeddingModel`, `VectorStore`).
- Les **embeddings** restent en local (volume élevé d'appels, pas de données envoyées).
- Modèles retenus : embedding `bge-m3` (**dimension 1024**, valeur à utiliser dans la propriété, la migration Flyway et le test de cohérence) ; génération `qwen3:8b` à titre **provisoire**, à confirmer après comparaison sur de vrais cours (V1.6) ; vision/OCR : À DÉCIDER (V2). Ces noms viennent **uniquement de la configuration**, jamais écrits en dur dans le code. Vérifier la disponibilité exacte des noms dans la bibliothèque Ollama avant de les télécharger.
- Les clés d'API vivent dans des **variables d'environnement**, jamais dans le dépôt.

## 9. Sécurité et confidentialité

- Aucun secret, mot de passe ou clé dans le dépôt (dont `application.yml`, `docker-compose.yml`, tests).
- Les cours de l'élève et les fichiers importés restent hors du dépôt Git (dossier `data/` à la racine, ignoré par Git : proposé).
- Profil `cloud` : n'envoyer que des passages de cours et de référentiel, après suppression des données personnelles. Voir principe 6.
- Vérifier les conditions d'utilisation des offres gratuites avant de les brancher : certaines utilisent les requêtes pour entraîner leurs modèles.

## 10. Build, exécution et tests

Système de l'utilisateur : **Windows**. Commandes (PowerShell ou invite de commandes, à la racine du projet) :

```
docker compose up -d          # démarre les services (PostgreSQL + pgvector, Ollama)
.\mvnw.cmd verify             # compile, teste, vérifie les modules
.\mvnw.cmd spring-boot:run    # lance l'application (profil local par défaut)
```

**Particularités Windows :**
- Docker Desktop est requis (avec WSL 2 en général). Les tests Testcontainers en dépendent : Docker Desktop doit être démarré avant `verify`.
- Chemins : ne jamais écrire de chemin en dur avec des `\` ; utiliser `Path` / `Path.of(...)` en Java.
- Fins de ligne : ajouter un `.gitattributes` qui force les fins de ligne LF pour `mvnw` et les scripts `.sh` (proposé), afin d'éviter des erreurs entre Windows et les conteneurs.
- **Matériel de l'utilisateur : carte NVIDIA GeForce RTX 3080** (10 Go de mémoire vidéo pour la version standard ; il existe une variante 12 Go : à confirmer). Ollama dans Docker doit utiliser ce GPU, ce qui demande une configuration explicite dans `docker-compose.yml` (réservation de GPU NVIDIA) et Docker Desktop avec WSL 2. Suivre la documentation Ollama pour Docker sur Windows. Sans GPU visible, les modèles tournent sur le processeur, beaucoup plus lentement : dans ce cas, le signaler à l'utilisateur plutôt que de continuer en silence.
- Ordre de grandeur pour le choix des modèles (à affiner lors du choix, voir section 8) : avec environ 10 Go de mémoire vidéo, viser des modèles **quantifiés de taille moyenne** ; un modèle trop gros déborde sur la RAM et ralentit fortement.

**Tests :**
- JUnit 5 pour la logique ; Testcontainers pour tout ce qui touche à PostgreSQL ; tests de module Modulith pour isoler un module.
- Tout comportement nouveau est livré avec son test. Une correction de bug est livrée avec un test qui échoue avant et passe après.
- Les appels réels à un modèle ne sont **pas** lancés dans les tests automatiques (lents, non déterministes, coûteux) : utiliser un faux client ou des réponses fixes.
- Un test lent ou nécessitant un modèle réel porte l'étiquette JUnit `@Tag("model")` (proposé) et est exclu du `verify` par défaut.

## 11. Façon de travailler (règles pour les agents)

1. **Une tâche = un objectif précis et vérifiable.** Si la tâche est trop large, propose un découpage avant de coder.
2. **En cas de doute, demande.** Ne devine pas une décision marquée `À DÉCIDER`, ne comble pas un trou par une valeur inventée.
3. **Ne modifie pas ce qui n'est pas demandé** : pas de refactoring opportuniste, pas de reformatage massif, pas de renommage hors périmètre.
4. **Ajout de dépendance, changement de version, modification de la structure des modules, du `pom.xml` ou de ce fichier : demande d'abord.**
5. **Avant de coder**, résume en quelques lignes ce que tu comptes faire (fichiers touchés, modules concernés). Après, indique ce que tu as modifié et comment tu l'as vérifié.
6. **Vérifie l'API de la version réellement utilisée** (Spring AI, Spring Modulith) dans sa documentation officielle plutôt que de te fier à ta mémoire : ces projets évoluent vite.
7. **Ne jamais désactiver, ignorer ou affaiblir un test** (dont `verify()`) pour faire passer un build.
8. **Reste économe** : ne relis pas tout le dépôt à chaque tâche, ne lance pas d'actions longues ou répétées sans raison.
9. Je veux **comprendre** le code : privilégie le code simple et lisible, et explique tes choix non évidents.

## 12. Git

- **Une branche par tâche.**
- **Les commits sont gérés par l'humain, pas par l'agent.** L'agent ne lance jamais `git commit`, `git push`, `git merge`, `git reset`, `git rebase` ni aucune commande qui écrit dans l'historique ou crée des branches. La convention de nommage (branches et messages de commit, langue incluse) est celle de l'humain : l'agent ne la définit pas et ne suggère pas de message de commit, sauf si on le lui demande.
- **L'agent prévient quand il est temps de commiter**, dès qu'un changement cohérent est terminé : le code compile, les tests passent et `verify()` est vert. Il indique alors en une ou deux phrases ce que contient ce lot de changements (fichiers et objectif), pour que l'humain décide du commit.
- Si la tâche démarre alors que la branche en cours n'est pas une branche dédiée (par exemple `main`), l'agent le signale avant de modifier quoi que ce soit.
- Ne jamais laisser entrer dans un commit de fichiers de données personnelles, de secrets ni de modèles téléchargés : l'agent signale tout fichier de ce type qu'il repère dans les changements.

## 13. Hors périmètre

- Reproduction de manuels scolaires ou de contenus commerciaux.
- Envoi de données personnelles à un service cloud.
- Multi-utilisateurs, authentification, déploiement en ligne (non prévus pour l'instant).
- Microservices : Cartable reste un monolithe modulaire.

## 14. Décisions en attente (récapitulatif)

À décider avec l'utilisateur, au moment où le sujet devient nécessaire :
- [ ] **Modèle de génération** : à confirmer après comparaison sur de vrais cours (V1.6)
- [ ] **Modèle de vision/OCR** : V2
- [ ] **Profil cloud** : optionnel, après la V1, uniquement si 100 % gratuit

Valeurs proposées par défaut (à signaler à chaque usage, modifiables) :
- Vocabulaire anglais des modules (section 4), `artifactId`, classe principale
- Tables gérées par Flyway, format YAML pour décrire les documents du référentiel
- Dossier `data/` ignoré par Git, étiquette `@Tag("model")` pour les tests avec modèle réel
- Remarque : la **convention de commits et de branches** est celle de l'humain (section 12), pas une décision en attente
