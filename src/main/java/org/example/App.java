package org.example;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.tesseract.TessBaseAPI;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bytedeco.leptonica.global.leptonica.pixDestroy;
import static org.bytedeco.leptonica.global.leptonica.pixReadMem;

/**
 * Hello world!
 *
 */
public class App 
{
    private static final int LONGUEUR_MAX_LITTERAL_FORMULE = 200;
    private static final int LONGUEUR_MAX_LIEN_EXCEL = 255;
    private static final String MOIS_TEXTE =
            "(?iu:jan|feb|fev|fév|mar|apr|avr|may|mai|jun|jui|jul|aug|aou|aoû|sep|oct|nov|dec|déc)";
    private static final String MOIS = "(?:\\d{1,2}|" + MOIS_TEXTE + ")";
    private static final Pattern NOM_FICHIER_PATTERN = Pattern.compile(
            "^(\\d{1,2}-\\d{1,2}_" + MOIS + "_\\d{4}|\\d{4}-\\d{4}|\\d{4}_" + MOIS
                    + "_\\d{1,2}|\\d{1,2}_" + MOIS
                    + "_\\d{4}|" + MOIS + "-" + MOIS + "_\\d{4}|" + MOIS + "_\\d{4}"
                    + "|\\d{4}|(?i:sd[^_]*))_(.+)$");
    private static final Pattern SEPARATEUR_NOM_ANTERIEUR = Pattern.compile("_{2,}");
    private static final String[] EXTENSIONS_SUPPORTES =
            {".jpg", ".jpeg", ".png", ".gif", ".heic", ".webp", ".pdf"};
    private static final float HAUTEUR_LIGNE = 18.0f;
    static final int SEUIL_CONFIANCE_OCR_PAR_DEFAUT = 10;

    public static void main(String[] args) {
        InterfaceUtilisateur.demarrer();
    }

