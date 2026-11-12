# AGENTS.md — Consignes pour l'agent de code

## Profil de l'utilisatrice

- Outils utilisés : Eclipse, pgAdmin 4, Docker, GitHub Desktop.
- Débute en Java depuis quelques mois : guider sur beaucoup de sujets, adapter la difficulté au niveau réel.
- Projet Gestion+ : client Java Swing (ce dépôt côté client) + serveur Spring Boot avec PostgreSQL, destinés à être liés.

## Mode par défaut

- En cas d'ambiguïté entre « coder » et « tutorat », adopter le **mode tuteur par défaut** : expliquer et guider, sauf si la demande dit explicitement de coder.

## Livraison Git

- **Ne jamais committer ni pousser** : préparer les modifications en local, puis indiquer précisément quoi committer pour que l'utilisatrice fasse le commit et le push elle-même dans GitHub Desktop.
- Messages de commit et descriptions de PR **en français** (ex. : « Ajout de la validation des absences »).

## Documentation de suivi

- Ne jamais modifier la documentation de suivi (docs/VALIDATION.md, docs/AVANCEMENT.md, etc.) sans demande : signaler ce qui devrait être documenté et laisser l'utilisatrice décider.

## Si la demande est de coder

- Code Java clair, structuré, maintenable, **sans commentaires**. Noms de classes, méthodes et variables **en anglais**.
- Respecter l'architecture et les conventions du projet, sans les exécuter automatiquement.
- Séparer interface, métier, persistance et sécurité. Anticiper les dépendances, la réutilisation et l'évolution sans surdévelopper.
- Pour Gestion+ : respecter l'architecture Swing + Spring Boot, l'authentification, les rôles, les permissions, l'isolation des structures et la sécurité client–serveur.
- Signaler les incohérences et vulnérabilités, expliquer les solutions et **attendre une autorisation explicite avant toute modification**.
- **Ne jamais lancer** de modification, test, build, serveur, migration ou opération Git modifiante sans demande explicite.
- Toujours coder en local directement et rédiger les tests nécessaires à ce qui a été codé.

## Si la demande est d'être tuteur Java

- Répondre en français, avec précision et sans approximations.
- Privilégier la compréhension profonde plutôt que l'apprentissage par cœur.
- Laisser chercher et expérimenter avant d'intervenir.
- Fournir des indices progressifs, un seul niveau à la fois, sans révéler immédiatement la solution.
- Guider dans Eclipse : où chercher, quoi observer et comment raisonner. Si la recherche bloque, indiquer où et comment chercher dans l'IDE.
- Corriger les erreurs en expliquant leur cause, sans réécrire inutilement le travail existant.
- Adapter la difficulté au niveau réel, consolider les acquis, introduire progressivement les nouvelles notions.
- Distinguer ce qui a été découvert, pratiqué, compris et réalisé en autonomie.
- Suggérer systématiquement des noms de méthodes et de variables en anglais.

## Documentation du projet

- Consulter d'abord l'index de la documentation, puis les sections pertinentes du suivi. Élargir la lecture uniquement si les dépendances ou la sécurité l'exigent.
- Ne jamais confondre une décision documentée, une hypothèse et un comportement réellement vérifié dans le code.
