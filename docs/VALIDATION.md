---
summary: "Codex : serveur 1143/1143, client 413/413 dans les deux modes ; activation isolée et limites fournisseur."
read_when:
  - "examiner les validations les régressions et les vérifications restant à autoriser"
---

# Tests et état des validations

<a id="activation-sms-swing-2026-11-21"></a>

## Activation invitée : suites complètes et preuves — 21 novembre 2026

Client `ec08c42`, serveur `165d2e0` relus. Les validations suivantes sont attribuées à **Codex**, avant cette actualisation ; elles ne sont pas déduites de la simple présence des tests.

### Validation du 21 novembre 2026 — Codex

| Périmètre réellement exécuté avant cette actualisation | Résultat | Portée |
| --- | --- | --- |
| Suite complète serveur | **1 143/1 143 réussis**, 99 suites recensées dans le rapport. | Dont **12 scénarios sur deux JVM**, Redis et PostgreSQL jetables ; pas un nouveau benchmark. |
| Suite complète client sur classpath | **413/413 réussis**. | Dispositifs isolés de transport et de Swing ; résultat distinct du mode modulaire. |
| Suite complète client en mode modulaire | **413/413 réussis**. | Même suite vérifiée en module ; ne pas additionner les deux modes pour inventer un total client. |
| Contrôle visuel rapporté | Formulaire vérifié en **clair et sombre**. | Contrôle du formulaire, pas recette complète jusqu’à un téléphone. |

Aucun échec, erreur ou test ignoré signalé. Les migrations ont été appliquées uniquement dans des PostgreSQL jetables ; processus et conteneurs de validation arrêtés. Aucun compte OVHcloud réel, appel réel OVHcloud/Free ou SMS facturé utilisé : les appels fournisseur sont simulés.

Le rapport a été établi sur des arbres adaptés avant commit : HEAD de référence `64dc70c` serveur et `15bc0cb` client. Les changements sont désormais présents dans les commits locaux **`165d2e0` serveur** et **`ec08c42` client**. Pendant cette mission documentaire, les empreintes des **23 fichiers serveur et 11 fichiers client inventoriés** correspondent au rapport, sans écart. Cela ne prouve pas l’état d’une branche distante et ne transpose pas les résultats à d’éventuelles modifications ultérieures.

Les tests HTTP serveur utilisent PostgreSQL réel jetable et un expéditeur simulé ; les tests Swing/transport utilisent leurs propres dispositifs isolés. **Aucune recette complète Swing → OVHcloud → téléphone n’est revendiquée.** Réception réelle, enrôlement initial TOTP des comptes concernés et déploiement restent à valider ou à construire. Les anciens résultats restent datés ; aucun test, build, serveur, migration, commit, push ou pull n’est lancé pendant cette actualisation.

### Scénarios et limites de lecture

`ActivationInvitationTest` vérifie l’extraction du fragment et les liens refusés ; `ActivationConnectionTest` couvre le transport vers le serveur configuré ; `ActivationControllerTest` et `ActivationWindowTest` portent sur formulaire, concurrence, refus, limites, nettoyage et fermeture. Ces dispositifs ne constituent pas un acheminement réel de SMS par OVHcloud.

Côté serveur, lire notamment `ActivationHttpPostgresqlIntegrationTest`, `ActivationIdentityChallengeServiceTest`, `ActivationIdentityChallengeServicePostgresqlIntegrationTest`, `ActivationChallengeRecoveryPostgresqlIntegrationTest`, `ActivationSmsConfirmationMigrationTest` et `OvhSmsActivationMessageSenderTest`. Les tests HTTP PostgreSQL utilisent un expéditeur simulé ; le test de l’adaptateur OVHcloud simule HTTP. Les douze scénarios à deux JVM ajoutent la reprise durable et le quota public partagé aux garanties de session déjà suivies.

Le profil serveur `application-test.yaml` utilise désormais PostgreSQL jetable Testcontainers. Les attentes des requêtes malformées ont été corrigées vers 400 ; ne pas traiter les anciennes attentes comme des anomalies actuelles. Les 291 tests ciblés du lot multi-instance restent un jalon antérieur, distinct de cette suite complète.