    static ResultatExport exporter(File dossier, boolean extraireOcr, int seuilConfianceOcr)
            throws IOException {
        verifierSeuilConfianceOcr(seuilConfianceOcr);
        File sortie = trouverFichierSortie(dossier);

        File[] fichiers;
        try {
            fichiers = trouverFichiers(dossier);
        } catch (IOException e) {
            throw new IOException("Impossible de lire le dossier : " + e.getMessage(), e);
        }

        Arrays.sort(fichiers);

        String nomDossier = dossier.getName();
        Path cheminRacine = dossier.toPath().toAbsolutePath().normalize();
        Path dossierOcr = extraireOcr ? trouverDossierOcr(cheminRacine) : null;
        MoteurOcr moteurOcr;
        try {
            moteurOcr = extraireOcr && fichiers.length > 0
                    ? creerMoteurOcr(seuilConfianceOcr) : null;
        } catch (IOException | UnsatisfiedLinkError e) {
            throw new IOException("Impossible de préparer l’OCR français : "
                    + e.getMessage(), e);
        }

        int ecrits = 0;
        int references = 0;
        boolean moteurOcrIndisponible = false;
        List<String> erreursExtraction = new ArrayList<>();

        try (MoteurOcr moteur = moteurOcr;
             XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Documents");

            Row entete = sheet.createRow(0);
            entete.setHeightInPoints(HAUTEUR_LIGNE);
            String[] colonnes = {
                    "Typologie",
                    "Référence",
                    "Nom du fichier",
                    "Nom antérieur",
                    "Titre",
                    "Date",
                    "Cote",
                    "Description",
                    "Localisation",
                    "Fonds",
                    "Dossier",
                    "Lien Senghoriana",
                    "Lien Camscanner",
                    "Lien OCR",
                    "Maxqda",
                    "Observations"
            };
            XSSFCellStyle styleEntete = wb.createCellStyle();
            XSSFColor accent6 = new XSSFColor();
            accent6.setTheme(9);
            styleEntete.setFillForegroundColor(accent6);
            styleEntete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font policeEntete = wb.createFont();
            policeEntete.setBold(true);
            policeEntete.setColor(IndexedColors.BLACK.getIndex());
            styleEntete.setFont(policeEntete);
            appliquerBordures(styleEntete);

            CellStyle styleCellule = wb.createCellStyle();
            appliquerBordures(styleCellule);

            CellStyle styleLien = wb.createCellStyle();
            styleLien.cloneStyleFrom(styleCellule);
            Font policeLien = wb.createFont();
            policeLien.setColor(IndexedColors.BLUE.getIndex());
            policeLien.setUnderline(Font.U_SINGLE);
            styleLien.setFont(policeLien);

            for (int i = 0; i < colonnes.length; i++) {
                Cell cellule = entete.createCell(i);
                cellule.setCellValue(colonnes[i]);
                cellule.setCellStyle(styleEntete);
            }

            int ligne = 1;
            for (File f : fichiers) {
                String titre = retirerExtension(f.getName());

                String[] parts = extraireInformationsFichier(titre);
                Row row = sheet.createRow(ligne++);
                row.setHeightInPoints(HAUTEUR_LIGNE);
                for (int i = 0; i < colonnes.length; i++) {
                    row.createCell(i).setCellStyle(styleCellule);
                }
                row.getCell(2).setCellValue(titre);
                String sousDossier = dossierRelatif(cheminRacine, f);
                if (!sousDossier.isEmpty()) {
                    row.getCell(10).setCellValue(sousDossier);
                }
                if (parts[0] != null) {
                    row.getCell(1).setCellValue(nomDossier + "_" + ++references);
                    row.getCell(4).setCellValue(parts[1]);
                    if (!parts[3].isEmpty()) {
                        row.getCell(3).setCellValue(parts[3]);
                    }
                    row.getCell(5).setCellValue(parts[0]);
                    row.getCell(6).setCellValue(parts[2]);
                }

                if (extraireOcr && (estPdf(f) || !moteurOcrIndisponible)) {
                    try {
                        String texte = extraireTexte(f, moteur);
                        if (texte != null && !texte.trim().isEmpty()) {
                            File fichierWord = creerDocumentWord(
                                    cheminRacine, dossierOcr, f, titre,
                                    seuilConfianceOcr, texte);
                            Cell lienCellule = row.getCell(13);
                            ajouterLienOcr(lienCellule, wb, cheminRacine, fichierWord);
                            lienCellule.setCellStyle(styleLien);
                        }
                    } catch (IOException e) {
                        erreursExtraction.add(f.getName() + " : " + e.getMessage());
                    } catch (UnsatisfiedLinkError e) {
                        erreursExtraction.add("Moteur OCR indisponible : " + e.getMessage());
                        moteurOcrIndisponible = true;
                    }
                }
                ecrits++;
            }

            sheet.setAutoFilter(new CellRangeAddress(0, ligne - 1, 0, colonnes.length - 1));
            for (int i = 0; i < colonnes.length; i++) {
                sheet.autoSizeColumn(i);
                int largeurMinimale = (colonnes[i].length() + 4) * 256;
                if (sheet.getColumnWidth(i) < largeurMinimale) {
                    sheet.setColumnWidth(i, largeurMinimale);
                }
            }

            try (FileOutputStream out = new FileOutputStream(sortie)) {
                wb.write(out);
            }
        } catch (IOException e) {
            throw new IOException("Impossible de créer le fichier Excel : "
                    + e.getMessage(), e);
        }

        String message = ecrits + " documents exportés dans " + sortie.getName();
        if (!erreursExtraction.isEmpty()) {
            message += "\n\nExtraction impossible pour "
                    + erreursExtraction.size() + " fichier(s) :";
            int limite = Math.min(erreursExtraction.size(), 5);
            for (int i = 0; i < limite; i++) {
                message += "\n- " + erreursExtraction.get(i);
            }
            if (erreursExtraction.size() > limite) {
                message += "\n- ...";
            }
        }
        return new ResultatExport(message, !erreursExtraction.isEmpty());
    }

    static final class ResultatExport {
        final String message;
        final boolean erreursExtraction;

        ResultatExport(String message, boolean erreursExtraction) {
            this.message = message;
            this.erreursExtraction = erreursExtraction;
        }
    }

