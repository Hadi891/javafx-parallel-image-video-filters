package com;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jcodec.api.awt.AWTSequenceEncoder;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.media.*;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

public class GUI extends Application {

    // —— Video controls ——
    private Label selectedFileLabel;
    private ComboBox<String> filterComboVideo;
    private Spinner<Integer> threadSpinnerVideo;
    private Button runVideoButton;
    private Button clearVideoButton;
    private Label sequentialTimeLabelVideo;
    private Label parallelTimeLabelVideo;
    private Label speedupLabelVideo;
    private Label systemCpuLabelVideo;
    private MediaView mediaViewOriginal;
    private MediaView mediaViewSeq;
    private MediaView mediaViewPar;


    // —— Image controls ——
    private Label selectedImageLabel;
    private ComboBox<String> filterComboImage;
    private Spinner<Integer> threadSpinnerImage;
    private Button runImageButton;
    private Button clearImageButton;
    private ImageView imageViewOriginal;
    private ImageView imageViewSeq;
    private ImageView imageViewPar;
    private Label sequentialTimeLabelImage;
    private Label parallelTimeLabelImage;
    private Label speedupLabelImage;

    // Selected files
    private File selectedVideoFile;
    private File selectedImageFile;

    // For CPU metrics
    private final OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Load OpenCV
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        primaryStage.setTitle("Video & Image Filter Processor");
        TabPane tabPane = new TabPane();

        Tab videoTab = new Tab("Video", buildVideoTabContent(primaryStage));
        videoTab.setClosable(false);
        Tab imageTab = new Tab("Image", buildImageTabContent(primaryStage));
        imageTab.setClosable(false);

