---
summary: "MFA Swing : clé masquée, confirmation TOTP et dix codes uniques ; reprise encore limitée."
read_when:
  - "travailler sur le design les thèmes et la navigation Swing"
---

<a id="accueil-et-composants-raven"></a>

# Identité graphique de Gestion+ dans Swing

<a id="mfa-swing-2026-11-22"></a>

## Deux écrans MFA raccordés — 22 novembre 2026

Sur le client **`ff68060`**, `ActivationController` lit l’éventuelle preuve renvoyée par l’activation et ouvre automatiquement `MfaEnrollmentWindow` lorsqu’un enrôlement est requis. Sans MFA requis, retour au parcours de connexion sans connexion automatique.

Le premier écran présente la **clé de configuration masquée par défaut**, avec une action explicite pour l’afficher et la saisir manuellement dans l’authentificateur. L’utilisateur confirme avec un TOTP à six chiffres ; échéance et attentes liées aux limites restent visibles. Requêtes hors EDT, mises à jour sur EDT ; refus, 429, erreurs réseau, annulation et expiration sont traités sans rejouer automatiquement la preuve ou le TOTP.

Après confirmation, le second écran présente **les dix codes de récupération une seule fois**, sans sauvegarde ni copie automatique. Le retour normal demande la confirmation de leur conservation. Une fermeture sans cette confirmation exige un avertissement explicite ; quitter pendant une confirmation prévient du risque de perdre la réponse et les codes.

La clé, le code saisi et les codes de récupération sont retirés des champs à la fermeture. Les deux écrans utilisent les ressources et thèmes existants, contrôlés en **clair et sombre** lors de la validation antérieure. Le retour est celui du formulaire de connexion, **sans connexion automatique ni accès métier**, avec indication d’attendre un **nouveau TOTP** : le code de confirmation est déjà consommé.

Une réponse de confirmation perdue est présentée comme un résultat incertain ; aucun renvoi automatique du TOTP. L’orientation vers l’administrateur après perte/expiration du contexte n’implique pas qu’une procédure administrative de reprise ait été développée. Les codes d’une réponse perdue ne peuvent pas être relus par le client.

