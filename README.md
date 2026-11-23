# Leopold

Leopold est une application de bureau qui catalogue des documents dans un fichier Excel et peut extraire leur texte avec l’OCR français.

## Fonctionnalités

- Parcourt un dossier et ses sous-dossiers pour répertorier les fichiers PDF et image.
- Crée un inventaire Excel avec le nom du fichier, son dossier et, si le nommage est reconnu, sa date, son titre, sa cote et son nom antérieur.
- Propose l’extraction OCR en français et crée des documents Word contenant le texte extrait, avec des liens depuis le fichier Excel.
- Pour les PDF contenant déjà du texte, extrait ce texte directement ; l’OCR est utilisé pour les pages sans texte intégré.

Formats pris en charge : `.pdf`, `.jpg`, `.jpeg`, `.png`, `.gif`, `.heic` et `.webp`.

## Prérequis

- JDK Java 17 ou supérieur.
- Maven 3 ou supérieur pour compiler le projet.

## Compiler et lancer

Depuis la racine du dépôt :

```sh
mvn -DskipTests package
java -jar target/Leopold-1.0-SNAPSHOT.jar
```

Au démarrage, sélectionnez le dossier à traiter. Le dossier et son contenu ne doivent pas dépasser 2 Go. Choisissez ensuite si vous souhaitez lancer l’OCR. Le seuil de confiance proposé par défaut est 10 ; il peut être réglé entre 0 et 100. Annuler la saisie du seuil poursuit l’export sans OCR.

## Résultats

Le fichier Excel est créé à la racine du dossier sélectionné sous le nom `<nom-du-dossier>_resultats.xlsx`. Si ce nom existe déjà, un numéro est ajouté (`_1`, `_2`, etc.). Le classeur contient notamment des colonnes pour le titre, la date, la cote, le dossier et les liens vers les documents OCR ; les autres champs peuvent être complétés dans Excel.

Lorsque l’OCR produit du texte, Leopold crée un fichier Word dans un sous-dossier `OCR` du dossier sélectionné et ajoute un lien vers ce document dans le classeur. Si ce sous-dossier existe déjà, un nom disponible tel que `OCR_1` est utilisé. Les fichiers d’origine ne sont pas déplacés.

## Nommage des fichiers

Leopold tente d’extraire certaines métadonnées à partir du nom du fichier. La date doit être placée au début, suivie d’un `_`, puis du titre et éventuellement de la cote. Les formats de date et de période reconnus sont :

- Année : `AAAA` — `1964_Titre.pdf`
- Période d’années : `AAAA-AAAA` — `1964-1965_Titre.pdf`
- Mois et année : `MM_AAAA` ou `MMM_AAAA` — `12_1964_Titre.pdf`, `dec_1964_Titre.pdf`
- Période de mois : `MM-MM_AAAA` ou `MMM-MMM_AAAA` — `01-03_1964_Titre.pdf`, `jan-mar_1964_Titre.pdf`
- Jour, mois et année : `JJ_MOIS_AAAA` ou `AAAA_MOIS_JJ` — `10_dec_1964_Titre.pdf`, `1964_12_10_Titre.pdf`
- Période de jours dans un même mois : `JJ-JJ_MOIS_AAAA` — `10-12_dec_1964_Titre.pdf`
- Date inconnue : `sd` suivi éventuellement d’un texte — `sd_Titre.pdf` (la date apparaît comme « unknown »)

Les mois peuvent être numériques (de 1 à 12) ou écrits avec ces abréviations : `jan`, `feb`/`fev`/`fév`, `mar`, `apr`/`avr`, `may`/`mai`, `jun`, `jui`/`jul`, `aug`/`aou`/`aoû`, `sep`, `oct`, `nov`, `dec`/`déc`.

Les anciens noms peuvent être indiqués après un double underscore :

```text
1964_12_10_Lettre__Ancien nom_C3995.pdf
```

Les fichiers dont le nom ne suit pas un de ces formats sont tout de même répertoriés, mais leurs métadonnées ne sont pas automatiquement extraites.

## Créer une application avec son icône

Les scripts `jpackage` créent une application pour le système sur lequel ils sont exécutés. Il faut donc effectuer le packaging sur le système cible.

### macOS

Depuis la racine du dépôt, sur un Mac avec JDK 17+ et Maven :

```sh
mvn -DskipTests package
chmod +x scripts/package-macos.sh
./scripts/package-macos.sh
```

L’application est générée dans `target/app-image/Leopold.app`, avec l’icône `packaging/macos/Leopold.icns`.

### Windows

Depuis PowerShell, sur Windows avec JDK 17+ et Maven :

```powershell
.\scripts\package-windows.ps1
```

L’application est générée dans `target\app-image\Leopold`, avec l’icône `packaging\windows\Leopold.ico`.
