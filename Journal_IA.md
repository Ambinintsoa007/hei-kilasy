# Journal IA — C1 Pénalité de retard

Outil utilisé : OpenCode, assistant IA de développement dans l’environnement IntelliJ IDEA / terminal.

L’historique de debug lié à l’environnement est conservé à part dans `Journal_IA_debug.md`.

## Échange 1 — Passage du besoin C1 à l’implémentation

### Demande
Appliquer la pénalité de retard demandée pour C1 au calcul des notes, en partant du projet existant et sans réorganiser le code.

### Proposition de l'IA
Lecture ciblée des trois points où une note sur 20 est produite :

- `SubmissionController.java` : `GET /submissions/{id}` et `PUT /submissions/{id}/grade` ;
- `GradeReport.java` : calcul des lignes du relevé étudiant ;
- `CsvExport.java` : génération de `/exports/grades.csv`.

Constat : ces trois composants calculaient la note de façon autonome, avec des arrondis différents (`Math.round` / `Math.floor` / `RoundingMode.HALF_UP`). Les données de démonstration de `src/main/resources/data.sql` ont été lues pour repérer les cas utiles : soumissions avant l’échéance, en retard d’un jour, de trois jours, de huit jours, et soumission non pénalisée avec un barème différent de 20.

La règle proposée était : conserver `raw_points` comme seule donnée stockée, calculer `raw_points × 20 / max_points`, puis appliquer la pénalité au moment de produire la note affichée ou exportée.

### Décision personnelle
Implémenter le calcul dans les trois fichiers Java existants, sans créer de nouvelle classe, de nouveau package, de migration ou de dépendance entre composants.

### Pourquoi
Le besoin C1 porte sur les valeurs restituées par l’application, pas sur le stockage. Modifier uniquement les trois points de sortie permet d’obtenir le même résultat dans l’API JSON, le relevé et le CSV tout en conservant l’organisation actuelle du projet.

### Vérification
Le diff Git ne montre que ces trois fichiers Java pour C1. Aucune classe ni configuration supplémentaire n’apparaît.

## Échange 2 — Choix de l’emplacement du calcul

### Demande
Décider où placer le calcul de la pénalité sans introduire de couche ni de dépendance inter-classes.

### Proposition de l'IA
Deux approches ont été présentées :

1. centraliser le calcul dans `GradeReport` et le réutiliser depuis les deux autres composants ;
2. conserver une méthode privée `penalizedGrade()` dans chacune des trois classes concernées.

La première option réduirait la duplication, mais elle ferait dépendre `SubmissionController` et `CsvExport` de `GradeReport`, qui est actuellement dédié aux relevés.

### Décision personnelle
Retenir la deuxième option : une méthode privée `penalizedGrade()` dupliquée dans `SubmissionController.java`, `GradeReport.java` et `CsvExport.java`. Ne pas centraliser le calcul dans `GradeReport`.

### Pourquoi
La duplication est ici préférée à un changement de dépendances. Elle garde chaque composant autonome et évite de modifier des relations qui ne sont pas concernées par C1.

### Vérification
Le diff affiche trois déclarations privées distinctes de `penalizedGrade()` et aucun import croisé entre ces classes.

## Échange 3 — Règle de calcul retenue

### Demande
Préciser comment compter les jours de retard et comment appliquer la pénalité, afin que les trois sorties restent cohérentes.

### Proposition de l'IA
Calcul proposé :

- convertir l’échéance et la date de rendu en dates calendaires avec `toEpochDay()` ;
- retenir `max(0, date de rendu - échéance)` comme nombre de jours de retard ;
- appliquer un coefficient de pénalité `min(jours × 0,10 ; 0,50)`, soit 10 % par jour, plafonné à 50 % ;
- calculer la note à partir de `BigDecimal`, avec un plancher à 0 ;
- effectuer un seul arrondi final au dixième avec `RoundingMode.HALF_UP`.

### Décision personnelle
Retenir cette règle telle quelle : l’heure de rendu n’est pas prise en compte pour compter les jours, une soumission le jour de l’échéance n’est pas pénalisée, et la pénalité est plafonnée à la moitié de la note.

### Pourquoi
Le comptage en jours calendaires rend le résultat prévisible et évite les pénalités négatives. L’arrondi unique évite les écarts entre les anciens calculs (`Math.round`, `Math.floor`) et le relevé. Le calcul direct en `BigDecimal` évite d’introduire des erreurs liées aux `double`.

