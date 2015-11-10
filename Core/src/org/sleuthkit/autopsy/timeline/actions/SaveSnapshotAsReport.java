/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-15 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.timeline.actions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.util.Pair;
import javax.imageio.ImageIO;
import org.controlsfx.control.action.Action;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;
import org.sleuthkit.datamodel.TskCoreException;

/**
 */
public class SaveSnapshotAsReport extends Action {

    private static final Image SNAP_SHOT = new Image("org/sleuthkit/autopsy/timeline/images/image.png", 16, 16, true, true);
    private static final String HTML_EXT = ".html";
    private static final String REPORT_IMAGE_EXTENSION = ".png";

    private static final Logger LOGGER = Logger.getLogger(SaveSnapshotAsReport.class.getName());

    @NbBundle.Messages({"SaveSnapshot.action.name.text=Snapshot Report",
        "SaveSnapshot.action.longText=Save a screen capture of the visualization as a report.",
        "SaveSnapshot.fileChoose.title.text=Save snapshot to",})
    public SaveSnapshotAsReport(TimeLineController controller, Node node) {
        super(Bundle.SaveSnapshot_action_name_text());
        setLongText(Bundle.SaveSnapshot_action_longText());
        setGraphic(new ImageView(SNAP_SHOT));
        setEventHandler(new Consumer<ActionEvent>() {

            @Override
            public void accept(ActionEvent t) {
                //choose location/name
                ZonedDateTime now = ZonedDateTime.now();
                String reportName = "timeline-report-" + now.toString();
                Path reportPath = Paths.get(Case.getCurrentCase().getReportDirectory(), reportName).toAbsolutePath();
                try {
                    Files.createDirectories(reportPath);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
                //gather metadata
                List<Pair<String, String>> reportMetaData = new ArrayList<>();

                reportMetaData.add(new Pair<>("Case", Case.getCurrentCase().getName())); // NON-NLS

                ZoomParams zoomParams = controller.getEventsModel().zoomParametersProperty().get();
                reportMetaData.add(new Pair<>("Time Range", zoomParams.getTimeRange().toString())); // NON-NLS
                reportMetaData.add(new Pair<>("Description Level of Detail", zoomParams.getDescriptionLOD().getDisplayName())); // NON-NLS
                reportMetaData.add(new Pair<>("Event Type Zoom Level", zoomParams.getTypeZoomLevel().getDisplayName())); // NON-NLS
                reportMetaData.add(new Pair<>("Filters", zoomParams.getFilter().getHTMLReportString())); // NON-NLS

                //save snapshot as png
                try {
                    WritableImage snapshot = node.snapshot(null, null);
                    ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), "png",
                            reportPath.resolve(reportName + REPORT_IMAGE_EXTENSION).toFile()); // NON-NLS
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "failed to write snapshot to disk", ex); // NON-NLS
                    return;
                }

                //build html string
                StringBuilder wrapper = new StringBuilder();
                wrapper.append("<html>\n<head>\n\t<title>").append("timeline snapshot").append("</title>\n\t<link rel=\"stylesheet\" type=\"text/css\" href=\"index.css\" />\n</head>\n<body>\n"); // NON-NLS
                wrapper.append("<div id=\"content\">\n<h1>").append(reportName).append("</h1>\n"); // NON-NLS
                wrapper.append("<img src = \"").append(reportName).append(REPORT_IMAGE_EXTENSION + "\" alt = \"snaphot\">"); // NON-NLS
                wrapper.append("<table>\n"); // NON-NLS
                for (Pair<String, String> pair : reportMetaData) {
                    wrapper.append("<tr><td>").append(pair.getKey()).append(": </td><td>").append(pair.getValue()).append("</td></tr>\n"); // NON-NLS
                }
                wrapper.append("</table>\n"); // NON-NLS
                wrapper.append("</div>\n</body>\n</html>"); // NON-NLS
                File reportHTMLFIle = reportPath.resolve(reportName + HTML_EXT).toFile();

                //write html wrapper
                try (Writer htmlWriter = new FileWriter(reportHTMLFIle)) {
                    htmlWriter.write(wrapper.toString());
                } catch (FileNotFoundException ex) {
                    LOGGER.log(Level.WARNING, "failed to open html wrapper file for writing ", ex); // NON-NLS
                    return;
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "failed to write html wrapper file", ex); // NON-NLS
                    return;
                }

                //copy css
                try (InputStream resource = this.getClass().getResourceAsStream("/org/sleuthkit/autopsy/timeline/index.css")) { // NON-NLS
                    Files.copy(resource, reportPath.resolve("index.css")); // NON-NLS
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "failed to copy css file", ex); // NON-NLS
                }

                //add html file as report to case
                try {
                    Case.getCurrentCase().addReport(reportHTMLFIle.getPath(), "Timeline", reportName + HTML_EXT); // NON-NLS
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.WARNING, "failed add html wrapper as a report", ex); // NON-NLS
                }

                ButtonType show = new ButtonType("Show in Autopsy");
                ButtonType open = new ButtonType("Open");

                Optional<ButtonType> showAndWait = new Alert(Alert.AlertType.INFORMATION, "Snapshot saved at " + reportPath, open, show, ButtonType.OK).showAndWait();

                showAndWait.ifPresent((ButtonType t1) -> {
                    if (t1 == show) {
                        //do something
                        System.out.println("show");
                    } else if (t1 == open) {
                        //do something
                        System.out.println("open");
                    } else {
                        //do something
                        System.out.println("nothing");
                    }
                });
            }
        });
    }
}
