package org.example;

import com.formdev.flatlaf.FlatLightLaf;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Image;
import java.awt.Taskbar;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

final class InterfaceUtilisateur {
    private InterfaceUtilisateur() {
    }

    static void demarrer() {
        SwingUtilities.invokeLater(InterfaceUtilisateur::afficherFenetrePrincipale);
    }

    private static void afficherFenetrePrincipale() {
        Image icone = chargerIconeApplication();
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (UnsupportedLookAndFeelException e) {
            throw new IllegalStateException("Impossible de charger FlatLaf.", e);
        }

        appliquerIconeBarreTaches(icone);
        JFrame fenetreParente = new JFrame();
        fenetreParente.setIconImage(icone);
        fenetreParente.setLocationRelativeTo(null);

        JFileChooser choixDossier = new JFileChooser();
        choixDossier.setDialogTitle("Choisir le dossier à lire");
        choixDossier.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (choixDossier.showOpenDialog(fenetreParente) != JFileChooser.APPROVE_OPTION) {
            fenetreParente.dispose();
            return;
        }
        File dossier = choixDossier.getSelectedFile();

        Object[] optionsOcr = {"Oui", "Non"};
        int choixOcr = JOptionPane.showOptionDialog(
                fenetreParente,
                "Voulez-vous extraire les textes OCR et créer les fichiers Word ?",
                "Extraction OCR",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                optionsOcr,
                optionsOcr[1]);
        boolean extraireOcr = choixOcr == 0;
        int seuilConfianceOcr = App.SEUIL_CONFIANCE_OCR_PAR_DEFAUT;
        if (extraireOcr) {
            Integer seuilSaisi = demanderSeuilConfianceOcr(fenetreParente);
            if (seuilSaisi == null) {
                extraireOcr = false;
            } else {
                seuilConfianceOcr = seuilSaisi;
            }
        }
        afficherProgression(fenetreParente, dossier, extraireOcr, seuilConfianceOcr);
    }

    private static Image chargerIconeApplication() {
        try (InputStream icone = InterfaceUtilisateur.class.getResourceAsStream(
                "/org/example/Leopold.png")) {
            if (icone == null) {
                throw new IllegalStateException("Ressource d’icône introuvable.");
            }
            Image image = ImageIO.read(icone);
            if (image == null) {
                throw new IllegalStateException("La ressource d’icône est illisible.");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de charger l’icône de Leopold.", e);
        }
    }

    private static void appliquerIconeBarreTaches(Image icone) {
        if (Taskbar.isTaskbarSupported()) {
            Taskbar barreTaches = Taskbar.getTaskbar();
            if (barreTaches.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                barreTaches.setIconImage(icone);
            }
        }
    }

    private static Integer demanderSeuilConfianceOcr(JFrame fenetreParente) {
        while (true) {
            Object saisie = JOptionPane.showInputDialog(
                    fenetreParente,
                    "Exclure les mots dont le score est inférieur à :\n"
                            + "Saisissez une valeur entière entre 0 et 100.\n"
                            + "Annuler poursuit l’export sans OCR.",
                    "Seuil d’exclusion OCR",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    null,
                    String.valueOf(App.SEUIL_CONFIANCE_OCR_PAR_DEFAUT));
            if (saisie == null) return null;

            Integer score = null;
            try {
                score = Integer.valueOf(saisie.toString().trim());
            } catch (NumberFormatException e) {
                // Le message de validation ci-dessous explique la valeur attendue.
            }
            if (score != null && score >= 0 && score <= 100) return score;

            JOptionPane.showMessageDialog(fenetreParente,
                    "Le score doit être un entier compris entre 0 et 100.",
                    "Score invalide", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void afficherProgression(
            JFrame fenetreParente, File dossier, boolean extraireOcr, int seuilConfianceOcr) {
        JProgressBar progression = new JProgressBar();
        progression.setIndeterminate(true);
        JPanel contenu = new JPanel(new BorderLayout(0, 12));
        contenu.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        contenu.add(new JLabel("Traitement des documents en cours, veuillez patienter..."),
                BorderLayout.NORTH);
        contenu.add(progression, BorderLayout.CENTER);

        JDialog fenetreProgression = new JDialog(fenetreParente, "Export en cours", true);
        fenetreProgression.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        fenetreProgression.setContentPane(contenu);
        fenetreProgression.pack();
        fenetreProgression.setLocationRelativeTo(null);

        SwingWorker<App.ResultatExport, Void> travailleur =
                new SwingWorker<App.ResultatExport, Void>() {
                    @Override
                    protected App.ResultatExport doInBackground() throws IOException {
                        return App.exporter(dossier, extraireOcr, seuilConfianceOcr);
                    }

                    @Override
                    protected void done() {
                        fenetreProgression.dispose();
                        try {
                            App.ResultatExport resultat = get();
                            int typeMessage = resultat.erreursExtraction
                                    ? JOptionPane.WARNING_MESSAGE
                                    : JOptionPane.INFORMATION_MESSAGE;
                            JOptionPane.showMessageDialog(fenetreParente, resultat.message,
                                    "Export terminé", typeMessage);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            JOptionPane.showMessageDialog(fenetreParente,
                                    "Le traitement a été interrompu.", "Erreur",
                                    JOptionPane.ERROR_MESSAGE);
                        } catch (ExecutionException e) {
                            Throwable cause = e.getCause();
                            String message = cause == null || cause.getMessage() == null
                                    ? "Une erreur inattendue est survenue."
                                    : cause.getMessage();
                            JOptionPane.showMessageDialog(fenetreParente, message, "Erreur",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                        fenetreParente.dispose();
                    }
                };
        SwingUtilities.invokeLater(travailleur::execute);
        fenetreProgression.setVisible(true);
    }
}