### Vérification
Les valeurs renvoyées par les endpoints HTTP ont été comparées aux données de démonstration et aux résultats attendus.

| Soumission | Échéance | Rendu | Note vérifiée |
|---|---|---|---|
| 1 | 2026-11-10 | 2026-11-09 18:00 | 16,0/20 |
| 2 | 2026-11-10 | 2026-11-11 02:00 | 14,4/20 |
| 3 | 2026-11-10 | 2026-11-13 10:00 | 11,2/20 |
| 4 | 2026-11-10 | 2026-11-18 15:00 | 8,0/20 |
| 11 | 2026-11-17 | 2026-11-17 18:30 | 13,8/20 |

Le relevé d’Alice affiche 16,0/20 pour « Tri à bulles ». Le relevé de Bob (`GET /students/2/transcript`) affiche 14,4/20 pour la soumission n°2, comme le détail `GET /submissions/2` et l’export CSV. L’export CSV affiche Bob 14,4, Clara 11,2, David 8,0 et Emma 13,8. Les soumissions non notées laissent la note vide.

## Échange 4 — Tests et données de démonstration

### Demande
Contrôler les effets de C1 sans modifier les données de démonstration et sans confondre les tests signalés avec les tests réellement vérifiés ici.

### Proposition de l'IA
Ne pas modifier `data.sql`, ne pas inventer de sortie de test, et distinguer explicitement :

- ce qui a été signalé par l’utilisateur ;
- ce qui a pu être relu dans la base, via HTTP ou dans Mailpit.

### Décision personnelle
Conserver les données de démonstration telles quelles. Enregistrer `mvn test` comme « signalé par l’utilisateur » seulement, et ne pas attribuer à l’IA les tests ou les résultats qui n’ont pas été exécutés par elle.

### Pourquoi
Le but était de documenter l’état réel de la vérification. Aucun rapport Maven n’est présent dans le dépôt, et aucun corps de requête ni aucune réponse HTTP n’ont été conservés pour le test de notation. Les présenter comme des résultats produits par l’IA serait inexact.

### Vérification
- `mvn test` : signalé par l’utilisateur comme réussi ; aucun rapport `target/surefire-reports` n’est présent dans le projet, donc non rejoué ici et non attribué à l’IA.
- `GET /submissions/{id}` et `/exports/grades.csv` : résultats relus directement ; valeurs détaillées dans l’échange 3.
- `PUT /submissions/2/grade` : signalé par l’utilisateur ; ni la requête ni la réponse n’ont été conservées. Un message est bien présent dans Mailpit pour `bob@etu.kilasy.test`, avec « 14.4/20 » et le commentaire « Test C1 : pénalité de retard ». Cela confirme la notification reçue, pas la réponse HTTP elle-même.
- Aucun test n’a été exécuté par l’IA après les modifications C1.

## Échange 5 — Préparation des fichiers de remise

### Demande
Préparer les fichiers de documentation pour la remise de C1 tout en conservant le journal de debug précédent.

### Proposition de l'IA
Renommer l’ancien `JOURNAL-IA.md` en `Journal_IA_debug.md` sans en modifier le contenu, puis créer un nouveau `Journal_IA.md` réservé au travail C1.

### Décision personnelle
Séparer les deux journaux : garder l’historique de debug intact et écrire le suivi de C1 dans le nouveau fichier.

### Pourquoi
Les problèmes d’environnement et l’implémentation de C1 n’ont pas le même objet. Les séparer rend le dossier de remise plus lisible sans perdre l’historique déjà écrit.

### Vérification
L’ancien fichier a été renommé sans altération de son contenu. Le nouveau fichier est créé. Les fichiers Java modifiés pour C1 restent inchangés pendant cette étape. Aucun commit ni push n’est effectué.

## Bilan final et fichiers modifiés

Fichiers modifiés pour C1 :

- `src/main/java/hei/school/SubmissionController.java` : ajout de `penalizedGrade(double, double, LocalDate, LocalDateTime)`, utilisation dans `get()` et `grade()`, commentaire de notation mis à jour ;
- `src/main/java/hei/school/GradeReport.java` : ajout de `penalizedGrade(BigDecimal, BigDecimal, LocalDate, LocalDateTime)` et remplacement du calcul du relevé ;
- `src/main/java/hei/school/CsvExport.java` : ajout de `penalizedGrade(BigDecimal, BigDecimal, Object, Object)`, import de `RoundingMode` et alignement de l’arrondi CSV.

