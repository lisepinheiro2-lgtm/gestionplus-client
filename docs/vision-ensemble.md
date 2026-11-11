---
title: "Gestion+ — vision d’ensemble client et serveur"
summary: "Vision commune et décisions métier : structures, droits, archives, concurrence, secours hors ligne et documents sensibles."
read_when:
  - "raisonner sur l’ensemble Gestion+ avant une évolution d’un composant"
  - "retrouver les besoins Archives Personnel Clients Planning comptes et sécurité"
  - "préparer la réunion des projets et distinguer cible et état observé"
  - "retrouver DEC01 DEC02 DEC03 DEC04 DEC05 DEC06 et les arbitrages du 29 octobre 2026"
---

# Gestion+ — vision d’ensemble

[Documentation du client](index.md) · [Documentation du serveur](https://github.com/lisepinheiro2-lgtm/gestionplus-server/blob/main/docs/index.md)

**Gestion+ est un même projet métier, aujourd’hui développé dans deux dépôts.** Le client Swing porte les écrans de gestion ; le chantier Spring Boot prépare les comptes, les contrôles et le stockage centralisé PostgreSQL.

Les projets client et serveur restent séparés et doivent pouvoir fonctionner ensemble. Leur intégration, leurs contrats d’échange et la compatibilité de leurs versions restent à construire. Cette page distingue les décisions fonctionnelles, leur historique et les validations techniques.

<a id="decisions-consolidees-2026-11-11"></a>

## Décisions consolidées — 11 novembre 2026

**Référence fonctionnelle actuelle.** Cette consolidation reprend le bilan fourni par l’utilisatrice le 11 novembre. Elle complète les décisions antérieures et remplace leurs formulations incompatibles. Une règle décidée ne constitue pas une fonctionnalité livrée ou testée. Les sections historiques plus bas conservent leur date ; leur ancien « reste à décider » ne rouvre pas les arbitrages ci-dessous.

### 1. Architecture et ordre du chantier

- Client Java Swing ; serveur Spring Boot responsable de l’authentification, des autorisations et des règles métier sensibles ; PostgreSQL source de vérité côté serveur.
- CSV provisoires pour le stockage de la démo autonome fictive, destinés à céder la place au serveur PostgreSQL pour l’usage connecté ; ils peuvent rester utiles aux imports et exports.
- Chaque entreprise cliente choisit et exploite son infrastructure, locale ou distante. Le serveur contrôle chaque requête, les permissions, l’entreprise et les structures autorisées. Swing ne fait jamais autorité en matière de sécurité.
- Sécuriser et terminer le serveur avant de reprendre l’intégration Swing. Le développement complet d’ADMIN_EMERGENCY reste ultérieur.
- Projets client et serveur séparés, pouvant fonctionner ensemble. Les contrats d’échange et la compatibilité des versions restent à construire au rythme du chantier.

### 2. Entreprise, structures et administration

- Une entreprise possède exactement un SUPER_ADMIN nominatif, créé à sa mise en service. Le provisionnement initial exclusivement par l’éditrice, décidé précédemment, est conservé.
- La titularité enregistrée sur l’entreprise établit son autorité. Aucun second rôle global contradictoire n’est nécessaire. Cette autorité ne s’étend jamais à une autre entreprise.
- Une structure active possède exactement un ADMIN. Une personne peut administrer plusieurs structures de la même entreprise. Le SUPER_ADMIN peut lui-même être ADMIN d’autant de ses structures qu’il le souhaite, sans plafond.
- Seul le SUPER_ADMIN transfère la responsabilité ADMIN et supprime une structure. La fermeture préserve les références d’audit nécessaires et les autres accès des personnes concernées ; elle n’autorise pas un effacement arbitraire des identités ou des preuves.

**Transfert ADMIN.** Le serveur exige une confirmation renforcée, contrôle les droits et l’état confirmé, puis effectue une opération atomique. Le successeur reçoit ADMIN sur la structure concernée. Le SUPER_ADMIN choisit explicitement les rôles et rattachements conservés par l’ancien titulaire, ou sa désactivation ; sans aucun rattachement valide, son compte est désactivé, sous réserve de sa propre titularité SUPER_ADMIN.

Les sessions des personnes concernées sont révoquées. Le récapitulatif présente précisément rôles et structures avant/après et permet d’ouvrir leur gestion pour des ajustements autorisés. L’ancien et le nouvel ADMIN sont informés dans Swing et par e-mail. Un échec de notification n’annule pas le transfert ; il est tracé et repris. Tous les postes ADMIN_EMERGENCY de l’ancien titulaire sont automatiquement révoqués ; le nouvel ADMIN choisit ses propres postes.

### 3. Rôles et permissions

| Rôle | Contrat retenu dans le périmètre autorisé |
| --- | --- |
| SUPER_ADMIN | Autorité générale dans son entreprise ; gestion des structures, ADMIN et SECURITY_OFFICER. |
| ADMIN | Administration complète de ses structures autorisées, sous réserve des opérations réservées et des protections des secrets et audits. |
| R_H | Gestion des comptes et rôles strictement inférieurs autorisés. |
| MANAGER | Gestion des salariés explicitement placés sous sa responsabilité, dans les limites de ses permissions. |
| SECRETARY | Invitations et gestion des comptes, rôles et rattachements autorisés pour EMPLOYEE et TEAM_LEAD. |
| SECURITY_OFFICER | Consultation des audits selon ses permissions et son périmètre ; aucun pouvoir général de modification. |
| TEAM_LEAD, EMPLOYEE, ACCOUNTANT | Aucun pouvoir général d’administration des comptes. Les droits métier propres restent distincts. |

- MANAGER et SECRETARY ne s’administrent pas mutuellement et ne sont pas des niveaux interchangeables. Les descriptions antérieures plus restrictives de leur gestion des comptes sont remplacées par ces règles.
- Un MANAGER n’agit que sur les salariés dont il a explicitement la responsabilité. ADMIN, R_H ou SUPER_ADMIN désignent le responsable, dans leur périmètre.
- Une opération groupée est refusée dès qu’elle contient un rôle non autorisé. Si une cible possède aussi un rôle que l’opérateur ne peut pas administrer, son compte entier est protégé contre ses modifications administratives.
- Attribution et retrait d’un rôle sont deux opérations distinctes, chacune contrôlée côté serveur. Une permission d’invitation n’autorise pas une attribution arbitraire.
- Le SUPER_ADMIN nomme les SECURITY_OFFICER et définit leurs rattachements.
- VIEW_AUDIT et VIEW_SENSITIVE_AUDIT sont deux permissions distinctes. L’accès aux événements sensibles ne permet jamais de sortir du périmètre autorisé. La matrice détaillée par catégorie d’audit reste à préciser sans inventer de droits pour les autres rôles.
- La participation de SECURITY_OFFICER à la conservation, avec accord du SUPER_ADMIN ci-dessous, est une compétence particulière ; elle ne lui donne pas un pouvoir général de modification.

### 4. Comptes, invitations et activation

Un salarié peut exister sans compte. Un nouveau compte reste PENDING_ACTIVATION jusqu’à la validation complète de son activation. La personne invitée choisit son identifiant et son mot de passe et valide son identité par le mécanisme prévu. Le parcours e-mail puis code SMS temporaire à tentatives limitées, décidé antérieurement, reste la référence ; sa réalisation doit être distinguée du simple contrat de vérification.

Invitations et codes sont temporaires, à usage limité et protégés contre les tentatives répétées. L’invitation expire après sept jours à compter de l’envoi effectif. Si l’activation n’a pas abouti, le jeton est invalidé et les rôles/rattachements préparés sont retirés atomiquement ; l’identité est conservée. La concurrence entre activation et expiration ne doit pas produire d’état partiel.

Une réintégration exige de nouveaux droits explicitement préparés et une nouvelle activation, sans rétablissement automatique des anciens accès. La perte du dernier accès désactive le compte et révoque ses sessions. Les comptes désactivés et références nécessaires aux audits sont conservés. Listes, recherches, fiches et exports respectent permissions, entreprises et structures. Invitations, renvois et activation résistent aux courses, rejouements et tentatives répétées.

Les précisions P1 déjà retenues restent applicables : réintégration désactivée jusqu’à l’envoi effectif, renvoi sans perte de l’ancien lien avant réussite, remplacement d’une invitation après correction d’adresse avec révocation de l’ancien lien. La nomination ADMIN préparée sur compte en attente et son délai de 24 heures restent un parcours distinct à réaliser ; le transfert courant sur compte actif ne les remplace pas.

### 5. Authentification

**Mots de passe.** Minimum 12 caractères, espaces significatifs autorisés, sans quotas de majuscules, chiffres ou symboles. Refuser les mots de passe compromis, notamment via Have I Been Pwned. Si la vérification est indisponible, création et changement de mot de passe sont bloqués avec un message explicatif dans Swing : aucun contournement silencieux. La connexion avec un mot de passe existant est un parcours distinct de la définition d’un nouveau secret.

Personne ne choisit directement le nouveau mot de passe d’un autre utilisateur. La récupération utilise la vérification d’identité et les canaux prévus, notamment e-mail et SMS. Tout changement de mot de passe révoque les sessions de tous les appareils, y compris celui ayant effectué le changement.

**Sessions en ligne.** Expiration après 30 minutes d’inactivité et 8 heures au maximum. Désactivation, changement sensible des droits ou révocation invalident les sessions concernées. Spring Security gère l’authentification avec sessions serveur et échanges HTTPS.

**MFA.** TOTP obligatoire pour SUPER_ADMIN, ADMIN, SECURITY_OFFICER, R_H, MANAGER et SECRETARY, facultatif pour les autres rôles. Le cumul applique l’exigence la plus forte. Solution sans achat obligatoire ; FIDO2 pourra être envisagé ultérieurement, notamment pour SUPER_ADMIN. Codes personnels de récupération à usage unique et réinitialisation encadrée. Une réinitialisation invalide les anciens facteurs/codes concernés et révoque les sessions.

| Appareil de confiance pour l’authentification en ligne | Durée |
| --- | --- |
| SUPER_ADMIN | 24 heures |
| ADMIN, SECURITY_OFFICER | 7 jours |
| R_H, MANAGER, SECRETARY | 14 jours |

Un appareil partagé ne peut pas bénéficier de cette confiance ; l’utilisateur peut révoquer ses appareils reconnus. Ces durées ne remplacent ni les durées de session, ni celles de consultation hors ligne, ni les autorisations ADMIN_EMERGENCY. Le traitement technique des cumuls de durées devra être explicité, sans créer d’extension implicite.

**Clés MFA.** Rotation normale par rechiffrement transparent des secrets TOTP, sans réinscription des utilisateurs. En cas de clé compromise : révocation des sessions, suspension des accès privilégiés concernés, puis nouvel enrôlement après vérification renforcée d’identité. Aucun secret d’authentification dans les audits. Le mécanisme de rotation, la conservation des anciennes clés et la restauration des sauvegardes restent à concevoir selon ces exigences.

### 6. Audit et conservation des preuves

L’audit est centralisé côté Spring Boot : authentifications, invitations, comptes, rôles, permissions, opérations sensibles et ADMIN_EMERGENCY. Succès, refus d’autorisation et échecs sont tracés avec acteur, cible, structures, rôles, opération, date et résultat. Les identifiants, périmètres et horodatages de référence proviennent du serveur, sans confiance aveugle dans Swing.

Une modification nécessitant un audit obligatoire est annulée si cet audit ne peut être enregistré. Les tentatives refusées restent traçables même si la transaction métier échoue. Pour le hors ligne, distinguer la date déclarée par le poste de la réception par le serveur. Protéger les preuves contre la falsification et les suppressions arbitraires, avec consultation limitée au périmètre et aux permissions.

**Conservation décidée.** Durées configurables par entreprise et catégorie de journal. Le SUPER_ADMIN modifie la politique ; SECURITY_OFFICER peut intervenir avec son accord. Chaque changement est justifié et audité. Les anciens événements conservent leur échéance initiale ; la nouvelle durée ne s’applique qu’aux nouveaux événements. Aucun nombre de jours légal ou uniforme n’est inventé.

Les événements liés à une enquête ou un litige peuvent être placés sous gel de conservation ciblé, justifié et tracé. Conditions de levée, autorité habilitée et contrôles associés restent à préciser s’ils ne sont pas déjà définis dans une autre décision explicite.

Après synchronisation hors ligne, l’audit des modifications reste obligatoirement enregistré côté serveur. Les journaux détaillés locaux ne sont nettoyés qu’après confirmation fiable de leur traitement ET de leur enregistrement serveur, y compris pour les opérations rejetées ou mises en quarantaine.

### 7. Consultation hors ligne des utilisateurs ordinaires

- Première connexion sur un ordinateur obligatoirement en ligne. Seuls les postes préalablement autorisés par ADMIN peuvent conserver des données hors ligne ; ADMIN et SUPER_ADMIN peuvent révoquer ces postes.
- Autorisation individuelle, authentification propre et espace chiffré isolé pour chaque utilisateur, y compris sur un ordinateur partagé. Les rôles soumis au MFA fournissent mot de passe et TOTP hors ligne.
- Consultation seule, sans modification métier. Sept jours maximum depuis la dernière vérification des droits en ligne ; à l’expiration, bloquer l’accès et supprimer les données locales concernées.
- Après perte de permissions, rendre les données correspondantes inaccessibles et les supprimer à la prochaine connexion au serveur. Verrouiller après 15 minutes d’inactivité hors ligne.
- Précharger automatiquement toutes les données autorisées, même jamais consultées ; stockage local chiffré. Synchroniser au démarrage, puis actualiser de façon automatique et incrémentale en arrière-plan.
- Passage hors ligne après confirmation et avertissement sur les données potentiellement périmées. Retour en ligne automatique après vérification des droits, avec information de l’utilisateur.

### 8. ADMIN_EMERGENCY

**Accès.** Mode de secours réservé à ADMIN dans ses structures autorisées. Chaque ADMIN autorise ses propres postes après authentification forte récente, avec deux ordinateurs au maximum par ADMIN et première préparation en ligne. Authentification hors ligne par mot de passe Gestion+ et TOTP. Aucune limite fixe de durée pour ce mode de secours ; cette décision remplace l’ancienne exigence générale d’expiration locale pour ADMIN_EMERGENCY, sans modifier les sept jours de consultation ordinaire.

Après cinq échecs de connexion, limitation progressive, persistante autant que techniquement possible. Modifications métier étendues pour les fonctions compatibles ; aucune gestion hors ligne des comptes, rôles ou permissions, ni exécution définitive d’opérations critiques nécessitant le serveur. La liste exacte reste à préciser module par module.

**Révocation.** Un poste définitivement révoqué ne synchronise plus ses modifications, même réalisées avant la révocation. À son retour au serveur, détruire ses données locales et clés associées. Un transfert ADMIN révoque automatiquement tous les postes de l’ancien titulaire ; le successeur choisit les siens. Un poste totalement déconnecté ne peut pas recevoir immédiatement cette révocation.

**Synchronisation.** Acceptation automatique des opérations sûres et sans conflit après contrôles serveur ; confirmation humaine des opérations importantes ; résolution hybride assistée des conflits, sans écrasement silencieux. Le serveur conserve l’autorité sur droits, règles métier, intégrité et dépendances. La quarantaine d’actions ambiguës d’un ancien ADMIN ne concerne éventuellement que le cas distinct où ses droits sont révoqués sans révocation définitive du poste ; voir le point à clarifier ci-dessous.

**Sauvegardes et récupération.** Sauvegardes locales chiffrées avec contrôle d’intégrité ; supports externes autorisés et chiffrés possibles. Restauration sur le deuxième poste déjà autorisé, même sans serveur, après authentification forte. En cas de corruption, utiliser une sauvegarde vérifiable. Ne récupérer les opérations plus récentes que si leur journal indépendant est fiable. Rapport détaillé des modifications non récupérées, non appliquées ou rejetées ; toute perte non entièrement reconstituable est signalée explicitement.

**Passage A/B.** Transfert sécurisé de l’état avant chaque changement de poste : contrôle léger sans modification, export chiffré/import/vérification sinon. Identifiants uniques pour éviter les doubles applications et dernier état fiable disponible sur le poste qui reprend. Si A tombe en panne avant transfert, B peut repartir de sa dernière sauvegarde fiable avec avertissement. Si A revient, réconciliation hors ligne via support sécurisé ; seuls les conflits et leurs dépendances bloquent. Après réconciliation, un seul poste poursuit les modifications et l’autre reste en consultation.

**Support perdu ou compromis.** Un ADMIN peut préparer un nouveau support depuis un poste autorisé pendant la panne. Le support déclaré perdu et remplacé est définitivement retiré. Retrouvé ensuite, ses anciennes modifications ne sont importées que dans une quarantaine isolée : examen individuel et réintégration locale provisoire des opérations vérifiables sans conflit. Les contrôles serveur restent obligatoires au retour en ligne.

### 9. Corrections, conflits et notifications

Une correction automatique n’est possible que si elle est sûre. Les opérations dépendantes non résolues passent en quarantaine ; les indépendantes restent utilisables. ADMIN et SUPER_ADMIN résolvent dans leurs périmètres, sans pouvoir approuver un contournement des règles serveur. Rapports détaillés et audit des corrections, acceptations et rejets.

L’ADMIN concerné et le SUPER_ADMIN reçoivent un rapport détaillé ; les personnes directement concernées sont automatiquement averties sans information hors de leurs permissions. Notifications systématiques dans Swing avec historique. Pour ces notifications de correction, l’e-mail complémentaire est facultatif, générique, sans données métier sensibles et soumis à la configuration de messagerie de l’entreprise. Le transfert ADMIN conserve sa règle spécifique d’information par e-mail. Un échec d’envoi ne bloque pas le traitement et doit pouvoir être repris.

Chaque notification de correction exige une confirmation explicite de lecture. Jusqu’à confirmation, seules les actions dépendant des données corrigées sont bloquées ; la consultation des données corrigées reste possible. Accusé enregistré uniquement en ligne auprès du serveur ; il ne constitue pas une acceptation de la modification.

### 10. Arbitrages fonctionnels encore ouverts

- Actions métier autorisées, différées ou interdites hors ligne, module par module.
- Catégories particulièrement sensibles à protéger davantage ou à exclure du stockage local.
- Comportement face à une horloge reculée ou incohérente, notamment pour les sept jours et le TOTP.
- Récupération extrême lorsque facteurs MFA, codes de secours et responsables habilités sont simultanément indisponibles.
- Conditions de levée du gel des preuves, autorités habilitées et contrôles associés.

### 11. Conception technique et vérifications restantes

Ces travaux ne sont pas des décisions déjà réalisées dans le code : protection et éventuel ancrage matériel des clés hors ligne ; séparation des utilisateurs ; détection des états anciens et doublons entre postes déconnectés ; intégrité et durabilité des journaux ; identifiants d’opérations, idempotence, dépendances et concurrence ; notifications et invitations durables avec reprises ; cohérence des anciennes données et migrations ; secrets, sauvegardes, restauration, mises à jour et exploitation selon l’infrastructure cliente.

Le choix des durées des appareils de confiance, du blocage en cas de panne HIBP et des objectifs de rotation MFA est acté. Les mécanismes nécessaires et leurs preuves restent à construire ou à vérifier, sans rouvrir ces décisions par une option technique contradictoire.

### 12. Incohérences à clarifier avant implémentation

**Ancien ADMIN et récupération du travail.** L’examen de ses modifications après retrait de ses droits et le rejet définitif après révocation du poste ne sont compatibles que dans des situations distinctes. Lors d’un transfert ordinaire, tous ses postes sont révoqués : aucune récupération de leurs modifications non synchronisées n’est donc autorisée par défaut. Le traitement exact de l’autre cas reste à confirmer avant implémentation ; une quarantaine ne contourne pas un rejet définitif.

**Fraîcheur entre deux postes déconnectés.** Une signature valide prouve l’intégrité d’un état, pas l’absence d’une modification ultérieure sur l’autre poste toujours déconnecté. La garantie du « dernier état » nécessite un dispositif supplémentaire ou une règle conservatrice à concevoir. Aucun mécanisme n’est réputé choisi ni validé par cette consolidation.

**Portée de la séance.** Mise à jour documentaire et comparaison statique des sources locales, y compris non commitées. Aucun code applicatif, test, build, serveur ou migration exécuté ou modifié, aucune opération Git modifiante. Les essais antérieurs restent limités à leurs dates et périmètres.

## Historique des décisions antérieures

Les entrées suivantes gardent leur contexte d’origine. En cas de divergence, appliquer la consolidation du 11 novembre ci-dessus.

<a id="inscription-et-rattachements-2026-11-09"></a>
## Compte global, rattachements et première inscription — 9 novembre 2026

**Décisions de conception validées.** Elles précisent DEC01 et DEC02 et remplacent, pour l’attribution de rôles lors d’un nouveau rattachement, l’ancienne restriction concernant `SECRETARY`.

- **Identité et isolation.** Un salarié possède un seul `UserAccount`, identifié par son `userID`. Il peut avoir plusieurs rattachements et des rôles différents dans les structures de la même entreprise cliente (`Tenant`). Des fiches salarié distinctes peuvent être reliées à cette identité. Les entreprises sont strictement isolées et les documents et données sensibles restent dans leur structure. Le `SUPER_ADMIN` est rattaché à son entreprise. L’absence de `StructureMembership` ne suffit pas à désactiver son compte.
- **Parcours Swing.** Pour un salarié déjà connu dans une autre structure de la même entreprise, l’interface signale son existence, propose de reprendre uniquement les informations autorisées et permet de choisir les nouveaux rôles. Le serveur réutilise le compte global. Si le rattachement cible existe déjà, Swing propose de gérer ses rôles sans le dupliquer. Un salarié inconnu suit la première inscription. Le serveur autorise la recherche et la reprise des données sans révéler l’existence de comptes d’autres entreprises.
- **Attribution initiale des rôles.** Le serveur exige à la fois `MANAGE_USERS`, `MANAGE_USER_ROLES`, une autorité strictement supérieure à chacun des rôles attribués et le respect du périmètre de la structure et de l’entreprise. Dans ces limites, `SUPER_ADMIN`, `ADMIN`, `R_H`, `MANAGER` et `SECRETARY` disposent des deux permissions. `TEAM_LEAD`, `EMPLOYEE` et `ACCOUNTANT` en sont dépourvus. L’attribution de la fonction `ADMIN` relève de son transfert dédié.
- **Première inscription.** Créer le compte en attente, préparer les rattachements et les rôles, vérifier les droits et enregistrer tous les accès de façon cohérente. Envoyer l’invitation après leur validation et leur enregistrement effectifs. Les accès sont inutilisables jusqu’à l’activation après vérifications.
- **Expiration.** Le délai de sept jours court à compter de l’envoi effectif de l’invitation. Si le compte est toujours en attente à l’échéance, le serveur invalide le jeton, passe le compte à `DISABLED` et retire atomiquement tous les rattachements et rôles préparés pour cette inscription. Il conserve l’identité et les données à retenir, trace les transitions et protège une activation concurrente réussie. Une réintégration exige une nouvelle validation, sans restauration automatique des accès.

Le parcours complet reste à développer. La coordination et les invitations concurrentes, le lien exact entre les fiches salarié et le compte, le sort du rattachement à l’entreprise à l’expiration, le déclenchement du traitement et les parcours de réintégration ou d’invitation d’un compte déjà actif restent à préciser.

<a id="decisions-securite-2026-11-05"></a>
## Décisions de référence — entreprises, autorisations et secours — 5 novembre 2026

**Décisions fermes de conception, consignées le 5 novembre 2026 (Europe/Paris).** Cette section précise DEC01, DEC02, DEC05 et DEC06 et prime sur les anciennes formulations incompatibles : compte multi-structures encore ouvert, simple protection du « dernier administrateur », retrait individuel de l’ADMIN et poursuite de l’authentification avant les autorisations. Les historiques restent conservés. Aucune nouvelle implémentation ni validation technique n’est revendiquée.

### Déploiement et entreprise cliente

- Spring Boot et PostgreSQL peuvent être installés **localement chez l’entreprise**, avec les clients Swing sur son réseau, **ou sur un serveur distant**. Aucun hébergement externe n’est obligatoire. Un serveur local joignable reste un usage connecté ; le mode de secours répond à l’indisponibilité du serveur pour un poste.
- Une **entreprise cliente peut regrouper plusieurs structures**. Elle possède **exactement un SUPER_ADMIN nominatif**, créé **exclusivement par la développeuse lors de la mise en service**. Aucun utilisateur ne peut en créer un autre.
- Le SUPER_ADMIN a autorité sur les seules structures de **son entreprise cliente**, jamais sur une autre entreprise, même si elles partagent une infrastructure. L’emplacement du serveur ne modifie pas ces frontières.
- Le **compte global multi-structures est retenu** : une identité peut avoir plusieurs rattachements, avec des rôles différents dans chaque structure. Structure indépendante et unité/équipe ne sont pas assimilées.

### ADMIN protégé et fermeture de structure

- Chaque structure possède **exactement un ADMIN nominatif**, limité à cette structure. Son compte **n’est jamais supprimable individuellement, même par le SUPER_ADMIN**. La règle ne se réduit plus à préserver un dernier ADMIN parmi plusieurs.
- **Seul le SUPER_ADMIN de l’entreprise cliente concernée peut supprimer une structure**, dans le cadre de sa fermeture. Cette suppression emporte le rattachement/la fonction ADMIN et **tous les accès à cette structure**.
- Distinguer le retrait des appartenances de la suppression de l’identité globale : **préserver les autres accès d’un compte multi-structures et les autres structures**. Le devenir d’un compte devenu sans rattachement reste à préciser ; aucun effacement global automatique n’est décidé.
- Concevoir ensemble obligations de conservation, documents/archives, dépendances, audit, révocation des sessions/autorisations concernées et invalidation des accès de secours. Prévoir transactions, échecs, opérations simultanées et synchronisations pour éviter une suppression partielle ou la recréation d’un accès par un poste ancien. Aucune cascade d’effacement de toutes les données n’est présumée conforme.
- Les anciennes possibilités de retrait ou de transfert de la fonction ADMIN ne constituent pas une exception permettant sa suppression individuelle. Les cas de remplacement, d’indisponibilité et de récupération restent à préciser sans laisser une structure active sans son ADMIN.

### ADMIN_EMERGENCY : mode hors ligne de chaque ADMIN

**ADMIN_EMERGENCY est un MODE de secours, pas un compte supplémentaire ni un rôle ordinaire de la hiérarchie.** Chaque ADMIN peut l’utiliser pour **sa propre structure**, sur un **poste préalablement autorisé**, en cas d’indisponibilité du serveur local ou distant.

Exigences retenues : authentification hors ligne sécurisée, données locales nécessaires chiffrées et isolées, journalisation protégée, autorisations limitées, synchronisation et réconciliation contrôlées avec gestion des conflits. L’accès complet en ligne n’implique pas des opérations hors ligne illimitées.

Une révocation ou une fermeture de structure survenue pendant une déconnexion ne peut pas être connue immédiatement du poste. Prévoir une autorisation locale limitée dans le temps, son renouvellement sécurisé et le traitement des changements devenus interdits à la reconnexion. Durées, opérations permises (notamment comptes, droits et finances), gestion des clés et règles de conflit restent à décider ; aucune révocation centrale instantanée hors ligne n’est promise.

### Source d’autorité côté serveur

L’acteur provient de **l’identité authentifiée par Spring Security**. Le serveur **recharge son compte**, vérifie son état courant et lit dans **StructureMembershipStore** ses rôles pour la structure concernée. Ce stockage fournit les appartenances ; les décisions d’autorisation relèvent du service côté serveur.

Les rôles et UUID fournis librement par Swing ne prouvent ni l’identité de l’acteur ni un droit d’accès. Un identifiant de structure ou de cible reçu du client doit être contrôlé dans le périmètre autorisé. Les rôles globaux historiques et les informations devenues obsolètes en session ne doivent pas contourner ce contrôle. Pour SUPER_ADMIN, vérifier côté serveur son rattachement à **l’entreprise propriétaire de la structure**, au lieu de se contenter de la présence du rôle.

### Hiérarchie et permissions métier

La hiérarchie de rôles sera définie via **Spring Security** ; les permissions métier restent une politique distincte. Un rang ne suffit jamais sans identité fiable, périmètre, permission d’action et protection du compte cible.

| Acteur ou relation | Décision retenue dans le périmètre autorisé |
| --- | --- |
| ADMIN / SUPER_ADMIN | Tous les droits métier dans leur périmètre respectif, sous réserve des opérations exclusivement réservées et des invariants ci-dessus : création du SUPER_ADMIN par la développeuse, suppression de structure par le SUPER_ADMIN, ADMIN non supprimable individuellement. Aucun accès aux secrets d’authentification ni contournement des traces. |
| ACCOUNTANT | Hors de la chaîne hiérarchique ordinaire, mais sous ADMIN et SUPER_ADMIN. Aucun classement supplémentaire par rapport à RH, MANAGER ou SECRETARY n’est déduit ; accès documentaire limité aux besoins de sa mission. |
| MANAGER / SECRETARY | Peuvent être **incomparables hiérarchiquement**. Aucun ordre total n’est imposé ; leurs permissions métier ne se déduisent pas d’une supériorité de l’un sur l’autre. |
| RH | Peut modifier les rôles inférieurs autorisés ; ADMIN peut gérer le compte RH. Cela n’autorise pas RH à gérer ADMIN, SUPER_ADMIN, ACCOUNTANT ou ses pairs par déduction. « RH » est le terme métier ; des suivis existants emploient `R_H` pour le nom technique, sans renommage décidé ici. |
| SECRETARY | Peut créer/éditer des contrats et inviter des utilisateurs ; ne peut **ni modifier les rôles ni désactiver les comptes**. Inviter n’autorise aucune attribution arbitraire de privilèges ; les rôles initiaux permis restent à préciser. |

La gestion d’une cible exige les permissions prévues et l’autorité supérieure requise, en tenant compte de **tous** ses rôles et des rôles demandés. La hiérarchie ne remplace pas la protection spéciale de l’ADMIN ou les compétences réservées au SUPER_ADMIN. Le graphe exact, les cas incomparables et la matrice par action restent à détailler sans inventer de droit implicite.

### Priorité, points ouverts et état réel

**Sécuriser d’abord les autorisations côté serveur**, notamment l’identité fiable et le périmètre dans AuthorizationService, avant de poursuivre l’authentification et l’intégration Swing. Les anciens ordres de travail sont historiques lorsqu’ils contredisent cette priorité.

Restent à préciser : représentation de l’entreprise et de l’unicité des comptes privilégiés ; procédure de mise en service et récupération/succession ; remplacement de l’ADMIN sans suppression individuelle ; graphe et matrice des droits ; conservation et fermeture transactionnelle ; portée de révocation des sessions multi-structures ; validité, clés, opérations et réconciliation hors ligne. Aucune durée légale ou technologie de stockage supplémentaire n’est choisie ici.

**Fonctionnalités non livrées par cette intervention :** garanties d’unicité et de cycle de vie, contrôles complets par entreprise/structure, hiérarchie Spring raccordée aux opérations, suppression sécurisée de structure et mode de secours. Les socles ou tests antérieurs restent uniquement couverts par leurs preuves datées ; ils ne démontrent pas ces parcours complets. Aucun code, test, build, serveur, migration, commit, push ou publication réalisé pour cette mise à jour.

## Situation actuelle et cible

| Composant | Situation documentée | Responsabilité dans la cible |
| --- | --- | --- |
| Client Swing | Application autonome avec fichiers CSV locaux et démonstration fictive ; documents techniques comportant des jalons historiques. | Écrans, navigation, saisies et présentation des résultats ou refus du serveur. |
| Serveur Spring Boot | Socles de comptes, authentification, permissions, salariés/unités, persistance et audit, avec un niveau d’achèvement variable. | Vérifier l’identité, les autorisations et la portée des données ; appliquer les règles métier et protéger les opérations. |
| PostgreSQL | Configuration et migrations du serveur présentes ; une migration écrite n’est pas une preuve de son exécution. | Conserver les données avec contraintes et transactions ; accès maîtrisé par le serveur. |
| Liaison client/serveur | Intégration complète prévue, sans validation de bout en bout revendiquée. | Contrats d’échange, sessions, erreurs, révocation et compatibilité des versions. |

Pour les faits détaillés, consulter les documents datés du client et [l’avancement du serveur](https://github.com/lisepinheiro2-lgtm/gestionplus-server/blob/main/docs/AVANCEMENT.md). Cette synthèse du **28 octobre 2026** organise ces références ; elle ne constitue pas une nouvelle revue exhaustive des sources ni une exécution de tests.

## Carte fonctionnelle à suivre ensemble

La [feuille de route du client](ROADMAP.md) conserve les demandes détaillées et leurs repères. La table ci-dessous relie ces domaines à la cible serveur ; elle ne transforme pas les fonctions prévues en fonctions opérationnelles.

| Domaine | Références | Responsabilité à conserver dans l’ensemble Gestion+ |
| --- | --- | --- |
| Personnel et unités | PER01–PER08, SOC04 | Fiches salarié, identités durables, rattachements, contrats et compteurs ; séparer calculs métier et présentation. |
| Comptes et préférences | CPT01–CPT04 | Invitation, activation, connexion, identifiants personnels, rôles, récupération, désactivation et préférences par compte. |
| Clients | CLI01–CLI06 | Contrats, coordonnées utiles, contacts, statuts et conséquences sur les interventions. |
| Planning | PLA01–PLA04 | Consultation des périodes, disponibilités, affectations et données nécessaires aux vues et exports. |
| Interventions et récurrences | INT01–INT13, SOC01–SOC02 | Cohérence occurrence/série, création et modifications, conflits, attributions et conservation de l’historique. |
| Absences et remplacements | SOC05–SOC06, RMP01–RMP06 | Liens durables, disponibilité, période concernée, attributions et reprise anticipée ; conserver les décisions encore ouvertes. |
| Trajets et corrections horaires | TRJ01, HOR01 | Règles de calcul, confirmation et conséquences sur les horaires et compteurs ; prestataire et détails restant à définir. |
| Courriels et SMS | PLA03, INT10, parcours de comptes | Destinataires, contenu, autorisation, confirmation selon le parcours, échecs et prévention des doublons. |
| Administration et Archives | ADM01–ADM07 | Informations et documents du personnel et des clients, historique des interventions, finances et journal des modifications. La séparation des écrans reste à concevoir. |
| Sécurité et audit | SOC07, ADM07, feuille de route serveur | Contrôles côté serveur, portée des accès, sessions, révocation, traçabilité protégée et cas de refus. |

<a id="decisions-metier-2026-10-29"></a>
## Décisions métier transversales — 29 octobre 2026

**Lecture historique :** les choix d’identité multi-structures, d’administration, de droits et de secours sont précisés par les [décisions du 5 novembre](#decisions-securite-2026-11-05), qui priment en cas d’incompatibilité.

**Cible de conception précisée par l'utilisatrice, puis ajustée après discussion des risques de concurrence et de fonctionnement hors ligne. Aucune de ces décisions n'est une preuve d'implémentation.** Les six repères DEC01–DEC06 ci-dessous constituent la référence commune ; les suivis client et serveur y renvoient. Les règles de comptes et de sécurité déjà retenues restent applicables, avec leur adaptation multi-structures encore à préciser.

<a id="dec-structures"></a>
### DEC01 — Plusieurs structures indépendantes

Gestion+ doit pouvoir accueillir plusieurs structures indépendantes, chacune avec ses salariés, clients, plannings, interventions, remplacements et documents. Le serveur devra imposer l'isolation lors des lectures et mutations, y compris recherches, téléchargements, exports et traces consultables. Le choix d'une structure dans Swing ne suffit pas à autoriser un accès.

Le découpage du stockage reste à concevoir ; aucune base par structure, colonne de partitionnement ou migration n'est décidée ici. Ne pas assimiler automatiquement une unité ou une équipe existante à une structure indépendante.

**Décision explicitement laissée ouverte :** une personne intervenant pour plusieurs structures utilisera-t-elle un compte unique avec des rattachements et droits séparés, ou un compte distinct par structure ? Cela concerne notamment les comptables externes. Aucune des deux options n'est retenue à ce stade. L'éventuelle administration technique transverse reste également à définir, sans accès inter-structures implicite.

<a id="dec-droits"></a>
### DEC02 — Responsables et secrétaires dans leur périmètre

Les responsables hiérarchiques liés aux personnes ou activités concernées et les secrétaires sont les acteurs de gestion envisagés pour les salariés, clients, interventions et remplacements. Leurs accès doivent être limités à la structure et au périmètre autorisés.

La matrice consulter/créer/modifier/supprimer reste à détailler pour chaque rôle et domaine, ainsi que les rattachements exacts des responsables et secrétaires. Cette orientation ne confère pas automatiquement tous les droits de suppression, d'accès documentaire ou de gestion des comptes. Les décisions antérieures sur MANAGER, RH et ADMIN pour les comptes ne sont pas annulées ; leur application aux structures reste à expliciter. L'autorisation finale relève du serveur.

<a id="dec-historique"></a>
### DEC03 — Départs, annulations, archives et facturation

- Le départ d'un salarié ou d'un client ne doit pas effacer automatiquement ses documents : contrats, fiches de paie, factures, solde de tout compte et autres pièces utiles restent conservés selon leur catégorie et les règles applicables.
- Les durées, accès après départ, archivage, éventuelle restauration et suppression à échéance restent à définir. « Conserver » ne signifie pas conservation illimitée ; aucune durée légale précise n'est fixée par cette note.
- Une intervention annulée reste consultable. Une alerte signale que sa facturation est à déterminer jusqu'à ce qu'une personne habilitée précise « facturée » ou « non facturée ». Conserver la décision et sa traçabilité ; aucun montant ou statut de facturation automatique n'est déduit de la seule annulation. Le parcours, les habilitations et les règles de validation de facture restent à concevoir.
- Distinguer ce cas de l'alerte future pour une intervention/remplacement devenu passé sans attribution, y compris une demande partiellement couverte. Un compteur limité aux occurrences futures peut être nul malgré ce passé à vérifier. L'absence d'attribution est un signal à examiner, pas une preuve de réalisation ou de non-réalisation ; l'absence d'alerte ne prouve pas non plus l'exécution. Prévenir la facturation erronée sans inventer des liens ou des faits manquants.

Ces besoins relèvent notamment d'INT01 et ADM02–ADM05. Archives, documents et facturation ne deviennent pas des modules opérationnels par leur inscription ici.

<a id="dec-concurrence"></a>
### DEC04 — Travail simultané sans écrasement silencieux

Deux protections complémentaires sont retenues comme cible :

1. Un verrou temporaire géré côté serveur signale qu'un autre utilisateur modifie déjà la ressource. L'utilisateur informé peut la consulter, mais ne peut pas la modifier tant que ce verrou s'applique.
2. Un contrôle au moment de l'enregistrement refuse une écriture devenue périmée ou concurrente, même si un verrou a expiré ou qu'une connexion a été perdue. Ne jamais écraser silencieusement la version d'un autre utilisateur ; présenter le conflit et permettre une reprise à partir des données courantes.

La portée du verrou (intervention, série ou autre ensemble), sa durée, son renouvellement/libération et le traitement d'une déconnexion restent à définir. Ces protections devront rester cohérentes avec les règles d'affectation et les autorisations ; un verrou n'accorde aucun droit supplémentaire. Aucune technologie de verrouillage ou de versionnement n'est implémentée par cette décision.

<a id="dec-hors-ligne"></a>
### DEC05 — ADMIN_EMERGENCY : continuité maximale sous restrictions

L'objectif est un mode de secours réellement utile sur un poste préalablement autorisé. La demande initiale « toutes les opérations » est ajustée : elle n'est pas retenue comme un accès illimité ni comme un contournement du serveur.

La liste des opérations permises et interdites reste à décider lors de la conception dédiée. Les changements de comptes, de permissions et de données financières font partie des opérations sensibles pour lesquelles une validation du serveur est à privilégier ; leurs restrictions exactes ne sont pas encore arbitrées.

Prévoir une authentification hors ligne sécurisée, des données locales chiffrées et limitées au périmètre autorisé, des traces protégées, puis une réconciliation contrôlée à la reconnexion. Un poste déconnecté ne connaît pas immédiatement les révocations ou modifications centrales ; définir la validité de l'autorisation locale, sa révocation et le traitement des conflits sans réinjecter automatiquement des changements obsolètes ou non autorisés. Aucun accès administrateur central implicite.

Ce chantier reste distinct de la démonstration Swing autonome avec données fictives. Aucun accès réel de secours, cache d'entreprise ou mécanisme de synchronisation n'est livré par cette note.

<a id="dec-documents"></a>
### DEC06 — Documents sensibles et accès administrateur/comptable

L'administrateur de la structure dispose de l'accès documentaire complet à celle-ci ; cette portée ne s'étend pas aux autres structures. Le comptable accède aux seuls documents nécessaires à sa mission, dans son périmètre autorisé. Les catégories exactes et les possibilités de lecture, téléchargement, ajout, modification, suppression ou export restent à détailler ; ne pas accorder un accès général aux autres documents personnels, médicaux ou administratifs par déduction.

« Accès complet » n'inclut jamais la consultation des mots de passe, codes ou jetons secrets. Les autres rôles ne reçoivent pas automatiquement des droits documentaires de leur seul droit de gestion métier.

Le stockage protégé, le chiffrement, les sauvegardes, la conservation, la traçabilité des accès et les refus inter-structures sont des exigences à concevoir et vérifier. Le serveur doit protéger la consultation et les téléchargements comme les modifications. Aucun fournisseur, emplacement de stockage ou format de chiffrement n'est sélectionné ici ; aucun document sensible réel dans les dépôts.

### Points encore ouverts et niveau de preuve

Restent à décider : compte unique ou séparé par structure ; rattachements et matrice des droits ; durées de conservation et règles de facturation ; portée et cycle de vie des verrous ; liste des opérations hors ligne et réconciliation ; catégories documentaires du comptable et stockage. Consulter ces décisions avant une évolution touchant les comptes, l'organisation, le planning ou les archives.

**Intervention du 29 octobre : documentation uniquement.** Aucun code, CSV, schéma, configuration applicative ou migration modifié ; aucun test, build, application ou serveur lancé. Les vérifications de métadonnées et de liens documentaires ne valident pas ces fonctionnalités. Aucune opération Git modifiante ni publication effectuée.

## Frontières à préserver

- Un bouton masqué dans Swing n’est pas une protection : le serveur doit vérifier les opérations de lecture comme de modification, d’export et de consultation des documents.
- La démonstration autonome utilise des données fictives isolées. Le serveur indisponible ne doit pas donner un accès local privilégié à des données d’entreprise.
- L’accès de secours `ADMIN_EMERGENCY` reste un mécanisme distinct à concevoir : poste autorisé, droits limités, audit et révocation. Il ne remplace pas un administrateur central.
- Préserver les règles déjà construites dans le client lorsqu’elles correspondent au besoin, puis définir leur responsabilité côté serveur ; éviter de créer deux politiques concurrentes.
- Les comptes, coordonnées, documents administratifs et traces réels ne servent pas d’exemples dans les sources ou la documentation publique.

## Préparer la réunion sans anticiper une fusion technique

1. Garder ces repères communs et une référence claire pour chaque décision.
2. Définir les contrats client/serveur au rythme des fonctionnalités autorisées : identifiants, données échangées, permissions et erreurs.
3. Distinguer les validations locales de chaque composant des essais connectés réellement exécutés.
4. Décider ultérieurement de l’organisation du projet réuni, de la reprise des historiques et de la compatibilité des versions.

Ces étapes sont des objectifs de suivi, pas des autorisations de déplacer du code, modifier Git ou lancer des services.

## Maintenir la documentation

| Information | Référence à actualiser lors d’une intervention autorisée |
| --- | --- |
| Demande fonctionnelle et décision d’écran | [Feuille de route client](ROADMAP.md), avec son repère existant |
| Structure et parcours Swing | [Architecture](ARCHITECTURE.md), [Personnel](PERSONNEL.md), [Interface](INTERFACE-RAVEN.md) |
| Protection des fichiers et résultats du client | [Fiabilisation](FIABILISATION.md), [Validations](VALIDATION.md) |
| Bloc serveur implémenté, en cours ou prévu ; preuve disponible | [Avancement serveur](https://github.com/lisepinheiro2-lgtm/gestionplus-server/blob/main/docs/AVANCEMENT.md) |
| Décision transversale affectant la réunion | Cette page, en renvoyant au détail concerné |
| Détails de séance et questions privées | Suivis locaux, sans copie intégrale dans les pages publiques |

Pour une preuve, préciser la date, le périmètre exécuté, le résultat et sa limite. Un test écrit mais non exécuté reste non vérifié. Conserver l’historique et comparer les versions des deux ordinateurs avant toute synchronisation des notes.
