package org.example;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Unit test for simple App.
 */
public class AppTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AppTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testApp()
    {
        assertTrue( true );
    }

    public void testExtraireDateNomEtCote()
    {
        assertExtractions("2024_03_17_Nom", "17/03/2024", "Nom", "", "");
        assertExtractions("17_03_2024_Nom", "17/03/2024", "Nom", "", "");
        assertExtractions("2024_3_7_Nom", "07/03/2024", "Nom", "", "");
        assertExtractions("7_3_2024_Nom", "07/03/2024", "Nom", "", "");
        assertExtractions("27-30_09_2005_Nom", "27/09/2005 - 30/09/2005", "Nom", "", "");
        assertExtractions("3-7_sep_2005_Nom_C123", "03/09/2005 - 07/09/2005", "Nom", "C123", "");
        assertExtractions("27-30_02_2022_Benkaddour",
                "27/02/2022 - 30/02/2022", "Benkaddour", "", "");
        assertExtractions("2000_sep_3_Nom", "03/09/2000", "Nom", "", "");
        assertExtractions("03_sep_2000_Nom", "03/09/2000", "Nom", "", "");
        assertExtractions("03_2024_Nom", "03/2024", "Nom", "", "");
        assertExtractions("3_2024_Nom_C123", "03/2024", "Nom", "C123", "");
        assertExtractions("11-12_2025_Nom", "11/2025 - 12/2025", "Nom", "", "");
        assertExtractions("nov-déc_2025_Nom", "11/2025 - 12/2025", "Nom", "", "");
        assertExtractions("sep_2000_Nom", "09/2000", "Nom", "", "");
        assertExtractions("FÉV_2000_Nom", "02/2000", "Nom", "", "");
        assertExtractions("jun_2000_Nom", "06/2000", "Nom", "", "");
        assertExtractions("jui_2000_Nom", "07/2000", "Nom", "", "");
        assertExtractions("2024_Nom", "2024", "Nom", "", "");
        assertExtractions("sd_Nom", "unknown", "Nom", "", "");
        assertExtractions("SD_Nom", "unknown", "Nom", "", "");
        assertExtractions(
                "Sd (1969 peut-etre)_Texte Senghor Fondation des arts du monde noir "
                        + "ou l’acropole - L’acropole - Castel de Gorée _MLX C 3995",
                "unknown",
                "Texte Senghor Fondation des arts du monde noir "
                        + "ou l’acropole - L’acropole - Castel de Gorée ",
                "MLX C 3995",
                "");
        assertExtractions("1842-1843_Nom", "1842-1843", "Nom", "", "");
        assertExtractions("1842-1843_Nom_C123", "1842-1843", "Nom", "C123", "");
        assertExtractions("2024_03_17_Nom_C123", "17/03/2024", "Nom", "C123", "");
        assertExtractions("2024_Nom_C123", "2024", "Nom", "C123", "");
        assertExtractions("2024_03_17_Nom___NomAntérieur", "17/03/2024", "Nom", "",
                "NomAntérieur");
        assertExtractions("2024_03_17_Nom_____NomAntérieur_C123", "17/03/2024", "Nom",
                "C123", "NomAntérieur");
        assertExtractions("2024_03_17_Nom___Nom_Antérieur_C123", "17/03/2024", "Nom",
                "C123", "Nom_Antérieur");
        assertFallback("2024_Nom_invalide_suffixe");
        assertFallback("Nom_sans_date");
        assertFallback("2024_19_40_Nom");
        assertFallback("30-32_09_2005_Nom");
        assertFallback("13-14_2025_Nom");
        assertFallback("12-11_2025_Nom");
    }

    public void testRetireExtensionSansPerdreLesPointsDeLaCote()
    {
        String sansExtension = App.retirerExtension("2024_Nom_C.12.3.PDF");
        assertEquals("2024_Nom_C.12.3", sansExtension);
        assertExtractions(sansExtension, "2024", "Nom", "C.12.3", "");
        assertEquals("2024_Nom_C.12.3", App.retirerExtension("2024_Nom_C.12.3"));
    }

    public void testDossierRelatif()
    {
        Path racine = Paths.get("racine").toAbsolutePath().normalize();
        assertEquals("", App.dossierRelatif(racine, racine.resolve("fichier.pdf").toFile()));
        assertEquals("sous-dossier",
                App.dossierRelatif(racine, racine.resolve("sous-dossier")
                        .resolve("fichier.pdf").toFile()));
        Path dossierImbrique = racine.resolve("sous-dossier").resolve("interne");
        assertEquals(racine.relativize(dossierImbrique).toString(),
                App.dossierRelatif(racine, dossierImbrique.resolve("fichier.pdf").toFile()));
    }

    public void testDossierOcrDisponibleIncrementeLeNom() throws IOException
    {
        Path racine = Files.createTempDirectory("leopold-ocr-dossier");
        Path source = racine.resolve("document.pdf");
        Path dossierOcrIncremental = racine.resolve("OCR_1");
        try {
            assertEquals(racine.resolve("OCR"), App.trouverDossierOcr(racine));
            Files.createDirectory(racine.resolve("OCR"));
            assertEquals(racine.resolve("OCR_1"), App.trouverDossierOcr(racine));

            Files.createFile(source);
            File word = App.creerDocumentWord(racine, App.trouverDossierOcr(racine),
                    source.toFile(), "document", 10, "Texte OCR");
            assertEquals(dossierOcrIncremental.resolve("document_SC10_1.docx").toFile(), word);
            assertEquals(racine.resolve("OCR_2"), App.trouverDossierOcr(racine));
        } finally {
            Files.deleteIfExists(dossierOcrIncremental.resolve("document_SC10_1.docx"));
            Files.deleteIfExists(dossierOcrIncremental);
            Files.deleteIfExists(racine.resolve("OCR"));
            Files.deleteIfExists(source);
            Files.deleteIfExists(racine);
        }
    }

    public void testFichierSortieDisponibleIncrementeLeNom() throws IOException
    {
        Path dossier = Files.createTempDirectory("leopold-test");
        String nomBase = dossier.getFileName() + "_resultats";
        try {
            assertEquals(nomBase + ".xlsx",
                    App.trouverFichierSortie(dossier.toFile()).getName());
            Files.createFile(dossier.resolve(nomBase + ".xlsx"));
            assertEquals(nomBase + "_1.xlsx",
                    App.trouverFichierSortie(dossier.toFile()).getName());
            Files.createFile(dossier.resolve(nomBase + "_1.xlsx"));
            assertEquals(nomBase + "_2.xlsx",
                    App.trouverFichierSortie(dossier.toFile()).getName());
        } finally {
            Files.deleteIfExists(dossier.resolve(nomBase + "_1.xlsx"));
            Files.deleteIfExists(dossier.resolve(nomBase + ".xlsx"));
            Files.deleteIfExists(dossier);
        }
    }

    public void testExtractionPdfMixteImageEtTexteDansUnSeulWord() throws IOException
    {
        Path racine = Files.createTempDirectory("leopold-pdf-mixte");
        Path pdfMixte = racine.resolve("document.pdf");
        Path dossierOcr = App.trouverDossierOcr(racine);
        Path word = dossierOcr.resolve("document_SC10.docx");
        try {
            BufferedImage imageTexte = new BufferedImage(1000, 240, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = imageTexte.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, imageTexte.getWidth(), imageTexte.getHeight());
                graphics.setColor(Color.BLACK);
                graphics.setFont(new Font("SansSerif", Font.BOLD, 100));
                graphics.drawString("Bonjour", 50, 165);
            } finally {
                graphics.dispose();
            }

            try (PDDocument document = new PDDocument()) {
                PDPage pageImage = new PDPage();
                document.addPage(pageImage);
                PDImageXObject imagePdf = LosslessFactory.createFromImage(document, imageTexte);
                try (PDPageContentStream contenu =
                             new PDPageContentStream(document, pageImage)) {
                    contenu.drawImage(imagePdf, 50, 500, 500, 120);
                }

                PDPage pageTexte = new PDPage();
                document.addPage(pageTexte);
                try (PDPageContentStream contenu = new PDPageContentStream(document, pageTexte)) {
                    contenu.beginText();
                    contenu.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contenu.newLineAtOffset(50, 700);
                    contenu.showText("Texte integre");
                    contenu.endText();
                }
                document.save(pdfMixte.toFile());
            }

            try (App.MoteurOcr moteur =
                         App.creerMoteurOcr(App.SEUIL_CONFIANCE_OCR_PAR_DEFAUT)) {
                String texte = App.extraireTextePdf(pdfMixte.toFile(), moteur);
                assertNotNull(texte);
                assertTrue("Le texte de la page image doit être OCRisé.",
                        texte.contains("Bonjour"));
                assertTrue("Le texte intégré de la page suivante doit être extrait.",
                        texte.contains("Texte integre"));
                assertTrue("Les pages doivent rester dans leur ordre.",
                        texte.indexOf("Bonjour") < texte.indexOf("Texte integre"));

                File documentWord = App.creerDocumentWord(
                        racine, dossierOcr, pdfMixte.toFile(), "document", 10, texte);
                assertEquals(word.toFile(), documentWord);
                assertTrue(Files.exists(word));
            }
        } finally {
            Files.deleteIfExists(word);
            Files.deleteIfExists(racine.resolve("OCR"));
            Files.deleteIfExists(pdfMixte);
            Files.deleteIfExists(racine);
        }
    }

    public void testCreerDocumentWordEtIncrementeLeNomEnCasDeDoublon() throws IOException
    {
        Path racine = Files.createTempDirectory("leopold-word-test");
        Path dossierSource = racine.resolve("sous-dossier");
        Path source = dossierSource.resolve("document.pdf");
        Path dossierOcr = App.trouverDossierOcr(racine);
        Path dossierWord = dossierOcr;
        try {
            Files.createDirectories(dossierSource);
            Files.createFile(source);

            File word = App.creerDocumentWord(
                    racine, dossierOcr, source.toFile(), "document", 10, "Texte OCR");
            assertEquals(dossierWord.resolve("document_SC10.docx").toFile(), word);
            assertFalse(Files.exists(dossierOcr.resolve("sous-dossier")));
            try (InputStream input = Files.newInputStream(word.toPath());
                 XWPFDocument document = new XWPFDocument(input)) {
                assertEquals("document.pdf", document.getParagraphs().get(0).getText());
                assertEquals("Texte OCR", document.getParagraphs().get(1).getText());
            }

            File doublon = App.creerDocumentWord(
                    racine, dossierOcr, source.toFile(), "document", 10, "Autre texte OCR");
            assertEquals("document_SC10_1.docx", doublon.getName());
        } finally {
            Files.deleteIfExists(dossierWord.resolve("document_SC10.docx"));
            Files.deleteIfExists(dossierWord.resolve("document_SC10_1.docx"));
            Files.deleteIfExists(dossierOcr);
            Files.deleteIfExists(dossierOcr);
            Files.deleteIfExists(source);
            Files.deleteIfExists(dossierSource);
            Files.deleteIfExists(racine);
        }
    }

    public void testCreerDocumentWordTronqueLeTitrePourRespecterLaLimiteExcel()
            throws IOException
    {
        Path racine = Files.createTempDirectory("leopold-word-long");
        Path source = racine.resolve("document.pdf");
        Path dossierOcr = App.trouverDossierOcr(racine);
        StringBuilder titreLong = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            titreLong.append('a');
        }
        File premierWord = null;
        File secondWord = null;
        try {
            Files.createFile(source);
            premierWord = App.creerDocumentWord(
                    racine, dossierOcr, source.toFile(), titreLong.toString(), 10, "Texte OCR");
            String premierLien = racine.relativize(premierWord.toPath()).toString()
                    .replace(File.separatorChar, '/');
            assertEquals(255, premierLien.length());
            assertTrue(premierWord.getName().endsWith("_SC10.docx"));

            secondWord = App.creerDocumentWord(
                    racine, dossierOcr, source.toFile(), titreLong.toString(), 10, "Autre texte");
            String secondLien = racine.relativize(secondWord.toPath()).toString()
                    .replace(File.separatorChar, '/');
            assertEquals(255, secondLien.length());
            assertTrue(secondWord.getName().endsWith("_SC10_1.docx"));
        } finally {
            if (premierWord != null) Files.deleteIfExists(premierWord.toPath());
            if (secondWord != null) Files.deleteIfExists(secondWord.toPath());
            Files.deleteIfExists(dossierOcr);
            Files.deleteIfExists(source);
            Files.deleteIfExists(racine);
        }
    }

    public void testOcrImageDepuisCheminUnicodeAvecModeleFrancais() throws IOException
    {
        Path imagePath = Files.createTempFile("leopold-ocr-épreuve-", ".png");
        try {
            BufferedImage image = new BufferedImage(1000, 240, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.setColor(Color.BLACK);
                graphics.setFont(new Font("SansSerif", Font.BOLD, 100));
                graphics.drawString("Bonjour", 50, 165);
            } finally {
                graphics.dispose();
            }
            ImageIO.write(image, "png", imagePath.toFile());

            try (App.MoteurOcr moteur =
                         App.creerMoteurOcr(App.SEUIL_CONFIANCE_OCR_PAR_DEFAUT)) {
                assertFalse(moteur.extraireTexte(imagePath.toFile()).trim().isEmpty());
                assertFalse(moteur.extraireTexte(imagePath.toFile()).trim().isEmpty());
            }
        } finally {
            Files.deleteIfExists(imagePath);
        }
    }

    public void testFiltreOcrSupprimeUniquementLesMotsSousSeuil() throws IOException
    {
        String tsv = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num"
                + "\tleft\ttop\twidth\theight\tconf\ttext\n"
                + "5\t1\t1\t1\t1\t1\t0\t0\t10\t10\t9.99\tillisible\n"
                + "5\t1\t1\t1\t1\t2\t0\t0\t10\t10\t10\tSENGHOR\n"
                + "5\t1\t1\t1\t2\t1\t0\t0\t10\t10\t85\tRaja\n"
                + "5\t1\t1\t1\t2\t2\t0\t0\t10\t10\t90\tRao.\n";
        assertEquals("SENGHOR\nRaja Rao.", App.MoteurOcr.filtrerTexteTsv(tsv, 10));
        assertEquals("illisible SENGHOR\nRaja Rao.",
                App.MoteurOcr.filtrerTexteTsv(tsv, 0));
        assertEquals("Rao.", App.MoteurOcr.filtrerTexteTsv(tsv, 90));
        assertEquals("", App.MoteurOcr.filtrerTexteTsv(tsv, 100));
        try {
            App.MoteurOcr.filtrerTexteTsv(tsv, -1);
            fail("Un score inférieur à 0 doit être refusé.");
        } catch (IllegalArgumentException expected) {
            // Le score est borné entre 0 et 100.
        }
        try {
            App.MoteurOcr.filtrerTexteTsv(tsv, 101);
            fail("Un score supérieur à 100 doit être refusé.");
        } catch (IllegalArgumentException expected) {
            // Le score est borné entre 0 et 100.
        }
    }

    public void testLienOcrEstRelatifAuDossierRacine() throws IOException
    {
        Path racine = Paths.get("racine").toAbsolutePath().normalize();
        File word = racine.resolve("OCR").resolve("document.docx").toFile();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Cell cellule = workbook.createSheet().createRow(0).createCell(0);
            App.ajouterLienOcr(cellule, workbook, racine, word);

            assertEquals("HYPERLINK(\"OCR/document.docx\",\"Ouvrir\")",
                    cellule.getCellFormula());
        }
    }

    public void testLienOcrEncodeEspacesEtAccentsEtResteValideApresExport() throws IOException
    {
        Path racine = Paths.get("racine").toAbsolutePath().normalize();
        String nomWord = "10_12_1975_Lettre de Senghor à Malraux _MLX C 3995.docx";
        File word = racine.resolve("OCR").resolve(nomWord).toFile();
        String formuleAttendue = "HYPERLINK(\"OCR/" + nomWord + "\",\"Ouvrir\")";
        ByteArrayOutputStream contenu = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Cell cellule = workbook.createSheet().createRow(0).createCell(0);
            App.ajouterLienOcr(cellule, workbook, racine, word);
            assertEquals(formuleAttendue, cellule.getCellFormula());
            workbook.write(contenu);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(contenu.toByteArray()))) {
            assertEquals(formuleAttendue,
                    workbook.getSheetAt(0).getRow(0).getCell(0).getCellFormula());
        }
    }

    public void testLienOcrAccepteUnCheminSuperieurALaLimiteDesFormules() throws IOException
    {
        Path racine = Paths.get("racine").toAbsolutePath().normalize();
        StringBuilder nomWord = new StringBuilder();
        for (int i = 0; i < 246; i++) {
            nomWord.append('a');
        }
        nomWord.append(".docx");
        File word = racine.resolve("OCR").resolve(nomWord.toString()).toFile();
        ByteArrayOutputStream contenu = new ByteArrayOutputStream();
        assertEquals(255, ("OCR/" + nomWord).length());

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Cell cellule = workbook.createSheet().createRow(0).createCell(0);
            App.ajouterLienOcr(cellule, workbook, racine, word);
            assertTrue(cellule.getCellFormula().length() > 255);
            assertTrue(cellule.getCellFormula().contains("\"&\""));
            workbook.write(contenu);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(contenu.toByteArray()))) {
            Cell cellule = workbook.getSheetAt(0).getRow(0).getCell(0);
            assertTrue(cellule.getCellFormula().length() > 255);
            assertTrue(cellule.getCellFormula().contains("\"&\""));
        }
    }

    private void assertExtractions(String titre, String dateAttendue, String nomAttendu,
                                   String coteAttendue, String nomAnterieurAttendu)
    {
        String[] resultat = App.extraireInformationsFichier(titre);
        assertNotNull(resultat);
        assertEquals(dateAttendue, resultat[0]);
        assertEquals(nomAttendu, resultat[1]);
        assertEquals(coteAttendue, resultat[2]);
        assertEquals(nomAnterieurAttendu, resultat[3]);
    }

    private void assertFallback(String titre)
    {
        String[] resultat = App.extraireInformationsFichier(titre);
        assertNotNull(resultat);
        assertNull(resultat[0]);
        assertNull(resultat[1]);
        assertNull(resultat[2]);
        assertNull(resultat[3]);
    }
}
