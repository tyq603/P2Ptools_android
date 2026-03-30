import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

public class LocalSendGUI extends Application {

    // --- 颜色常量 ---
    private final String COLOR_BG = "#eef7f6";
    private final String COLOR_SIDEBAR = "#eef7f6";
    private final String COLOR_NAV_IDLE = "transparent";
    private final String COLOR_NAV_HOVER = "#d1e8e4";
    private final String COLOR_NAV_SELECTED = "#b2d8d2";
    private final String COLOR_TEAL = "#006d63";
    private final String COLOR_CARD_BG = "#ffffff";
    private final String COLOR_TEXT_MAIN = "#2c3e50";
    private final String COLOR_TEXT_SUB = "#7f8c8d";

    // --- 后台服务 ---
    private final NetworkService networkService = new NetworkService();
    private List<File> selectedFiles;

    // --- UI 容器 ---
    private StackPane contentArea;
    private VBox sendView, receiveView, settingsView;
    private Button currentSelectedNavBtn;

    // 发送预览组件
    private VBox topSelectionArea, initialGrid, previewCard;
    private Label fileCountLabel, fileSizeLabel;
    private ImageView thumbnailView;

    @Override
    public void start(Stage primaryStage) {
        networkService.init();

        VBox sidebar = createSidebar();
        initSendView();
        initReceiveView();
        initSettingsView();

        contentArea = new StackPane(sendView);
        HBox.setHgrow(contentArea, Priority.ALWAYS);

        HBox root = new HBox(sidebar, contentArea);
        Scene scene = new Scene(root, 1000, 750);

        primaryStage.setTitle("LocalSend - JavaFX (NIC Selector)");
        primaryStage.setScene(scene);

        primaryStage.setOnCloseRequest(e -> System.exit(0));
        primaryStage.show();
    }

    // ==========================================
    //             UI: 侧边栏
    // ==========================================
    private VBox createSidebar() {
        VBox sb = new VBox(20);
        sb.setPrefWidth(220);
        sb.setStyle("-fx-background-color: " + COLOR_SIDEBAR + "; -fx-padding: 40 20 20 25;");

        Label logo = new Label("LocalSend");
        logo.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #333;");

        VBox nav = new VBox(15);
        Button bReceive = createNavBtn("接收", "📶", false);
        Button bSend = createNavBtn("发送", "➤", true);
        Button bSettings = createNavBtn("设置", "⚙", false);

        bReceive.setOnAction(e -> switchMainView(receiveView, bReceive));
        bSend.setOnAction(e -> switchMainView(sendView, bSend));
        bSettings.setOnAction(e -> switchMainView(settingsView, bSettings));

        nav.getChildren().addAll(bReceive, bSend, bSettings);
        sb.getChildren().addAll(logo, new Region(), nav);
        VBox.setVgrow(nav, Priority.ALWAYS);
        return sb;
    }

