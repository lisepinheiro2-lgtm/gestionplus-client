---
summary: "Client ff68060 : MFA Swing raccordé, connexion transférée, codes uniques et limites de reprise."
read_when:
  - "retrouver les responsabilités des classes et les formats CSV du client"
  - "préparer une évolution vers le serveur sans confondre historique et cible"
---

# Architecture du projet gestion

<a id="mfa-swing-2026-11-22"></a>

## Architecture de l’enrôlement MFA Swing raccordé — 22 novembre 2026

**Client relu `ff68060`, serveur `4a71440` ; arbres propres au début de la mission.** Les douze empreintes de la preuve correspondent au code présent. Le formulaire n’est plus seulement à développer : le raccordement et la recette isolée sont réalisés, avec les limites de reprise décrites ci-dessous.

| Responsabilité | Classes et comportement vérifié |
| --- | --- |
| Réponse d’activation | `ServerConnection.completeActivation` lit `ActivationResult` et son éventuelle `enrollmentProof`. Sans preuve, activation terminée et retour à la connexion sans connexion automatique. Avec preuve, `ActivationController` ouvre automatiquement le dialogue MFA. |
| Propriété du transport | `ActivationController` transfère la responsabilité de sa `ServerConnection` à `MfaEnrollmentController`. Le contrôleur d’activation ne ferme pas la connexion transférée ; le contrôleur MFA en assure la fin, sans perte des cookies entre les étapes. |
| Ouverture et CSRF | `openMfaEnrollment` ouvre le contexte restreint avec la preuve une seule fois. Chaque POST via `postJson` relit le CSRF courant ; les cookies conservés suivent la rotation de session. `beginMfaEnrollment` charge la configuration, sans renvoyer la preuve à chaque nouvelle lecture. |
| Réponses et échéance | `MfaEnrollment` porte enrollmentID, setupKey et expiresAt ; `MfaEnrollmentObservation` borne le temps restant à dix minutes, à partir de l’échéance serveur et du temps de transport. Le compte à rebours Swing utilise une horloge monotone et ne repousse pas une échéance déjà observée ; le serveur reste l’autorité. |
| Présentation | `MfaEnrollmentWindow` présente une clé masquée par défaut, affichable explicitement pour saisie manuelle dans l’authentificateur, puis un TOTP à six chiffres. `MfaRecoveryCodes` valide dix codes distincts ; présentation unique, sans sauvegarde ni copie automatique. Confirmation de conservation avant le retour normal. |
| Coordination | `MfaEnrollmentController` utilise `SwingWorker` : réseau hors EDT, mises à jour Swing sur EDT, une demande en cours ; annulation/nettoyage et garde contre les retours après fermeture. Une confirmation déjà en cours à l’échéance est attendue sans nouvel envoi. |
| Refus et attente | Gestion des 400/403, 401/expiration, 429/`Retry-After`, indisponibilités et erreurs réseau. « Réessayer » recherche le contexte déjà ouvert par begin ; il ne réémet pas une preuve consommée et n’installe pas une reprise autonome. |
| Fin et incertitude | Après succès, présentation des codes et fermeture du transport ; retour à la connexion avec un **nouveau TOTP**, celui de confirmation étant déjà consommé. Aucune connexion automatique ni accès métier. Une confirmation sans réponse fiable est terminale et incertaine, sans répétition automatique du TOTP. |

Les réponses sensibles ont des représentations `toString` protégées ; champs de clé/code/récupération et références de parcours sont nettoyés à la fermeture. Cela n’implique aucune sauvegarde externe automatique. Les thèmes et ressources existants sont réutilisés.

### Limites ouvertes : étape MFA non entièrement close

