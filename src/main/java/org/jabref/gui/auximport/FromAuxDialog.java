package org.jabref.gui.auximport;

import java.nio.file.Path;
import java.util.Optional;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import org.jabref.gui.DialogService;
import org.jabref.gui.JabRefFrame;
import org.jabref.gui.LibraryTab;
import org.jabref.gui.StateManager;
import org.jabref.gui.theme.ThemeManager;
import org.jabref.gui.util.BaseDialog;
import org.jabref.gui.util.FileDialogConfiguration;
import org.jabref.gui.util.ViewModelListCellFactory;
import org.jabref.logic.auxparser.AuxParser;
import org.jabref.logic.auxparser.AuxParserResult;
import org.jabref.logic.auxparser.DefaultAuxParser;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.shared.DatabaseLocation;
import org.jabref.logic.util.StandardFileType;
import org.jabref.logic.util.io.FileUtil;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.preferences.PreferencesService;

import com.airhacks.afterburner.views.ViewLoader;
import jakarta.inject.Inject;

/**
 * A wizard dialog for generating a new sub database from existing TeX AUX file
 */
public class FromAuxDialog extends BaseDialog<Void> {
    public ComboBox<BibDatabaseContext> libraryListView;
    private final LibraryTab libraryTab;
    private LibraryTab librarySelectedTab;

    @FXML private ButtonType generateButtonType;
    private final Button generateButton;
    @FXML private TextField auxFileField;
    @FXML private ListView<String> notFoundList;
    @FXML private TextArea statusInfos;
    private AuxParserResult auxParserResult;

    @Inject private PreferencesService preferences;
    @Inject private DialogService dialogService;
    @Inject private ThemeManager themeManager;
    @Inject private StateManager stateManager;

    public FromAuxDialog(JabRefFrame frame) {
        libraryTab = frame.getCurrentLibraryTab();
        this.setTitle(Localization.lang("AUX file import"));

        ViewLoader.view(this)
                  .load()
                  .setAsDialogPane(this);

        libraryListView.setEditable(false);
        libraryListView.getItems().addAll(stateManager.getOpenDatabases());
        new ViewModelListCellFactory<BibDatabaseContext>()
                .withText(database -> {
                    Optional<String> dbOpt = Optional.empty();
                    if (database.getDatabasePath().isPresent()) {
                        dbOpt = FileUtil.getUniquePathFragment(stateManager.collectAllDatabasePaths(), database.getDatabasePath().get());
                    }
                    if (database.getLocation() == DatabaseLocation.SHARED) {
                        return database.getDBMSSynchronizer().getDBName() + " [" + Localization.lang("shared") + "]";
                    }

                    if (dbOpt.isEmpty()) {
                        return Localization.lang("untitled");
                    }

                    return dbOpt.get();
                })
                .install(libraryListView);
        libraryListView.getSelectionModel().select(libraryTab.getBibDatabaseContext());
        generateButton = (Button) this.getDialogPane().lookupButton(generateButtonType);
        generateButton.setDisable(true);
        generateButton.defaultButtonProperty().bind(generateButton.disableProperty().not());
        setResultConverter(button -> {
            if (button == generateButtonType) {
                BibDatabaseContext context = new BibDatabaseContext(auxParserResult.getGeneratedBibDatabase());
                frame.addTab(context, true);
            }
            return null;
        });

        themeManager.updateFontStyle(getDialogPane().getScene());
    }

    @FXML
    private void parseActionPerformed() {
        notFoundList.getItems().clear();
        statusInfos.setText("");

//        BibDatabase refBase = libraryTab.getDatabase();
        if (libraryListView.getSelectionModel().getSelectedItem() == null) {
            dialogService.showErrorDialogAndWait(Localization.lang("Aux"),
                    Localization.lang("please select one library in the combox below"));
            return;
        }
        BibDatabase refBase = libraryListView.getSelectionModel().getSelectedItem().getDatabase();
        String auxName = auxFileField.getText();

        if ((auxName != null) && (refBase != null) && !auxName.isEmpty()) {
            AuxParser auxParser = new DefaultAuxParser(refBase);
            auxParserResult = auxParser.parse(Path.of(auxName));
            notFoundList.getItems().setAll(auxParserResult.getUnresolvedKeys());
            statusInfos.setText(new AuxParserResultViewModel(auxParserResult).getInformation(false));

            if (!auxParserResult.getGeneratedBibDatabase().hasEntries()) {
                // The generated database contains no entries -> no active generate-button
                statusInfos.setText(statusInfos.getText() + "\n" + Localization.lang("empty library"));
                generateButton.setDisable(true);
            } else {
                generateButton.setDisable(false);
            }
        } else {
            generateButton.setDisable(true);
        }
    }

    @FXML
    private void browseButtonClicked() {
        FileDialogConfiguration fileDialogConfiguration = new FileDialogConfiguration.Builder()
                .addExtensionFilter(StandardFileType.AUX)
                .withDefaultExtension(StandardFileType.AUX)
                .withInitialDirectory(preferences.getFilePreferences().getWorkingDirectory()).build();
        dialogService.showFileOpenDialog(fileDialogConfiguration).ifPresent(file -> auxFileField.setText(file.toAbsolutePath().toString()));
    }
}