    private static void appliquerBordures(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static boolean estPdf(File fichier) {
        return fichier.getName().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    static MoteurOcr creerMoteurOcr(int seuilConfianceOcr) throws IOException {
        verifierSeuilConfianceOcr(seuilConfianceOcr);
        Path donneesOcr = Files.createTempDirectory("leopold-tessdata");
        donneesOcr.toFile().deleteOnExit();
        Path modeleFrancais = donneesOcr.resolve("fra.traineddata");
        try (InputStream modele = App.class.getResourceAsStream("/tessdata/fra.traineddata")) {
            if (modele == null) {
                throw new IOException("Le modèle fra.traineddata est absent de l'application.");
            }
            Files.copy(modele, modeleFrancais);
        }
        modeleFrancais.toFile().deleteOnExit();

        return new MoteurOcr(donneesOcr, seuilConfianceOcr);
    }

    private static void verifierSeuilConfianceOcr(int seuilConfianceOcr) {
        if (seuilConfianceOcr < 0 || seuilConfianceOcr > 100) {
            throw new IllegalArgumentException("Le score d’exclusion OCR doit être entre 0 et 100.");
        }
    }

    private static String extraireTexte(File fichier, MoteurOcr moteurOcr) throws IOException {
        if (estPdf(fichier)) return extraireTextePdf(fichier, moteurOcr);
        return moteurOcr.extraireTexte(fichier);
    }

    static String extraireTextePdf(File fichier, MoteurOcr moteurOcr) throws IOException {
        try (PDDocument document = Loader.loadPDF(fichier)) {
            PDFTextStripper extracteur = new PDFTextStripper();
            PDFRenderer rendu = new PDFRenderer(document);
            StringBuilder texteComplet = new StringBuilder();
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                extracteur.setStartPage(pageIndex + 1);
                extracteur.setEndPage(pageIndex + 1);
                String textePage = extracteur.getText(document).trim();
                if (textePage.isEmpty()) {
                    BufferedImage imagePage = rendu.renderImageWithDPI(pageIndex, 300,
                            ImageType.GRAY);
                    textePage = moteurOcr.extraireTexte(imagePage).trim();
                }
                if (!textePage.isEmpty()) {
                    if (texteComplet.length() > 0) texteComplet.append("\n\n");
                    texteComplet.append(textePage);
                }
            }
            return texteComplet.length() == 0 ? null : texteComplet.toString();
        }
    }

    static Path trouverDossierOcr(Path racine) {
        Path dossierOcr = racine.toAbsolutePath().normalize().resolve("OCR");
        int numero = 1;
        while (Files.exists(dossierOcr)) {
            dossierOcr = racine.toAbsolutePath().normalize().resolve("OCR_" + numero++);
        }
        return dossierOcr;
    }

    static File creerDocumentWord(Path racine, Path dossierOcr, File source,
                                  String nomSansExtension, int seuilConfianceOcr,
                                  String texte) throws IOException {
        verifierSeuilConfianceOcr(seuilConfianceOcr);
        Path racineAbsolue = racine.toAbsolutePath().normalize();
        Path dossierOcrAbsolu = dossierOcr.toAbsolutePath().normalize();
        Path repertoireWord = dossierOcrAbsolu;

        String nomDossierOcr = dossierOcrAbsolu.getFileName().toString();
        String suffixeOcr = nomDossierOcr.startsWith("OCR_")
                ? nomDossierOcr.substring("OCR".length()) : "";
        String suffixeWord = "_SC" + seuilConfianceOcr + suffixeOcr;
        String extensionWord = ".docx";
        String prefixeLien = prefixeLienRelatif(racineAbsolue, repertoireWord);
        Path cheminWord;
        int numero = 0;
        while (true) {
            String suffixeDoublon = numero == 0 ? "" : "_" + numero;
            int longueurTitreMaximale = LONGUEUR_MAX_LIEN_EXCEL - prefixeLien.length()
                    - suffixeWord.length() - suffixeDoublon.length() - extensionWord.length();
            if (longueurTitreMaximale < 0) {
                throw new IOException("Impossible de respecter la limite Excel du lien OCR.");
            }

            String titreTronque = tronquerTitre(nomSansExtension, longueurTitreMaximale);
            String nomWord = titreTronque + suffixeWord + suffixeDoublon + extensionWord;
            cheminWord = repertoireWord.resolve(nomWord);
            if (!Files.exists(cheminWord)) {
                break;
            }
            numero++;
        }

        String cheminLien = cheminLienRelatif(racineAbsolue, cheminWord);
        if (cheminLien.length() > LONGUEUR_MAX_LIEN_EXCEL) {
            throw new IOException("Impossible de respecter la limite Excel du lien OCR.");
        }
        Files.createDirectories(repertoireWord);

        try (XWPFDocument document = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(cheminWord.toFile())) {
            XWPFParagraph titre = document.createParagraph();
            XWPFRun runTitre = titre.createRun();
            runTitre.setBold(true);
            runTitre.setText(source.getName());

            XWPFParagraph paragraphe = document.createParagraph();
            XWPFRun runTexte = paragraphe.createRun();
            String[] lignes = texte.split("\\R", -1);
            for (int i = 0; i < lignes.length; i++) {
                if (i > 0) runTexte.addBreak();
                runTexte.setText(lignes[i]);
            }
            document.write(out);
        }
        return cheminWord.toFile();
    }

    private static String tronquerTitre(String titre, int longueurMaximale) {
        int fin = Math.min(titre.length(), longueurMaximale);
        if (fin < titre.length() && fin > 0 && Character.isHighSurrogate(titre.charAt(fin - 1))) {
            fin--;
        }
        return titre.substring(0, fin);
    }

    private static String prefixeLienRelatif(Path racine, Path repertoire) {
        String prefixe = cheminLienRelatif(racine, repertoire);
        return prefixe.isEmpty() ? "" : prefixe + "/";
    }

    private static String cheminLienRelatif(Path racine, Path cible) {
        return racine.toAbsolutePath().normalize()
                .relativize(cible.toAbsolutePath().normalize())
                .toString().replace(File.separatorChar, '/');
    }

    static final class MoteurOcr implements AutoCloseable {
        private final TessBaseAPI api;
        private final int seuilConfianceOcr;

        private MoteurOcr(Path donneesOcr, int seuilConfianceOcr) throws IOException {
            api = new TessBaseAPI();
            this.seuilConfianceOcr = seuilConfianceOcr;
            if (api.Init(donneesOcr.toString(), "fra") != 0) {
                api.close();
                throw new IOException("Tesseract n’a pas pu charger le modèle français.");
            }
        }

        String extraireTexte(File fichier) throws IOException {
            return extraireTexte(Files.readAllBytes(fichier.toPath()));
        }

        String extraireTexte(BufferedImage imagePage) throws IOException {
            ByteArrayOutputStream imagePng = new ByteArrayOutputStream();
            if (!ImageIO.write(imagePage, "png", imagePng)) {
                throw new IOException("Impossible d’encoder la page PDF pour l’OCR.");
            }
            return extraireTexte(imagePng.toByteArray());
        }

        private String extraireTexte(byte[] contenuImage) throws IOException {
            PIX image = pixReadMem(contenuImage, contenuImage.length);
            if (image == null || image.isNull()) {
                throw new IOException("Format d’image non pris en charge.");
            }
            return extraireTexte(image);
        }

        private String extraireTexte(PIX image) throws IOException {
            try {
                api.SetImage(image);
                BytePointer texte = api.GetTSVText(0);
                if (texte == null || texte.isNull()) return "";
                try {
                    return filtrerTexteTsv(texte.getString(), seuilConfianceOcr);
                } finally {
                    texte.deallocate();
                }
            } finally {
                api.Clear();
                pixDestroy(image);
            }
        }

        static String filtrerTexteTsv(String tsv, int seuilConfianceOcr) throws IOException {
            verifierSeuilConfianceOcr(seuilConfianceOcr);
            StringBuilder texte = new StringBuilder();
            StringBuilder ligne = new StringBuilder();
            String cleLigneCourante = null;

            for (String ligneTsv : tsv.split("\\R")) {
                String[] champs = ligneTsv.split("\\t", -1);
                if (champs.length == 0 || !"5".equals(champs[0])) continue;
                if (champs.length < 12) {
                    throw new IOException("Résultat TSV OCR incomplet.");
                }

                String cleLigne = champs[2] + ":" + champs[3] + ":" + champs[4];
                if (cleLigneCourante != null && !cleLigne.equals(cleLigneCourante)) {
                    ajouterLigneOcr(texte, ligne);
                }
                cleLigneCourante = cleLigne;

                float confiance;
                try {
                    confiance = Float.parseFloat(champs[10]);
                } catch (NumberFormatException e) {
                    throw new IOException("Score de confiance OCR invalide.", e);
                }
                if (confiance < seuilConfianceOcr || champs[11].isEmpty()) continue;

                if (ligne.length() > 0) ligne.append(' ');
                ligne.append(champs[11]);
            }

            ajouterLigneOcr(texte, ligne);
            return texte.toString();
        }

        private static void ajouterLigneOcr(StringBuilder texte, StringBuilder ligne) {
            if (ligne.length() == 0) return;
            if (texte.length() > 0) texte.append('\n');
            texte.append(ligne);
            ligne.setLength(0);
        }

        @Override
        public void close() {
            api.End();
            api.close();
        }
    }

    static void ajouterLienOcr(Cell cellule, XSSFWorkbook wb, Path racine, File fichierWord)
            throws IOException {
        String cheminLien = cheminLienRelatif(racine, fichierWord.toPath());
        if (cheminLien.length() > LONGUEUR_MAX_LIEN_EXCEL) {
            throw new IOException("Le chemin du lien OCR dépasse la limite Excel de 255 caractères.");
        }
        String formule = "HYPERLINK(" + formaterLitteralFormule(cheminLien)
                + ",\"Ouvrir\")";
        cellule.setCellFormula(formule);
        wb.setForceFormulaRecalculation(true);
    }

    private static String formaterLitteralFormule(String valeur) {
        StringBuilder formule = new StringBuilder();
        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < valeur.length(); i++) {
            char caractere = valeur.charAt(i);
            int longueurEchappee = caractere == '"' ? 2 : 1;
            if (segment.length() + longueurEchappee > LONGUEUR_MAX_LITTERAL_FORMULE) {
                ajouterSegmentFormule(formule, segment);
                segment.setLength(0);
            }
            if (caractere == '"') {
                segment.append("\"\"");
            } else {
                segment.append(caractere);
            }
        }
        ajouterSegmentFormule(formule, segment);
        return formule.toString();
    }

