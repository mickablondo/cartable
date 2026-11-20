# cartable
Application modulaire permettant la génération de fiches pour le brevet depuis des cours

## Technique
### Stack technique
- Java 25 ;
- Spring Boot ;
- Spring Modulith ;
- Spring AI ;
- Apache Tika pour l'extraction du texte provenant des fichiers de type pdf ou Word ;
- Modèle multimodal ou Tesseract pour l'extraction du texte provenant des images ;
- Thymeleaf à tester pour l'interface ;
- PostgreSQL avec pgvector ;
- Docker Compose ;

### Configuration locale
- Le profil `local` est le profil par défaut ; l’URL d’Ollama se configure avec `OLLAMA_BASE_URL` (par défaut `http://localhost:11434`).
- Les modèles configurés sont `bge-m3` pour les embeddings et `qwen3:8b` pour la rédaction. Ils peuvent être remplacés avec `CARTABLE_EMBEDDING_MODEL` et `CARTABLE_CHAT_MODEL`.
- La dimension des embeddings est fixée à 1024 dans la configuration Spring AI et la migration Flyway. Le test vérifie leur cohérence avec la dimension mesurée du modèle.
- Flyway crée les tables métier, `vector_store` et `event_publication`. L’initialisation automatique des schémas pgvector et Modulith est désactivée.

### Modules
Les modules imaginés pour le moment sont :
- cours : import des fichiers des différents cours, découpage par chapitre et indexation ;
- referentiel : le programme officiel attendu pour le brevet, en complément des cours (exemple via Eduscol) ;
- fiches : recherche dans les deux autres modules pour générer une fiche de révision ;
- revision : questions au hasard (sous forme de quiz par exemple) depuis les différentes fiches créées ;

## Fonctionnel
### Lots
- v0 - Les fondations : mise en place du projet et affichage d'un magnifique "Hello MiK@" pour valider le tout ;
- v1 - Mode Fichiers Numériques : import pdf + word, indexation, import du référentiel, génération de fiches génériques ;
- v2 - Mode Scans et Images : lecture des images et scans par OCR ;
- v3 - Fiches spécialisées : ajout des spécificités sur chaque matière (chronologie en histoire, formules en maths ...) ;
- v4 - Révision : questions générées depuis les fiches validées ;

### Règles métier
- chaque affirmation d'une fiche doit citer sa source ;
- chaque fiche générée passe par un statut "à valider" qui sera, du coup, à valider ;

## Travaux en cours
### v0
- Branche 1-v0---fondations
- Création du docker-compose.yml (pour ollama et postgre/pgvector)
- Validation du docker-compose via :
```shell
docker compose up -d
docker compose logs ollama
```
- ajout des "program arguments" :
```
--spring.docker.compose.enabled=false --spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/cartable --spring.datasource.username=cartable --spring.datasource.password=***** --spring.ai.ollama.base-url=http://localhost:11434
```
- ajout d'une page d'accueil très basique sur http://localhost:8080
### v1
#### v1.1
- ajout d'un modèle de base pour l'IA (bge-m3) via :
```shell
docker compose exec ollama ollama pull bge-m3
```
#### v1.2
- Import des cours PDF et Word avec conservation du fichier original dans `data/courses/` (ignoré par Git) et du texte extrait en base ;
- Le chemin du fichier enregistré en base reste relatif à `data/` ; `CARTABLE_DATA_DIRECTORY` permet de changer le dossier de stockage.
#### v1.3
- Découpage sur les titres explicites « Chapitre », « Partie », « Thème », « Séquence » et « Dossier » ; sinon, découpage par taille avec recouvrement, en conservant le statut « Sans chapitre détecté » ;
- Indexation asynchrone des passages dans pgvector avec la matière choisie à l'import et les métadonnées de source ; un échec reste récupérable dans le registre d'événements Modulith.
#### v1.4
- Import manuel de PDF officiels accompagnés de descriptions YAML homonymes dans `data/reference/` (remplacé par v1.4b).
#### v1.4b
- La liste unique des documents officiels se trouve dans `src/main/resources/reference-documents.yaml`. Chaque entrée précise l'identifiant, le titre, la matière, les URL directes du PDF et de la page officielle, la date de publication, la licence, la classe et les années scolaires.
- Le bouton « Mettre à jour le référentiel » télécharge les PDF dans `data/reference/<id>.pdf`, vérifie leur taille (25 Mo) et leur signature PDF, puis les indexe. Les téléchargements ont des délais d'attente et ne suivent pas les redirections.
- Une mise à jour n'est pas lancée au démarrage par défaut. Les erreurs sont affichées par document; si le site bloque le téléchargement, déposer le PDF dans `data/reference/<id>.pdf` puis relancer la mise à jour.
- Les anciens YAML par PDF sont laissés en place, mais ignorés. Un PDF local est utilisé uniquement si son identifiant est déclaré dans le manifeste.