**Historique antérieur à cette reprise activation SMS/Swing : les dates, preuves et anciennes limites ci-dessous gardent leur contexte ; la reprise ci-dessus décrit l’état courant.**


<a id="reprise-2026-11-21"></a>

## Validations de session, structures et identité — 21 novembre 2026

Sources relues : client `45f02c6`, `9b5a895`, correction identité `53d110d` ; serveur structures `63046e8`, correction HIBP `72aa7f4`.

### Validations attribuées — 21 novembre 2026

Ces résultats proviennent de la mission transmise par Lise ; ils ne sont pas réexécutés pendant cette actualisation documentaire.

| État | Auteur et résultat | Portée et limites |
| --- | --- | --- |
| Avant les deux corrections | Lise : **924/924 serveur** sur `63046e8` et **381/381 client** sur `9b5a895`. | Suites complètes rapportées ; totaux non transposés aux commits suivants. |
| Avant corrections, tests ciblés serveur | Codex : **24 tests sur PostgreSQL jetable**. | Distincts d’une suite complète. |
| Avant corrections, tests ciblés client | Codex : **130 tests client**. | Portées distinctes : composants, fenêtres Swing et serveurs HTTP fictifs. Ces derniers ne constituent pas une recette Swing contre Spring Boot. |
| Après correction HIBP `72aa7f4` | Codex : **19/19** dans `PwnedPasswordsCheckerTest`, client HTTP simulé. | Aucun serveur, PostgreSQL ni migration lancé pour ces dernières vérifications. |
| Après correction identité `53d110d` | Codex : **7/7** dans `ApplicationHeaderIdentityTest`, sur classpath **et** en mode modulaire. | Même jeu vérifié dans les deux modes ; aucun nouveau total global calculé. |
| Confirmation ultérieure | Lise : **« tout est vert »**. | Aucun nouveau total communiqué ; ne pas inventer les nouveaux totaux des suites complètes. |

Les 900/900 serveur du 19 novembre et les 284/284 client du 20 novembre restent des résultats historiques avec leurs dates et versions. Aucun test, build, serveur, migration, commit ou push n’est lancé pendant cette mission documentaire.

### Tests présents et limites de preuve

`SessionScheduleTest`, `SessionMonitoringTest` et `SessionMonitoringConcurrencyTest` portent sur les échéances, pannes, refus et réponses concurrentes. `StructureContextTest`, `StructureSelectionControllerTest`, `StructureConnectionTest`, `StructureSelectorPanelTest` et `ConnectedSessionLifecycleTest` portent sur contexte, sélection, retrait d’accès, isolation et nettoyage. `ApplicationHeaderIdentityTest` vérifie le texte exact, l’absence de rendu HTML et le maintien après thème/remplacement d’identité.

Distinguer les tests de composants, les fenêtres Swing avec affichage et les échanges sur HTTP fictif. La recette réelle du 20 novembre reste datée : elle ne valide pas automatiquement les nouveaux parcours de structures, les pannes ou le changement entre deux comptes sur le vrai serveur. Le métier connecté et les fiches autorisées restent à raccorder et à valider.


<a id="validation-connexion-2026-11-20"></a>

## Connexion, sessions et preuves — 20 novembre 2026

**Historique du 20 novembre ; lire la reprise du 21 novembre ci-dessus pour l’état courant.**

| Preuve | Résultat et attribution | Portée |
| --- | --- | --- |
| Suite du client `03387cc` | **284/284 tests réussis dans Eclipse, rapportés par Lise**, puis push confirmé. | Aucune exécution de cette suite par l'assistant pendant cette mise à jour documentaire. |
| Recette de Lise | Connexion réelle avec TOTP, `HomePanel`, identité, déconnexion, formulaire et seconde connexion réussis. Démonstration également confirmée fonctionnelle. | Serveur local et PostgreSQL isolé, compte SUPER_ADMIN fictif. La reconnexion utilise le même compte ; elle ne prouve pas une recette manuelle entre deux utilisateurs distincts. |
| Contrôles de l'assistant | Copie du serveur `b2ca9e3` compilée, V1–V27 dans une nouvelle base dédiée ; HTTP réel : sans TOTP 401, avec TOTP 200, identité 200, échéance 200, logout 204, puis lecture protégée 401. | Préparation locale avant cette mission documentaire ; aucune suite serveur relancée. Ce parcours précède la simplification ultérieure des identifiants locaux. |