    private static void ajouterSegmentFormule(StringBuilder formule, StringBuilder segment) {
        if (formule.length() > 0) {
            formule.append('&');
        }
        formule.append('"').append(segment).append('"');
    }

    static File trouverFichierSortie(File dossier) {
        String nomDossier = dossier.getName();
        if (nomDossier.isEmpty()) {
            nomDossier = "dossier";
        }
        String nomBase = nomDossier + "_resultats";
        File sortie = new File(dossier, nomBase + ".xlsx");
        int numero = 1;
        while (sortie.exists()) {
            sortie = new File(dossier, nomBase + "_" + numero++ + ".xlsx");
        }
        return sortie;
    }

    private static File[] trouverFichiers(File racine) throws IOException {
        List<File> fichiers = new ArrayList<>();
        Files.walkFileTree(racine.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path fichier, BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && estFormatSupporte(fichier)) {
                    fichiers.add(fichier.toFile());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return fichiers.toArray(new File[0]);
    }

    private static boolean estFormatSupporte(Path fichier) {
        String nom = fichier.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS_SUPPORTES) {
            if (nom.endsWith(extension)) return true;
        }
        return false;
    }

    static String retirerExtension(String nomFichier) {
        String nomMinuscule = nomFichier.toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS_SUPPORTES) {
            if (nomMinuscule.endsWith(extension) && nomFichier.length() > extension.length()) {
                return nomFichier.substring(0, nomFichier.length() - extension.length());
            }
        }
        return nomFichier;
    }

    static String dossierRelatif(Path racine, File fichier) {
        Path cheminRacine = racine.toAbsolutePath().normalize();
        Path dossierFichier = fichier.toPath().toAbsolutePath().normalize().getParent();
        return cheminRacine.equals(dossierFichier)
                ? ""
                : cheminRacine.relativize(dossierFichier).toString();
    }

    static String[] extraireInformationsFichier(String nomFichier) {
        Matcher matcher = NOM_FICHIER_PATTERN.matcher(nomFichier);
        if (!matcher.matches()) return new String[] {null, null, null, null};

        String suffixe = matcher.group(2);
        String nom;
        String nomAnterieur = "";
        String cote = "";
        Matcher separateur = SEPARATEUR_NOM_ANTERIEUR.matcher(suffixe);
        if (separateur.find()) {
            nom = suffixe.substring(0, separateur.start());
            String anterieurEtCote = suffixe.substring(separateur.end());
            if (nom.isEmpty() || anterieurEtCote.isEmpty()) {
                return new String[] {null, null, null, null};
            }

            int separateurCote = anterieurEtCote.lastIndexOf('_');
            if (separateurCote >= 0) {
                nomAnterieur = anterieurEtCote.substring(0, separateurCote);
                cote = anterieurEtCote.substring(separateurCote + 1);
                if (nomAnterieur.isEmpty() || cote.isEmpty()) {
                    return new String[] {null, null, null, null};
                }
            } else {
                nomAnterieur = anterieurEtCote;
            }
        } else {
            String[] nomEtCote = suffixe.split("_", -1);
            if (nomEtCote.length > 2 || nomEtCote[0].isEmpty()) {
                return new String[] {null, null, null, null};
            }
            nom = nomEtCote[0];
            cote = nomEtCote.length == 2 ? nomEtCote[1] : "";
        }

        String date = matcher.group(1);
        if (date.regionMatches(true, 0, "sd", 0, 2)) {
            date = "unknown";
        } else if (date.contains("_")) {
            date = normaliserDate(date);
            if (date == null) return new String[] {null, null, null, null};
        }
        return new String[] {date, nom, cote, nomAnterieur};
    }

    private static String normaliserDate(String date) {
        String[] dateParts = date.split("_", -1);
        try {
            if (dateParts.length == 2) {
                int annee = Integer.parseInt(dateParts[1]);
                if (annee < 1) return null;
                String[] moisParts = dateParts[0].split("-", -1);
                if (moisParts.length == 2) {
                    int debut = moisNumero(moisParts[0]);
                    int fin = moisNumero(moisParts[1]);
                    if (debut < 1 || debut > 12 || fin < debut || fin > 12) return null;
                    LocalDate.of(annee, debut, 1);
                    return deuxChiffres(debut) + "/" + dateParts[1] + " - "
                            + deuxChiffres(fin) + "/" + dateParts[1];
                }
                if (moisParts.length != 1) return null;
                int mois = moisNumero(moisParts[0]);
                LocalDate.of(annee, mois, 1);
                return deuxChiffres(mois) + "/" + dateParts[1];
            }
            if (dateParts.length != 3) return null;

            String jour;
            String moisTexte;
            String anneeTexte;
            if (dateParts[0].length() == 4) {
                anneeTexte = dateParts[0];
                moisTexte = dateParts[1];
                jour = dateParts[2];
            } else {
                jour = dateParts[0];
                moisTexte = dateParts[1];
                anneeTexte = dateParts[2];
            }

            int mois = moisNumero(moisTexte);
            int annee = Integer.parseInt(anneeTexte);
            if (annee < 1 || mois < 1 || mois > 12) return null;
            if (jour.contains("-")) {
                String[] jours = jour.split("-", -1);
                if (jours.length != 2) return null;
                int debut = Integer.parseInt(jours[0]);
                int fin = Integer.parseInt(jours[1]);
                if (debut < 1 || debut > 31 || fin < debut || fin > 31) return null;
                return formaterDate(debut, mois, anneeTexte) + " - "
                        + formaterDate(fin, mois, anneeTexte);
            }

            int jourNumero = Integer.parseInt(jour);
            LocalDate.of(annee, mois, jourNumero);
            return formaterDate(jourNumero, mois, anneeTexte);
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static int moisNumero(String mois) {
        switch (mois.toLowerCase(Locale.ROOT)) {
            case "jan":
                return 1;
            case "feb":
            case "fev":
            case "fév":
                return 2;
            case "mar":
                return 3;
            case "apr":
            case "avr":
                return 4;
            case "may":
            case "mai":
                return 5;
            case "jun":
                return 6;
            case "jui":
            case "jul":
                return 7;
            case "aug":
            case "aou":
            case "aoû":
                return 8;
            case "sep":
                return 9;
            case "oct":
                return 10;
            case "nov":
                return 11;
            case "dec":
            case "déc":
                return 12;
            default:
                return Integer.parseInt(mois);
        }
    }

    private static String formaterDate(int jour, int mois, String annee) {
        return deuxChiffres(jour) + "/" + deuxChiffres(mois) + "/" + annee;
    }

    private static String deuxChiffres(int valeur) {
        return valeur < 10 ? "0" + valeur : Integer.toString(valeur);
    }
}