Fichiers de documentation préparés pour la remise :

- `Journal_IA_debug.md` : ancien `JOURNAL-IA.md` contenant l'historique des problèmes rencontrés lors du démarrage de l'application, conservé intégralement ;
- `Journal_IA.md` : présent fichier, consacré à C1.

Aucun autre fichier du projet n’a été modifié pour C1. Aucune classe, migration, dépendance ou configuration n’a été ajoutée. `raw_points` reste la seule donnée stockée. Aucun commit ni push n’a été effectué.

État final des vérifications : les notes affichées par l’API, le relevé et le CSV correspondent à la règle retenue ; la notification Mailpit pour la soumission 2 est vérifiable ; `mvn test` et la réponse du `PUT` restent des éléments signalés par l’utilisateur, non des tests exécutés ou des sorties produites par l’IA.

---

# C2 — Notification par SMS

## Échange 1 — Analyse de l’existant

### Demande
Démarrer le TD C2 sans réécrire le projet : vérifier le commit C1 sur le fork, analyser `PUT /submissions/{id}/grade`, localiser le numéro de téléphone et identifier les fichiers concernés.

### Proposition de l'IA
Vérification en lecture seule du dépôt et du code existant :

- `origin/main` pointe sur le commit `6581a79` (« C1 »), identique à `HEAD` en local ; `upstream/main` reste sur le commit initial.
- `SubmissionController.grade()` valide les points, récupère la soumission avec jointures, met à jour `raw_points`, `feedback` et `graded_at`, calcule la note pénalisée C1, envoie l’email puis retourne le JSON.
- La requête SQL de `grade()` sélectionne déjà `st.phone`, mais ne l’utilise pas.
- Le numéro est stocké dans `student.phone`, défini dans `schema.sql` et renseigné dans `data.sql`.
- Aucune dépendance SMS ni service de messagerie n’existe dans le projet.

### Décision personnelle
Partir du fonctionnement actuel, sans réorganisation générale et sans Clean Architecture complète. Ne modifier ni le calcul C1 ni le fonctionnement de l’email.

### Pourquoi
Le besoin C2 porte uniquement sur l’ajout d’une notification SMS simulée. Les fichiers de notation et de relevé fonctionnent déjà ; les modifier augmenterait le risque de régression.

### Vérification
Analyse en lecture seule de `SubmissionController.java`, `schema.sql`, `data.sql`, `pom.xml`, `application.properties` et `compose.yaml`. Aucune modification à ce stade.

## Échange 2 — Choix du mécanisme de notification

### Demande
Conserver l’email, simuler un SMS dans les logs, garder la note publiée même si le SMS échoue, et permettre un remplacement ultérieur du fournisseur sans modifier la logique de notation.

### Proposition de l'IA
Cinq options ont été présentées :

1. log direct dans `grade()` : très court, mais fournisseur codé en dur et future modification du contrôleur ;
2. interface + implémentation Spring : petite abstraction, remplacement simple, léger à mettre en place ;
3. événements Spring : découplage maximal, mais plus de fichiers et de concepts, excessif pour ce TD ;
4. bean lambda dans la configuration : évite une classe, mais rend la logique moins lisible ;
5. véritable fournisseur SMS : hors périmètre, car il demanderait dépendance, clés et configuration réseau.

### Décision personnelle
Retenir l’option interface + implémentation, avec un contrat générique `MessageSender` plutôt qu’un `SmsSender` spécifique.

### Pourquoi
Le nom générique permet d’envisager un futur passage à WhatsApp sans changer `SubmissionController.java`. L’interface garde le code de notation dépendant d’une abstraction, pas d’un fournisseur concret. Les événements Spring et un vrai fournisseur étaient trop lourds pour une simulation pédagogique.

### Vérification
Aucune dépendance externe n’a été ajoutée. Le diff final ne concerne que deux nouveaux fichiers et `SubmissionController.java`.

## Échange 3 — Implémentation retenue

### Demande
Appliquer uniquement les modifications autorisées : créer le contrat et l’implémentation de simulation, puis intégrer l’envoi SMS après l’email.

