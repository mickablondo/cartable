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
