---
summary: "24 novembre : cycle serveur lié aux sessions dans 908c24d ; HTTP de production et Swing ouverts, validations client antérieures."
read_when:
  - "retrouver les fonctionnalités prévues Personnel Clients Planning et Archives"
  - "préparer l’intégration des comptes et permissions avec le serveur"
  - "retrouver les décisions DEC01–DEC06 du 29 octobre 2026"
---

# Feuille de route du projet gestion

<a id="succession-cycle-2026-11-24"></a>

## Succession côté serveur et raccordement client restant — 24 novembre 2026

Le serveur publié **`908c24d`**, après `77ec48e`, possède désormais le cycle métier jusqu’à COMPLETED. Cela dépasse le jalon du 23 novembre où l’ancien `acceptSuccession()` ignorait l’offre ; ce jalon reste historique. **Aucun nouveau parcours Swing ni résultat client** n’est livré par cette évolution serveur. Le client local est sur `48d8f94` (documentation ; code MFA `ff68060`), arbre propre avant cette mission.

Le futur parcours devra présenter séparément :

1. Le récapitulatif, puis mot de passe/TOTP du sortant et **preuve de création unique de cinq minutes**, liée à compte/version/action, empreinte de sa session HTTP et récapitulatif. La preuve est consommée dans la transaction qui crée l’offre.
2. Une **offre de 24 heures**, annulable par le sortant tant qu’elle est PENDING ou ACCEPTED et non expirée. Création et acceptation ne transfèrent aucun droit.
3. L’acceptation par le successeur, puis **deux confirmations indépendantes de cinq minutes**, chacune issue du mot de passe/TOTP de son participant, liée à l’offre, au compte/version, à l’empreinte de sa session et au récapitulatif. Renouveler l’une ne renouvelle pas l’autre ou l’offre.
4. La finalisation par **le successeur depuis la session qui a confirmé**, avec contrôle conjoint des deux sessions. Une autre session du même compte ne convient pas.

Le contrôle Redis conjoint fixe **`authorizedAt`** : déconnexion avant, refus ; après, une opération déjà autorisée peut se terminer. Transfert PostgreSQL, COMPLETED et suppression des confirmations sont atomiques ; les deux comptes voient leurs sessions invalidées, avec révocation Redis après commit. **Une panne Redis après commit peut laisser le transfert effectué et une réponse en erreur sans révocation immédiate garantie** : ne pas promettre un rollback ou un résultat réseau certain. Le digest du récapitulatif dépend encore du `toString()` Java, pas d’un format canonique versionné.

V34 invalide les anciennes preuves et ajoute leur liaison à la session ; V35 stocke les confirmations par offre/participant ; V1 à V33 inchangées. **Basculement V34 à coordonner avec les anciennes instances.** Les tables métier conservent des empreintes de session, sans modifier le stockage interne Spring Session ni les clés d’activité Redis existantes. Verrous structures triées → comptes triés → organisation → offre ; relecture SQL de l’organisation sous verrou, ordre à préserver.

Codex : **1 302/1 302 serveur**, 19 empreintes conformes, copie isolée avec PostgreSQL/Redis jetables. Concurrence de finalisation sur deux JVM, expiration/session différente, annulation, panne Redis et rollback couverts. Le point d’entrée utilisé sur deux JVM est dans **le dispositif d’essai**, pas dans l’API HTTP de production. Aucun e-mail/SMS réel, réception réelle ou déploiement validé ; aucun test relancé pendant l’actualisation documentaire. Les **443/443 client par mode** restent ceux du 22 novembre.

**À réaliser :** raccordements HTTP de production et Swing ; audit spécifique des nouvelles opérations et refus ; limitation distribuée dédiée ; notifications du cycle distinctes des avis de transferts ADMIN déjà présents ; reprise de révocation après commit et stabilisation du digest. **Limites MFA conservées :** aucune reprise autonome par nouvelle preuve après contexte perdu/expiré, aucun mécanisme administratif supplémentaire, codes non récupérables après réponse perdue ; étape non entièrement close avant traitement ou acceptation explicite.