        tabPane.getTabs().addAll(videoTab, imageTab);
        primaryStage.setScene(new Scene(tabPane, 1200, 700));
        primaryStage.show();
    }

    /** Force‐load libs so first pass isn’t hit by JIT/warm‐up delays. */
    private void warmUpLibraries() {
        Mat dummy = new Mat(2, 2, CvType.CV_8UC3);
        new VideoProcessor().matToBufferedImage(dummy);
        dummy.release();

        try {
            File tmp = File.createTempFile("warmup", ".mp4");
            AWTSequenceEncoder enc = AWTSequenceEncoder.createSequenceEncoder(tmp, 1);
            enc.finish();
            tmp.delete();
        } catch (IOException ignored) {}

        BufferedImage tiny = new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB);
        FilterProcessor.applyFilterParallel(tiny, "blur", 1);
        FilterProcessor.applyFilterParallel(tiny, "log", 1);
        FilterProcessor.applyFilterParallel(tiny, "dog", 1);

        Runtime.getRuntime().gc();
    }

    /** Builds the “Video” tab UI. */
    private VBox buildVideoTabContent(Stage stage) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setAlignment(Pos.TOP_LEFT);

        // — File chooser —
        Button chooseFileBtn = new Button("Choose Input Video...");
        selectedFileLabel = new Label("No video selected");
        HBox fileRow = new HBox(10, chooseFileBtn, selectedFileLabel);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        // — Filter selector —
        filterComboVideo = new ComboBox<>();
        filterComboVideo.getItems().addAll("Blur","DoG","LoG");
        filterComboVideo.setPromptText("Select filter");
        HBox filterRow = new HBox(10, new Label("Filter:"), filterComboVideo);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        // — Thread count —
        threadSpinnerVideo = new Spinner<>(1,
                Runtime.getRuntime().availableProcessors()*2, 4);
        threadSpinnerVideo.setEditable(true);
        HBox threadsRow = new HBox(10,
                new Label("Parallel threads:"), threadSpinnerVideo);
        threadsRow.setAlignment(Pos.CENTER_LEFT);

        // — Run / Clear buttons —
        runVideoButton = new Button("Run Video Processing");
        runVideoButton.setDisable(true);
        clearVideoButton = new Button("Clear Video");
        clearVideoButton.setDisable(true);
        HBox runClearRow = new HBox(10, runVideoButton, clearVideoButton);
        runClearRow.setAlignment(Pos.CENTER_LEFT);

        // — Media previews —
        mediaViewOriginal = new MediaView(); mediaViewOriginal.setFitWidth(350); mediaViewOriginal.setPreserveRatio(true);
        mediaViewSeq      = new MediaView(); mediaViewSeq     .setFitWidth(350); mediaViewSeq     .setPreserveRatio(true);
        mediaViewPar      = new MediaView(); mediaViewPar     .setFitWidth(350); mediaViewPar     .setPreserveRatio(true);
        HBox previewRow = new HBox(10,
                new VBox(new Label("Original Video"), mediaViewOriginal),
                new VBox(new Label("Sequential Output"), mediaViewSeq),
                new VBox(new Label("Parallel Output"), mediaViewPar)
        );
        previewRow.setAlignment(Pos.CENTER);
        previewRow.setPadding(new Insets(10));

        // — Stats labels —
        sequentialTimeLabelVideo  = new Label("Sequential time (ms): –");
        parallelTimeLabelVideo    = new Label("Parallel time (ms): –");
        speedupLabelVideo         = new Label("Speedup       (seq ÷ par): –");
        systemCpuLabelVideo  = new Label("System CPU  (%): –");
        VBox resultsBox = new VBox(5,
                sequentialTimeLabelVideo,
                parallelTimeLabelVideo,
                speedupLabelVideo,
                systemCpuLabelVideo
        );
        resultsBox.setPadding(new Insets(10,0,0,0));

        root.getChildren().addAll(
                fileRow,
                filterRow,
                threadsRow,
                runClearRow,
                new Separator(),
                previewRow,
                new Separator(),
                resultsBox
        );

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Video File");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Video Files","*.mp4","*.avi","*.mov","*.mkv")
        );
        chooseFileBtn.setOnAction(e->{
            File f = chooser.showOpenDialog(stage);
            if(f!=null){
                selectedVideoFile = f;
                selectedFileLabel.setText(f.getAbsolutePath());
                MediaPlayer mp = new MediaPlayer(new Media(f.toURI().toString()));
                mediaViewOriginal.setMediaPlayer(mp);
                mp.setOnReady(()->{ mp.seek(Duration.ZERO); mp.pause(); });
                mp.setOnEndOfMedia(()-> mp.seek(Duration.ZERO));
                clearVideoButton.setDisable(false);
                filterComboVideo.valueProperty().addListener((o,ov,nv)->
                        runVideoButton.setDisable(nv==null)
                );
            }
        });

        runVideoButton.setOnAction(e->runVideoProcessing());

        clearVideoButton.setOnAction(e->{
            if(mediaViewOriginal.getMediaPlayer()!=null) mediaViewOriginal.getMediaPlayer().dispose();
            if(mediaViewSeq.getMediaPlayer()!=null) mediaViewSeq.getMediaPlayer().dispose();
            if(mediaViewPar.getMediaPlayer()!=null) mediaViewPar.getMediaPlayer().dispose();
            mediaViewOriginal.setMediaPlayer(null);
            mediaViewSeq.setMediaPlayer(null);
            mediaViewPar.setMediaPlayer(null);

            selectedVideoFile = null;
            selectedFileLabel.setText("No video selected");
            filterComboVideo.getSelectionModel().clearSelection();
            runVideoButton.setDisable(true);
            clearVideoButton.setDisable(true);
            sequentialTimeLabelVideo.setText("Sequential time (ms): –");
            parallelTimeLabelVideo.setText("Parallel time (ms): –");
            speedupLabelVideo.setText("Speedup       (seq ÷ par): –");
            systemCpuLabelVideo.setText("System CPU  (%): –");
            Runtime.getRuntime().gc();
        });

        installMediaHoverBehavior(mediaViewOriginal, stage);
        installMediaHoverBehavior(mediaViewSeq, stage);
        installMediaHoverBehavior(mediaViewPar, stage);

        return root;
    }


    private void installMediaHoverBehavior(MediaView mediaView, Stage stage) {
        Popup popup = new Popup();

        MediaView enlarged = new MediaView();
        enlarged.setPreserveRatio(true);
        enlarged.setSmooth(true); // optional: makes video scaling nicer

        StackPane pane = new StackPane(enlarged);
        pane.setAlignment(Pos.CENTER);  // 👈 ensures video is centered
        pane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75);");
        pane.setPadding(new Insets(10));
        popup.getContent().add(pane);
        pane.setUserData(enlarged);

        mediaView.setOnMouseEntered(e -> {
            MediaPlayer player = mediaView.getMediaPlayer();
            if (player != null) {
                MediaPlayer enlargedPlayer = new MediaPlayer(player.getMedia());
                enlarged.setMediaPlayer(enlargedPlayer);
                enlargedPlayer.setMute(true);
                enlargedPlayer.setOnReady(() -> {
                    enlargedPlayer.seek(Duration.ZERO);
                    enlargedPlayer.play();
                });

                enlarged.setFitWidth(stage.getWidth() * 0.75);
                enlarged.setFitHeight(stage.getHeight() * 0.75);

                popup.setOnShown(ev -> {
                    double popupWidth = popup.getWidth();
                    double popupHeight = popup.getHeight();

                    Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
                    double popupX = screenBounds.getMinX() + (screenBounds.getWidth() - popupWidth) / 2;
                    double popupY = screenBounds.getMinY() + (screenBounds.getHeight() - popupHeight) / 2;

                    popup.setX(popupX);
                    popup.setY(popupY);
                });

                popup.show(stage);
            }
        });


        mediaView.setOnMouseExited(e -> {
            MediaPlayer enlargedPlayer = enlarged.getMediaPlayer();
            if (enlargedPlayer != null) {
                enlargedPlayer.stop();
                enlargedPlayer.dispose();
            }
            popup.hide();
        });
    }


    /** Runs sequential then parallel passes for video. */
    private void runVideoProcessing() {
        warmUpLibraries();
        if(selectedVideoFile==null||filterComboVideo.getValue()==null){
            showAlert("Missing selection","Please choose both a video file and a filter.");
            return;
        }
        runVideoButton.setDisable(true);
        filterComboVideo.setDisable(true);
        threadSpinnerVideo.setDisable(true);

        String filter = filterComboVideo.getValue().toLowerCase();
        int threads = threadSpinnerVideo.getValue();
        String path = selectedVideoFile.getAbsolutePath();

        String name = selectedVideoFile.getName();
        String base = name.contains(".")?name.substring(0,name.lastIndexOf('.')):name;
        String dir = System.getProperty("user.dir")+"/outputs/videos";
        new File(dir).mkdirs();
        String seqFile = dir+"/"+base+"_"+filter+"_sequential.mp4";
        String parFile = dir+"/"+base+"_"+filter+"_parallel.mp4";

        sequentialTimeLabelVideo.setText("Sequential time (ms): –");
        parallelTimeLabelVideo.setText("Parallel time (ms): –");
        speedupLabelVideo.setText("Speedup       (seq ÷ par): –");
        systemCpuLabelVideo.setText("System CPU (%): -");
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                Runtime rt = Runtime.getRuntime();
                Mat frame = new Mat();

                // Sequential
                VideoCapture cap1 = new VideoCapture(path);
                if(!cap1.isOpened()) throw new RuntimeException("Cannot open video");
                int fps = (int)Math.round(cap1.get(Videoio.CAP_PROP_FPS));
                AWTSequenceEncoder enc1 = AWTSequenceEncoder.createSequenceEncoder(new File(seqFile), fps);

                rt.gc(); Thread.sleep(100);
                long memBefore1 = rt.totalMemory()-rt.freeMemory();
                long t0 = System.nanoTime();
                while(cap1.read(frame)){
                    BufferedImage in = new VideoProcessor().matToBufferedImage(frame);
                    BufferedImage out = FilterProcessor.applyFilterParallel(in, filter, 1);
                    enc1.encodeImage(out);
                }
                cap1.release();
                enc1.finish();
                long t1 = (System.nanoTime()-t0)/1_000_000;
                long memAfter1 = rt.totalMemory()-rt.freeMemory();
                long used1 = memAfter1-memBefore1;
                frame.release();

                Platform.runLater(()->{
                    MediaPlayer mp = new MediaPlayer(new Media(new File(seqFile).toURI().toString()));
                    mediaViewSeq.setMediaPlayer(mp);
                    mp.setOnReady(()->{mp.seek(Duration.ZERO);mp.pause();});
                    mp.setOnEndOfMedia(()->mp.seek(Duration.ZERO));
                    sequentialTimeLabelVideo.setText(String.format("Sequential time (ms): %d",t1));
                });

                // Parallel

                VideoCapture cap2 = new VideoCapture(path);
                if(!cap2.isOpened()) throw new RuntimeException("Cannot open video");
                AWTSequenceEncoder enc2 = AWTSequenceEncoder.createSequenceEncoder(new File(parFile), fps);

                rt.gc(); Thread.sleep(100);
                long memBefore2 = rt.totalMemory()-rt.freeMemory();
                long t2Start = System.nanoTime();
                CpuSampler cpuSampler = new CpuSampler(osBean);
                cpuSampler.start();

                while(cap2.read(frame)){
                    BufferedImage in = new VideoProcessor().matToBufferedImage(frame);
                    BufferedImage out = FilterProcessor.applyFilterParallel(in, filter, threads);
                    enc2.encodeImage(out);
                }
                cap2.release();
                enc2.finish();
                long t2 = (System.nanoTime()-t2Start)/1_000_000;
                long memAfter2 = rt.totalMemory()-rt.freeMemory();
                long used2 = memAfter2-memBefore2;
                cpuSampler.stopSampling();
                cpuSampler.join();
                double avgCpu = cpuSampler.getAverageCpuPercent();
                frame.release();

                double speedup = t1/(double)t2;
                double memRatio = used2/(double)used1;

                Platform.runLater(()->{
                    MediaPlayer orig = mediaViewOriginal.getMediaPlayer();
                    if(orig!=null){orig.seek(Duration.ZERO);orig.play();}
                    MediaPlayer seq = mediaViewSeq.getMediaPlayer();
                    if(seq!=null) seq.play();
                    MediaPlayer par = new MediaPlayer(new Media(new File(parFile).toURI().toString()));
                    mediaViewPar.setMediaPlayer(par);
                    par.setOnReady(()->{par.seek(Duration.ZERO);par.play();});
                    par.setOnEndOfMedia(()->par.seek(Duration.ZERO));

                    parallelTimeLabelVideo.setText(String.format("Parallel time (ms): %d",t2));
                    speedupLabelVideo.setText(String.format("Speedup       (seq ÷ par): %.2f×",speedup));
                    systemCpuLabelVideo.setText(String.format("System CPU  (%%): %.1f%%", avgCpu));
                    runVideoButton.setDisable(false);
                    filterComboVideo.setDisable(false);
                    threadSpinnerVideo.setDisable(false);
                    Runtime.getRuntime().gc();
                });
                return null;
            }
        };
        task.setOnFailed(e->Platform.runLater(()->{
            showAlert("Error",task.getException().getMessage());
            runVideoButton.setDisable(false);
            filterComboVideo.setDisable(false);
            threadSpinnerVideo.setDisable(false);
        }));
        new Thread(task).start();
    }

    //───────────────────────────────────────────────────────────────────────────
    private VBox buildImageTabContent(Stage stage) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setAlignment(Pos.TOP_LEFT);

        HBox fileRow = new HBox(10);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        Button chooseImageBtn = new Button("Choose Input Image...");
        selectedImageLabel = new Label("No image selected");
        fileRow.getChildren().addAll(chooseImageBtn, selectedImageLabel);

        HBox filterRow = new HBox(10);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.getChildren().add(new Label("Filter:"));
        filterComboImage = new ComboBox<>();
        filterComboImage.getItems().addAll("Blur","DoG","LoG");
        filterComboImage.setPromptText("Select filter");
        filterRow.getChildren().add(filterComboImage);

        HBox threadsRow = new HBox(10);
        threadsRow.setAlignment(Pos.CENTER_LEFT);
        threadSpinnerImage = new Spinner<>(1,Runtime.getRuntime().availableProcessors()*2,4);
        threadSpinnerImage.setEditable(true);
        threadsRow.getChildren().addAll(new Label("Parallel threads:"), threadSpinnerImage);

        HBox runClearRow = new HBox(10);
        runClearRow.setAlignment(Pos.CENTER_LEFT);
        runImageButton = new Button("Run Image Filter");
        runImageButton.setDisable(true);
        clearImageButton = new Button("Clear Image");
        clearImageButton.setDisable(true);
        runClearRow.getChildren().addAll(runImageButton, clearImageButton);

        HBox preview = new HBox(10);
        preview.setAlignment(Pos.CENTER);
        preview.setPadding(new Insets(10));
        imageViewOriginal = new ImageView(); imageViewOriginal.setFitWidth(250); imageViewOriginal.setPreserveRatio(true);
        imageViewSeq      = new ImageView(); imageViewSeq     .setFitWidth(250); imageViewSeq     .setPreserveRatio(true);
        imageViewPar      = new ImageView(); imageViewPar     .setFitWidth(250); imageViewPar     .setPreserveRatio(true);
        preview.getChildren().addAll(
                new VBox(new Label("Original Image"), imageViewOriginal),
                new VBox(new Label("Sequential Output"), imageViewSeq),
                new VBox(new Label("Parallel Output"), imageViewPar)
        );

        VBox resultsBox = new VBox(5);
        resultsBox.setPadding(new Insets(10,0,0,0));
        sequentialTimeLabelImage = new Label("Sequential time (ms): –");
        parallelTimeLabelImage   = new Label("Parallel time (ms): –");
        speedupLabelImage        = new Label("Speedup       (seq ÷ par): –");
        resultsBox.getChildren().addAll(
                sequentialTimeLabelImage,
                parallelTimeLabelImage,
                speedupLabelImage
        );

        root.getChildren().addAll(
                fileRow,
                filterRow,
                threadsRow,
                runClearRow,
                new Separator(),
                preview,
                new Separator(),
                resultsBox
        );

        Popup p1 = createHoverPopup(stage), p2 = createHoverPopup(stage), p3 = createHoverPopup(stage);
        installHoverBehavior(imageViewOriginal,p1,stage);
        installHoverBehavior(imageViewSeq,p2,stage);
        installHoverBehavior(imageViewPar,p3,stage);

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Image File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files","*.png","*.jpg","*.jpeg","*.bmp")
        );
        chooseImageBtn.setOnAction(e->{
            File f=chooser.showOpenDialog(stage);
            if(f!=null){
                selectedImageFile=f;
                selectedImageLabel.setText(f.getAbsolutePath());
                clearImageButton.setDisable(false);
                filterComboImage.valueProperty().addListener((o,ov,nv)->
                        runImageButton.setDisable(nv==null)
                );
                try{
                    BufferedImage bi=ImageIO.read(f);
                    imageViewOriginal.setImage(SwingFXUtils.toFXImage(bi,null));
                }catch(IOException ex){
                    showAlert("Error loading image",ex.getMessage());
                }
                imageViewSeq.setImage(null);
                imageViewPar.setImage(null);
                sequentialTimeLabelImage.setText("Sequential time (ms): –");
                parallelTimeLabelImage.setText("Parallel time (ms): –");
                speedupLabelImage.setText("Speedup       (seq ÷ par): –");
            }
        });

        runImageButton.setOnAction(e->runImageProcessing());
        clearImageButton.setOnAction(e->{
            imageViewOriginal.setImage(null);
            imageViewSeq.setImage(null);
            imageViewPar.setImage(null);
            selectedImageFile=null;
            selectedImageLabel.setText("No image selected");
            runImageButton.setDisable(true);
            clearImageButton.setDisable(true);
            filterComboImage.getSelectionModel().clearSelection();
            filterComboImage.setDisable(false);
            threadSpinnerImage.setDisable(false);
            sequentialTimeLabelImage.setText("Sequential time (ms): –");
            parallelTimeLabelImage.setText("Parallel time (ms): –");
            speedupLabelImage.setText("Speedup       (seq ÷ par): –");
            Runtime.getRuntime().gc();
        });

        return root;
    }

    private Popup createHoverPopup(Stage owner){
        Popup pop=new Popup();pop.setAutoHide(false);pop.setAutoFix(true);
        ImageView iv=new ImageView();iv.setPreserveRatio(true);
        StackPane sp=new StackPane(iv);
        sp.setStyle("-fx-background-color: rgba(0,0,0,0.75);");
        sp.setPadding(new Insets(10));
        pop.getContent().add(sp);
        sp.setUserData(iv);
        return pop;
    }

    private void installHoverBehavior(ImageView thumb,Popup pop,Stage owner){
        thumb.setOnMouseEntered(ev->{
            Image img=thumb.getImage();
            if(img!=null){
                StackPane sp=(StackPane)pop.getContent().get(0);
                ImageView iv=(ImageView)sp.getUserData();
                iv.setImage(img);
                double w=owner.getWidth()*0.75, h=owner.getHeight()*0.75;
                iv.setFitWidth(w);iv.setFitHeight(h);
                double x=owner.getX()+(owner.getWidth()-iv.getBoundsInParent().getWidth())/2;
                double y=owner.getY()+(owner.getHeight()-iv.getBoundsInParent().getHeight())/2;
                pop.show(owner,x,y);
            }
        });
        thumb.setOnMouseExited(ev->pop.hide());
    }

    private void runImageProcessing(){
        warmUpLibraries();
        if(selectedImageFile==null||filterComboImage.getValue()==null){
            showAlert("Missing selection","Please choose an image file and a filter.");
            return;
        }
        runImageButton.setDisable(true);
        filterComboImage.setDisable(true);
        threadSpinnerImage.setDisable(true);

        String filter=filterComboImage.getValue().toLowerCase();
        int threads=threadSpinnerImage.getValue();
        String path=selectedImageFile.getAbsolutePath();

        String name=selectedImageFile.getName();
        String base=name.contains(".")?name.substring(0,name.lastIndexOf('.')):name;
        String dir=System.getProperty("user.dir")+"/outputs/images";
        new File(dir).mkdirs();
        String seqFile=dir+"/"+base+"_"+filter+"_sequential.png";
        String parFile=dir+"/"+base+"_"+filter+"_parallel.png";

        Task<Void> task=new Task<>() {
            @Override protected Void call() throws Exception {
                BufferedImage src=ImageIO.read(new File(path));
                Runtime rt=Runtime.getRuntime();

                rt.gc();Thread.sleep(100);
                long mem1=rt.totalMemory()-rt.freeMemory();
                long t0=System.nanoTime();
                BufferedImage out1=FilterProcessor.applyFilterParallel(src,filter,1);
                long dt1=(System.nanoTime()-t0)/1_000_000;
                long used1=(rt.totalMemory()-rt.freeMemory())-mem1;
                ImageIO.write(out1,"png",new File(seqFile));
                Image fx1=SwingFXUtils.toFXImage(out1,null);

                Platform.runLater(()->{
                    imageViewSeq.setImage(fx1);
                    sequentialTimeLabelImage.setText(String.format("Sequential time (ms): %d",dt1));
                });

                rt.gc();Thread.sleep(100);
                long mem2=rt.totalMemory()-rt.freeMemory();
                long t1=System.nanoTime();

                BufferedImage out2=FilterProcessor.applyFilterParallel(src,filter,threads);


                long dt2=(System.nanoTime()-t1)/1_000_000;
                long used2=(rt.totalMemory()-rt.freeMemory())-mem2;

                ImageIO.write(out2,"png",new File(parFile));
                Image fx2=SwingFXUtils.toFXImage(out2,null);

                double speedup=dt1/(double)dt2;
                double memRatio=used2/(double)used1;

                Platform.runLater(()->{
                    imageViewPar.setImage(fx2);
                    parallelTimeLabelImage.setText(String.format("Parallel time (ms): %d",dt2));
                    speedupLabelImage.setText(String.format("Speedup       (seq ÷ par): %.2f×",speedup));
                    runImageButton.setDisable(false);
                    filterComboImage.setDisable(false);
                    threadSpinnerImage.setDisable(false);
                    Runtime.getRuntime().gc();
                });

                return null;
            }
        };
        task.setOnFailed(ev->Platform.runLater(()->{
            showAlert("Error",task.getException().getMessage());
            runImageButton.setDisable(false);
            filterComboImage.setDisable(false);
            threadSpinnerImage.setDisable(false);
        }));
        new Thread(task).start();
    }

    private void showAlert(String header,String content){
        Alert a=new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(header);
        a.setContentText(content);
        a.showAndWait();
    }


    private class CpuSampler extends Thread {
        private volatile boolean running = true;
        private final OperatingSystemMXBean osBean;
        private final List<Double> samples = new ArrayList<>();

        public CpuSampler(OperatingSystemMXBean osBean) {
            this.osBean = osBean;
        }

        @Override
        public void run() {
            while (running) {
                double load = osBean.getSystemCpuLoad();
                if (load >= 0) {
                    samples.add(load);
                }
                try {
                    Thread.sleep(100); // sample every 100ms
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }

        public void stopSampling() {
            running = false;
        }

        public double getAverageCpuPercent() {
            List<Double> filtered = samples.stream()
                    .filter(d -> d < 1.0) // exclude samples equal to 100%
                    .sorted(Comparator.reverseOrder())
                    .limit(5)
                    .toList();

            double avg = filtered.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(100.0)
                * 100.0;

            return Math.floor(avg);
        }

    }
}



