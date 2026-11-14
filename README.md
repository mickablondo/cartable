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
- ajout d'un modèle de base pour l'IA (bge-m3) via :
```shell
docker compose exec ollama ollama pull bge-m3
```