/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */
package io.github.dsheirer.gui.playlist.discovery;

import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.eventbus.MyEventBus;
import io.github.dsheirer.gui.playlist.channel.ViewChannelRequest;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.discovery.BandScanController;
import io.github.dsheirer.module.discovery.Discovery;
import io.github.dsheirer.module.discovery.DiscoveryModel;
import io.github.dsheirer.module.discovery.DiscoveryState;
import io.github.dsheirer.module.discovery.ScanState;
import io.github.dsheirer.module.discovery.SignalKind;
import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.preference.discovery.DiscoveryPreference;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX editor tab showing band-scan discovery results.
 *
 * <p>Provides a toolbar with scan/stop/progress controls, an "add all ≥ N pips" action,
 * clear-finished, settings, and manage-ignored buttons, plus a {@link TableView} bound
 * directly to the observable list in {@link DiscoveryModel}.</p>
 *
 * <h3>Threading</h3>
 * All JavaFX UI interactions happen on the FX Application Thread. The {@link BandScanController}
 * and {@link DiscoveryModel} both marshal mutations to the FX thread automatically, so binding
 * to the model's observable list is safe.
 */
public class DiscoveryEditor extends BorderPane
{
    private static final Logger mLog = LoggerFactory.getLogger(DiscoveryEditor.class);
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    // ---- dependencies -------------------------------------------------------
    private final BandScanController mBandScanController;
    private final DiscoveryModel mDiscoveryModel;
    private final UserPreferences mUserPreferences;
    private final DiscoveryPreference mDiscoveryPreference;

    // ---- toolbar controls ---------------------------------------------------
    private Button mScanButton;
    private Button mStopButton;
    private ProgressBar mProgressBar;
    private Label mStateLabel;
    private ComboBox<Integer> mMinPipsCombo;
    private Button mAddAllButton;
    private Button mClearFinishedButton;
    private Button mClearAllButton;
    private Button mSettingsButton;
    private Button mManageIgnoredButton;

    // ---- table --------------------------------------------------------------
    private TableView<Discovery> mTable;

    // ---- stored ScanSpanRequest pre-fill (set before opening ScanDialog) ----
    private long mPreFillMinHz = 0;
    private long mPreFillMaxHz = 0;

