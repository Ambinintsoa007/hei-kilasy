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