#### v1.5
- Les API publiques de `course` et `reference` recherchent les quatre passages les plus proches pour une requête et une matière ; le filtrage par origine et matière s'effectue sur les métadonnées pgvector.
- Chaque résultat conserve sa référence : fichier et chapitre pour un cours ; titre, URL, fichier, chapitre et date de publication pour le référentiel.
- Les recherches depuis `sheet` doivent passer par ces API publiques, sans accès direct au VectorStore ni aux tables des autres modules.
#### v1.6
- Le module `sheet` génère une fiche hybride : le contenu issu des cours et du référentiel garde ses identifiants de source, tandis que le complément de connaissances générales est séparé, étiqueté `GENERAL_KNOWLEDGE` et à vérifier.
- Un élément sourcé sans identifiant de source valide ou un élément du complément qui cite une source est refusé. Sans passage de cours trouvé pour le thème, la génération s'arrête avec un message explicite.
- Le prompt de génération est dans `src/main/resources/prompts/sheet-generation.st`. En mathématiques et sciences, il interdit les exemples numériques inventés et demande que les formules du complément soient marquées « à vérifier ». Les fiches sont enregistrées par Flyway au statut `TO_VERIFY`, puis l'événement `SheetGenerated` est publié.
- Les tests de génération utilisent un faux `ChatClient` avec des réponses fixes ; aucun modèle réel n'est appelé automatiquement.

#### v1.7
- L'API du module `sheet` permet de valider explicitement une fiche ; son statut passe de `TO_VERIFY` à `VERIFIED` et l'événement `SheetVerified` est publié.
- L'export Markdown inclut les notions, définitions, repères et références des sources. Une fiche `TO_VERIFY` porte un avertissement visible.
#### v1.8
- La page d'accueil permet d'importer un fichier PDF ou Word avec choix obligatoire de la matière et affiche les cours importés ainsi que leur état d'indexation.
- Le formulaire de création et la page de fiche permettent de demander une fiche, consulter son contenu, ses sources et son statut, la valider, et l'exporter en Markdown.
- Les contrôleurs du module `web` ne communiquent avec les modules métier que par leurs API publiques ; l'interface utilise Thymeleaf sans HTMX.

### v2.1 — Préparation
- L'import accepte JPEG, PNG et WebP, seuls ou en plusieurs pages dans l'ordre sélectionné, avec une matière obligatoire.
- Les PDF sans texte extractible sont conservés et marqués comme nécessitant une lecture d'images ; aucune lecture OCR ou multimodale n'est lancée.
- Les états du cours sont `IMPORTED`, `TEXT_EXTRACTION_IN_PROGRESS`, `TEXT_TO_REVIEW`, `VALIDATED`, `INDEXED` et `FAILED`.

### v2.2 — Lecture des images
- Les images importées et les pages des PDF scannés sont envoyées une par une au modèle multimodal Ollama ; une seule requête de vision s'exécute à la fois.
- Le nom du modèle est requis dans `VISION_MODEL` (sans modèle par défaut). Le délai HTTP par défaut est de 5 minutes et se règle avec `CARTABLE_VISION_READ_TIMEOUT`.
- Chaque transcription est conservée avec son numéro de page. La progression, les erreurs, et le statut `TEXT_TO_REVIEW` sont affichés sur la page d'accueil.
- Le prompt interdit d'ajouter ou de corriger du texte, demande `[illisible]` pour les passages illisibles et `[formule à vérifier]` après les formules.
- Le raisonnement du modèle est désactivé explicitement dans les options de chaque requête. Les tests utilisent des réponses simulées et n'appellent aucun modèle.

### v2.3 — Relecture avant indexation
- Un texte lu depuis une image reste `TEXT_TO_REVIEW` et n'est pas indexé tant que l'utilisateur ne l'a pas relu.
- La page de relecture permet de corriger chaque page séparément ; la validation publie `CourseTextValidated` et déclenche le découpage et l'indexation existants.
- Les passages de cours portent `origin=COURSE` et la métadonnée `textReviewedAndValidated`; celle-ci vaut `true` pour les textes d'image relus. Les imports PDF et Word numériques gardent leur indexation directe.
- Les tests vérifient l'absence d'indexation avant validation puis l'indexation du texte corrigé après validation, sans appel au modèle de vision.
