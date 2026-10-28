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
| Glisser vers le haut | menu : minuteur, prénom, mode nuit, son |

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