Les ajouts de `03387cc` couvrent `ConnectedApplicationContextTest`, `SessionCoordinatorTest`, `ConnectedHomeWindowTest` et `ConnectedSessionLifecycleTest`. Le test de succès de `LoginControllerTest` n'actionne plus la croix pour terminer le dialogue. Ils couvrent nettoyage idempotent, états, EDT, déconnexion confirmée ou non, demandes répétées, fermeture, retour au formulaire et deux comptes fictifs successifs avec des cookies distincts.

Les tests du parcours Swing utilisent des réponses HTTP fictives sur boucle locale ; ils ne démarrent pas le vrai serveur Spring Boot. Les classes graphiques portent le tag `desktop`. Elles font partie de la suite Eclipse avec affichage ; `tools/verify.py` les exclut de son exécution sans affichage et copie les ressources `icons` et `demo-data`.

Les **252/252** précédemment rapportés concernaient le socle `31fe887`, avant le raccordement. Les décomptes d'octobre ci-dessous gardent leur date et ne décrivent pas la suite actuelle.

**Restent à valider ou développer :** suivi Swing de l'expiration et des 401 pendant l'usage, annulation des futures tâches métier, isolation de leurs réponses tardives et recette entre comptes distincts sur le vrai serveur. Les cartes métier connectées sont désactivées. Ces preuves ne constituent pas une validation de déploiement.

## Vérification de la fiabilisation — 20 octobre 2026

Compilation de toutes les sources et du descripteur de module avec JDK 25.0.2, dépendances FlatLaf/JSVG locales, puis tests JUnit 6.0.1 sur fixtures fictives dans un dossier extérieur au projet de données. Les tests sont exécutés en mode classique et avec le module `gestionRemplacement`. Les préférences de test sont en mémoire ; aucune fenêtre principale ni service sur les CSV réels n'est lancé.

La suite comprend les 39 tests préexistants et les régressions `ReliabilityRegressionTest`, `CsvReliabilityTest`, `UiReliabilityTest`. Elle couvre notamment les six lecteurs/écrivains, les invariants et références, les restaurations mémoire, les échecs après une première installation, une vraie interruption de processus, la récupération idempotente, le refus d'écraser un fichier modifié extérieurement, la compensation incomplète, les liens des groupes d'absence et les erreurs/sélections Swing. Les tests comparent les octets des CSV fictifs avant/après échec et rechargent les états valides.

Le script [../tools/verify.py](../tools/verify.py) reproduit la compilation et les deux modes JUnit. Il exige un JDK 25, les JAR JUnit locaux et un dossier de sortie **extérieur au projet**, qu'il utilise aussi pour tous les temporaires. L'exécution avec un autre dossier temporaire non autorisé peut provoquer des refus d'accès propres au bac à sable.

Les contrôles Swing sont automatisés sur composants sans fenêtre affichée. Un parcours manuel dans Eclipse sur une base fictive reste recommandé pour la disposition et les dialogues modaux complets ; aucun résultat de recette visuelle complète n'est revendiqué. Les tests ne certifient pas la résistance à toutes les pannes matérielles ni l'utilisation concurrente des CSV.

Le bilan privé de livraison contient le nombre final de tests et les journaux d'exécution. Les résultats et constats plus anciens ci-dessous sont conservés pour leur chronologie.

État documentaire au **20 octobre 2026**. Présence de code, test déclaré et résultat exécuté sont trois informations différentes. Cette revue a vérifié des textes, chemins et sources, sans compiler ni lancer l'application ou les tests Java.

## Inventaire historique du 20 octobre 2026