Voir [le guide serveur](https://github.com/lisepinheiro2-lgtm/gestionplus-server/blob/main/docs/DEVELOPPEMENT.md#succession-cycle-2026-11-24).

**Historique conservé : la reprise du 24 novembre 2026 ci-dessus actualise le cycle ; les jalons du 23 novembre et antérieurs restent datés.**


<a id="notifications-confirmations-succession-2026-11-23"></a>

## Décisions serveur du 23 novembre 2026 — conséquences pour le client

Le serveur publié **`aa9eff7`** ajoute les notifications de comptes/MFA (V31), les preuves renforcées persistées (V32) et le stockage des offres de succession (V33). Ces développements serveur **ne constituent pas un nouveau parcours client livré ou validé**. Le client local reste sur `9c9e1b9` (documentation ; code MFA `ff68060`), arbre propre avant cette mission.

Les notifications sont enregistrées dans la transaction métier et livrées ultérieurement à leur destinataire : désactivation après dernier accès, deux participants de chaque transfert ADMIN, y compris pendant une succession, et activation du facteur MFA sans secret/code. **Trois tentatives SMTP puis abandon audité** restent la règle : ne pas promettre une réception, une reprise manuelle ou l’absence de doublons.

La confirmation renforcée est une preuve de **cinq minutes maximum**, liée au compte, à sa version de sécurité, à l’action et au récapitulatif, pas à une session HTTP individuelle. Empreinte seule en PostgreSQL, une preuve courante par compte/action, OFFER et ACCEPTANCE indépendantes, consommation atomique dans la transaction métier et conservation en cas de rollback. Le vérificateur est maintenant un bean Spring (`468edda`) ; limitation dédiée des tentatives et audit spécifique restent à réaliser.

L’offre de succession est distincte : **24 heures** à la création par le service, droits et sessions conservés. Son stockage V33 sérialise les créations par verrou PostgreSQL partagé d’organisation ; une seule PENDING ou ACCEPTED non expirée. L’ancien **`acceptSuccession()` ne consulte pas l’offre et ne la marque pas COMPLETED**. Les parcours métier d’annulation/finalisation n’ont pas été réécrits par le dernier lot : cycle complet et raccordement HTTP/Swing restent ouverts. Conserver structures triées → comptes triés → organisation → offre lors du raccordement, pour éviter l’interblocage ; la recherche des anciennes offres ne les verrouille pas à la création.

Codex a validé côté serveur, en copies isolées, **1 215/1 215**, puis **1 255/1 255**, puis **58/58 ciblés et 1 284/1 284 complets**. La dernière suite comprend 14 scénarios existants de sessions à deux JVM et un nouveau scénario de concurrence du stockage entre deux JVM ; aucun nouveau résultat client, e-mail/SMS réel, parcours HTTP/Swing complet de succession ou déploiement validé. Les **443/443 client par mode** restent ceux du 22 novembre.

**MFA toujours non entièrement clos :** perte/expiration du contexte sans reprise autonome par nouvelle preuve vérifiée ; orientation vers l’administrateur sans mécanisme administratif supplémentaire. Codes de récupération non récupérables après perte de leur réponse. Ces limites restent à traiter ou à accepter explicitement.


Voir [les contrats serveur](https://github.com/lisepinheiro2-lgtm/gestionplus-server/blob/main/docs/DEVELOPPEMENT.md#notifications-confirmations-succession-2026-11-23).

**Historique conservé : les jalons antérieurs ci-dessous restent datés ; cette reprise décrit les évolutions du 23 novembre 2026.**


<a id="mfa-swing-2026-11-22"></a>

## MFA Swing raccordé, reprise encore ouverte — 22 novembre 2026

**Présent dans `ff68060` :** lecture de la réponse d’activation et de sa preuve, dialogue MFA automatique si nécessaire, contexte restreint/cookies/CSRF, clé masquée affichable explicitement, confirmation TOTP, dix codes de récupération et retour à la connexion après confirmation de conservation. Aucun accès métier ni connexion automatique.

Transfert de responsabilité de connexion, réseau hors EDT, nettoyage, annulation et contrôle des réponses tardives sont présents. La recette intégrée valide quatre parcours avec le vrai serveur Spring Boot et des PostgreSQL/Redis jetables. Codex : **443/443 client sur classpath et 443/443 en mode modulaire** ; les **1 191/1 191 serveur** restent la validation précédente, sans réexécution de sa suite complète.

- [ ] Définir puis réaliser une reprise autonome après perte/expiration du contexte, à partir d’une **nouvelle preuve vérifiée**. L’orientation actuelle vers l’administrateur ne fournit pas de mécanisme administratif supplémentaire.
- [ ] Traiter ou faire accepter explicitement la perte non récupérable des codes de récupération si la réponse de confirmation est perdue. Résultat incertain affiché, pas de répétition automatique du TOTP.
- [ ] Réception réelle de SMS et déploiement ; la recette utilise SMS simulés et HIBP remplacé, aucun appel Free/OVHcloud.
- [ ] Préserver les chantiers distincts : confirmations privilégiées, ADMIN_EMERGENCY, exploitation, audit SMS par entreprise et métier connecté.

**L’étape MFA n’est pas entièrement close tant que ses limites de reprise ne sont pas traitées ou explicitement acceptées.** Les anciennes mentions « formulaire à raccorder » ci-dessous restent des constats historiques. Voir [preuves attribuées et limites](VALIDATION.md#mfa-swing-2026-11-22).

**Jalon antérieur au raccordement MFA Swing du 22 novembre : les dates, résultats et anciennes limites ci-dessous restent historiques ; lire la reprise ci-dessus pour l’état courant.**


<a id="mfa-restreint-2026-11-22"></a>

## Raccordement MFA restant côté Swing — 22 novembre 2026

Le parcours serveur d’enrôlement MFA restreint est finalisé, mais **le formulaire Swing reste à raccorder**. L’interface doit encore prendre en charge ce parcours distinct de l’activation SMS et revenir à la connexion normale après confirmation ; aucune connexion automatique n’en découle.

Cette mission ne livre aucun changement de code client et n’exécute aucune nouvelle validation client. Les résultats Swing antérieurs conservent leur date et leur périmètre. Voir l’[état serveur vérifié](https://github.com/lisepinheiro2-lgtm/gestionplus-server/blob/main/docs/AVANCEMENT.md#mfa-restreint-2026-11-22).

**Historique antérieur à la reprise MFA du 22 novembre : les résultats, décisions et anciennes limites ci-dessous gardent leur date et leur périmètre.**


<a id="activation-sms-swing-2026-11-21"></a>

## Activation raccordée : acquis et suite — 21 novembre 2026

Sur **`ec08c42` client** et **`165d2e0` serveur**, sont présents : accès à l’activation depuis la connexion, formulaire dédié, lien masqué analysé par fragment, demande SMS explicite, validation identifiant/mot de passe/code et retour à la connexion sans connexion automatique. Les appels restent dirigés vers le serveur configuré, hors EDT, avec annulation/nettoyage et attente `Retry-After` sur 429.

Le serveur revalide le défi sous verrous compte/invitation, envoie après commit, conserve l’échec initial en cas de compensation défaillante et dispose de la reprise durable V28. L’adaptateur OVHcloud EU est implémenté ; **réception réelle non vérifiée**. Le filtre public de Lise est raccordé, avec quota Redis commun aux deux routes.

Les anciennes tâches « route finale », « refus d’invitation », « revalidation avant SMS », « compensation » et « activation Swing » ne sont plus entièrement absentes. Distinguer code réalisé et recette d’exploitation : les listes historiques du gist restent datées, sans clôturer par extrapolation les autres sujets.

- [ ] Réception et comportement opérationnel avec compte OVHcloud réel, après préparation explicite de l’environnement.
- [ ] Premier enrôlement TOTP des comptes concernés, distinct de l’activation SMS et du POST normal de connexion.
- [ ] Déploiement, TLS/proxy/réseau, exploitation multi-instance, audits SMS par entreprise et confirmation des garanties du fournisseur.
- [ ] Confirmations privilégiées, ADMIN_EMERGENCY, préférences par compte et fiches/planning/interventions/documents connectés.

La démo CSV reste indépendante et les cartes métier connectées désactivées. Les [suites complètes de Codex](VALIDATION.md#activation-sms-swing-2026-11-21) sont 1 143/1 143 serveur et 413/413 client dans les deux modes ; elles ne prouvent pas une recette complète jusqu’au téléphone.

**Historique antérieur à cette reprise activation SMS/Swing : les dates, preuves et anciennes limites ci-dessous gardent leur contexte ; la reprise ci-dessus décrit l’état courant.**


<a id="reprise-2026-11-21"></a>

## Reprise et tâches ouvertes — 21 novembre 2026

**Présents sur `53d110d` :** suivi actif des échéances, avertissement, suspension en cas de panne, traitement central des 401, retour au formulaire et nettoyage du contexte ; sélecteur des structures autorisées, vérification du choix, actualisation après refus et rejet des réponses obsolètes. Démo CSV indépendante et cartes métier connectées désactivées.

Les [validations](VALIDATION.md#reprise-2026-11-21) distinguent les 381/381 client et 924/924 serveur rapportés avant revue, les contrôles ciblés de Codex et la confirmation sans nouveau total après corrections.

- [ ] Fiche salarié (`ADM01`) : téléphone/e-mail et distinction entre coordonnées salarié et compte pour invitation/activation ; API autorisée à raccorder.
- [ ] Fiche client (`CLI02`, `ADM03`) : clarifier coordonnées client, contact d’urgence et gestionnaire d’interventions avant raccordement.
- [ ] Fiches connectées : valider saisie, sauvegarde/relecture et autorisations serveur.

Préférences par compte (`CPT04`), planning, interventions et documents sensibles restent à réaliser côté connecté. Création/fermeture des structures et ADMIN_EMERGENCY suivent des chantiers distincts. Les services serveur d’accès, transfert ADMIN, succession volontaire, cycle des comptes et responsabilité MANAGER existent ; leurs parcours complets restent à raccorder.

Les **13 tâches ouvertes** de l’ancien gist, l’annexe multiserveur et les réserves conservées sont regroupées dans l’[avancement serveur](https://github.com/lisepinheiro2-lgtm/gestionplus-server/blob/main/docs/AVANCEMENT.md#reprise-2026-11-21). Les [décisions d’activation/MFA et de confirmation](vision-ensemble.md#reprise-2026-11-21) restent des tâches tant qu’elles ne sont pas implémentées. Premier déploiement sur une seule instance.


<a id="suite-connexion-2026-11-20"></a>

## Parcours connecté : acquis et suite — 20 novembre 2026

**Historique du 20 novembre ; lire la reprise du 21 novembre ci-dessus pour l’état courant.**

**Acquis sur `03387cc` :** contexte connecté, choix du mode, formulaire TOTP, accueil sur `HomePanel`, identité confirmée et déconnexion coordonnée. Lise rapporte 284/284 tests et confirme les deux modes et la reconnexion sur serveur local isolé.

| Chantier | État |
| --- | --- |
| Démo CSV | Maintenue dans son assemblage et son dossier fictifs dédiés. Aucun repli implicite depuis CONNECTED. |
| Accueil et retour au formulaire | Raccordés ; Compte déclenche directement le logout. |
| Expiration dans Swing | Lecture des échéances disponible ; moniteur, avertissement, traitement central des 401 et retour à la connexion à développer. |
| Compte et métier connecté | Écran de compte et API métier à raccorder ; cartes connectées désactivées. Droits et périmètres restent contrôlés par le serveur. |
| Isolation entre utilisateurs | Tests avec HTTP fictif et cookies distincts présents. Poursuivre la recette réelle entre deux comptes et contrôler les tâches/réponses tardives lors de l'ajout des écrans métier. |
| Exploitation | SUPER_ADMIN fictif et environnement isolé préparés hors dépôt ; aucun provisionnement automatique de production livré. |

Ces acquis ne clôturent pas globalement les anciens repères CPT, ADM ou SOC. Les décisions métier et leurs historiques ci-dessous restent conservés.

<a id="priorite-autorisations-2026-11-05"></a>
## Priorité et décisions actualisées — 5 novembre 2026

**Documentation de conception uniquement.** Les [décisions communes du 5 novembre](vision-ensemble.md#decisions-securite-2026-11-05) priment sur les anciennes hypothèses de comptes et les ordres de travail incompatibles ci-dessous. Sécuriser **les autorisations côté serveur en premier**, puis reprendre l’authentification et l’intégration Swing.

| Repères concernés | Décisions fermes à conserver |
| --- | --- |
| SOC07, CPT01–CPT04 | Serveur Spring Boot/PostgreSQL local chez l’entreprise ou distant ; aucune obligation d’hébergement externe. Une entreprise cliente peut regrouper plusieurs structures ; exactement un SUPER_ADMIN créé par la développeuse à la mise en service, limité à cette entreprise. |
| CPT01–CPT03, ADM01–ADM07 | Compte global multi-structures, rôles propres à chaque rattachement ; exactement un ADMIN nominatif par structure, jamais supprimable individuellement même par SUPER_ADMIN. Seul le SUPER_ADMIN de l’entreprise peut supprimer une structure ; retirer tous ses accès et son rattachement ADMIN en préservant les autres accès du compte global. Conservation, transactions, révocation et synchronisations à concevoir. |
| SOC07, CPT01–CPT03 | Identité authentifiée Spring, compte rechargé au serveur et rôles lus dans StructureMembershipStore pour la structure ; rôles/UUID libres fournis par Swing sans valeur d’autorité. SUPER_ADMIN contrôlé dans son entreprise. |
| CPT03, CLI, ADM | Hiérarchie via Spring Security, distincte des permissions métier. ACCOUNTANT hors chaîne ordinaire mais sous ADMIN/SUPER_ADMIN ; MANAGER et SECRETARY peuvent être incomparables. ADMIN/SUPER_ADMIN ont tous les droits métier de leur périmètre sous les réserves de cycle de vie ; RH modifie les rôles inférieurs autorisés, ADMIN gère RH. SECRETARY crée/édite des contrats et invite, sans modifier les rôles ni désactiver de comptes. |
| SOC07, ADM07 | ADMIN_EMERGENCY est un mode hors ligne pour chaque ADMIN dans sa propre structure sur poste préautorisé, pas un compte ni un rôle ordinaire. Authentification sécurisée, chiffrement local, audit, droits limités, réconciliation et révocation différée à concevoir. |

Swing présentera les périmètres, opérations permises et refus ; la décision de sécurité appartient au serveur. Le détail des droits, les parcours de fermeture/récupération et les opérations hors ligne restent à préciser. Ces fonctions ne sont pas annoncées comme implémentées ou vérifiées par cette note ; la démonstration autonome fictive reste distincte. Aucun code ni exécution applicative, test, build, migration ou action Git modifiante.

<a id="tests-serveur-2026-11-05"></a>
## Point de reprise — tests serveur du 5 novembre 2026

Pour SOC07, CPT01–CPT03 et ADM07, **huit fichiers de tests serveur ont été adaptés et deux ajoutés, avec 87 méthodes déclarées non compilées et non exécutées**. Les scénarios préparent les vérifications d’identité Spring, de protections ADMIN/SUPER_ADMIN, de hiérarchie et droits par structure, d’entreprise propriétaire, d’audit et de conservation des autres accès. Les fixtures incluent désormais les tenants ; voir le [bilan serveur et ses limites](https://github.com/lisepinheiro2-lgtm/gestionplus-server/blob/main/docs/AVANCEMENT.md#tests-autorisations-2026-11-05).

Cette écriture ne valide pas l’intégration Swing, la révocation effective des sessions, le rattachement personnel au manager, l’unicité des administrateurs en stockage, la fermeture de structure ou le mode hors ligne. Les problèmes de production repérés sont signalés dans le bilan et restent sans correction. Aucun code client modifié ; aucune compilation ni exécution applicative, test, serveur ou migration lancée. La priorité des autorisations serveur est maintenue.

## Décisions transversales du 29 octobre 2026

**Précisions de conception, sans livraison de code.** Cette section complète les demandes ci-dessous ; les états techniques historiques ne sont pas réévalués. La référence détaillée commune est la [vision d'ensemble — décisions du 29 octobre](vision-ensemble.md#decisions-metier-2026-10-29).

| Repère commun | Besoin retenu et lien au carnet client |
| --- | --- |
| [DEC01 — Structures](vision-ensemble.md#dec-structures) | Plusieurs structures indépendantes et données isolées côté serveur. Le choix compte unique multi-structures ou comptes séparés reste explicitement ouvert. |
| [DEC02 — Droits](vision-ensemble.md#dec-droits) | Gestion par responsables liés et secrétaires dans leur périmètre ; matrice consulter/créer/modifier/supprimer à préciser. Concerne SOC07 et CPT01–CPT04. |
| [DEC03 — Historique](vision-ensemble.md#dec-historique) | Conserver les documents après départ selon les règles de conservation ; garder les interventions annulées avec une alerte « facturation à déterminer », puis une décision habilitée facturée/non facturée. Concerne INT01 et ADM02–ADM05 ; préserver aussi l'alerte future sur le passé non attribué sans présumer qu'une intervention a été effectuée. |
| [DEC04 — Concurrence](vision-ensemble.md#dec-concurrence) | Verrou temporaire côté serveur et information de l'autre utilisateur, plus contrôle final à l'enregistrement ; aucune modification concurrente écrasée silencieusement. Concerne notamment PLA, INT et RMP. |
| [DEC05 — Secours hors ligne](vision-ensemble.md#dec-hors-ligne) | ADMIN_EMERGENCY utile sur poste autorisé, avec restrictions à définir ; authentification locale sécurisée, chiffrement, audit et réconciliation. « Toutes les opérations » ne vaut pas accès illimité validé. |
| [DEC06 — Documents](vision-ensemble.md#dec-documents) | Administrateur : accès documentaire complet dans sa structure ; comptable : seules pièces nécessaires à sa mission. Catégories, droits et stockage à détailler. Aucun accès aux secrets d'authentification. Concerne SOC07 et ADM01–ADM07. |

L'interface Swing devra présenter les périmètres et les conflits sans tenir lieu de contrôle d'autorisation. Documents et Archives restent des chantiers ; ne pas transformer ces décisions en fonctionnalités annoncées disponibles. Les paramètres encore ouverts sont regroupés dans la référence commune. Aucun test, build, serveur, migration ou changement Git réalisé par cette mise à jour.

## Décisions et livraison de fiabilisation du 20 octobre 2026

**Nouvelle cible validée :** plusieurs postes et comptes, base centrale, autorisations vérifiées côté serveur. Pas de migration ni de serveur mis en place dans cette livraison. Limiter désormais l'investissement dans les CSV aux protections indispensables pendant la transition.

Les règles effectivement livrées et leurs limites sont décrites dans [FIABILISATION.md](FIABILISATION.md). Cette mise à jour prime sur les constats historiques ci-dessous : écritures/lectures et restaurations des six dépôts, coordination avec récupération, protection des références, validations et confirmations, sélection stable, remplacements à la bonne période.

**Absences :** lien durable aux interventions futures désattribuées et regroupement dans la demande, attribution collective des non-attribuées ou individuelle depuis Planning. Les anciennes absences/demandes ne sont pas converties automatiquement. **À développer ensuite : reprise anticipée d'une absence ayant des interventions liées, actuellement bloquée par décision explicite de l'utilisatrice.** Définir le sort des remplaçants déjà prévus, les conflits et le rétablissement des affectations avant de débloquer cette action.

**Historique :** actions globales des séries limitées aux occurrences non commencées ; prévoir ultérieurement une case explicite autorisant le traitement des occurrences passées. Les définitions nécessaires à l'historique sont conservées.

**Autres décisions différées :** découpage d'une intervention partiellement remplacée avec accord du client ; identifiants stables et homonymes avec la base centrale ; modification des dates/clients d'interventions liées à une absence ; suppression/archivage de personnes référencées. Pas de fusion des deux formulaires de création dans cette passe.

Les entrées plus anciennes ci-dessous restent un carnet historique et ne signifient pas que ces corrections sont encore toutes à faire.

Mise en cohérence documentaire du **20 octobre 2026**. Cette liste conserve les améliorations prévues ainsi que les chantiers de fiabilité déjà suivis. **Une amélioration inscrite ici n'est pas une fonctionnalité terminée.** Aucun calendrier de livraison n'est fixé.

« À réaliser » signifie qu'aucune implémentation complète n'est établie dans l'état lu. « Partiel » signale un socle ou une partie existante. La réussite des parcours reste distincte de leur présence dans le code ; voir [VALIDATION.md](VALIDATION.md).

## Ordre de travail

**Ordre historique, à lire sous la [priorité autorisations du 5 novembre](#priorite-autorisations-2026-11-05).** L’ancienne mention de commit ci-dessous ne constitue aucune autorisation Git.

Inventaire → factorisation → fonctionnalités → design → tests regroupés → commit après bilan. La reprise porte d'abord sur la sécurisation des affectations et des erreurs, puis les trajets et Personnel. Les autres domaines restent au carnet de demandes sans priorité relative inventée.

## Fiabilité et services communs

| Repère | État | Travail à terminer |
| --- | --- | --- |
| SOC01 | En cours | Stabiliser les préconditions, les conventions de retour et les exceptions de PlanningService ; garder le succès sans modification pour le même employé et décider si les dates doivent être validées avant ce retour. |
| SOC02 | Partiel | Consolider l'attribution/désattribution/remplacement avec rollback par index ; examiner aussi ajout, édition, suppression, génération/suppression récurrente et tous les autres chemins de mutation. |
| SOC03 | Partiel | Sécuriser les écritures et erreurs des six CSV et leur cohérence. Le temporaire et ATOMIC_MOVE concernent les interventions ; aucune transaction globale n'est démontrée. |
| SOC04 | À réaliser | Harmoniser l'identité des employés/clients, y compris après changement de nom, homonymie et rechargement. |
| SOC05 | À réaliser | Consulter, modifier et annuler absences actuelles/futures ; intégrer leur disponibilité aux attributions et garder une cohérence avec les demandes et transferts. |
| SOC06 | Partiel | Respecter dates et heures de remplacement, dates inclusives des absences et fin exclusive à minuit des demandes ; éviter de sélectionner à tort le jour suivant. |
| TRJ01 | À réaliser | Intégrer les trajets automatiques avec la marge demandée de +5 minutes ; TravelTimeService est présent mais vide. |
| HOR01 | À réaliser | Permettre les corrections d'horaires avec confirmation avant application et recalcul des conséquences ; le détail du parcours reste à préciser. |
| SOC07 | À réaliser | Protéger les données sensibles et secrets, en cohérence avec les futurs comptes/droits ; aucun suivi privé ou fichier personnel réel ne doit devenir une documentation publique. |

Pour les trajets, définir les entrées, la règle précise des cinq minutes, les échecs/adresses absentes, les recalculs et l'effet sur les compteurs. Aucun prestataire de trajet n'est choisi dans cet état.

## Personnel

| Repère | Type | Demande et état |
| --- | --- | --- |
| PER01 | Fonction | Attribuer ou désattribuer des heures par double clic sur le compteur, avec choix des interventions. Non branché dans Personnel ; les méthodes de PlanningService sont un socle à utiliser. |
| PER02 | Fonction et UI | Regrouper les deux choix de période en une seule sélection : attribuées/restantes pour la même semaine ou le même mois. Les sélecteurs indépendants sont l'état actuel, pas la cible future. |
| PER03 | Navigation | Remplacer les navigations séparées par un seul spinner suivant semaine/mois selon le choix commun. |
| PER04 | UI | Éloigner les labels du bord gauche et les champs du bord droit en gardant l'adaptation au redimensionnement. |
| PER05 | UI | Corriger la lueur qui reste active après un clic sans sélection et augmenter légèrement l'effet demandé. |
| PER06 | UI | Opérationnel : lueur verte interne et personnage vert marchant ; Congés : lueur rouge et personnage orange sur chaise longue ; Maladie : lueur rouge et croix médicale. L'apparence « Autre absence » reste à préciser. |
| PER07 | UI | Lueur bleue au survol de chaque zone réellement modifiable. |
| PER08 | Compte | Remplacer le bouton Paramètres par un pictogramme/menu Compte ; détails communs ci-dessous. |

## Comptes et préférences utilisateur

Les [décisions du 9 novembre sur les comptes et rattachements](vision-ensemble.md#inscription-et-rattachements-2026-11-09) actualisent le parcours CPT03 et la portée des droits de `MANAGER` et `SECRETARY` à la création d’un rattachement. Swing distingue un salarié connu dans une autre structure du même tenant, un salarié déjà rattaché à la structure cible et une première inscription. Il présente les seuls résultats et données autorisés par le serveur. La première invitation est envoyée après validation et enregistrement des accès, puis expire après sept jours. Ces parcours restent à développer.

| Repère | Type | Demande et état |
| --- | --- | --- |
| CPT01 | Fonction | Nouvelle vue de connexion avant MainUI ; conserver HomePanel pour l'utilisateur connecté. À réaliser. |
| CPT02 | Fonction et UI | Menu avec Mon compte, Préférences utilisateur, Changer d'utilisateur et Se déconnecter. À réaliser. |
| CPT03 | Fonction | Comptes distincts, stockage sécurisé des mots de passe et gestion d'une session active ; droits d'accès éventuels à concevoir. Aucun système d'authentification n'est établi actuellement. |
| CPT04 | Fonction | Préférences par utilisateur : thème, affichage, période par défaut et autres réglages retenus. Les préférences actuelles sont celles du profil système, pas de comptes applicatifs distincts. |

La conception des comptes et des droits doit précéder l'exposition des documents sensibles et du journal complet. Le fournisseur d'authentification, le stockage et le détail des rôles ne sont pas arrêtés.

## Clients

| Repère | Type | Demande et état |
| --- | --- | --- |
| CLI01 | Fonction | Refuser une adresse incomplète, notamment sans code postal ou ville. Le contrôle actuel vérifie surtout qu'elle n'est pas vide. |
| CLI02 | Fonction et UI | Ajouter téléphone, saisie et colonne ; permettre de choisir l'affichage du numéro du client, du contact d'urgence ou du gestionnaire des interventions (parent, tuteur, etc.). |
| CLI03 | Fonction et UI | Statut des interventions du client éditable par double clic : en cours vert, suspendu rouge avec dates au survol, délocalisé orange avec nouvelle adresse au survol. Définir ses effets sur le planning ; ne pas le confondre avec le statut d'un employé. |
| CLI04 | Présentation | Afficher les dates en `dd/MM/uuuu`. FormatUtils sait déjà le faire, mais le tableau Clients utilise encore `LocalDate.toString()` ; harmonisation restante. Ne pas modifier le format CSV pour une seule demande d'affichage. |
| CLI05 | UI | Lueur bleue au survol des zones éditables par double clic ; relier toute édition à une vraie sauvegarde ou la rendre non modifiable. |
| CLI06 | Fonction | Conserver le besoin déjà suivi d'un profil client aidant au choix de l'intervenant ; articuler ce profil avec les futures informations Administration/Archives. |

## Planning

| Repère | Type | Demande et état |
| --- | --- | --- |
| PLA01 | Navigation et UI | Aujourd'hui doit faire défiler jusqu'au jour courant et surligner sa date en bleu. Le retour à sa période existe ; le défilement/surlignage demandé reste à réaliser. |
| PLA02 | Fonction | Ajouter une vue journée aux vues semaine et mois. |
| PLA03 | Fonction | Envoyer le planning semaine/mois par courriel à tous ou à un seul destinataire. Destinataires, aperçu et prestataire à définir. |
| PLA04 | UI | Remplacer les hachures des périodes passées par un voile grisé. Les hachures restent l'état actuel. |

## Remplacements

| Repère | Type | Demande et état |
| --- | --- | --- |
| RMP01 | Calcul et UI | Remplacer Heures contrat par les heures nécessitant un remplaçant et les heures disponibles des remplaçants potentiels ; définir la période et les règles de disponibilité. |
| RMP02 | Présentation | Regrouper début date+heure et fin date+heure dans leurs deux colonnes respectives. |
| RMP03 | Fonction | Double clic sur les heures à remplacer : ouvrir la liste des interventions concernées et permettre une attribution unique ou multiple. Le socle collectif du service ne constitue pas cette interface. |
| RMP04 | Fonction et UI | Retirer le champ Employé remplaçant et la case Tout attribuer une fois le nouveau parcours branché ; ils existent encore actuellement. |
| RMP05 | UI | Lueur bleue sur les zones éditables au survol. |
| RMP06 | Calcul et UI | Vert si tout est attribué, rouge si rien ne l'est, orange si partiellement attribué ; calculer ces états depuis les interventions, pas seulement depuis un remplaçant global. |

Préserver le lien à l'absence, les affectations existantes et les annulations. Supprimer une demande ne doit pas être assimilé silencieusement à une annulation d'absence.

## Liste des interventions et récurrences

| Repère | Type | Demande et état |
| --- | --- | --- |
| INT01 | Fonction | Masquer les interventions passées de la liste active et les rendre consultables dans Archives. Prévoir leur accès avant de les masquer ; aucune suppression de l'historique n'est demandée. |
| INT02 | Présentation | Rétablir les majuscules des noms/prénoms clients et intervenants. Le tableau force actuellement les minuscules ; conserver l'orthographe stockée puis préciser la règle de présentation voulue. |
| INT03 | Fonction et UI | Retirer le sélecteur Trier par et utiliser l'ordre Dates par défaut. Cette demande récente remplace l'ancien souhait de conserver ce sélecteur ; préserver la recherche. |
| INT04 | Fonction | Rendre la colonne Client non modifiable et supprimer l'édition visuelle sans persistance. Le modèle actuel laisse éditer les cellules sans enregistrer ces changements. |
| INT05 | Fonction | Repenser les récurrences pour plusieurs jours et horaires, identiques ou différents, avec un modèle de série explicite. La définition actuelle représente un jour et une plage horaire. |
| INT06 | Fonction | Rendre le double clic effectif sur les autres colonnes avec validation, sauvegarde et rafraîchissement ; définir le périmètre occurrence/série. |
| INT07 | Fonction et UI | Une intervention unique ne doit pas demander de date de fin dans le parcours courant. Préciser le cas des nuits/pluri-journalières pour conserver la fin réelle dans le modèle. |
| INT08 | Fonction | Préremplir les dates de récurrence depuis le contrat du client, en les gardant éditables ; définir le cas des dates de contrat absentes. |
| INT09 | Fonction | Après Aucun résultat, ne pas laisser la liste vide une fois le message fermé ; choisir retour à la liste précédente ou remise à zéro du filtre. Le code actuel rafraîchit avec une liste vide. |
| INT10 | Fonction | Lors du déplacement d'une intervention, envoyer automatiquement un SMS au client si l'utilisatrice coche l'option prévue. Envoi non implémenté ; préciser destinataire, contenu, confirmation et prévention des doublons. |
| INT11 | UI | Lueur verte si attribuée, rouge sinon. |
| INT12 | UI | Lueur bleue au survol des zones éditables. |
| INT13 | UI | Retirer la lueur au survol des titres de colonnes sans action. |

La création, modification, suppression et attribution de séries doivent conserver la cohérence entre la définition et les occurrences, y compris lors d'un échec de sauvegarde. Examiner aussi les récurrences nocturnes, que la génération actuelle ne modélise pas avec une date de fin différente.

## Administration et Archives

Le cahier de demandes regroupe sous « Archives » des informations actuelles, documents administratifs et historique. La séparation future Administration/Archives est à concevoir ; ne pas faire passer ce choix d'organisation pour un écran déjà créé.

| Repère | Type | Demande et état |
| --- | --- | --- |
| ADM01 | Fonction | Informations du personnel : adresse, courriel, téléphone et contact d'urgence. |
| ADM02 | Fonction | Documents du personnel : fiches de paie, contrat, RIB, diplômes/certifications. |
| ADM03 | Fonction | Informations clients : adresse, coordonnées du gestionnaire du contrat, contexte familial, pathologie, besoins et contact d'urgence. |
| ADM04 | Fonction | Documents clients : factures, contrat, RIB et autorisation de prélèvement. |
| ADM05 | Fonction | Consultation de l'archive des interventions. |
| ADM06 | Fonction | Frais et revenus de l'entreprise, liste détaillée par mois/année et prévisionnel. |
| ADM07 | Traçabilité | Journal des modifications réservé à un compte avec accès complet : changement effectué, auteur, date/heure et appareil. Le niveau de détail et l'identification de l'appareil restent à définir. |

Ces besoins peuvent concerner des données sensibles. Définir accès, stockage et conservation avant leur implémentation. Aucun document réel ni donnée personnelle n'est nécessaire dans le dépôt public pour décrire ces fonctions.

## Décisions encore ouvertes

- Règle des cinq minutes et comptabilisation des trajets ; parcours précis des corrections d'horaires.
- Date de fin implicite d'une intervention unique nocturne/pluri-journalière.
- Format souple des récurrences et portée occurrence/série des modifications.
- Actions exactes des nouveaux statuts clients sur les interventions existantes et futures.
- Retour après une recherche sans correspondance ; présentation exacte des majuscules.
- Effet d'un double clic sur des heures nulles/négatives ou une période passée.
- Apparence du statut Autre absence, rôles des comptes et séparation Administration/Archives.
- Prestataires et configuration des courriels/SMS, en évitant les doublons et tout secret publié.

Aucune de ces décisions n'a été tranchée par la seule mise à jour documentaire.