    private Button createNavBtn(String text, String icon, boolean isDefault) {
        Button btn = new Button(icon + "    " + text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        String base = "-fx-font-size: 15; -fx-padding: 12 20; -fx-background-radius: 15; -fx-cursor: hand;";
        if (isDefault) {
            currentSelectedNavBtn = btn;
            btn.setStyle(base + "-fx-background-color: " + COLOR_NAV_SELECTED + "; -fx-font-weight: bold;");
        } else {
            btn.setStyle(base + "-fx-background-color: " + COLOR_NAV_IDLE + ";");
        }
        btn.setOnMouseEntered(e -> { if(btn != currentSelectedNavBtn) btn.setStyle(base + "-fx-background-color: " + COLOR_NAV_HOVER + ";"); });
        btn.setOnMouseExited(e -> { if(btn != currentSelectedNavBtn) btn.setStyle(base + "-fx-background-color: " + COLOR_NAV_IDLE + ";"); else btn.setStyle(base + "-fx-background-color: " + COLOR_NAV_SELECTED + "; -fx-font-weight: bold;"); });
        return btn;
    }

    private void switchMainView(VBox target, Button clickedBtn) {
        contentArea.getChildren().setAll(target);
        String base = "-fx-font-size: 15; -fx-padding: 12 20; -fx-background-radius: 15; -fx-cursor: hand;";
        if (currentSelectedNavBtn != null) currentSelectedNavBtn.setStyle(base + "-fx-background-color: " + COLOR_NAV_IDLE + ";");
        currentSelectedNavBtn = clickedBtn;
        currentSelectedNavBtn.setStyle(base + "-fx-background-color: " + COLOR_NAV_SELECTED + "; -fx-font-weight: bold;");
    }

    // ==========================================
    //             UI: 接收视图 (已绑定动态 IP)
    // ==========================================
    private void initReceiveView() {
        receiveView = new VBox(40);
        receiveView.setAlignment(Pos.CENTER);
        receiveView.setStyle("-fx-background-color: " + COLOR_BG + ";");

        StackPane logo = new StackPane();
        Circle c1 = new Circle(60, Color.web(COLOR_TEAL));
        Circle c2 = new Circle(40, Color.web(COLOR_BG));
        Circle c3 = new Circle(25, Color.web(COLOR_TEAL));
        logo.getChildren().addAll(c1, c2, c3);

        Label nameLbl = new Label();
        nameLbl.setStyle("-fx-font-size: 40; -fx-font-weight: 300;");
        nameLbl.textProperty().bind(networkService.deviceNameProperty);

        VBox infoBox = new VBox(15);
        infoBox.setAlignment(Pos.CENTER);
        Label tipLbl = new Label("保持此界面开启以接收文件");
        tipLbl.setStyle("-fx-text-fill: gray;");

        HBox ipContainer = new HBox(0);
        ipContainer.setAlignment(Pos.CENTER);

        // 使用绑定确保 IP 切换时文字更新
        Label ipLbl = new Label();
        ipLbl.textProperty().bind(networkService.localIpProperty.concat(":").concat(String.valueOf(networkService.tcpPort)));
        ipLbl.setStyle("-fx-font-family: monospace; -fx-font-size: 18; " +
                "-fx-background-color: #d1e8e4; -fx-padding: 8 20; " +
                "-fx-background-radius: 12 0 0 12;");

        Button copyBtn = new Button("复制 📋");
        copyBtn.setStyle("-fx-background-color: " + COLOR_TEAL + "; -fx-text-fill: white; " +
                "-fx-font-size: 14; -fx-padding: 8 20; " +
                "-fx-background-radius: 0 12 12 0; -fx-cursor: hand; -fx-font-weight: bold;");

        copyBtn.setOnAction(e -> {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            content.putString(ipLbl.getText());
            clipboard.setContent(content);

            copyBtn.setText("已复制! ✓");
            copyBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; " +
                    "-fx-font-size: 14; -fx-padding: 8 20; " +
                    "-fx-background-radius: 0 12 12 0;");

            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ex) {}
                Platform.runLater(() -> {
                    copyBtn.setText("复制 📋");
                    copyBtn.setStyle("-fx-background-color: " + COLOR_TEAL + "; -fx-text-fill: white; " +
                            "-fx-font-size: 14; -fx-padding: 8 20; " +
                            "-fx-background-radius: 0 12 12 0; -fx-cursor: hand; -fx-font-weight: bold;");
                });
            }).start();
        });

        ipContainer.getChildren().addAll(ipLbl, copyBtn);
        infoBox.getChildren().addAll(tipLbl, ipContainer);
        receiveView.getChildren().addAll(logo, nameLbl, infoBox);
    }

    // ==========================================
    //             UI: 设置视图 (新增网卡选择)
    // ==========================================
    private void initSettingsView() {
        settingsView = new VBox(30);
        settingsView.setPadding(new Insets(50, 80, 50, 80));
        settingsView.setStyle("-fx-background-color: " + COLOR_BG + ";");
        settingsView.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("设置");
        title.setStyle("-fx-font-size: 32; -fx-font-weight: bold; -fx-text-fill: " + COLOR_TEXT_MAIN + ";");

        VBox generalCard = createStyledCard("通用", "⚙️");

        HBox rowName = createSettingRow("设备名称", "修改此设备在网络中的显示名称");
        TextField nameField = new TextField();
        nameField.setPrefWidth(250);
        nameField.setStyle("-fx-background-color: #f1f3f5; -fx-background-radius: 8; -fx-padding: 8;");
        nameField.textProperty().bindBidirectional(networkService.deviceNameProperty);
        rowName.getChildren().add(nameField);

        Separator sep1 = new Separator(); sep1.setPadding(new Insets(10, 0, 10, 0));

        HBox rowPin = createSettingRow("传输 PIN 码", "设置 4 位数字，开启后他人向你发送文件需验证");
        TextField pinField = new TextField();
        pinField.setPromptText("留空则无需验证");
        pinField.setPrefWidth(250);
        pinField.setStyle("-fx-background-color: #f1f3f5; -fx-background-radius: 8; -fx-padding: 8;");
        pinField.textProperty().addListener((obs, old, nv) -> {
            if (!nv.matches("\\d*")) pinField.setText(nv.replaceAll("[^\\d]", ""));
            if (pinField.getText().length() > 4) pinField.setText(pinField.getText().substring(0, 4));
        });
        pinField.textProperty().bindBidirectional(networkService.pinProperty);
        rowPin.getChildren().add(pinField);

        Separator sep2 = new Separator(); sep2.setPadding(new Insets(10, 0, 10, 0));

        HBox rowPath = createSettingRow("保存位置", "接收到的文件将存储在此目录");
        TextField pathDisplay = new TextField();
        pathDisplay.setEditable(false);
        pathDisplay.setPrefWidth(300);
        pathDisplay.setStyle("-fx-background-color: #f1f3f5; -fx-background-radius: 8 0 0 8; -fx-padding: 8; -fx-border-color: transparent;");
        pathDisplay.textProperty().bind(networkService.downloadPathProperty);

        Button btnBrowse = new Button("更改");
        btnBrowse.setStyle("-fx-background-color: " + COLOR_TEAL + "; -fx-text-fill: white; -fx-background-radius: 0 8 8 0; -fx-padding: 8 20; -fx-cursor: hand;");
        btnBrowse.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File selected = dc.showDialog(settingsView.getScene().getWindow());
            if (selected != null) networkService.downloadPathProperty.set(selected.getAbsolutePath());
        });
        HBox pathBox = new HBox(pathDisplay, btnBrowse);
        rowPath.getChildren().add(pathBox);
        generalCard.getChildren().addAll(rowName, sep1, rowPin, sep2, rowPath);

        // --- 网络卡片：新增网卡选择下拉框 ---
        VBox networkCard = createStyledCard("网络状态", "🌐");

        HBox rowIpSelector = createSettingRow("网络接口", "在多网卡环境下手动选择要监听的地址");
        ComboBox<String> nicCombo = new ComboBox<>(networkService.availableIps);
        nicCombo.setPrefWidth(300);
        nicCombo.setStyle("-fx-background-color: #f1f3f5; -fx-background-radius: 8;");
        nicCombo.valueProperty().bindBidirectional(networkService.localIpProperty);
        rowIpSelector.getChildren().add(nicCombo);

        Separator sep3 = new Separator(); sep3.setPadding(new Insets(10, 0, 10, 0));

        HBox rowIp = createSettingRow("当前监听 IP 地址", "其他设备看到的连接地址");
        Label ipVal = new Label();
        ipVal.textProperty().bind(networkService.localIpProperty.concat(":").concat(String.valueOf(networkService.tcpPort)));
        ipVal.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14; -fx-background-color: #e8f4f2; -fx-padding: 5 12; -fx-background-radius: 5;");
        rowIp.getChildren().add(ipVal);

        networkCard.getChildren().addAll(rowIpSelector, sep3, rowIp);

        settingsView.getChildren().addAll(title, generalCard, networkCard);
    }

    private VBox createStyledCard(String title, String icon) {
        VBox card = new VBox(15);
        card.setPadding(new Insets(25));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 20;");
        card.setEffect(new DropShadow(10, Color.rgb(0,0,0,0.05)));
        Label head = new Label(icon + "  " + title);
        head.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: " + COLOR_TEAL + ";");
        card.getChildren().add(head);
        return card;
    }

    private HBox createSettingRow(String label, String desc) {
        HBox row = new HBox(); row.setAlignment(Pos.CENTER_LEFT);
        VBox left = new VBox(3);
        Label title = new Label(label); title.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #333;");
        Label sub = new Label(desc); sub.setStyle("-fx-font-size: 12; -fx-text-fill: " + COLOR_TEXT_SUB + ";");
        left.getChildren().addAll(title, sub);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(left, spacer);
        return row;
    }

    // ==========================================
    //             UI: 发送视图 (保持原样)
    // ==========================================
    private void initSendView() {
        sendView = new VBox(30);
        sendView.setPadding(new Insets(40, 50, 40, 50));
        sendView.setStyle("-fx-background-color: white;");

        sendView.setOnDragOver(event -> {
            if (event.getGestureSource() != sendView && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        sendView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                handleFileSelection(db.getFiles());
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        topSelectionArea = new VBox();
        initInitialGrid();
        initPreviewCard();
        topSelectionArea.getChildren().add(initialGrid);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label("附近的设备");
        lbl.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");

        Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setStyle("-fx-background-color: transparent; -fx-border-color: #ddd; -fx-border-radius: 10; -fx-cursor: hand; -fx-font-size: 12;");
        refreshBtn.setOnAction(e -> networkService.manualRefresh());

        Button manualBtn = new Button("手动输入 ⌨");
        manualBtn.setStyle("-fx-background-color: transparent; -fx-border-color: #ddd; -fx-border-radius: 10; -fx-cursor: hand;");
        manualBtn.setOnAction(e -> showManualInputDialog());

        header.getChildren().addAll(lbl, refreshBtn, s, manualBtn);

        ListView<NetworkService.DeviceInfo> lv = new ListView<>(networkService.deviceList);
        lv.setPrefHeight(400);
        lv.setCellFactory(param -> new DeviceCell());

        lv.setOnMouseClicked(e -> {
            NetworkService.DeviceInfo target = lv.getSelectionModel().getSelectedItem();
            if (target != null) {
                if (selectedFiles == null || selectedFiles.isEmpty()) {
                    showModernWarning("未选择文件", "请先在上方选择或拖入要发送的文件。");
                } else {
                    if (target.pinRequired) showPinInputDialog(target);
                    else showModernConfirmDialog(target, "");
                }
                lv.getSelectionModel().clearSelection();
            }
        });

        sendView.getChildren().addAll(topSelectionArea, header, lv);
    }

    // ==========================================
    //             现代 UI 对话框
    // ==========================================
    private void showModernWarning(String title, String content) {
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        VBox root = new VBox(20); root.setAlignment(Pos.CENTER); root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: white; -fx-background-radius: 20; -fx-border-color: #eee; -fx-border-radius: 20;");
        root.setEffect(new DropShadow(15, Color.gray(0.8)));
        Label icon = new Label("⚠️"); icon.setStyle("-fx-font-size: 40;");
        Label tLbl = new Label(title); tLbl.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");
        Label cLbl = new Label(content); cLbl.setStyle("-fx-text-fill: #666;");
        Button btn = new Button("好的");
        btn.setStyle("-fx-background-color: " + COLOR_TEAL + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-padding: 8 25; -fx-cursor: hand;");
        btn.setOnAction(e -> stage.close());
        root.getChildren().addAll(icon, tLbl, cLbl, btn);
        stage.setScene(new Scene(root, 300, 220, Color.TRANSPARENT));
        stage.show();
    }

    private void showPinInputDialog(NetworkService.DeviceInfo target) {
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        VBox root = new VBox(20); root.setPadding(new Insets(30)); root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: white; -fx-background-radius: 20; -fx-border-color: #eee; -fx-border-radius: 20;");
        root.setEffect(new DropShadow(15, Color.gray(0.8)));

        Label head = new Label("该设备需要 PIN 码"); head.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");
        PasswordField pinField = new PasswordField();
        pinField.setPromptText("请输入 4 位 PIN");
        pinField.setStyle("-fx-font-size: 20; -fx-alignment: center; -fx-background-color: #f1f3f5;");
        pinField.setPrefWidth(200);

        Button btn = new Button("继续");
        btn.setStyle("-fx-background-color: " + COLOR_TEAL + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-padding: 10 40; -fx-cursor: hand;");
        btn.setOnAction(e -> {
            String pin = pinField.getText();
            if (pin.length() == 4) {
                stage.close();
                showModernConfirmDialog(target, pin);
            }
        });

        root.getChildren().addAll(new Label("🔒"), head, pinField, btn);
        stage.setScene(new Scene(root, 350, 250, Color.TRANSPARENT));
        stage.show();
    }

    private void showModernConfirmDialog(NetworkService.DeviceInfo target, String pin) {
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        VBox root = new VBox(25); root.setPadding(new Insets(35)); root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: white; -fx-background-radius: 25; -fx-border-color: #eee; -fx-border-radius: 25;");
        root.setEffect(new DropShadow(20, Color.gray(0.7)));
        Label icon = new Label("📤"); icon.setStyle("-fx-font-size: 48;");
        VBox textFlow = new VBox(8); textFlow.setAlignment(Pos.CENTER);
        Label head = new Label("确认发送文件？"); head.setStyle("-fx-font-size: 20; -fx-font-weight: bold;");
        String fileName = selectedFiles.size() == 1 ? selectedFiles.get(0).getName() : selectedFiles.size() + " 个文件";
        Label sub = new Label("发送 " + fileName + "\n至设备: " + target.name);
        sub.setStyle("-fx-text-fill: #777; -fx-text-alignment: center;");
        textFlow.getChildren().addAll(head, sub);
        HBox btns = new HBox(15); btns.setAlignment(Pos.CENTER);
        Button cancel = new Button("取消");
        cancel.setStyle("-fx-background-color: transparent; -fx-border-color: #ccc; -fx-border-radius: 12; -fx-padding: 10 25; -fx-cursor: hand;");
        cancel.setOnAction(e -> stage.close());
        Button confirm = new Button("发送");
        confirm.setStyle("-fx-background-color: " + COLOR_TEAL + "; -fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 10 35; -fx-font-weight: bold; -fx-cursor: hand;");
        confirm.setOnAction(e -> { stage.close(); startSendTask(target, selectedFiles.get(0), pin); });
        btns.getChildren().addAll(cancel, confirm);
        root.getChildren().addAll(icon, textFlow, btns);
        stage.setScene(new Scene(root, 400, 320, Color.TRANSPARENT));
        stage.show();
    }

    private void showModernProgressDialog(String title, Task<?> t) {
        Platform.runLater(() -> {
            Stage stage = new Stage(StageStyle.TRANSPARENT);
            stage.initModality(Modality.APPLICATION_MODAL);

            VBox root = new VBox(25);
            root.setPadding(new Insets(35, 40, 30, 40));
            root.setAlignment(Pos.TOP_LEFT);
            root.setStyle("-fx-background-color: white; -fx-background-radius: 25; -fx-border-color: #f0f0f0; -fx-border-width: 1;");
            root.setEffect(new DropShadow(30, Color.gray(0.8, 0.5)));

            Label titleLbl = new Label(title);
            titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 20; -fx-text-fill: #333;");

            ProgressBar pb = new ProgressBar(0);
            pb.setPrefWidth(480);
            pb.setPrefHeight(12);
            pb.progressProperty().bind(t.progressProperty());
            pb.setStyle("-fx-accent: #006d63; -fx-control-inner-background: #f0f0f0; -fx-background-radius: 6;");

            Label statusLbl = new Label("正在初始化...");
            statusLbl.setStyle("-fx-text-fill: #555; -fx-font-size: 16;");
            statusLbl.textProperty().bind(t.messageProperty());

            Button closeBtn = new Button("关闭");
            closeBtn.setStyle("-fx-background-color: #f5f5f5; -fx-text-fill: #888; -fx-background-radius: 10; -fx-padding: 8 30; -fx-cursor: hand; -fx-border-color: #e0e0e0; -fx-border-radius: 10; -fx-font-size: 14;");
            closeBtn.setOnAction(e -> { t.cancel(); stage.close(); });

            HBox footer = new HBox(closeBtn);
            footer.setAlignment(Pos.BOTTOM_RIGHT);

            root.getChildren().addAll(titleLbl, pb, statusLbl, footer);

            stage.setScene(new Scene(root, 560, 260, Color.TRANSPARENT));
            new Thread(t).start();
            stage.show();
        });
    }

    private void showManualInputDialog() {
        Stage dialog = new Stage(); dialog.initModality(Modality.APPLICATION_MODAL); dialog.setTitle("手动连接");
        VBox root = new VBox(15); root.setPadding(new Insets(20)); root.setAlignment(Pos.CENTER);
        Label label = new Label("输入目标 IP:端口 (例 192.168.1.5:9010)");
        TextField field = new TextField();
        Button btn = new Button("连接并添加"); btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> {
            String input = field.getText().trim();
            if (input.contains(":")) {
                try {
                    String[] parts = input.split(":");
                    networkService.addManualDevice(parts[0], Integer.parseInt(parts[1]));
                    dialog.close();
                } catch (Exception ex) { field.setText("格式错误"); }
            }
        });
        root.getChildren().addAll(label, field, btn);
        dialog.setScene(new Scene(root, 350, 180));
        dialog.show();
    }

    private void initInitialGrid() {
        initialGrid = new VBox(15);
        HBox grid = new HBox(15);
        grid.getChildren().addAll(createActionBtn("文件", "📄"), createActionBtn("文件夹", "📁"));
        initialGrid.getChildren().addAll(new Label("选择"), grid);
    }

    private void initPreviewCard() {
        previewCard = new VBox(15);
        previewCard.setPadding(new Insets(20));
        previewCard.setStyle("-fx-background-color: #f1f3f5; -fx-background-radius: 15;");
        Label close = new Label("✕"); close.setStyle("-fx-cursor: hand;");
        close.setOnMouseClicked(e -> { selectedFiles = null; topSelectionArea.getChildren().setAll(initialGrid); });
        fileCountLabel = new Label(); fileSizeLabel = new Label();
        thumbnailView = new ImageView(); thumbnailView.setFitHeight(80); thumbnailView.setFitWidth(80); thumbnailView.setPreserveRatio(true);
        previewCard.getChildren().addAll(close, fileCountLabel, fileSizeLabel, thumbnailView);
    }

    private void handleFileSelection(List<File> fs) {
        if (fs != null && !fs.isEmpty()) {
            selectedFiles = fs;
            fileCountLabel.setText("已选内容: " + (fs.size() == 1 ? fs.get(0).getName() : fs.size() + " 个项目"));
            long size = 0;
            for (File f : fs) size += getFileOrFolderSize(f);
            fileSizeLabel.setText("总大小: " + formatSize(size));
            if (fs.size() == 1 && isImg(fs.get(0))) {
                thumbnailView.setImage(new Image(fs.get(0).toURI().toString(), 160, 160, true, true, true));
            } else {
                thumbnailView.setImage(null);
            }
            topSelectionArea.getChildren().setAll(previewCard);
        }
    }

    private long getFileOrFolderSize(File f) {
        if (f.isFile()) return f.length();
        long size = 0;
        File[] files = f.listFiles();
        if (files != null) {
            for (File child : files) size += getFileOrFolderSize(child);
        }
        return size;
    }

    private VBox createActionBtn(String txt, String icon) {
        VBox b = new VBox(10, new Label(icon), new Label(txt));
        b.setAlignment(Pos.CENTER); b.setPrefSize(100, 90);
        b.setStyle("-fx-background-color: #f1f8f6; -fx-background-radius: 15; -fx-cursor: hand;");
        b.setOnMouseClicked(e -> {
            if (txt.equals("文件")) {
                FileChooser fc = new FileChooser();
                List<File> fs = fc.showOpenMultipleDialog(null);
                handleFileSelection(fs);
            } else if (txt.equals("文件夹")) {
                DirectoryChooser dc = new DirectoryChooser();
                File f = dc.showDialog(null);
                if (f != null) handleFileSelection(Collections.singletonList(f));
            }
        });
        return b;
    }

    // ==========================================
    //             核心传输逻辑 (保持原样)
    // ==========================================
    private void startSendTask(NetworkService.DeviceInfo t, File f, String pin) {
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                try (Socket s = new Socket();
                     FileInputStream fis = new FileInputStream(f)) {

                    s.connect(new InetSocketAddress(t.ip, t.port), 5000);
                    DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                    DataInputStream dis = new DataInputStream(s.getInputStream());

                    dos.writeUTF(pin);
                    if (!dis.readBoolean()) {
                        updateMessage("PIN 码错误，传输被拒绝 ❌");
                        return null;
                    }

                    dos.writeUTF(f.getName());
                    long totalSize = f.length();
                    dos.writeLong(totalSize);

                    long startOffset = dis.readLong();
                    if (startOffset > 0) {
                        if (startOffset >= totalSize) {
                            updateMessage("文件已存在且完整 ✅");
                            updateProgress(totalSize, totalSize);
                            return null;
                        }
                        fis.skip(startOffset);
                        updateMessage("正在从断点恢复...");
                    }

                    byte[] b = new byte[65536];
                    int len; long sent = startOffset;
                    long lastTime = System.currentTimeMillis();
                    long lastSent = sent;

                    while ((len = fis.read(b)) != -1) {
                        if (isCancelled()) break;
                        dos.write(b, 0, len); sent += len;

                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastTime >= 500) {
                            double speed = (double)(sent - lastSent) / (currentTime - lastTime) * 1000.0;
                            updateMessage(formatSize(sent) + " / " + formatSize(totalSize) + "   (" + formatSize((long)speed) + "/s)");
                            lastTime = currentTime;
                            lastSent = sent;
                        }
                        updateProgress(sent, totalSize);
                    }
                    updateMessage(isCancelled() ? "传输已暂停" : "发送完成！✅");
                } catch (Exception e) { updateMessage("连接中断: " + e.getMessage()); throw e; }
                return null;
            }
        };
        showModernProgressDialog("正在发送文件", task);
    }

    private void handleReceive(Socket s) {
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                try (DataInputStream dis = new DataInputStream(s.getInputStream());
                     DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {

                    String incomingPin = dis.readUTF();
                    String myPin = networkService.pinProperty.get();
                    if (myPin != null && !myPin.isEmpty()) {
                        if (!myPin.equals(incomingPin)) {
                            dos.writeBoolean(false);
                            updateMessage("检测到错误的 PIN 尝试。");
                            return null;
                        }
                    }
                    dos.writeBoolean(true);

                    String name = dis.readUTF();
                    long totalSize = dis.readLong();
                    File saveDir = new File(networkService.downloadPathProperty.get());
                    if (!saveDir.exists()) saveDir.mkdirs();
                    File targetFile = new File(saveDir, name);

                    long existingSize = 0;
                    if (targetFile.exists()) {
                        existingSize = targetFile.length();
                        if (existingSize > totalSize) existingSize = 0;
                    }

                    dos.writeLong(existingSize);

                    try (FileOutputStream fos = new FileOutputStream(targetFile, existingSize > 0)) {
                        byte[] b = new byte[65536];
                        int len; long read = existingSize;
                        long lastTime = System.currentTimeMillis();
                        long lastRead = read;

                        while (read < totalSize) {
                            if (isCancelled()) break;
                            int toRead = (int) Math.min(b.length, totalSize - read);
                            len = dis.read(b, 0, toRead);
                            if (len == -1) break;

                            fos.write(b, 0, len); read += len;

                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastTime >= 500) {
                                double speed = (double)(read - lastRead) / (currentTime - lastTime) * 1000.0;
                                updateMessage(formatSize(read) + " / " + formatSize(totalSize) + "   (" + formatSize((long)speed) + "/s)");
                                lastTime = currentTime;
                                lastRead = read;
                            }
                            updateProgress(read, totalSize);
                        }
                    }
                    updateMessage(isCancelled() ? "传输已暂停" : "接收成功！");
                } catch (Exception e) { updateMessage("接收错误: " + e.getMessage()); }
                finally { s.close(); }
                return null;
            }
        };
        showModernProgressDialog("正在接收文件", task);
    }

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double)v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private boolean isImg(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".jpeg");
    }

    // ==========================================
    //             P2P 网络服务类 (增强多网卡选择)
    // ==========================================
    class NetworkService {
        private final int UDP_PORT = 8888;
        public int tcpPort;

        // 修改点：IP 改为属性以支持动态监听
        public StringProperty localIpProperty = new SimpleStringProperty();
        public ObservableList<String> availableIps = FXCollections.observableArrayList();

        private final Preferences prefs = Preferences.userNodeForPackage(LocalSendGUI.class);
        public StringProperty deviceNameProperty = new SimpleStringProperty();
        public StringProperty downloadPathProperty = new SimpleStringProperty();
        public StringProperty pinProperty = new SimpleStringProperty();

        public ObservableList<DeviceInfo> deviceList = FXCollections.observableArrayList();
        private ConcurrentHashMap<String, DeviceInfo> deviceMap = new ConcurrentHashMap<>();

        public void init() {
            String defaultHostName = "Unknown Device";
            try { defaultHostName = InetAddress.getLocalHost().getHostName(); } catch (Exception e) {}
            deviceNameProperty.set(prefs.get("device_name", defaultHostName));
            downloadPathProperty.set(prefs.get("save_path", System.getProperty("user.home") + "/Downloads"));
            pinProperty.set(prefs.get("device_pin", ""));

            // 1. 初始化网卡列表
            refreshIps();

            // 2. 读取上次保存的 IP，如果已失效则自动选第一个
            String savedIp = prefs.get("last_selected_ip", "");
            if (!availableIps.contains(savedIp)) {
                localIpProperty.set(availableIps.isEmpty() ? "127.0.0.1" : availableIps.get(0));
            } else {
                localIpProperty.set(savedIp);
            }

            // 3. 监听变化：当用户在设置里切换 IP 时，保存偏好并重新开始 UDP 广播
            localIpProperty.addListener((obs, old, nv) -> {
                prefs.put("last_selected_ip", nv);
                broadcastToAllInterfaces();
            });

            deviceNameProperty.addListener((o, old, nv) -> { prefs.put("device_name", nv); broadcastToAllInterfaces(); });
            downloadPathProperty.addListener((o, old, nv) -> prefs.put("save_path", nv));
            pinProperty.addListener((o, old, nv) -> { prefs.put("device_pin", nv); broadcastToAllInterfaces(); });

            try (ServerSocket s = new ServerSocket(0)) { tcpPort = s.getLocalPort(); } catch (Exception e) { tcpPort = 9010; }

            startUdpListener();
            startAutoRefreshTimer();
            startTcpServer();
        }

        private void refreshIps() {
            List<String> ips = new ArrayList<>();
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (ni.isLoopback() || !ni.isUp()) continue;
                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        if (ia.getAddress() instanceof Inet4Address) ips.add(ia.getAddress().getHostAddress());
                    }
                }
            } catch (Exception e) {}
            availableIps.setAll(ips);
        }

        public void addManualDevice(String ip, int port) {
            if (!deviceMap.containsKey(ip)) {
                DeviceInfo d = new DeviceInfo("手动连接", ip, port, false);
                deviceMap.put(ip, d);
                Platform.runLater(() -> deviceList.add(d));
            }
        }

        public void manualRefresh() {
            refreshIps(); // 刷新网卡列表（以防中途插拔）
            new Thread(this::broadcastToAllInterfaces).start();
        }

        private void startAutoRefreshTimer() {
            Thread t = new Thread(() -> {
                while (true) {
                    try {
                        broadcastToAllInterfaces();
                        Thread.sleep(5000);
                    } catch (InterruptedException e) { break; }
                }
            });
            t.setDaemon(true); t.start();
        }

        private void broadcastToAllInterfaces() {
            try {
                String pinFlag = (pinProperty.get() == null || pinProperty.get().isEmpty()) ? "0" : "1";
                // 协议 V3：广播时附带自己当前选定的 IP，方便对方回连
                String msg = "LOCALSEND_V3:" + deviceNameProperty.get() + ":" + tcpPort + ":" + pinFlag + ":" + localIpProperty.get();
                byte[] data = msg.getBytes();
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (ni.isLoopback() || !ni.isUp()) continue;
                    for (InterfaceAddress address : ni.getInterfaceAddresses()) {
                        InetAddress broadcast = address.getBroadcast();
                        if (broadcast == null) continue;
                        try (DatagramSocket socket = new DatagramSocket()) {
                            socket.setBroadcast(true);
                            socket.send(new DatagramPacket(data, data.length, broadcast, UDP_PORT));
                        } catch (Exception e) {}
                    }
                }
            } catch (Exception e) {}
        }

        private void startUdpListener() {
            new Thread(() -> {
                try (DatagramSocket ds = new DatagramSocket(UDP_PORT, InetAddress.getByName("0.0.0.0"))) {
                    byte[] buf = new byte[1024];
                    while (true) {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        ds.receive(p);
                        String data = new String(p.getData(), 0, p.getLength());

                        // 过滤逻辑：支持旧版 V2 和新版 V3 协议
                        if (data.startsWith("LOCALSEND_V2:") || data.startsWith("LOCALSEND_V3:")) {
                            String[] pts = data.split(":");
                            String newName = pts[1];
                            int port = Integer.parseInt(pts[2]);
                            boolean pinReq = pts[3].equals("1");

                            // 获取发送者 IP：如果是 V3 协议则使用报文内的 IP，否则使用包头 IP
                            String senderIp = (pts.length >= 5) ? pts[4] : p.getAddress().getHostAddress();

                            // 关键：不要显示自己（通过当前 localIpProperty 过滤）
                            if (senderIp.equals(localIpProperty.get())) continue;

                            Platform.runLater(() -> {
                                if (deviceMap.containsKey(senderIp)) {
                                    DeviceInfo existing = deviceMap.get(senderIp);
                                    existing.pinRequired = pinReq;
                                    if (!existing.name.equals(newName)) {
                                        existing.name = newName;
                                        int index = deviceList.indexOf(existing);
                                        if (index >= 0) deviceList.set(index, existing);
                                    }
                                } else {
                                    DeviceInfo d = new DeviceInfo(newName, senderIp, port, pinReq);
                                    deviceMap.put(senderIp, d);
                                    deviceList.add(d);
                                }
                            });
                        }
                    }
                } catch (Exception e) {}
            }).start();
        }

        private void startTcpServer() {
            new Thread(() -> {
                try (ServerSocket ss = new ServerSocket(tcpPort)) {
                    while (true) { Socket s = ss.accept(); handleReceive(s); }
                } catch (Exception e) {}
            }).start();
        }

        static class DeviceInfo {
            String name, ip;
            int port;
            boolean pinRequired;
            DeviceInfo(String n, String i, int p, boolean pin) {
                name = n; ip = i; port = p; this.pinRequired = pin;
            }
        }
    }

    static class DeviceCell extends ListCell<NetworkService.DeviceInfo> {
        @Override protected void updateItem(NetworkService.DeviceInfo i, boolean e) {
            super.updateItem(i, e);
            if (e || i == null) setGraphic(null);
            else {
                Label icon = new Label(i.pinRequired ? "🔒" : "💻");
                icon.setStyle("-fx-font-size: 24;");
                VBox text = new VBox(2, new Label(i.name), new Label(i.ip + (i.pinRequired ? " (需验证)" : "")));
                ((Label)text.getChildren().get(0)).setStyle("-fx-font-weight: bold;");
                ((Label)text.getChildren().get(1)).setStyle("-fx-text-fill: gray; -fx-font-size: 12;");
                HBox h = new HBox(15, icon, text);
                h.setAlignment(Pos.CENTER_LEFT); h.setPadding(new Insets(10));
                h.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 10; -fx-border-color: #eee; -fx-cursor: hand;");
                setGraphic(h);
            }
        }
    }

    public static void main(String[] args) { launch(args); }
}