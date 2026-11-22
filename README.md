# cartable

![logo.png](logo.png)

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
- Les règles communes du prompt sont dans `src/main/resources/prompts/sheet-generation-common.st` et sont assemblées avec le prompt de la variante (`sheet-generation-generic.st`). En mathématiques et sciences, elles interdisent les exemples numériques inventés et demandent que les formules du complément soient marquées « à vérifier ». Les fiches sont enregistrées par Flyway au statut `TO_VERIFY`, puis l'événement `SheetGenerated` est publié.
- Les tests de génération utilisent un faux `ChatClient` avec des réponses fixes ; aucun modèle réel n'est appelé automatiquement.

#### v1.7
- L'API du module `sheet` permet de valider explicitement une fiche ; son statut passe de `TO_VERIFY` à `VERIFIED` et l'événement `SheetVerified` est publié.
- L'export Markdown inclut les notions, définitions, repères et références des sources. Une fiche `TO_VERIFY` porte un avertissement visible.
#### v1.8
- La page d'accueil permet d'importer un fichier PDF ou Word avec choix obligatoire de la matière et affiche les cours importés ainsi que leur état d'indexation.
- Le formulaire de création et la page de fiche permettent de demander une fiche, consulter son contenu, ses sources et son statut, la valider, et l'exporter en Markdown.
- Les contrôleurs du module `web` ne communiquent avec les modules métier que par leurs API publiques ; l'interface utilise Thymeleaf sans HTMX.

### v2
#### v2.1 — Préparation
- L'import accepte JPEG, PNG et WebP, seuls ou en plusieurs pages dans l'ordre sélectionné, avec une matière obligatoire.
- Les PDF sans texte extractible sont conservés et marqués comme nécessitant une lecture d'images ; aucune lecture OCR ou multimodale n'est lancée.
- Les états du cours sont `IMPORTED`, `TEXT_EXTRACTION_IN_PROGRESS`, `TEXT_TO_REVIEW`, `VALIDATED`, `INDEXED` et `FAILED`.

#### v2.2 — Lecture des images
- Les images importées et les pages des PDF scannés sont envoyées une par une au modèle multimodal Ollama ; une seule requête de vision s'exécute à la fois.
- Le modèle multimodal est configuré par `VISION_MODEL` ; il reste local à Ollama. Le délai HTTP par défaut est de 5 minutes et se règle avec `CARTABLE_VISION_READ_TIMEOUT`.
- Chaque transcription est conservée avec son numéro de page. La progression, les erreurs, et le statut `TEXT_TO_REVIEW` sont affichés sur la page d'accueil.
- Le prompt interdit d'ajouter ou de corriger du texte, demande `[illisible]` pour les passages illisibles et `[formule à vérifier]` après les formules.
- Le raisonnement du modèle est désactivé explicitement dans les options de chaque requête. Les tests utilisent des réponses simulées et n'appellent aucun modèle.

#### v2.3 — Relecture avant indexation
- Un texte lu depuis une image reste `TEXT_TO_REVIEW` et n'est pas indexé tant que l'utilisateur ne l'a pas relu.
- La page de relecture permet de corriger chaque page séparément ; la validation publie `CourseTextValidated` et déclenche le découpage et l'indexation existants.
- Les passages de cours portent `origin=COURSE` et la métadonnée `textReviewedAndValidated`; celle-ci vaut `true` pour les textes d'image relus. Les imports PDF et Word numériques gardent leur indexation directe.
- Les tests vérifient l'absence d'indexation avant validation puis l'indexation du texte corrigé après validation, sans appel au modèle de vision.

#### v2.4 — Interface pour les scans
- Le formulaire permet de réordonner plusieurs images avec des boutons simples, ou d'importer un PDF scanné, et conserve le choix obligatoire de la matière.
- La page d'accueil s'actualise toutes les 10 secondes tant qu'un cours est en cours de traitement ; un lien permet aussi l'actualisation manuelle.
- La relecture affiche l'image de chaque page à côté du texte modifiable. « Valider le texte » enregistre la page ; la validation finale de toutes les pages déclenche l'indexation.
- Les tests de contrôleurs vérifient le formulaire, l'actualisation et l'affichage/accès aux images. Les tests de cours vérifient la lecture des images et le rendu des pages PDF sans appeler de modèle.