| Classe | Déclarations @Test | Périmètre existant |
| --- | --- | --- |
| [InterventionTest](../test/gestionTest/InterventionTest.java) | 8 | Durées, minutes, nuits et interventions sur plusieurs jours. |
| [PlanningTest](../test/gestionTest/PlanningTest.java) | 8 | Heures dans une période, intersections et limites de mois ; cible WorkHoursService. |
| [BehaviorRegressionTest](../test/gestionTest/BehaviorRegressionTest.java) | 23 | CSV historiques, références, personnel/tri, heures, absences/reprise, erreurs de sauvegarde d'absence, demandes liées, récurrences et assemblage Swing. |
| [InMemoryPreferencesFactory](../test/gestionTest/support/InMemoryPreferencesFactory.java) | Infrastructure | Préférences en mémoire pour isoler les essais des préférences personnelles. |

Le total de **39 déclarations** n'est pas un résultat de 39 réussites pour la version actuelle. Les scénarios de rollback d'absence existants ne couvrent pas automatiquement tous les nouveaux chemins de rollback d'affectation.

## Chronologie des résultats rapportés

| Jalon | Résultat rapporté | Limite |
| --- | --- | --- |
| 2 octobre 2026 | Kit autonome de 48 scénarios : 45 réussites, 3 échecs. | Outil séparé, extérieur au dépôt actuel ; état ancien. |
| Refactorisation du 19 octobre | Compilation Java 25 et 39 tests JUnit réussis, avec comparaison de certains CSV avant/après et observations de Personnel en clair/sombre. | Jalon historique décrit dans la carte du projet ; pas une validation après les derniers changements. |
| Avant la dernière vague du 19 octobre | 23/23 tests de régression passés. | Résultat de régression rapporté avant les changements récents ; ne pas en déduire une exécution récente de toutes les classes. |
| Après cette vague | Aucune campagne établie. | Affectations, périodes, sauvegarde, interface et autres parcours restent à vérifier. |

Les anciens défauts ne deviennent ni résolus ni encore présents uniquement parce qu'un ancien rapport les mentionne : relire le code et, à la phase de tests, vérifier le comportement ciblé.

## Configuration Eclipse

- Sources de production sous `src/gestion`, tests sous `test/gestionTest`, support sous `gestionTest.support`.
- Point d'entrée `gestion.Main`, module encore nommé `gestionRemplacement`.
- `.classpath` déclare le conteneur JUnit 6 et un dossier de sortie distinct `bin-test`.
- Le jalon utilisait Java 25. Sélectionner la classe ou le dossier `test`, puis Run As → JUnit Test.
- Pour la configuration de régression utilisant les préférences en mémoire, conserver l'option VM suivante, constatée dans la configuration locale de lancement :

```text
--add-exports=gestionRemplacement/gestionTest.support=java.prefs
```

Cette option dépend du nom du module, pas du nom du projet Eclipse ; la revoir si le module change. La configuration de lancement locale n'est pas un fichier présent dans le dépôt examiné.

## Vérifications à regrouper après le bloc de modifications

- Périodes nulles, inversées, durée nulle, journée unique, fin à minuit, nuits, limites de semaine/mois et créneaux jointifs.
- Attribution simple/collective, cible absente, doublon, conflit interne/externe, remplacement par le même employé, aucune cible et désattribution.
- Erreur d'écriture/déplacement : propagation, restauration mémoire exacte, conservation du fichier cible et nettoyage du temporaire.
- Autres mutations du planning et cohérence définition/occurrences/demandes/absences entre fichiers.
- Rechargement, identité des personnes, changement de nom et données historiques invalides.
- Parcours des vues, annulations, dialogues, messages de succès/refus/erreur et compteurs rafraîchis.
- Après leur réalisation : trajets, comptes, SMS/courriels et accès aux archives.

Utiliser des données synthétiques, un dossier isolé et les préférences en mémoire. Le démarrage initialise la synchronisation des demandes ; il ne constitue pas une lecture sans effet de tous les fichiers. Ne pas utiliser les données réelles pour provoquer un échec.

Ordre de travail retenu : inventaire, factorisation, fonctionnalités, design, campagne regroupée, puis bilan avant commit. Les vérifications de documentation et de liens ne remplacent pas cette campagne.
