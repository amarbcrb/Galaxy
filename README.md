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
| Tap à deux doigts | il t'écoute (sans fenêtre Google), avec des petites barres qui bougent |
| Appui de deux doigts pendant 1 s | menu secret pour lui faire faire ce que tu veux |

L'écran suit l'orientation du téléphone (portrait ou paysage).

## Voix et caméra

- **Commandes vocales** (micro) : « Dors », « Réveille-toi », « Rigole », « Souris »,
  « Fâche-toi », « Triste », « Je t'aime », « Surprise », « Tourne », « Tu as faim ? »,
  « Quelle heure est-il ? », « Minuteur 5 minutes », « Stop ».
- **Mains en l'air !** : il a peur et tremble. Puis « Pan ! » (ou un tap sur l'écran) : l'écran
  a l'air cassé, Galaxy est K.O. avec des yeux en X, puis tout se répare.
- **Couleur des yeux** : « Change la couleur de tes yeux », « Yeux rouges », « Mets tes yeux en
  violet »... ou dans le menu.
- **Suivi du regard** (caméra frontale) : il te suit des yeux et il est content de te voir
  quand tu reviens. Rien n'est affiché ni enregistré.

Galaxy répond avec des bips de robot plutôt qu'avec des mots (réglable dans le menu).
Quand il est triste, il pleure ; quand il a le tournis, ses yeux tournent en spirale et des étoiles
tournent autour de sa tête.

Les deux s'arrêtent la nuit et se coupent dans le menu. Ce que Galaxy entend s'affiche en bas de
l'écran. Si l'écoute continue ne marche pas sur un téléphone, le bouton « Parler à Galaxy » du menu
ouvre la reconnaissance vocale d'Android.

## Tour de magie

1. Dans le menu secret (deux doigts 1 s), section « Tour de magie », choisis la carte que tu vas
   forcer (ou un nombre).
2. Fais ton tour, puis dis : « Vas-y Galaxy, dis-lui c'est quoi sa carte ».
3. Galaxy réfléchit (roulement de tambour), puis montre la carte à l'écran et l'annonce.

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