#### v2.5 — Durcissement des scans
- Les images restent sous `data/courses/<id>/pages/` ; la base conserve uniquement des chemins relatifs. `data/` est ignoré par Git. Le texte transcrit reste dans PostgreSQL local.
- Le lecteur d'images utilise `OllamaChatModel` et refuse au démarrage toute URL Ollama autre que `localhost`, `127.0.0.1` ou `::1`. Au démarrage, une vérification refuse également tout modèle de conversation ou d'embedding qui n'est pas fourni par Ollama : l'ajout ultérieur d'un fournisseur cloud nécessite donc une décision et ne peut pas envoyer silencieusement les images ou leur texte hors de la machine.
- Les limites par défaut sont de 20 images par cours, 15 Mo par image, 25 Mo par fichier PDF ou document, et 100 Mo par requête. Elles se règlent avec `CARTABLE_COURSE_MAX_IMAGES_PER_COURSE`, `CARTABLE_COURSE_MAX_IMAGE_SIZE`, `CARTABLE_COURSE_MAX_SCANNED_PDF_SIZE`, `CARTABLE_COURSE_MAX_FILE_SIZE` et `CARTABLE_COURSE_MAX_REQUEST_SIZE`. Le PDF est limité avant d'examiner s'il est scanné. Le démarrage refuse des valeurs incohérentes, notamment une requête qui ne peut contenir au moins trois images à leur taille maximale ou un fichier individuel maximal. La limite globale multipart d'un fichier est `max-file-size` ; elle doit couvrir les limites d'image et de PDF.
- Le modèle de vision provisoire est `qwen2.5vl:7b`, réglable par `VISION_MODEL`. Le nom ne figure que dans la configuration. `OLLAMA_BASE_URL` reste configurable pour une instance Ollama locale, mais une URL distante fait échouer le démarrage.
- Qwen2.5-VL documente une plage par défaut de 4 à 16 384 jetons visuels par image et propose des plafonds plus bas comme compromis de performance/coût. Le prétraitement conserve le plafond haut par défaut : `CARTABLE_VISION_MAX_IMAGE_PIXELS` vaut `16 384 × 28 × 28` (12 845 056 pixels), et `CARTABLE_VISION_JPEG_QUALITY` vaut `1.0`. Seules les images JPEG/PNG et les pages PDF rendues au-delà du plafond sont réduites ; les fichiers originaux ne sont jamais modifiés. Le JDK n'ayant pas de décodeur WebP intégré, les WebP sont transmis tels quels à Ollama pour son prétraitement natif. Références : [fiche officielle Qwen2.5-VL](https://huggingface.co/Qwen/Qwen2.5-VL-7B-Instruct#quickstart) et [vision Ollama](https://docs.ollama.com/capabilities/vision).
- Pour changer de modèle, installer un modèle multimodal local dans Ollama, puis définir `VISION_MODEL` à son nom et tag exacts (par exemple `qwen2.5vl:7b`) avant de lancer l'application. Garder `OLLAMA_BASE_URL` sur une adresse locale ; les noms sont configurés dans `application-local.yaml`, pas dans le code.
- Pour comparer manuellement la qualité, conserver un petit jeu identique de pages non personnelles avec leur transcription de référence, couvrant écriture manuscrite, accents, tableaux et formules. Pour chaque modèle, comparer la transcription ligne par ligne, compter les mots erronés/omis par page, relever séparément les erreurs de formules et de structure, et noter les passages signalés illisibles ainsi que le temps de lecture. Garder les mêmes images et consignes pour chaque essai ; aucun modèle réel n'est appelé par les tests automatiques.

### v3
#### v3.1 — Structure des fiches spécialisées
- `Sheet` est une interface scellée ; `GenericSheet` reste le type par défaut. Le type est sélectionné explicitement avec la matière, sans déduction depuis le contenu.
- Le registre associe les couples matière/type disponibles. Chaque variante dispose d'un prompt distinct et partage les consignes communes sans les recopier.
- Les colonnes communes restent relationnelles ; le contenu propre à la variante est stocké dans `variant_data` en JSONB avec `schema_version`. La migration V12 copie le contenu générique historique dans ce JSON et conserve les colonnes `definitions` et `landmarks`.
- Un type, une version ou un contenu JSON non pris en charge est présenté en mode dégradé avec un message, sans empêcher la lecture des données communes.