    /**
     * Constructs the editor.
     *
     * @param bandScanController controller that drives scans and operator actions
     * @param userPreferences    application user preferences
     */
    public DiscoveryEditor(BandScanController bandScanController, UserPreferences userPreferences)
    {
        mBandScanController = bandScanController;
        mDiscoveryModel = bandScanController.getDiscoveryModel();
        mUserPreferences = userPreferences;
        mDiscoveryPreference = userPreferences.getDiscoveryPreference();

        setTop(buildToolBar());
        setCenter(buildTable());
        setPadding(new Insets(4));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Pre-fills the scan-dialog frequency range and immediately opens it.
     * Called when a {@link io.github.dsheirer.gui.playlist.channel.ScanSpanRequest} arrives.
     *
     * @param minHz lower bound in Hz
     * @param maxHz upper bound in Hz
     */
    public void openScanDialogWithSpan(long minHz, long maxHz)
    {
        mPreFillMinHz = minHz;
        mPreFillMaxHz = maxHz;
        openScanDialog();
    }

    /**
     * Selects the table row closest to {@code focusFrequencyHz}.
     * Called when a {@link io.github.dsheirer.gui.playlist.channel.ShowDiscoveryRequest} arrives.
     *
     * <p>Thread safety: this method always runs on the JavaFX Application Thread.
     * The call chain is {@code JavaFxWindowManager.execute()} →
     * {@code PlaylistEditor.process(ShowDiscoveryRequest)} → this method.
     * {@code JavaFxWindowManager.execute()} wraps every invocation in
     * {@code Platform.runLater()} when not already on the FX thread, so no additional
     * threading guard is required here.</p>
     *
     * @param focusFrequencyHz frequency to focus; 0 = no-op
     */
    public void focusFrequency(long focusFrequencyHz)
    {
        if(focusFrequencyHz == 0 || mTable == null)
        {
            return;
        }

        Discovery best = null;
        long bestDelta = Long.MAX_VALUE;

        for(Discovery d : mDiscoveryModel.snapshot())
        {
            long delta = Math.abs(d.getCenterFrequencyHz() - focusFrequencyHz);

            if(delta < bestDelta)
            {
                bestDelta = delta;
                best = d;
            }
        }

        if(best != null)
        {
            mTable.getSelectionModel().select(best);
            mTable.scrollTo(best);
        }
    }

    // -------------------------------------------------------------------------
    // Toolbar
    // -------------------------------------------------------------------------

    private ToolBar buildToolBar()
    {
        mScanButton = new Button("Scan…");
        mScanButton.setTooltip(new Tooltip("Open the scan dialog to start a new band scan"));
        mScanButton.setOnAction(e -> openScanDialog());

        mStopButton = new Button("Stop");
        mStopButton.setTooltip(new Tooltip("Stop the current scan"));
        mStopButton.setOnAction(e -> mBandScanController.stop());
        // Enabled while scanning or waiting between continuous scans
        mStopButton.disableProperty().bind(
            Bindings.createBooleanBinding(
                () -> !isStoppableState(mBandScanController.getScanState()),
                mBandScanController.scanStateProperty()
            )
        );

        mProgressBar = new ProgressBar(0.0);
        mProgressBar.setPrefWidth(120);
        mProgressBar.progressProperty().bind(mBandScanController.progressProperty());
        mProgressBar.visibleProperty().bind(
            Bindings.createBooleanBinding(
                () -> isActiveState(mBandScanController.getScanState()),
                mBandScanController.scanStateProperty()
            )
        );

        mStateLabel = new Label("Idle");
        mStateLabel.textProperty().bind(
            Bindings.createStringBinding(
                () -> formatState(mBandScanController.getScanState(),
                    (int)(mBandScanController.getProgress() * 100.0)),
                mBandScanController.scanStateProperty(),
                mBandScanController.progressProperty()
            )
        );
        mStateLabel.setMinWidth(120);
        // Show error message as tooltip when state is ERROR
        mBandScanController.scanStateProperty().addListener((obs, oldState, newState) -> {
            if(newState == ScanState.ERROR)
            {
                String msg = mBandScanController.getLastErrorMessage();
                mStateLabel.setTooltip(msg != null && !msg.isEmpty()
                    ? new Tooltip(msg) : null);
            }
            else
            {
                mStateLabel.setTooltip(null);
            }
        });

        // "Add all ≥ N pips" combo + button
        mMinPipsCombo = new ComboBox<>();
        mMinPipsCombo.getItems().addAll(1, 2, 3, 4);
        mMinPipsCombo.setValue(2);
        mMinPipsCombo.setPrefWidth(60);
        mMinPipsCombo.setTooltip(new Tooltip("Minimum confidence pips for bulk-add"));

        mAddAllButton = new Button("Add all ≥");
        mAddAllButton.setTooltip(new Tooltip("Add all identified discoveries at or above the chosen confidence"));
        mAddAllButton.setOnAction(e -> {
            int minPips = mMinPipsCombo.getValue() != null ? mMinPipsCombo.getValue() : 2;
            mBandScanController.addAllAtLeast(minPips);
        });

        mClearFinishedButton = new Button("Clear finished");
        mClearFinishedButton.setTooltip(new Tooltip("Remove probed rows (IDENTIFIED, UNIDENTIFIED, ERROR, KNOWN)"));
        mClearFinishedButton.setOnAction(e -> mDiscoveryModel.clearFinished());

        mClearAllButton = new Button("Clear all");
        mClearAllButton.setTooltip(new Tooltip("Remove all discovery rows"));
        mClearAllButton.setOnAction(e -> mDiscoveryModel.clear());

        mSettingsButton = new Button("Settings…");
        mSettingsButton.setTooltip(new Tooltip("Open discovery preferences"));
        mSettingsButton.setOnAction(e -> openSettings());

        mManageIgnoredButton = new Button("Manage ignored…");
        mManageIgnoredButton.setTooltip(new Tooltip("View and edit the ignored-frequency list"));
        mManageIgnoredButton.setOnAction(e -> openManageIgnored());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToolBar toolbar = new ToolBar(
            mScanButton, mStopButton, mProgressBar, mStateLabel, spacer,
            mAddAllButton, mMinPipsCombo, mClearFinishedButton, mClearAllButton,
            mSettingsButton, mManageIgnoredButton
        );

        return toolbar;
    }

    private static boolean isActiveState(ScanState state)
    {
        return state == ScanState.SURVEYING || state == ScanState.PROBING;
    }

    private static boolean isStoppableState(ScanState state)
    {
        return isActiveState(state) || state == ScanState.IDLE_CONTINUOUS;
    }

    private static String formatState(ScanState state, int pct)
    {
        if(state == null)
        {
            return "Idle";
        }

        return switch(state)
        {
            case IDLE           -> "Idle";
            case SURVEYING      -> "Surveying · " + pct + "%";
            case PROBING        -> "Probing · " + pct + "%";
            case DONE           -> "Done";
            case IDLE_CONTINUOUS -> "Continuous (waiting)";
            case CANCELLED      -> "Cancelled";
            case ERROR          -> "Error";
        };
    }

    static javafx.beans.binding.StringBinding powerSnrBinding(Discovery discovery)
    {
        return Bindings.createStringBinding(
            () -> String.format(Locale.ROOT, "%.1f / %.1f dB", discovery.getPowerDb(), discovery.getSnrDb()),
            discovery.powerDbProperty(), discovery.snrDbProperty());
    }

    static String detectedLabel(Discovery discovery)
    {
        DecoderType decoder = discovery.getDetectedDecoder();
        SignalKind kind = discovery.getKind();

        if(decoder == null)
        {
            return "";
        }

        String kindLabel = kind == null ? "" : switch(kind)
        {
            case CONTROL -> " · control";
            case DATA -> " · data";
            case CONVENTIONAL -> " · conventional";
            case TRAFFIC -> " · traffic";
            case UNKNOWN -> "";
        };
        return decoder.getShortDisplayString() + kindLabel;
    }

    // -------------------------------------------------------------------------
    // Table
    // -------------------------------------------------------------------------

    private TableView<Discovery> buildTable()
    {
        mTable = new TableView<>();
        mTable.setPlaceholder(new Label("No discoveries yet — start a scan."));
        mTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        // Bind to the observable list so changes from the background thread (already
        // marshalled to the FX thread by DiscoveryModel) flow directly into the table.
        SortedList<Discovery> sorted = new SortedList<>(mDiscoveryModel.getDiscoveries());
        sorted.comparatorProperty().bind(mTable.comparatorProperty());
        mTable.setItems(sorted);

        // --- Column: State ---
        // The cell value is a composite of stateProperty + createdChannelProperty, so we use
        // the row's Discovery as the cell value and observe both observable properties.
        TableColumn<Discovery, Discovery> stateCol = new TableColumn<>("State");
        stateCol.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(f.getValue()));
        stateCol.setCellFactory(col -> new TableCell<>()
        {
            private Discovery mDiscovery;
            private final InvalidationListener mDiscoveryListener = observable -> render();

            private void updateDiscovery(Discovery discovery)
            {
                if(mDiscovery == discovery)
                {
                    return;
                }

                if(mDiscovery != null)
                {
                    mDiscovery.stateProperty().removeListener(mDiscoveryListener);
                    mDiscovery.createdChannelProperty().removeListener(mDiscoveryListener);
                }

                mDiscovery = discovery;

                if(mDiscovery != null)
                {
                    mDiscovery.stateProperty().addListener(mDiscoveryListener);
                    mDiscovery.createdChannelProperty().addListener(mDiscoveryListener);
                }
            }

            private void render()
            {
                if(isEmpty() || mDiscovery == null)
                {
                    setText(null);
                    return;
                }
                if(mDiscovery.getCreatedChannel() != null)
                {
                    setText(mDiscovery.getCreatedChannel().isTemporaryLive() ? "● live" : "● saved");
                    return;
                }
                DiscoveryState state = mDiscovery.getState();
                setText(state == null ? null : switch(state)
                {
                    case ENERGY_DETECTED -> "⚡ energy";
                    case PROBING        -> "⏳ probing";
                    case IDENTIFIED     -> "✓";
                    case UNIDENTIFIED   -> "?";
                    case KNOWN          -> "known";
                    case ERROR          -> "✕";
                });
            }

            @Override
            protected void updateItem(Discovery d, boolean empty)
            {
                super.updateItem(d, empty);
                updateDiscovery(empty ? null : d);
                render();
            }
        });
        stateCol.setPrefWidth(90);

        // --- Column: Frequency (MHz) ---
        TableColumn<Discovery, Long> freqCol = new TableColumn<>("Frequency");
        freqCol.setCellValueFactory(f -> f.getValue().centerFrequencyHzProperty().asObject());
        freqCol.setCellFactory(col -> new TableCell<>()
        {
            @Override
            protected void updateItem(Long freq, boolean empty)
            {
                super.updateItem(freq, empty);
                setText(empty || freq == null ? null
                    : String.format(Locale.ROOT, "%.5f MHz", freq / 1e6));
            }
        });
        freqCol.setComparator(Long::compare);
        freqCol.setPrefWidth(130);

        // --- Column: Bandwidth ---
        TableColumn<Discovery, Integer> bwCol = new TableColumn<>("BW");
        bwCol.setCellValueFactory(f -> f.getValue().bandwidthHzProperty().asObject());
        bwCol.setCellFactory(col -> new TableCell<>()
        {
            @Override
            protected void updateItem(Integer bw, boolean empty)
            {
                super.updateItem(bw, empty);
                setText(empty || bw == null ? null
                    : String.format(Locale.ROOT, "%.1f kHz", bw / 1000.0));
            }
        });
        bwCol.setPrefWidth(80);

        // --- Column: Detected decoder + kind ---
        TableColumn<Discovery, String> decoderCol = new TableColumn<>("Detected");
        decoderCol.setCellValueFactory(f -> Bindings.createStringBinding(
            () -> detectedLabel(f.getValue()),
            f.getValue().detectedDecoderProperty(), f.getValue().kindProperty()));
        decoderCol.setPrefWidth(160);

        // --- Column: Confidence pips ---
        TableColumn<Discovery, Integer> confCol = new TableColumn<>("Conf");
        confCol.setCellValueFactory(f -> f.getValue().confidenceProperty().asObject());
        confCol.setCellFactory(col -> new TableCell<>()
        {
            @Override
            protected void updateItem(Integer conf, boolean empty)
            {
                super.updateItem(conf, empty);

                if(empty || conf == null)
                {
                    setText(null);
                    return;
                }

                int pips = Math.max(0, Math.min(4, conf));
                setText("●".repeat(pips) + "○".repeat(4 - pips));
            }
        });
        confCol.setPrefWidth(60);

        // --- Column: Power / SNR ---
        TableColumn<Discovery, String> powerCol = new TableColumn<>("Power/SNR");
        powerCol.setCellValueFactory(f -> powerSnrBinding(f.getValue()));
        powerCol.setPrefWidth(120);

        // --- Column: First seen ---
        TableColumn<Discovery, Instant> firstSeenCol = new TableColumn<>("First seen");
        firstSeenCol.setCellValueFactory(f -> f.getValue().firstSeenProperty());
        firstSeenCol.setCellFactory(col -> new TableCell<>()
        {
            @Override
            protected void updateItem(Instant t, boolean empty)
            {
                super.updateItem(t, empty);
                setText(empty || t == null ? null : TIME_FMT.format(t));
            }
        });
        firstSeenCol.setPrefWidth(75);

        // --- Column: Last seen ---
        TableColumn<Discovery, Instant> lastSeenCol = new TableColumn<>("Last seen");
        lastSeenCol.setCellValueFactory(f -> f.getValue().lastSeenProperty());
        lastSeenCol.setCellFactory(col -> new TableCell<>()
        {
            @Override
            protected void updateItem(Instant t, boolean empty)
            {
                super.updateItem(t, empty);
                setText(empty || t == null ? null : TIME_FMT.format(t));
            }
        });
        lastSeenCol.setPrefWidth(75);

        // --- Column: Notes (metadata summary) ---
        // Binds to metadataVersionProperty() so the cell re-renders whenever metadata changes.
        TableColumn<Discovery, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(f -> {
            Discovery d = f.getValue();
            return Bindings.createStringBinding(
                () -> d.getMetadata().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", ")),
                d.metadataVersionProperty());
        });
        notesCol.setPrefWidth(200);

        // --- Column: Actions (+ / save / remove / watch / ignore / reprobe) ---
        // Using Discovery as the cell value so that updateItem re-fires when the row's
        // observable properties change (state, createdChannel, watched).
        TableColumn<Discovery, Discovery> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(f.getValue()));
        actionsCol.setCellFactory(col -> new TableCell<>()
        {
            private final Button mAddBtn    = new Button("+");
            private final Button mSaveBtn   = new Button("Save");
            private final Button mRemoveBtn = new Button("Remove");
            private final Button mWatchBtn  = new Button("👁");
            private final Button mIgnoreBtn = new Button("✕");
            private final Button mReprobeBtn= new Button("↻");
            private final HBox mBox = new HBox(2, mAddBtn, mSaveBtn, mRemoveBtn, mWatchBtn, mIgnoreBtn, mReprobeBtn);
            private Discovery mDiscovery;
            private Channel mObservedChannel;
            private final InvalidationListener mDiscoveryListener = observable ->
            {
                updateObservedChannel();
                render();
            };
            private final InvalidationListener mChannelListener = observable -> render();

            {
                mAddBtn.setTooltip(new Tooltip("Add as channel"));
                mSaveBtn.setTooltip(new Tooltip("Save live channel to playlist"));
                mRemoveBtn.setTooltip(new Tooltip("Remove live channel"));
                mWatchBtn.setTooltip(new Tooltip("Toggle watched"));
                mIgnoreBtn.setTooltip(new Tooltip("Ignore this frequency"));
                mReprobeBtn.setTooltip(new Tooltip("Re-probe"));

                mAddBtn.setOnAction(e -> {
                    Discovery d = getDiscovery();
                    if(d != null) mBandScanController.addAsChannel(d);
                });
                mSaveBtn.setOnAction(e -> {
                    Discovery d = getDiscovery();
                    if(d != null) mBandScanController.saveCreatedChannel(d);
                });
                mRemoveBtn.setOnAction(e -> {
                    Discovery d = getDiscovery();
                    if(d != null) mBandScanController.removeCreatedChannel(d);
                });
                mWatchBtn.setOnAction(e -> {
                    Discovery d = getDiscovery();
                    if(d != null) mBandScanController.setWatched(d, !d.isWatched());
                });
                mIgnoreBtn.setOnAction(e -> {
                    Discovery d = getDiscovery();
                    if(d != null) mBandScanController.ignore(d);
                });
                mReprobeBtn.setOnAction(e -> {
                    Discovery d = getDiscovery();
                    if(d != null) mBandScanController.reprobe(d);
                });
            }

            private Discovery getDiscovery()
            {
                return mDiscovery;
            }

            private void updateDiscovery(Discovery discovery)
            {
                if(mDiscovery == discovery)
                {
                    return;
                }

                if(mDiscovery != null)
                {
                    mDiscovery.stateProperty().removeListener(mDiscoveryListener);
                    mDiscovery.createdChannelProperty().removeListener(mDiscoveryListener);
                    mDiscovery.watchedProperty().removeListener(mDiscoveryListener);
                }

                mDiscovery = discovery;

                if(mDiscovery != null)
                {
                    mDiscovery.stateProperty().addListener(mDiscoveryListener);
                    mDiscovery.createdChannelProperty().addListener(mDiscoveryListener);
                    mDiscovery.watchedProperty().addListener(mDiscoveryListener);
                }

                updateObservedChannel();
            }

            private void updateObservedChannel()
            {
                Channel channel = mDiscovery != null ? mDiscovery.getCreatedChannel() : null;
                if(mObservedChannel == channel)
                {
                    return;
                }

                if(mObservedChannel != null)
                {
                    mObservedChannel.temporaryLiveProperty().removeListener(mChannelListener);
                }

                mObservedChannel = channel;

                if(mObservedChannel != null)
                {
                    mObservedChannel.temporaryLiveProperty().addListener(mChannelListener);
                }
            }

            private void render()
            {
                if(isEmpty() || mDiscovery == null)
                {
                    setGraphic(null);
                    return;
                }

                // + button: only enabled when IDENTIFIED and not yet added
                mAddBtn.setDisable(mDiscovery.getState() != DiscoveryState.IDENTIFIED
                    || mDiscovery.getCreatedChannel() != null);
                boolean hasTemporaryChannel = mDiscovery.getCreatedChannel() != null
                    && mDiscovery.getCreatedChannel().isTemporaryLive();
                mSaveBtn.setDisable(!hasTemporaryChannel);
                mRemoveBtn.setDisable(!hasTemporaryChannel);
                // 👁 button: always available
                mWatchBtn.setStyle(mDiscovery.isWatched() ? "-fx-font-weight:bold;" : "");
                // ✕ button: always available
                mIgnoreBtn.setDisable(false);
                // ↻ button: enabled unless already probing
                mReprobeBtn.setDisable(mDiscovery.getState() == DiscoveryState.PROBING);

                setGraphic(mBox);
            }

            @Override
            protected void updateItem(Discovery item, boolean empty)
            {
                super.updateItem(item, empty);
                updateDiscovery(empty ? null : item);
                render();
            }
        });
        actionsCol.setPrefWidth(260);
        actionsCol.setSortable(false);

        mTable.getColumns().addAll(stateCol, freqCol, bwCol, decoderCol, confCol,
            powerCol, firstSeenCol, lastSeenCol, notesCol, actionsCol);

        // --- Row right-click context menu ---
        mTable.setRowFactory(tv -> {
            TableRow<Discovery> row = new TableRow<>();

            row.setOnContextMenuRequested(e -> {
                if(row.isEmpty()) return;
                Discovery d = row.getItem();
                buildContextMenu(d).show(row, e.getScreenX(), e.getScreenY());
            });

            return row;
        });

        return mTable;
    }

    private ContextMenu buildContextMenu(Discovery discovery)
    {
        ContextMenu menu = new ContextMenu();

        MenuItem addItem = new MenuItem("+ Add as channel");
        addItem.setDisable(discovery.getState() != DiscoveryState.IDENTIFIED
            || discovery.getCreatedChannel() != null);
        addItem.setOnAction(e -> mBandScanController.addAsChannel(discovery));

        MenuItem watchItem = new MenuItem(discovery.isWatched() ? "Unwatch" : "Watch");
        watchItem.setOnAction(e -> mBandScanController.setWatched(discovery, !discovery.isWatched()));

        MenuItem ignoreItem = new MenuItem("Ignore");
        ignoreItem.setOnAction(e -> mBandScanController.ignore(discovery));

        MenuItem reprobeItem = new MenuItem("Re-probe");
        reprobeItem.setDisable(discovery.getState() == DiscoveryState.PROBING);
        reprobeItem.setOnAction(e -> mBandScanController.reprobe(discovery));

        menu.getItems().addAll(addItem, watchItem, ignoreItem, reprobeItem);

        // "View/Edit channel" only when channel has been created
        Channel created = discovery.getCreatedChannel();
        if(created != null)
        {
            menu.getItems().add(new SeparatorMenuItem());

            if(created.isTemporaryLive())
            {
                MenuItem saveChannelItem = new MenuItem("Save live channel to playlist");
                saveChannelItem.setOnAction(e -> mBandScanController.saveCreatedChannel(discovery));
                menu.getItems().add(saveChannelItem);

                MenuItem removeChannelItem = new MenuItem("Remove live channel");
                removeChannelItem.setOnAction(e -> mBandScanController.removeCreatedChannel(discovery));
                menu.getItems().add(removeChannelItem);
            }

            MenuItem viewChannelItem = new MenuItem("View/Edit channel");
            viewChannelItem.setOnAction(e ->
                MyEventBus.getGlobalEventBus().post(new ViewChannelRequest(created)));
            menu.getItems().add(viewChannelItem);
        }

        return menu;
    }

    // -------------------------------------------------------------------------
    // Dialog openers
    // -------------------------------------------------------------------------

    private void openScanDialog()
    {
        long minHz = mPreFillMinHz;
        long maxHz = mPreFillMaxHz;
        // Clear the pre-fill after use
        mPreFillMinHz = 0;
        mPreFillMaxHz = 0;

        ScanDialog dialog = new ScanDialog(mBandScanController, mDiscoveryPreference, minHz, maxHz,
            mBandScanController.getTunerControl());
        dialog.show();
    }

    private void openSettings()
    {
        io.github.dsheirer.gui.preference.ViewUserPreferenceEditorRequest req =
            new io.github.dsheirer.gui.preference.ViewUserPreferenceEditorRequest(
                io.github.dsheirer.gui.preference.PreferenceEditorType.DISCOVERY);
        MyEventBus.getGlobalEventBus().post(req);
    }

    private void openManageIgnored()
    {
        ManageIgnoreListDialog dialog = new ManageIgnoreListDialog(mDiscoveryPreference);
        dialog.show();
    }
}