- **Contexte perdu ou expiré :** le serveur ne propose pas encore de reprise autonome avec une nouvelle preuve vérifiée. L’interface oriente vers l’administrateur ; **aucun mécanisme administratif de reprise supplémentaire n’a été développé dans ce lot**. Cette orientation ne constitue donc pas une procédure de reprise déjà disponible.
- **Réponse de confirmation perdue :** le facteur peut avoir été confirmé, mais les dix codes de récupération ne peuvent pas être relus par le client. Le résultat est présenté comme incertain, **sans répétition automatique du TOTP**. L’interface propose une connexion normale avec nouveau TOTP et une assistance sécurisée si nécessaire, sans garantir la récupération des codes perdus.
- **Validation isolée :** SMS simulés et HIBP remplacé pour la recette, aucun appel Free/OVHcloud, aucun SMS réel. La recette Swing → Spring Boot utilise de vrais PostgreSQL/Redis jetables ; elle ne valide ni la réception réelle de SMS ni un déploiement.
- **Attribution des résultats :** les **1 191/1 191 serveur** appartiennent à la validation précédente ; la suite serveur complète n’a pas été relancée pour ce raccordement Swing. Les résultats client antérieurs restent datés et les validations de Codex restent distinctes des résultats rapportés par Lise.

**Le raccordement est réalisé ; l’étape MFA n’est pas annoncée entièrement close tant que ces limites de reprise ne sont pas traitées ou explicitement acceptées.**

