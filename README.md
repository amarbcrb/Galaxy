# Galaxy

Galaxy est un compagnon virtuel pour Android : le téléphone devient la tête d'un petit robot
aux yeux lumineux qui réagit au toucher, aux mouvements, à l'heure et à la batterie.

## Installer

Télécharger le dernier APK : https://github.com/amarbcrb/Galaxy/releases/latest/download/galaxy.apk

## Jouer avec Galaxy

| Geste | Réaction |
|---|---|
| Caresse (va-et-vient du doigt) | content, puis amoureux |
| Tap | il sursaute |
| Plein de taps rapides | il se fâche |
| Appui long | il rit |
| Double-tap | il affiche et dit l'heure |
| Secouer | il a le tournis |
| Incliner | ses yeux glissent vers le bas |
| Poser à plat 1 min ou retourner | il s'endort |
| Tourner vite le téléphone | sa tête tourne, et un tour complet lui donne le tournis |
| Glisser vers le haut | menu : minuteur, prénom, mode nuit, son, micro, caméra |
| Appui de deux doigts pendant 1 s | menu secret pour lui faire faire ce que tu veux |

L'écran suit l'orientation du téléphone (portrait ou paysage).

## Voix et caméra

- **Commandes vocales** (micro) : « Dors », « Réveille-toi », « Rigole », « Souris »,
  « Fâche-toi », « Triste », « Je t'aime », « Surprise », « Tourne », « Tu as faim ? »,
  « Quelle heure est-il ? », « Minuteur 5 minutes », « Stop ».
- **Suivi du regard** (caméra frontale) : il te suit des yeux et il est content de te voir
  quand tu reviens. Rien n'est affiché ni enregistré.

Les deux s'arrêtent la nuit et se coupent dans le menu.

Il vit aussi tout seul : il cligne des yeux, regarde autour de lui, baille après 2 minutes sans
attention et s'endort après 5. Il dit bonjour le matin, bonne nuit le soir, a faim quand la
batterie passe sous 20 % et reste allumé quand il est en charge.

## Code

- `core/` : la logique de Galaxy en Kotlin pur (cerveau, formes des yeux, détection des gestes
  et des mouvements, sons). Testable sans Android : `cd core && gradle test`.
- `app/` : l'appli Android (Jetpack Compose) qui dessine le visage et branche les capteurs,
  la batterie, la voix et les sons sur le cerveau.

GitHub Actions lance les tests, compile l'APK et le publie dans les Releases à chaque modification
de la branche principale.
