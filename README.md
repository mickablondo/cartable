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
- Déposer les PDF officiels et leurs descriptions YAML homonymes dans `data/reference/` (champs `title`, `sourceUrl`, `retrievedAt`, `license`, `subject`, `publicationDate`) ;
- Lancer l'import une seule fois avec `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--cartable.reference.import.enabled=true"` ; `CARTABLE_REFERENCE_DIRECTORY` permet de changer le dossier ;
- Les matières YAML utilisent les noms de l'enum, par exemple `HISTORY_GEOGRAPHY`. Les dates sont au format `AAAA-MM-JJ`. Les PDF inchangés déjà indexés sont ignorés ; une modification remplace les passages associés à la même URL source.

#### v1.5
- Les API publiques de `course` et `reference` recherchent les quatre passages les plus proches pour une requête et une matière ; le filtrage par origine et matière s'effectue sur les métadonnées pgvector.
- Chaque résultat conserve sa référence : fichier et chapitre pour un cours ; titre, URL, fichier, chapitre et date de publication pour le référentiel.
- Les recherches depuis `sheet` doivent passer par ces API publiques, sans accès direct au VectorStore ni aux tables des autres modules.
#### v1.6
- Le module `sheet` génère une fiche générique structurée à partir des passages trouvés pour le thème et la matière demandés ; chaque notion, définition et repère conserve ses identifiants de source.
- Le prompt de génération est dans `src/main/resources/prompts/sheet-generation.st`. Les fiches sont enregistrées par Flyway au statut `TO_VERIFY`, puis l'événement `SheetGenerated` est publié.
- Les tests de génération utilisent un faux `ChatClient` avec des réponses fixes ; aucun modèle réel n'est appelé automatiquement.

#### v1.7
- L'API du module `sheet` permet de valider explicitement une fiche ; son statut passe de `TO_VERIFY` à `VERIFIED` et l'événement `SheetVerified` est publié.
- L'export Markdown inclut les notions, définitions, repères et références des sources. Une fiche `TO_VERIFY` porte un avertissement visible.

RAF v1 : v1.8 puis corriger v1.4 par v1.4b