Voir [interface](INTERFACE-RAVEN.md#mfa-swing-2026-11-22), [preuves attribuées](VALIDATION.md#mfa-swing-2026-11-22) et [suite](ROADMAP.md#mfa-swing-2026-11-22).

**Jalon antérieur au raccordement MFA Swing du 22 novembre : les dates, résultats et anciennes limites ci-dessous restent historiques ; lire la reprise ci-dessus pour l’état courant.**


<a id="activation-sms-swing-2026-11-21"></a>

## Architecture du parcours d’activation — 21 novembre 2026

**Client relu `ec08c42`, serveur `165d2e0`.** Le parcours d’activation est raccordé ; les cartes métier connectées restent désactivées et la démo CSV indépendante.

| Responsabilité | Classes et frontières |
| --- | --- |
| Accès au formulaire | `LoginWindow` expose « Activer un compte invité ». `Main` crée `ActivationWindow` et son `ActivationController` avec une `ServerConnection` propre au parcours, fermée à sa fin. |
| Présentation | `ActivationWindow`, dialogue modal, réutilise Raven, `UIUtils`, ressources de marque et thèmes. Lien complet reçu par e-mail masqué, identifiant, mot de passe et confirmation, code SMS à six chiffres ; demande de SMS uniquement par action explicite. |
| Analyse du lien | `ActivationInvitation.fromLink` extrait `invitation=…&token=…` du fragment, avec UUID et jeton Base64URL de 43 caractères. L’UUID seul ne suffit pas. Lien HTTPS (HTTP limité à la boucle locale), sans query ni user-info, longueur bornée. `toString` ne révèle pas le jeton. |
| Destination réseau | Le lien collé **ne devient jamais une URL de requête**. `ServerConnection` appelle l’URL serveur configurée, obtient le CSRF et effectue les POST `/api/invitations/{invitationID}/sms-challenge` ou `/activation`. |
| Coordination | `ActivationController` utilise `SwingWorker` : réseau hors EDT, mises à jour sur EDT, une demande en cours au maximum. Fermeture : annulation, rejet des retours tardifs, fermeture du transport ; nettoyage des champs sensibles et des tableaux temporaires de mots de passe. |
| Résultats | Refus 400/403, conflit métier 409, limite 429 et indisponibilité 503 ont des retours dédiés. `HttpApiException` transporte `Retry-After` ; le formulaire bloque les demandes pendant ce délai, avec attente de 15 minutes si le 429 n’en fournit pas de valide. Aucune répétition automatique. |
| Fin | Le succès efface les champs sensibles et confirme l’activation ; le bouton « Retour à la connexion » revient au formulaire. **Aucune connexion automatique**, aucun contexte connecté créé par l’activation. |

Le serveur décide de l’acceptation du jeton/code, de la politique du mot de passe, des droits et de la consommation transactionnelle. Un succès de demande SMS signifie acceptation par l’expéditeur, pas réception sur le téléphone. Le premier enrôlement TOTP des comptes concernés reste distinct ; la connexion normale conserve son POST unique avec TOTP.

Voir [interface](INTERFACE-RAVEN.md#activation-sms-swing-2026-11-21), [validations et portée](VALIDATION.md#activation-sms-swing-2026-11-21) et [suite](ROADMAP.md#activation-sms-swing-2026-11-21).

**Historique antérieur à cette reprise activation SMS/Swing : les dates, preuves et anciennes limites ci-dessous gardent leur contexte ; la reprise ci-dessus décrit l’état courant.**


<a id="reprise-2026-11-21"></a>

## Sessions actives et structures — 21 novembre 2026

**État relu : `53d110d`**, après le suivi des sessions `45f02c6` et le sélecteur `9b5a895`. Les reprises du 20 novembre ci-dessous sont historiques.

| Responsabilité | Classes et comportement présents |
| --- | --- |
| Échéances | `SessionCoordinator`, `SessionSchedule`, `SessionObservation` : lecture active de `/api/auth/session`, suivi de `warnAt`/`expiresAt`, horloge monotone et temps d’aller-retour pris en compte. `SessionBanner` présente état et avertissement. |
| Panne et refus | CHECKING/UNAVAILABLE suspendent l’usage connecté. `ServerConnection.send` centralise les 401 et notifie le coordinateur ; la fin de session nettoie le contexte et revient au formulaire. |
| Déconnexion | Compte déclenche le logout hors EDT ; nettoyage local même sans confirmation serveur, annulation des vérifications, fermeture du transport et suppression des cookies. Nouveau formulaire avec une nouvelle connexion ; la croix demande de quitter. |
| Structures | `StructureSelectionController`, `StructureSelectionState`, `StructureSelectorPanel` et `ServerConnection.fetchAccessibleStructures`/`fetchAccessibleStructure` consomment les deux GET authentifiés. |
| Zéro, une ou plusieurs | Zéro accès explicite ; sélection automatique si une seule structure ; choix explicite si plusieurs au premier chargement. Une actualisation dans le même contexte peut conserver un choix encore autorisé. |
| Sélection et refus | Liste et sélection vivent uniquement dans `ConnectedApplicationContext`, sans préférence persistée. Un choix vérifie l’accès par le détail serveur ; après refus 404, rechargement de la liste sans conserver la sélection refusée. |
| Concurrence et comptes | Annulation des requêtes en cours ; génération de demande et état du contexte rejettent les réponses obsolètes. Suspension, fermeture ou changement de compte nettoient liste/sélection. Contexte et transport propres à chaque connexion. |

Le serveur contrôle les droits et l’isolation à chaque requête ; le choix Swing ne confère aucune permission. Les **cartes métier connectées restent désactivées** : le sélecteur ne transforme pas les écrans CSV en écrans connectés. La **démo CSV reste indépendante**, sans repli implicite depuis CONNECTED.

`ApplicationHeader.setIdentity` impose le **texte brut** avec `html.disable` : les balises d’un identifiant ne sont plus interprétées comme du HTML (`53d110d`, `ApplicationHeaderIdentityTest`). Voir [validations](VALIDATION.md#reprise-2026-11-21) et [suite](ROADMAP.md#reprise-2026-11-21).


<a id="sessions-connectees-2026-11-20"></a>

## Deux modes et cycle connecté — 20 novembre 2026

**Historique du 20 novembre ; lire la reprise du 21 novembre ci-dessus pour l’état courant.**

État du client `03387cc`, après le socle `31fe887`, le raccordement `82ad003` et les corrections `6c3fd47`. Cette section décrit le code actuel ; les relevés d'octobre ci-dessous restent historiques.

| Responsabilité | Classes et comportement |
| --- | --- |
| Choix et assemblage | [`Main`](../src/gestion/Main.java) installe FlatLaf sur l'EDT, ouvre le sélecteur modal et choisit explicitement le mode. Aucun drapeau `gestionplus.demo` ne remplace le sélecteur. |
| Démonstration | `DemoDataInitializer`, puis `ApplicationContext`, utilisent exclusivement `%LOCALAPPDATA%/GestionPlus/Demo`. Cet assemblage CSV reste propre au mode DEMO. |
| Authentification | `LoginWindow` présente identifiant, mot de passe et TOTP dans le même formulaire. `LoginController` exécute le réseau hors EDT, attend l'identité confirmée par `/api/auth/me`, ferme le dialogue après succès et transfère la connexion. |
| Session connectée | [`ConnectedApplicationContext`](../src/gestion/app/ConnectedApplicationContext.java) possède l'identité, le transport et un UUID de contexte, avec états ACTIVE, CLOSING et CLOSED. `close()` libère localement la connexion, sans POST de logout. |
| Accueil | `ConnectedHomeWindow` assemble `ApplicationHeader` et le `HomePanel` existant, sans services CSV ni écrans métier connectés. Les cartes sans action sont désactivées. |
| Déconnexion | [`SessionCoordinator`](../src/gestion/app/SessionCoordinator.java) passe en CLOSING sur l'EDT, exécute le logout hors EDT, ferme le contexte et transmet `LogoutResult`. Les demandes répétées ne multiplient pas les appels ; la croix peut demander de quitter pendant le logout. |
| Destination | Après déconnexion, l'ancienne fenêtre est disposée et le formulaire revient directement avec un nouveau transport. Après la croix, l'application se termine. L'absence de confirmation serveur est signalée. |

[`ServerConnection`](../src/gestion/net/ServerConnection.java) conserve ses propres cookies par instance, obtient un CSRF courant avant les POST et ferme les ressources HTTP localement. Le serveur décide de la validité de session et des droits. HTTP est limité aux adresses de boucle locale ; une adresse distante exige HTTPS.

Jackson Core/Databind/Java Time sont en **2.23.0** ; Annotations est en **2.23**. Les JAR sont sous `lib/`, déclarés dans `.classpath` et le module. `JavaTimeModule` prend en charge les échéances ISO sous forme d'`Instant` ; l'ouverture JPMS de `gestion.net` reste ciblée vers Databind.

**Suite :** le transport sait lire `/api/auth/session`, mais aucun moniteur Swing de l'expiration n'est raccordé. Les API métier, l'écran de compte et la recette réelle entre deux comptes distincts restent à compléter. Voir les [validations](VALIDATION.md#validation-connexion-2026-11-20).

État relu le **20 octobre 2026**. Cette page décrit le code présent, pas les fonctionnalités futures de la [feuille de route](ROADMAP.md). Les résultats d'exécution sont séparés dans [VALIDATION.md](VALIDATION.md).

## Noms et arborescence

Le dépôt GitHub se nomme désormais `gestionplus-client`. Le projet Eclipse et le package racine se nomment `gestion` ; le module Java et le nœud de préférences conservent le nom historique `gestionRemplacement`.

```text
src/
  module-info.java             module gestionRemplacement
  gestion/
    Main.java                  point d'entrée gestion.Main
    app/                       assemblage des dépendances
    model/                     données métier, résumés et records de périodes
    repository/                lecture et écriture des six CSV
    service/                   règles, collections et calculs
    controller/                coordination de Personnel
    ui/                        navigation, thème, clients et remplacements
      personnel/               tableau, formulaire, statut et périodes
      planning/                calendrier et dialogues
      intervention/            liste et dialogues de création
      raven/                   composants visuels partagés
    util/                      conversions et règles de périodes
  icons/                       icône d'application et SVG de thème
test/gestionTest/              trois classes JUnit
test/gestionTest/support/      préférences en mémoire
```

[Main](../src/gestion/Main.java) ouvre l'application sur le thread Swing. [ApplicationContext](../src/gestion/app/ApplicationContext.java) reçoit un dossier de données, construit les services partagés et les transmet aux vues. L'initialisation des absences synchronise les demandes de remplacement : démarrer l'application n'est donc pas une opération nécessairement sans écriture.

## Responsabilités

| Couche | État actuel |
| --- | --- |
| Modèles | Employés, clients, absences, interventions, séries, remplacements et valeurs calculées ; pas de Swing ni de persistance. |
| Dépôts | Sérialisation CSV et reconstruction des liens. `CsvReferences` recherche historiquement les personnes par prénom/nom ; le dépôt des remplacements reconstruit encore ses propres `Employee`. |
| Services | Collections en mémoire, règles et opérations avec sauvegarde déléguée. `WorkHoursService` lit les interventions réelles et accepte une horloge pour les calculs d'alertes. |
| Contrôleur Personnel | `PersonnelController` valide les saisies et coordonne personnel, absences et heures, sans composant Swing. |
| Vues | Composants, événements, dialogues et présentation des erreurs. Les autres écrans conservent une partie de leur orchestration locale. |
| Utilitaires | `FormatUtils` convertit/affiche dates et heures ; `PeriodUtils` centralise validation et chevauchement des périodes. |

Il n'existe pas encore de contrôleur pour chaque page. `TravelTimeService` est une classe vide, pas un calculateur de trajet intégré. Aucun composant de compte, SMS, courriel ou Administration/Archives opérationnel n'est établi dans cet état.

## Périodes et affectations

- [DateRange](../src/gestion/model/DateRange.java) représente des dates inclusives et accepte une seule journée ; une fin antérieure au début ou une borne nulle est refusée.
- [DateTimeRange](../src/gestion/model/DateTimeRange.java) exige une durée strictement positive. [PeriodUtils](../src/gestion/util/PeriodUtils.java) utilise des comparaisons strictes pour les créneaux : deux créneaux jointifs ne se chevauchent pas.
- Les records de `model` appellent `PeriodUtils`, qui importe ces records ; cette dépendance dans les deux sens reste à discuter lors de la revue d'architecture.
- [PlanningService](../src/gestion/service/PlanningService.java) centralise la comparaison de deux interventions, les surcharges client/employé, un filtre de période et trois filtres d'affectation.
- L'attribution individuelle délègue à l'attribution collective. Celle-ci contrôle les cibles, doublons et conflits avant de passer au helper privé `applyAssignments`.
- Le helper conserve les anciens employés et restaure les affectations par index sur `UncheckedIOException`. Il sert aussi à la désattribution et au remplacement ; les autres mutations ne bénéficient pas toutes de cette restauration.
- Le remplacement retourne `true` si les employés sont la même référence ou si aucune intervention n'est concernée, `false` pour un conflit du remplaçant. Les dates sont construites après le retour « même employé » ; décider s'il faut les valider avant ce retour.
- Les comparaisons de personnes restent hétérogènes : identité dans les filtres, noms dans un contrôle de conflit. `Employee` ne redéfinit pas `equals`/`hashCode` dans cet état.

## Données et cohérence

| Fichier | Dépôt | Usage |
| --- | --- | --- |
| `employees.csv` | `EmployeeCsvRepository` | Employés et anciens champs compatibles. |
| `clients.csv` | `ClientCsvRepository` | Clients et contrats. |
| `employee_absences.csv` | `AbsenceCsvRepository` | Périodes d'absence à dates inclusives et identifiant. |
| `interventions.csv` | `InterventionCsvRepository` | Occurrences réelles, affectations et `recurringId` éventuel. |
| `recurringInterventions.csv` | `RecurringInterventionCsvRepository` | Définitions des séries. |
| `replacements.csv` | `ReplacementCsvRepository` | Demandes manuelles ou liées à une absence par `absenceId`. |

Planning et liste Interventions utilisent les mêmes occurrences ; les définitions récurrentes ne sont pas un second volume d'heures à additionner. Une attribution de série peut modifier occurrences et définition dans deux fichiers distincts.

Une absence synchronise une demande de remplacement sans désattribuer automatiquement le planning. Supprimer une demande ne supprime pas son absence et n'annule pas un transfert. Les demandes automatiques convertissent le dernier jour inclus en lendemain à minuit ; les opérations globales du planning sélectionnent encore par dates inclusives. Cette différence de granularité reste à traiter.

[InterventionCsvRepository](../src/gestion/repository/InterventionCsvRepository.java) écrit dans un temporaire puis utilise `Files.move` avec `REPLACE_EXISTING` et `ATOMIC_MOVE`. Les `IOException` remontent en `UncheckedIOException` ; `finally` tente le nettoyage. Aucun repli non atomique n'est prévu. Cette stratégie et le rollback ne constituent pas une transaction entre les six fichiers.

`ReplacementCsvRepository.save` écrit encore directement et intercepte ses erreurs sans les propager. `AbsenceService` restaure la mémoire si la sauvegarde de l'absence échoue, mais l'absence puis sa demande sont sauvegardées séparément. La validation de dates inversées existe au contrôleur Personnel et au chargement CSV ; `EmployeeAbsence`/`AbsenceService.addAbsence` ne centralisent pas encore toutes les préconditions.

## Correspondance des anciens noms

| Ancien type | Organisation actuelle |
| --- | --- |
| `PersonnelUI` | `gestion.ui.personnel.PersonnelPanel`, contrôleur, modèle de tableau, sélecteurs et dialogue de statut. |
| `PersonnelList` | `PersonnelService` et `EmployeeCsvRepository`. |
| `ClientList` | `ClientService` et `ClientCsvRepository`. |
| `Planning` | `PlanningService`, `WorkHoursService`, `InterventionCsvRepository`. |
| `EmployeeAbsenceList` | `AbsenceService`, `AbsenceCsvRepository`. |
| `ReplacementList` | `ReplacementService`, `ReplacementCsvRepository`. |
| `RecurringInterventionList` | `RecurringInterventionService`, `RecurringInterventionCsvRepository`. |
| Ancien package `gestionRemplacement` | Package racine `gestion`, avec sous-packages par responsabilité. |
| Anciens composants Raven | `gestion.ui.raven`, SVG sous `src/icons/theme`. |
| `EmployeeSettings`, `ArchivedInterventionUI` | Anciennes classes vides retirées ; Archives est désactivé sans écran actif. |

`getMonthyHours()` est devenu `WorkHoursService.getMonthlyHours()` ; les anciens accesseurs de compteurs deviennent `getAssignedHours()` et `getRemainingHours()`. Les anciens champs d'`Employee` restent présents pour le CSV ; Personnel affiche les calculs d'`EmployeeSummary`.

## Configuration du jalon d’octobre et suite

Le module déclare `java.desktop`, `java.prefs`, FlatLaf, FlatLaf Extras et JSVG. `.classpath` contient les bibliothèques locales et le conteneur JUnit 6. Le jalon historique utilisait Java 25 ; aucune compilation actuelle n'a été exécutée pour cette revue.

Les préférences de thème et de fenêtre restent sous `/gestionRemplacement`. Les préférences par compte sont futures. Les formats CSV historiques lus restent pris en charge ; cela n'établit pas que toutes les données ou références sont protégées contre les erreurs.

Pour poursuivre : lire [Personnel](PERSONNEL.md), la [feuille de route](ROADMAP.md) et les [validations](VALIDATION.md). Stabiliser les responsabilités et contrats d'erreur avant de brancher trajets, comptes ou nouveaux écrans.