Voir [architecture](ARCHITECTURE.md#mfa-swing-2026-11-22), [recette et portée](VALIDATION.md#mfa-swing-2026-11-22) et [limites encore ouvertes](ROADMAP.md#mfa-swing-2026-11-22). **Le raccordement ne clôture pas entièrement l’étape MFA.**

**Jalon antérieur au raccordement MFA Swing du 22 novembre : les dates, résultats et anciennes limites ci-dessous restent historiques ; lire la reprise ci-dessus pour l’état courant.**


<a id="activation-sms-swing-2026-11-21"></a>

## Formulaire d’activation invitée — 21 novembre 2026

Sur `ec08c42`, la fenêtre de connexion propose **« Activer un compte invité »**. Le dialogue dédié `ActivationWindow` reprend composants Raven, icônes, ressources et thèmes existants ; le formulaire a été contrôlé visuellement en **clair et sombre** pendant la validation antérieure de Codex.

Le titulaire colle son **lien complet reçu par e-mail dans un champ masqué**, demande explicitement le SMS, puis renseigne identifiant, mot de passe, confirmation et code à six chiffres. Le fragment fournit l’invitation et le jeton ; le lien collé ne détermine jamais la destination des appels, qui reste le serveur configuré.

Une seule demande peut être en cours, avec progression et commandes adaptées ; le réseau est hors EDT. Refus de saisie, activation refusée, cooldown/quota, 429 et indisponibilité ont des messages dédiés. Sur 429, l’interface respecte `Retry-After`, affiche l’attente et bloque les demandes ; elle ne relance rien automatiquement. Fermeture : annulation, nettoyage du lien/mots de passe/code et fermeture du transport.

Au succès, confirmation affichée et champs sensibles nettoyés ; **« Retour à la connexion »** ramène au formulaire. Aucune connexion automatique. Le premier enrôlement TOTP reste distinct ; les cartes métier connectées restent désactivées et la démo CSV indépendante.

Voir [architecture et responsabilités](ARCHITECTURE.md#activation-sms-swing-2026-11-21) et [preuves et limites](VALIDATION.md#activation-sms-swing-2026-11-21). Le contrôle visuel n’est pas une recette Swing → OVHcloud → téléphone.

**Historique antérieur à cette reprise activation SMS/Swing : les dates, preuves et anciennes limites ci-dessous gardent leur contexte ; la reprise ci-dessus décrit l’état courant.**


<a id="reprise-2026-11-21"></a>

## Interface connectée actualisée — 21 novembre 2026

Sur `53d110d`, `SessionBanner` affiche vérification de session, avertissement d’échéance et indisponibilité. Une panne suspend l’usage connecté ; un 401 centralisé ou la fin de session nettoie le contexte et ramène au formulaire, sans repli CSV. Compte garde son action directe de déconnexion.

`StructureSelectorPanel` présente zéro accès, une structure sélectionnée automatiquement ou plusieurs avec choix explicite. La liste vient du serveur, le choix est vérifié et un refus déclenche son actualisation. Liste, sélection et réponses tardives sont nettoyées ou écartées à la suspension/fermeture et entre comptes ; la sélection reste uniquement dans le contexte connecté.

`ApplicationHeader` impose le **texte brut** pour l’identité : les balises d’un identifiant sont affichées telles quelles, avec maintien lors des changements de thème (`ApplicationHeaderIdentityTest`). Les cartes métier restent désactivées ; les écrans CSV ne deviennent pas connectés par le sélecteur. Voir [architecture](ARCHITECTURE.md#reprise-2026-11-21) et [preuves attribuées](VALIDATION.md#reprise-2026-11-21).


<a id="interface-connectee-2026-11-20"></a>

## Sélecteur, connexion et accueil connecté — 20 novembre 2026

**Historique du 20 novembre ; lire la reprise du 21 novembre ci-dessus pour l’état courant.**

Complément au relevé graphique du 19 novembre : le client `03387cc` réutilise la même identité visuelle en clair et sombre.

`LaunchSelectorWindow` reste modal : Démonstration retourne DEMO, Connexion retourne CONNECTED et la fermeture retourne `null`. `LoginWindow` affiche toujours identifiant, mot de passe et TOTP ; celui-ci est facultatif selon le compte. Les trois éléments partent dans le même POST. Le refus reste générique, sans révéler lequel est incorrect.

Après confirmation de l'identité, le dialogue se ferme automatiquement et `ConnectedHomeWindow` affiche le `HomePanel` existant avec `ApplicationHeader.setIdentity`. Les cartes métier connectées sont désactivées ; elles n'ouvrent pas les écrans CSV.

**Compte déclenche actuellement directement la déconnexion.** Aucun menu ni écran « Mon compte » n'est raccordé. Lise a confirmé ce comportement ; un futur menu ou libellé plus explicite reste une évolution d'ergonomie, sans changement dans cette livraison documentaire.

Le réseau s'exécute hors EDT et les changements Swing sur l'EDT. Une déconnexion non confirmée dispose l'accueil et affiche un avertissement avant le retour au formulaire ou la fermeture. La croix tente aussi le logout puis ferme l'application.

La recette de Lise valide accueil après TOTP, déconnexion/reconnexion et démonstration ; elle n'est pas une nouvelle revue exhaustive de tous les écrans. Voir les [preuves](VALIDATION.md#validation-connexion-2026-11-20).

[Original dans le dépôt client][fiche-client] · [Sources et historique](index.md#lire-les-dates-et-les-preuves)

Revue du **19 novembre 2026** à partir du [code et des ressources du client Swing sur `main` à la révision `788bfb2`][revision]. La refonte des 28–29 octobre a étendu la palette de l’accueil et du Planning aux écrans métier. Les repères ci-dessous décrivent l’interface à cette révision.

## Marque et ressources

Le bandeau affiche le nom **« Gestion + »** et la signature **« Organisation & remplacements »**. Le logo fusionne le **G** et le **+** dans un tracé aux angles obliques. Les deux SVG reprennent la même géométrie, avec un tracé à l’échelle 54/64 dans une emprise de **64 × 64**. Seules les couleurs du dégradé bleu–cyan et du contour varient selon le thème.

![Monogramme G+ pour le bandeau clair][logo-clair-image] ![Monogramme G+ pour le bandeau sombre][logo-sombre-image]

| Usage | Ressource dans `src/icons/` | Repère visuel |
| --- | --- | --- |
| Logo du bandeau clair | [`gestion-header-light.svg`][logo-clair] | Dégradé de `#005AA4` à `#00A2A4`. |
| Logo du bandeau sombre | [`gestion-header-dark.svg`][logo-sombre] | Dégradé de `#008AFC` à `#00FAFC`. |
| Icône de la fenêtre et de la barre des tâches | [`gestion-taskbar.png`][logo-fenetre] | Monogramme clair au liseré cyan lumineux dans un carré bleu nuit arrondi. `MainUI` décline le PNG de **1 254 × 1 254** en tailles de 16 à 256. |
| Variante du monogramme | [`gestion-64.png`][logo-64] | Contour cyan sur fond transparent. Cette ressource est référencée par des tests du bandeau. |
| Illustration de l’accueil | [`home-banner-background.png`][visuel-accueil] | Fond bleu nuit, formes obliques bleues et cyan sur la droite. Le PNG de **2 176 × 723** est recadré dans la bannière. |
| Interrupteur de thème | [`theme/light.svg` et `theme/dark.svg`][icones-theme] | Soleil turquoise `#00A0AD` et croissant blanc. |

Les autres pictogrammes de navigation et d’action sont dessinés en Java par [`NavigationIcon`][navigation-icon]. Ils partagent une grille de 24 unités et un trait arrondi, avec une taille et une couleur choisies par le composant qui les affiche. Les pictogrammes de statut du Personnel ont leurs propres [SVG][icones-statut].

## Palette, typographie et composants communs

Les couleurs partagées sont définies dans [`UIUtils`][ui-utils]. Le bandeau et la bannière d’accueil ont leurs propres teintes.

| Usage | Clair | Sombre |
| --- | --- | --- |
| Fond des pages | `#F4F7FB` | `#171E2A` |
| Surfaces des cartes et des champs | `#FFFFFF` | `#212A38` |
| Bordures | `#DEE5EE` | `#333F50` |
| Texte principal | `#1E2B3F` | `#EDF2F9` |
| Texte secondaire et indications de saisie | `#66768B` | `#9FAEC2` |
| Fond de survol des actions et des cartes | `#F5F9FF` | `#283446` |
| Fond alterné des tableaux | `#EDF3FA` | `#1D2735` |
| Fond de sélection dans les tableaux, listes et champs | `#D8EAFE` | `#304D68` |
| Turquoise | `#087F78` | `#67D9CD` |
| Violet | `#7845AE` | `#C5A0ED` |
| Champagne | `#88642A` | `#DFC391` |

| Accent | Rôle dans l’interface |
| --- | --- |
| Turquoise | Action principale (ajouter, attribuer ou confirmer), destination active, focus des actions, attribution effectuée. Pour les heures, il indique une attribution en bonne voie ou un objectif atteint. |
| Violet | Suppression et désattribution, intervention non attribuée, urgence ou dépassement d’heures, statut Maladie. |
| Champagne | Vigilance sur les heures, statuts Congés et Autre absence. |

- **Typographie.** Segoe UI est déclarée à **13 pt** pour l’interface commune. Le bandeau affiche le nom en gras à 18 pt, la signature à 11 pt et les libellés de navigation à 12 pt, en gras pour la destination active. Sur l’accueil, le titre de bannière est à 24 pt, les titres de carte en gras à 18 pt et leurs descriptions à 12 pt à l’échelle de base.
- **Formes et interactions.** Aplats, bordures fines et angles arrondis. FlatLaf utilise une valeur d’arrondi `arc` de 14 pour les boutons et de 12 pour les composants. Les actions portent un libellé et, selon leur type, un pictogramme (plus cerclé, flèches d’attribution ou corbeille). Le focus et le survol ont leurs propres bordures.
- **Tableaux et badges.** Le style commun fixe une hauteur de ligne d’au moins 28 et un en-tête de 32, avec séparateurs verticaux. [`BadgeRenderer`][badge] dessine une capsule teintée et bordée sur le fond de la cellule, en conservant les repères d’alternance et de sélection. Le libellé et, selon le cas, une icône précisent l’état.

## Bandeau, navigation et accueil

### Bandeau commun

Le monogramme et le nom sont à gauche, l’interrupteur de thème et le bouton Compte à droite. En clair, le bandeau a un fond blanc, un texte principal `#1C2A3F` et un filet inférieur `#E0E7F0`. En sombre, ces couleurs sont `#1B2330`, `#EEF3FB` et `#2D394A`. Le titre **Accueil** est centré et la rangée de navigation est masquée sur cette page.

Dans les autres vues, la navigation affiche **Accueil**, **Salariés**, **Clients**, **Planning**, **Remplacements** et **Documents**. L’entrée Salariés désigne l’écran Personnel. À partir de **1 160** unités de largeur du bandeau, la navigation vient au centre du bandeau. En dessous, elle passe en deuxième rangée. Le titre Interventions reste visible dans les deux dispositions, car cet écran s’ouvre par sa carte d’accueil. Le bouton Compte et l’entrée Documents sont désactivés.

L’interrupteur Raven anime le déplacement et la transition soleil/lune en **500 ms**. Il appelle `UIUtils.setDarkMode`, qui enregistre la préférence, réinstalle FlatLaf et réapplique le thème aux fenêtres Swing. Le thème clair est le défaut en l’absence de préférence enregistrée. Le bouton Paramètres mentionné dans le relevé du 20 octobre n’est plus branché dans le bandeau.

### Accueil

La bannière conserve son bleu nuit `#05172B` sous l’illustration dans les deux thèmes. Elle réunit à gauche **« Votre espace de gestion »**, avec « espace de gestion » en gras, un texte d’introduction et un court trait cyan `#00D9ED`. Le cartouche date affiche le jour en français, un calendrier cyan et **« Bonne journée ! »** sur un dégradé translucide de `#052339` à `#095B71`. Il est à droite en largeur normale et passe sous le titre quand la bannière se resserre.

Les six cartes forment **trois rangées de deux** dans l’ordre ci-dessous. À l’échelle de base, leur arrondi FlatLaf est de 22 et le pictogramme de 27 unités est placé sur un carré arrondi teinté de 54 × 54. Il est à gauche du titre et de la description. Les cartes actives montrent une flèche à droite et utilisent leur propre accent pour la bordure au survol et au focus.

| Rangée | Carte | Accent défini | Pictogramme |
| --- | --- | --- | --- |
| 1 | Personnel | `#5B94F3` | Équipe |
| 1 | Clients | `#2BAAA0` | Mallette |
| 2 | Planning | `#9A82E8` | Calendrier |
| 2 | Remplacements | `#D99C49` | Deux flèches opposées |
| 3 | Interventions | `#5D9EBE` | Porte-bloc avec coche |
| 3 | Archives | `#91A0B6` | Boîte d’archives |

**Archives** est désactivé, avec la mention **« À venir »**, un pictogramme atténué et aucune flèche. L’ancienne classe vide `ArchivedInterventionUI` a été retirée. La grille garde ses deux colonnes à petite taille. L’accueil réduit certains espacements en hauteur et agrandit les textes et pictogrammes quand la fenêtre offre assez de place.

## Repères graphiques par écran

### Planning

La barre de période associe un calendrier, le choix **Mois / Semaine**, des chevrons et **Aujourd’hui**. La recherche salarié/client comporte une loupe et un bouton d’effacement. En semaine, les sept jours sont alignés sur une grille de 24 heures et le panneau **DÉTAILS DU JOUR** occupe la droite. Le jour sélectionné est repéré par un fond bleuté et du texte turquoise dans son en-tête.

| Repère | Clair | Sombre |
| --- | --- | --- |
| Fond du jour sélectionné en semaine | `#E4F3FF` | `#21374F` |
| Créneau attribué, bordé de turquoise | `#D5F3EE` | `#244C50` |
| Créneau non attribué, bordé de violet | `#EBDDF9` | `#44345C` |
| Voile du passé | `#42464D` à 48/255 d’opacité | `#42464D` à 82/255 d’opacité |

En semaine, le voile couvre les jours passés et la partie écoulée de la journée en cours. En mois, il couvre le fond des jours passés. Les blocs individuels terminés sont également voilés. En semaine, le bloc affiche l’intervenant ou **« Non attribué »**, puis le client si la hauteur le permet. L’infobulle donne les noms et les horaires complets. Les créneaux superposés peuvent être regroupés sous **« +n »**. En mois, deux interventions au plus sont affichées dans chaque case, avec **« Voir les n »** au-delà.

### Personnel

Le tableau est précédé des champs Nom, Prénom et Heures hebdomadaires au contrat. Les colonnes **Heures attribuées** et **Heures restantes** partagent un sélecteur **Semaine / Mois** et les mêmes chevrons de période. Les heures sont centrées, en gras, avec une couleur d’alerte et une légende en infobulle. Un filet turquoise arrondi signale la sélection, le survol ou l’édition des cellules concernées.

| Statut affiché | Couleur du badge | Pictogramme et couleur propre |
| --- | --- | --- |
| Opérationnel | Turquoise | Empreintes vertes, `walking.svg` (`#35D47B`). |
| Congés | Champagne | Soleil jaune, `vacation.svg` (`#FFD54F`). |
| Maladie | Violet | Croix rouge, `sick.svg` (`#FF5252`). |
| Autre absence | Champagne | Pause orangée, `other.svg` (`#FFB74D`). |

La revue d’attribution utilise un aperçu **Avant → Après / Contrat**. La présentation par cartes reprend les surfaces arrondies et les couleurs d’alerte des heures, avec une jauge et un repère du contrat en cas de dépassement.

### Clients, Remplacements et Interventions

| Écran | Repères visuels |
| --- | --- |
| Clients | Formulaire encadré **Ajouter un client**, six champs en deux rangées et bouton d’ajout centré. Dans le tableau à six colonnes, la corbeille violette apparaît à droite de la date de fin de contrat, avec une infobulle et une confirmation à l’activation. |
| Remplacements | Formulaire encadré en deux colonnes, avec l’option **« Aucun remplaçant désigné »** et les actions Ajouter, Supprimer et Attribuer les non attribuées. L’état **Attribué (futur)** est turquoise. **Partiellement attribué** et **Non attribué** sont violets. **Sans occurrence future** et **Suivi individuel** sont atténués. Les demandes en suivi individuel affichent **—** dans les colonnes de compteurs. |
| Interventions | Commandes d’ajout turquoise et de suppression violettes. Quatre commandes à la suite en largeur normale, deux par rangée sous **1 180** unités disponibles. La loupe, les chevrons de pagination et le tri accompagnent le tableau. Le badge **Récurrent** est turquoise avec une flèche circulaire, le badge **Unique** est violet avec un calendrier. |

Les contenus des dialogues qui appellent `UIUtils.applyTheme` reprennent les champs, bordures et sélections du thème courant. `ActionDialogs` s’appuie sur `JOptionPane` pour les messages d’erreur et les confirmations.

## Composants actuels

| Fichier | Responsabilité |
| --- | --- |
| [`HomePanel`][home-panel] et [`HomeCardButton`][home-card] | Bannière, cartouche date, grille et rendu des cartes. Les cartes dérivent de `RavenStyledButton` et définissent ensuite leurs propres accents. |
| [`ApplicationHeader`][bandeau] et [`MainUI`][main-ui] | Marque, navigation, thème, assemblage des vues et préférences de fenêtre. |
| [`UIUtils`][ui-utils] et [`AppThemeAware`][theme-aware] | Palette commune, règles FlatLaf et prise en charge des composants à rendu spécifique. |
| [`NavigationIcon`][navigation-icon] et [`BadgeRenderer`][badge] | Pictogrammes vectoriels dessinés en Java et capsules des tableaux. |
| [`PlanningUI`][planning] et [`PlanningInterventionButton`][creneau] | Grilles, détail du jour, créneaux, regroupements et voile du passé. |
| [`PersonnelPanel`][personnel], [`PersonnelTable`][table-personnel], [`PersonnelStatusRenderer`][statut-personnel] et [`PersonnelHoursRenderer`][heures-personnel] | Disposition du tableau, contours de sélection, badges de statut et couleurs des heures. |
| [`ClientUI`][clients], [`ReplacementUI`][remplacements] et [`InterventionUI`][interventions] | Formulaires, tableaux et actions propres à chaque écran. |
| [Composants Raven][raven] | `RavenStyledButton`, `RavenTone`, `RavenDarkLightSwitch` et `DarkLightSwitchIcon`. |

Les dépendances configurées dans `lib` sont FlatLaf **3.7.2**, FlatLaf Extras **3.7.2** et JSVG **2.1.0**. Voir la [notice MIT de Raven][raven-mit] et les [attributions et limites de provenance du client][attributions].

Les préférences locales de thème, taille et maximisation utilisent `/gestionRemplacement`. La fenêtre a une taille minimale de **760 × 650** et s’ouvre maximisée par défaut. Le point d’entrée est `gestion.Main` et le module est `gestionRemplacement`.

## Évolutions demandées

La [feuille de route](ROADMAP.md) conserve les demandes de période commune pour les heures et de voile gris dans le Planning. Le code relu contient déjà ces deux éléments. La connexion, l’espace Compte, Documents et Archives figurent toujours parmi les parcours à développer.

## Validation

La revue du 19 novembre a comparé cette fiche, l’original du client, le suivi privé et les sources Java. Les SVG et PNG ont également été examinés. Les [validations datées](VALIDATION.md) gardent la trace des compilations, des exécutions JUnit et des observations déjà rapportées. La recette dans l’application reste à faire pour les deux thèmes, le redimensionnement du bandeau et de l’accueil, les badges sélectionnés, le Planning dense et les dialogues.

Voir aussi [l’architecture](ARCHITECTURE.md) et [la carte Personnel](PERSONNEL.md).

[revision]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/tree/788bfb2a73e6a463988f22e22773ec0ca6faa895
[fiche-client]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/docs/INTERFACE-RAVEN.md
[logo-clair]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/icons/gestion-header-light.svg
[logo-sombre]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/icons/gestion-header-dark.svg
[logo-clair-image]: https://raw.githubusercontent.com/lisepinheiro2-lgtm/gestionplus-client/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/icons/gestion-header-light.svg
[logo-sombre-image]: https://raw.githubusercontent.com/lisepinheiro2-lgtm/gestionplus-client/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/icons/gestion-header-dark.svg
[logo-fenetre]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/icons/gestion-taskbar.png
[logo-64]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/icons/gestion-64.png
[visuel-accueil]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/icons/home-banner-background.png
[icones-theme]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/tree/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/icons/theme
[icones-statut]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/tree/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/icons/status
[bandeau]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/ApplicationHeader.java
[main-ui]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/MainUI.java
[home-panel]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/HomePanel.java
[home-card]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/HomeCardButton.java
[ui-utils]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/UIUtils.java
[theme-aware]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/AppThemeAware.java
[navigation-icon]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/NavigationIcon.java
[badge]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/BadgeRenderer.java
[planning]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/planning/PlanningUI.java
[creneau]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/planning/PlanningInterventionButton.java
[personnel]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/personnel/PersonnelPanel.java
[table-personnel]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/personnel/PersonnelTable.java
[statut-personnel]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/personnel/PersonnelStatusRenderer.java
[heures-personnel]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/personnel/PersonnelHoursRenderer.java
[clients]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/ClientUI.java
[remplacements]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/ReplacementUI.java
[interventions]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/intervention/InterventionUI.java
[raven]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/tree/788bfb2a73e6a463988f22e22773ec0ca6faa895/src/gestion/ui/raven
[raven-mit]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/licenses/Raven-MIT.txt
[attributions]: https://github.com/lisepinheiro2-lgtm/gestionplus-client/blob/788bfb2a73e6a463988f22e22773ec0ca6faa895/THIRD_PARTY_NOTICES.md