### Proposition de l'IA
- `MessageSender.java` : interface avec `void send(String phone, String message)`.
- `LoggingMessageSender.java` : implémentation Spring qui affiche le SMS simulé dans les logs ; une propriété `kilasy.sms.fail`, lue par défaut à `false`, permet de simuler une panne en levant une exception.
- `SubmissionController.java` : injection de `MessageSender`, lecture du numéro déjà présent dans la ligne SQL, formatage de la note avec une virgule décimale (`14,4/20`), envoi SMS dans un `try/catch` indépendant.

### Décision personnelle
Créer `MessageSender.java` et `LoggingMessageSender.java`, puis ajouter le bloc SMS après l’email existant. Si `phone` est absent ou vide, le SMS est ignoré. En cas d’exception, le message d’échec est journalisé et la réponse HTTP reste normale.

### Pourquoi
L’email et la note C1 restent inchangés. Isoler le SMS dans son propre `try/catch` garantit qu’une panne de messagerie n’empêche jamais la publication de la note. Le format français est appliqué uniquement au SMS, sans toucher à l’email.

### Vérification
Le diff montre trois fichiers : les deux nouveaux fichiers de messagerie et `SubmissionController.java`. Aucun fichier SQL, relevé, CSV, configuration ni journal de debug n’a été modifié pendant l’implémentation.

## Échange 4 — Tests et simulation de panne

### Demande
Documenter les tests réalisés par l’utilisateur, y compris la simulation de panne SMS, sans inventer de sorties.

### Proposition de l'IA
Distinguer les résultats signalés par l’utilisateur de ceux réellement observés par l’IA. La simulation de panne est provoquée au démarrage avec :

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--kilasy.sms.fail=true"
```

### Décision personnelle
Enregistrer les résultats de tests comme « signalés par l’utilisateur comme réussis ». Ne pas attribuer ces exécutions à l’IA.

### Pourquoi
L’IA n’a pas exécuté Maven ni les appels HTTP après l’implémentation C2. Consigner ces tests comme si elle les avait lancés serait inexact.

### Vérification
Résultats signalés par l’utilisateur comme réussis :

- compilation et tests Maven réussis ;
- envoi normal : email envoyé et SMS simulé visible dans les logs ;
- pour Bob, la note finale affichée dans le SMS est `14,4/20` ;
- simulation de panne avec `--kilasy.sms.fail=true` réussie : malgré l’exception SMS, la note reste publiée et consultable ;
- aucun fournisseur SMS réel n’a été utilisé.

Aucun test n’a été exécuté par l’IA après les modifications C2.

## Échange 5 — Préparation de la trace de remise

### Demande
Ajouter la trace C2 à la fin de `Journal_IA.md`, après une séparation Markdown, sans réécrire la partie C1.

### Proposition de l'IA
Conserver intégralement la partie C1, ajouter `---`, puis le titre `# C2 — Notification par SMS`, suivi des échanges, du bilan et des fichiers modifiés.

### Décision personnelle
N’ajouter que cette section au journal. Ne pas modifier les fichiers Java, SQL, la configuration ou `Journal_IA_debug.md`. Ne faire ni `add`, ni commit, ni push.

### Pourquoi
La partie C1 doit rester intacte pour la remise. La nouvelle partie documente seulement les choix C2 et leur contexte.

### Vérification
La partie C1 est conservée sans réécriture. Le journal de debug reste inchangé. Aucune commande Git d’indexation ou de publication n’est exécutée.

## Bilan final et fichiers modifiés

Fichiers modifiés pour C2 :

- `src/main/java/hei/school/SubmissionController.java` : injection de `MessageSender`, envoi SMS après l’email, gestion des numéros absents, formatage français de la note et `try/catch` isolé ;
- `src/main/java/hei/school/MessageSender.java` : définition du contrat `send(String phone, String message)` ;
- `src/main/java/hei/school/LoggingMessageSender.java` : simulation de l’envoi dans les logs et simulation de panne via `kilasy.sms.fail`.

Non modifiés : le calcul de pénalité C1, l’email, les fichiers SQL, le relevé, le CSV, la configuration et `Journal_IA_debug.md`.

Conclusion technique : un futur passage à WhatsApp nécessiterait de remplacer l’implémentation de `MessageSender` et éventuellement sa configuration, sans modifier le code de notation dans `SubmissionController.java`. Le contrôleur dépend de l’abstraction `MessageSender`, pas d’un fournisseur concret.

Durée réelle passée sur C2 : **2 heures**.
