# Composants tiers — attributions et licences

Inventaire documentaire initial du **27 octobre 2026**, complété le **19 novembre 2026** pour Jackson. Les restrictions du [code original de Gestion+](LICENSE.md) ne s'appliquent pas aux éléments tiers. Les textes complets des licences prévalent sur les résumés ci-dessous. Aucune exclusivité sur ces composants n'est revendiquée.

## Raven — composants Swing

Attribution conservée : **Raven Laing (DJ-Raven)**, auteur de composants graphiques réutilisés et adaptés dans Gestion+. Merci à Raven pour leur mise à disposition. [Profil officiel](https://github.com/DJ-Raven).

La notice existante [licenses/Raven-MIT.txt](licenses/Raven-MIT.txt), laissée intacte, porte `Copyright (c) 2022 Raven Laing` et le texte MIT complet. `src/gestion/ui/raven/DarkLightSwitchIcon.java` conserve `@author RAVEN`. `RavenDarkLightSwitch.java`, `RavenStyledButton.java` et `RavenTone.java` se présentent comme des adaptations RavenGallery relevant de la licence des sources Raven originales. Les portions Raven de ces fichiers restent sous MIT ; elles ne sont pas placées sous les restrictions du code original de Gestion+.

MIT permet notamment l'utilisation, la modification, la redistribution et la vente, y compris commerciale, sous réserve de conserver la notice de copyright et la permission dans les copies ou portions substantielles. Conserver le texte complet fourni, y compris l'exclusion de garantie. Une utilisation conforme à MIT n'est pas un vol et ne nécessite pas l'autorisation de l'autrice de Gestion+ pour les portions Raven.

**Limite de provenance :** aucun lien vers le dépôt amont exact, tag ou commit d'origine n'a été retrouvé dans les fichiers locaux examinés. Le profil officiel confirme l'identité Raven Laing ; il ne suffit pas à authentifier la provenance de chaque adaptation. La notice locale est conservée sans prétendre qu'une comparaison complète avec une révision amont a été effectuée. Les modifications locales (dont le package `gestion.ui.raven` et les adaptations d'intégration) ne doivent pas être attribuées indistinctement à Raven.

Les ressources `src/icons/theme/dark.svg` et `light.svg`, utilisées par cet interrupteur, ne comportent pas de notice de provenance dans les fichiers examinés. Leur auteur et leur licence exacte restent à confirmer ; leur présence aux côtés du code Raven ne prouve pas qu'elles sont sous MIT. Aucune nouvelle licence ni revendication de propriété exclusive n'est attribuée à ces SVG par cette documentation. Cette incertitude reste à résoudre pour établir une provenance complète.

## FlatLaf et FlatLaf Extras — Apache License 2.0

- Fichiers présents et référencés dans `.classpath` : `lib/flatlaf-3.7.2.jar` et `lib/flatlaf-extras-3.7.2.jar`.
- Éditeur : **FormDev Software GmbH** ; les manifestes indiquent la version 3.7.2 et le copyright 2019–2026 FormDev Software GmbH.
- Chaque JAR contient `META-INF/LICENSE`, texte Apache 2.0. Une copie exacte de celui du JAR FlatLaf est fournie dans [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt) ; les deux textes embarqués ont été comparés.
- Aucun fichier `NOTICE` n'a été trouvé dans ces deux JAR lors de cette inspection. Cela ne dispense pas de conserver un éventuel `NOTICE` fourni par une autre version ou distribution.
- Références officielles : [FlatLaf](https://www.formdev.com/flatlaf/), [licence du tag 3.7.2](https://github.com/JFormDesigner/FlatLaf/blob/3.7.2/LICENSE), [texte Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0).

Apache 2.0 autorise les usages commerciaux. Lors d'une redistribution, fournir la licence, conserver les notices pertinentes dans les sources distribuées et reproduire les attributions d'un éventuel `NOTICE` selon la section 4. Les fichiers tiers modifiés doivent porter une indication visible de modification. La licence comprend aussi des dispositions sur les brevets, les marques et les garanties ; ce résumé ne les remplace pas. Les JAR et leurs notices embarquées n'ont pas été modifiés.

## JSVG — MIT

- Fichier présent et référencé dans `.classpath` : `lib/jsvg-2.1.0.jar` ; version 2.1.0 confirmée dans son manifeste.
- Auteur et notice embarquée : **Jannis Weis**, `Copyright (c) 2021-2024 Jannis Weis`.
- `META-INF/LICENSE` contient MIT. Copie exacte dans [licenses/JSVG-MIT.txt](licenses/JSVG-MIT.txt).
- Référence officielle : [weisJ/jsvg](https://github.com/weisJ/jsvg), qui annonce MIT. La notice du JAR local fait foi pour le texte reproduit ici, sans déduire sa date de celle de la dernière version du site.

Conserver la notice de copyright et la permission dans les copies ou portions substantielles, avec le texte complet fourni et son exclusion de garantie. Les usages commerciaux restent autorisés selon MIT.

## Jackson — Apache License 2.0

**Consultation directe de Maven Central le 19 novembre 2026.** La dernière version stable 2.x observée pour `jackson-core` et `jackson-databind` est **2.23.0** ; celle de `jackson-annotations` est **2.23**. Ces trois versions constituent le même ensemble de publication : le [BOM officiel 2.23.0](https://repo.maven.apache.org/maven2/com/fasterxml/jackson/jackson-bom/2.23.0/jackson-bom-2.23.0.pom) définit explicitement ces valeurs. Depuis 2.20, les annotations n'utilisent plus de numéro de correctif, selon la [politique de versionnement FasterXML](https://github.com/FasterXML/jackson/blob/main/VERSIONING.md). Les noms publiés sont conservés.

Vérification des métadonnées : [jackson-core](https://repo.maven.apache.org/maven2/com/fasterxml/jackson/core/jackson-core/maven-metadata.xml), [jackson-annotations](https://repo.maven.apache.org/maven2/com/fasterxml/jackson/core/jackson-annotations/maven-metadata.xml), [jackson-databind](https://repo.maven.apache.org/maven2/com/fasterxml/jackson/core/jackson-databind/maven-metadata.xml). La [note de version 2.23](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.23) date la publication du 7 novembre 2026 ; une version 2.23.1 annoncée mais non publiée n'est pas retenue.

### Fichiers et provenance

| JAR local | Version de l'artéfact | Taille en octets | Copies exactes des textes embarqués |
| --- | --- | ---: | --- |
| [`jackson-core-2.23.0.jar`](lib/jackson-core-2.23.0.jar) | 2.23.0 | 603 520 | [Apache 2.0](licenses/Jackson-Core-Apache-2.0.txt) · [NOTICE](licenses/Jackson-Core-NOTICE.txt) |
| [`jackson-annotations-2.23.jar`](lib/jackson-annotations-2.23.jar) | 2.23 | 84 212 | [Apache 2.0](licenses/Jackson-Annotations-Apache-2.0.txt) · [NOTICE](licenses/Jackson-Annotations-NOTICE.txt) |
| [`jackson-databind-2.23.0.jar`](lib/jackson-databind-2.23.0.jar) | 2.23.0 | 1 716 668 | [Apache 2.0](licenses/Jackson-Databind-Apache-2.0.txt) · [NOTICE](licenses/Jackson-Databind-NOTICE.txt) |

- Éditeur indiqué par les manifestes : **FasterXML**. Les `NOTICE` attribuent l'origine de Jackson à **Tatu Saloranta**, avec la mention `Copyright 2007-`.
- Les trois JAR proviennent de `https://repo.maven.apache.org/maven2/com/fasterxml/jackson/core/`, sous leurs coordonnées officielles `com.fasterxml.jackson.core`. Ce sont les JAR binaires ordinaires sans classifier, sans sources ni Javadoc et sans branche 3.x.
- Chaque fichier `META-INF/LICENSE` et chaque `META-INF/NOTICE` est reproduit sans modification dans les fichiers liés ci-dessus. Les trois licences Apache 2.0 ont été comparées octet par octet et sont identiques entre elles ; les notices sont conservées séparément.
- `jackson-core` embarque aussi du code **FastDoubleParser** et **Schubfach**. Les textes complémentaires sont copiés intégralement depuis ce JAR : [FastDoubleParser-LICENSE](licenses/Jackson-Core-FastDoubleParser-LICENSE.txt), [FastDoubleParser-ThirdParty-LICENSE](licenses/Jackson-Core-FastDoubleParser-ThirdParty-LICENSE.txt), [Schubfach-LICENSE](licenses/Jackson-Core-Schubfach-LICENSE.txt). Le `NOTICE` de core décrit ces inclusions ; aucune réécriture de leurs attributions n'est effectuée.
- Références amont : [jackson-core](https://github.com/FasterXML/jackson-core), [jackson-annotations](https://github.com/FasterXML/jackson-annotations), [jackson-databind](https://github.com/FasterXML/jackson-databind), [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

Les empreintes SHA-512 des trois téléchargements ont été comparées aux fichiers `.jar.sha512` publiés à côté des JAR sur Maven Central. Empreintes SHA-256 calculées localement pour la traçabilité :

```text
449798beb4c4ec26de9cc25b39ca2198036883fb905ca968cbef7136d19a25d9  lib/jackson-core-2.23.0.jar
e5b60c3088e0887b290ff9226394354589a1dad583743944b7f5538661056b4c  lib/jackson-annotations-2.23.jar
abd1edd2736c764da03fe6cb7a9de70d48dd0a198aed131604c564bff62f9cc1  lib/jackson-databind-2.23.0.jar
```

### Intégration Eclipse et portée

Les trois entrées de `.classpath` utilisent des chemins relatifs `lib/...` avec l'attribut `module=true`, comme les dépendances FlatLaf existantes. Les descripteurs JPMS embarqués déclarent `com.fasterxml.jackson.core`, `com.fasterxml.jackson.annotation` et `com.fasterxml.jackson.databind`. Le module databind requiert les deux autres de façon transitive.

Sur autorisation complémentaire de Lise, seule la ligne `requires com.fasterxml.jackson.databind;` est ajoutée à `src/module-info.java`, en conservant notamment son ajout préalable de `java.net.http`. Aucun parseur, DTO, réglage d'ObjectMapper, appel HTTP ou changement de logique applicative n'est fourni par cette intégration. L'accès réflexif à d'éventuels DTO dans des packages non exportés devra être déterminé avec les classes réellement utilisées, sans ouverture globale du module.

Les archives ZIP, manifestes, modules, licences, notices et chemins Eclipse sont inspectés sans exécuter Jackson. Les signatures PGP ne sont pas vérifiées. Aucune compilation, aucun test, serveur, migration, commit ou push n'est lancé.

### Avis de sécurité consultés le 19 novembre 2026

Les avis publiés des dépôts officiels [jackson-databind](https://github.com/FasterXML/jackson-databind/security/advisories), [jackson-core](https://github.com/FasterXML/jackson-core/security/advisories) et [jackson-annotations](https://github.com/FasterXML/jackson-annotations/security/advisories) sont consultés, avec leurs plages affectées et versions corrigées.

**Statut de l'ensemble retenu : aucune des plages 2.x déclarées dans ces avis ne couvre 2.23.0.** Aucun avis publié n'est retourné pour le dépôt jackson-annotations à cette date. Ce constat est limité à ces sources et à cette consultation ; il ne remplace pas la veille des dépendances ni une vérification de la configuration applicative.

Le tableau recense les CVE 2025–2026 présentes dans ces avis et concernant une branche 2.x. Les correctifs indiqués sont des exemples dans les branches antérieures, pas des versions à substituer au jeu 2.23 retenu.

| CVE | Artéfact | Exemple de version corrigée annoncé par l'avis |
| --- | --- | --- |
| [CVE-2026-110879](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-m8xf-2cr7-77ph) | `jackson-databind` | 2.22.4 |
| [CVE-2026-91776](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-wv8q-qhhj-9h54) | `jackson-databind` | 2.22.3 |
| [CVE-2026-91777](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-cxp5-3px4-pw24) | `jackson-databind` | 2.22.3 |
| [CVE-2026-83557](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-gx83-3vf8-gh7j) | `jackson-databind` | 2.22.2 |
| [CVE-2026-19032](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-wjgm-6hv5-3cvf) | `jackson-databind` | 2.22.2 |
| [CVE-2026-68497](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-q4xh-88c3-wmh7) | `jackson-databind` | 2.22.2 |
| [CVE-2026-77310](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-vvgp-rfg2-7rr6) | `jackson-databind` | 2.21.5 (branche 2.21) |
| [CVE-2026-59889](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-5gvw-p9qm-jgwh) | `jackson-databind` | 2.22.1 |
| [CVE-2026-59888](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-3pjw-73gf-8qr5) | `jackson-databind` | 2.21.4 |
| [CVE-2026-54518](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-rcqc-6cw3-h962) | `jackson-databind` | 2.21.4 |
| [CVE-2026-54517](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-5hh8-q8hv-fr38) | `jackson-databind` | 2.21.4 |
| [CVE-2026-54516](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-9fxm-vc8v-hj55) | `jackson-databind` | 2.18.12 |
| [CVE-2026-54515](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-5jmj-h7xm-6q6v) | `jackson-databind` | 2.22.1 |
| [CVE-2026-54514](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-hgj6-7826-r7m5) | `jackson-databind` | 2.21.4 |
| [CVE-2026-54513](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-rmj7-2vxq-3g9f) | `jackson-databind` | 2.21.4 |
| [CVE-2026-54512](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-j3rv-43j4-c7qm) | `jackson-databind` | 2.21.4 |
| [CVE-2026-50193](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-3wrr-7qpf-2prh) | `jackson-databind` | 2.14.0 |
| [CVE-2026-89425](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-7hhh-6rmp-j9qf) | `jackson-core` | 2.22.3 |
| [CVE-2026-89407](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-p6pp-m3f8-5c89) | `jackson-core` | 2.22.3 |
| [CVE-2026-68498](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-649p-m576-vr99) | `jackson-core` | 2.22.2 |
| [CVE-2025-52999](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-h46c-h94j-95f3) | `jackson-core` | 2.15.0 |
| [CVE-2025-49128](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-wf8f-6423-gfxg) | `jackson-core` | 2.13.0 |

**Point databind récent.** [CVE-2026-110879](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-m8xf-2cr7-77ph), publiée le 20 octobre 2026, complète le correctif de [CVE-2026-54512](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-j3rv-43j4-c7qm) : contournement de la validation des types polymorphes. Les correctifs annoncés sont 2.18.12, 2.21.8 et 2.22.4 ; la publication 2.23.0 retenue est postérieure et hors des plages affectées. Les CVE de septembre sur les identifiants de type et les références différées sont également corrigées dans les branches antérieures à 2.23.0.

Trois autres avis 2.x de core sont publiés sans identifiant CVE dans les données consultées : [GHSA-72hv-8253-57qq](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-72hv-8253-57qq), [GHSA-2m67-wjpj-xhg9](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-2m67-wjpj-xhg9) et [GHSA-r7wm-3cxj-wff9](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-r7wm-3cxj-wff9), concernant des contournements de limites de lecture. Leurs plages déclarées excluent aussi 2.23.0. Databind comporte également [GHSA-mhm7-754m-9p8w](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-mhm7-754m-9p8w), sans CVE ni version corrigée renseignée ; sa plage 2.x déclarée se termine à 2.21.4 et ne couvre pas 2.23.0. Les avis core [CVE-2026-29062](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-6v53-7c9g-w56r) et [CVE-2026-110729](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-vvjm-vc27-799r) visent uniquement 3.x.

Les fonctionnalités polymorphes concernées par certains avis ne sont pas activées par ce dépôt de JAR. La configuration du futur lecteur JSON et les limites de lecture restent une étape de développement distincte.

## Portée de la vérification

L'inspection initiale du 27 octobre 2026 couvre les fichiers locaux, la documentation d'interface, les sources et mentions Raven, les JAR FlatLaf, FlatLaf Extras et JSVG et leurs licences embarquées. Le complément Jackson du 19 novembre 2026 est détaillé dans sa section, avec les versions, empreintes, licences et avis de sécurité consultés. Les JAR ont été lus comme archives, sans exécution. Ces inspections ne sont ni une certification de provenance exhaustive de tout l'historique, ni une validation juridique.

Pour toute distribution autorisée de Gestion+, accompagner les composants des licences et attributions applicables, y compris si l'emballage change. Les permissions propres aux composants tiers restent indépendantes des permissions portant sur l'application originale.
