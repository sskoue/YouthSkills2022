package application;
	
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.transform.Affine;


public class Main extends Application {
	
	
	public static void main(String[] args) {
		launch(args);
	}

	
	// DB接続
    private static final String url = "jdbc:postgresql://localhost:5432/postgres" ;
    private static final String user = "postgres";
    private static final String password = "P@ssw0rd" ;
    
    // 広島市の画像
    private Image originalImage = null;		// 元画像
    private int original_w = 0;
    private int original_h = 0;
    private Canvas originalCanvas = null;
    
    // クリック処理
    private Point2D clickPoint = null;		// クリックされた座標(キャンバス上の座標)
    private int px = 0;			// クリックされた座標(キャンバス上の座標)
    private int py = 0;			// クリックされた座標(キャンバス上の座標)
    
    // ドラッグ処理
    private Point2D prevDragPoint = null;	// ドラッグの始点の座標(ウィンドウ上の座標)
    private int move_x = 0;		// ドラッグしたときのX方向の移動量
    private int move_y = 0;		// ドラッグしたときのY方向の移動量
    
    // ワールド座標系
    private static final int psize_w = 500;	// キャンバスの幅
    private static final int psize_h = 500;
    private static final int frame_w = 4;	// 外枠の幅
    
    // ローカル座標系
    private double scaleRatio = 1.0;		// 表示倍率
    private int lx = 0;			// 元画像のうち描画される範囲の左上のX座標
    private int ly = 0;			// 元画像のうち描画される範囲の左上のY座標
    private int lsize = 0;		// 元画像のうち描画される範囲の幅
    private int lsizeMax = 0;	// 元画像から描画できる範囲の最大幅
    private int lsizeMin = 0;	// 元画像から描画できる範囲の最小幅
    
    // 単位距離
    private int dist = 42000;
    private double unit_dist = 0;	// 1メートルが何ピクセルか
	
	// GUIパーツ
    private Canvas canvas = null;
    private CheckBox cb_reverseX = null;
    private CheckBox cb_reverseY = null;
    private Slider slider = null;
    private CheckBox cb_2g = null;
    private CheckBox cb_800m = null;
    private CheckBox cb_virtual = null;
    private TextField txtField_dist = null;
    private Label lbl_distError = null;
    private ListView<String> listView = null; 
    private ObservableList<String> list_baseStation = null;
    private Button btn_delete = null;
    private Button btn_edit = null;
    private TextField txtField_configX = null;
    private TextField txtField_configY = null;
    private ComboBox<String> comboBox_frequency = null;
    private ComboBox<Integer> comboBox_radioStrength = null;	
	
	// 見出し用のフォント
	Font font_index = new Font("Tahoma", 15);

	@Override
	public void start(Stage primaryStage) {
		
		// 画像読み込み
		LoadImage();
		
		// 土台を作成
		HBox root = new HBox();
		VBox left = new VBox();
		VBox right = new VBox();
		right.setPadding(new Insets(50));	//右側土台の余白
		right.setSpacing(10);
		root.getChildren().addAll(left, right);
		
		// GUIを組み立て
		guiMap(left);
		guiGuide(left);
		HBox canvasOption = new HBox();
		canvasOption.setSpacing(20);
		canvasOption.setPadding(new Insets(30, 50, 50, 50));
		left.getChildren().add(canvasOption);
		guiHandle(canvasOption);
		guiDisplay(canvasOption);
		guiDistance(canvasOption);
		guiListView(right);
		guiEditBaseSta(right);
		
		// シーンを作成
		Scene scene = new Scene(root, 1200, 850);
				
		// ウィンドウ表示
		primaryStage.setScene(scene);
		primaryStage.setTitle("基地局マップ");	//ウィンドウのタイトルを設定
		primaryStage.setResizable(false);
		primaryStage.show();		
		draw();
	}
	
	/**
	 *  キャンバスに描画
	 */
	private void draw() {
		GraphicsContext gc = canvas.getGraphicsContext2D();
		originalCanvas = new Canvas(original_w, original_h);
		GraphicsContext originalGc = originalCanvas.getGraphicsContext2D();
		originalGc.drawImage(originalImage, 0, 0);	//画像を描画
		drawData(originalGc);	//基地局データを表示		
		WritableImage snap = originalCanvas.snapshot(null, null);
		Affine af = new Affine();					//
		af.appendScale(scaleRatio, scaleRatio);		//計算した表示倍率でアフィン変換
		gc.setTransform(af);						//
		WritableImage resizedImage = new WritableImage(snap.getPixelReader(), lx, ly, lsize, lsize);	//元画像のうち描画する範囲を指定して切り抜く
		gc.drawImage(resizedImage, 0, 0);	//画像を描画
	}

	/**
	 * DBから取得した基地局情報を加工して表示
	 */
	private void drawData(GraphicsContext gc) {
		gc.setGlobalAlpha(0.5);		//キャンバスに描画するときの不透明度を下げる
		list_baseStation.clear();
		ResultSet data = getDataFromDB();
		try {
			while (data.next()) {
				String id = data.getString(1);			//ID取得
				int baseStationX = data.getInt(2);		//基地局のX座標を取得
				int baseStationY = data.getInt(3);		//基地局のY座標を取得
				String frequency = data.getString(4);	//周波数を取得
				int radio_strength = data.getInt(5);	//電波強度
				int diameter = 0;			//電波の直径
				String isVirtual = data.getString(6);
				if (isVirtual.equals("t")) {
					if (cb_virtual.isSelected()) {
						isVirtual = "  (仮想)";
					} else {
						break;
					}
				} else {
					isVirtual = "";
				}
				if ((frequency.equals("2GHz") && cb_2g.isSelected()) || (frequency.equals("800MHz") && cb_800m.isSelected())) {
					diameter = (frequency.equals("2GHz")) ? 3000 : 9000;
					String line = "ID:" + id + " X:" + baseStationX + " Y:" + baseStationY + " 周波数:" + frequency + " 電波強度:" + radio_strength + isVirtual;
					list_baseStation.add(line);
					drawBaseStation(gc, id, baseStationX, baseStationY, diameter, radio_strength, data.getString(6));	//キャンバスに描画
				}
			}
		} catch (SQLException e) {
	        e.printStackTrace();	//本番はコメントアウト	
		}
		gc.setGlobalAlpha(1.0);		//キャンバスに描画するときの不透明度を元に戻す
	}
	
	/**
	 *  データベースに接続して検索
	 *  @return ResultSetを返します
	 */
	private ResultSet getDataFromDB() {
        // SELECT文の作成
        int xEnd = lx + lsize;
        int yEnd = ly + lsize;
        String sql = 
        		"SELECT * "
        		+ "from base_stations "
        		+ "where x >= " + lx + " and x <= " + xEnd + " and y >= " + ly + " and y <= " + yEnd;
        // PostgreSQLに接続
        ResultSet result = null;
        try {
        	Connection con = DriverManager.getConnection ( url, user, password );
        	try {
                Statement stmt = con.createStatement();
                result = stmt.executeQuery(sql);
        	} finally {
        		con.close();
        	}
        } catch ( SQLException e ) {
            e.printStackTrace() ;	//本番はコメントアウト
        }
        return result;
	}
	
	/**
	 * キャンバスに基地局を描画
	 */
	private void drawBaseStation(GraphicsContext gc, String id, int X, int Y, int diameter, int radio_strength, String isVirtual) {
		switch (radio_strength) {
			case 5:
				gc.setFill(Color.RED);
				break;
			case 4:
				gc.setFill(Color.YELLOW);
				break;
			case 3:
				gc.setFill(Color.GREEN);
				break;
			case 2:
				gc.setFill(Color.LIGHTBLUE);
				break;
			case 1:
				gc.setFill(Color.BLUE);
				break;
			case 0:
				gc.setFill(Color.PURPLE);
				break;
		}
		int newSize = (int)((double)diameter * unit_dist);
		gc.fillOval(X - newSize / 2, Y - newSize / 2, newSize, newSize);
		gc.setGlobalAlpha(1);
		gc.setFill(Color.WHITE);
		gc.fillRect(X - 25, Y - 15, 50, 30);
		gc.setFont(new Font(30));
		gc.setFill(Color.BLACK);
		gc.fillText(id, X - 15, Y + 12);
		if (isVirtual.equals("t")) {
			gc.setFill(Color.ORANGE);
			gc.fillRect(X - 30, Y + 15, 60, 30);
			gc.setFont(new Font(30));
			gc.setFill(Color.BLACK);
			gc.fillText("仮想", X - 30, Y + 42);
		}
		gc.setGlobalAlpha(0.5);
	}

   /**
     *  画像読み込み処理
     */
    private void LoadImage() {
 		originalImage = new Image("hiroshima.png");	//イメージ読み込み
		
		original_h = (int) originalImage.getHeight();	//元画像の縦のピクセル数
		original_w = (int)originalImage.getWidth();		//元画像の横のピクセル数
		if (original_h > original_w) {	//縦、横のうち短いほうをlsizeMaxにする
			lsizeMax = original_w - 1;	//座標は0から始まるので1ひく
		} else {
			lsizeMax = original_h - 1;
		}
		lsizeMin = lsizeMax / 10;	//描画する範囲の最大幅の1/10を最小幅にする
		lsize = lsizeMax;			//初期に描画する幅を最大にする
		scaleRatio = (double) psize_w / (double) lsize;	//表示倍率
		unit_dist = (double)lsize / (double)dist;
    }
    
    /**
     *  GUIのマップ
     */
    private void guiMap(VBox root) {
		// 矩形でキャンバスの枠を作成
		Rectangle rect_frame = new Rectangle(psize_w + (frame_w * 2), psize_h + (frame_w * 2));
		rect_frame.setFill(Color.GRAY);
		// キャンバスを作成
		canvas = new Canvas(psize_w, psize_h);
		// キャンバス上でクリックしたとき
		canvas.setOnMousePressed(ev -> {
			prevDragPoint = new Point2D(ev.getSceneX(), ev.getSceneY());	//ドラッグの始点の座標（ウィンドウ上の座標）を取得
			clickPoint = new Point2D(ev.getSceneX() - 54, ev.getSceneY() - 54);		// クリックされた座標(キャンバス上の座標)
			px = (int)(clickPoint.getX());		//クリックされたX座標
			py = (int)(clickPoint.getY());		//クリックされたY座標
			txtField_configX.setText(Integer.toString((int)(lx + px / scaleRatio)));
			txtField_configY.setText(Integer.toString((int)(ly + py / scaleRatio)));
		});
		// キャンバス上でドラッグしたとき
		canvas.setOnMouseDragged(ev -> {	//ドラッグしたとき
			int reverseX = -1;	//X方向反転フラグ
			int reverseY = -1;	//Y方向反転フラグ
			if (cb_reverseX.isSelected()) {		//チェックボックスの状態に応じてフラグを切り替え
				reverseX = 1;
			}
			if (cb_reverseY.isSelected()) {
				reverseY = 1;
			}
			move_x = (int)(( ev.getSceneX() - prevDragPoint.getX() ) * slider.getValue() * reverseX);	//X方向の移動量　[移動量 = (現在座標 - 視点座標) * 感度 * 反転フラグ]
			move_y = (int)(( ev.getSceneY() - prevDragPoint.getY() ) * slider.getValue() * reverseY);	//Y方向の移動量
			lx += move_x;	//X方向の移動量を加算
			if (lx < 0) {	//lxが左端を超えていたら、
				lx = 0;		//左端から描画するようにする
			} else if (lx >= original_w - lsize) {	//lxの値が、描画すると右端を超えるような値の場合、
				lx = original_w - lsize - 1;		//右端まで描画するようにする
			}
			ly += move_y;	//Y方向の移動量を加算
			if (ly < 0) {	//lyが上端を超えていたら、
				ly = 0;		//lyを上端の値にする
			} else if (ly >= original_h - lsize) {	//lyの値が、描画すると下端を超えるような値の場合、
				ly = original_h - lsize - 1;		//下端まで描画するようにする
			}

			Point2D dragPoint = new Point2D(ev.getSceneX(),ev.getSceneY());	//ドラッグ後の座標を取得
			prevDragPoint = dragPoint;	//現在座標を更新
			draw();
		});			
		// キャンバス上でスクロールして拡大縮小
		canvas.setOnScroll(ev -> {
			if (ev.getDeltaY() > 0 && lsize != lsizeMin) {			//上にスクロール（lsizeが最小値でなければ拡大）
				lx += 20;		//lxを右にずらす
				ly += 20;		//lyを下にずらす
				lsize -= 40;	//描画する幅を小さくする
				if (lsize < lsizeMin) {		//lsizeが最小幅より小さい場合、
					lsize = lsizeMin;		//lsizeMin(定数)の値をlsizeに代入
				}
			}
			else if (ev.getDeltaY() < 0 && lsize != lsizeMax) {		//下にスクロール（lsizeが最大値でなければ縮小)
				lx -= 20;		//lxの値を左にずらす
				ly -= 20;		//lyの値を上にずらす
				lsize += 40;	//描画する幅を大きくする     
				if (lsize > lsizeMax) {		//lsizeが最大幅より大きい場合、
					lsize = lsizeMax;		//lsizeMax(定数)の値をlsizeに代入
				}
				if (lx < 0) {	//lxが左端を超えていたら、
					lx = 0;		//左端から描画するようにする
				}
				if (ly < 0) {	//lyが上端を超えていたら、
					ly = 0;		//lyを上端の値にする
				}
				if (lx + lsize >= original_w) {		//描画範囲が右端を超えていた場合、
					lsize = original_w - lx - 1;	//右端まで描画するようにlsizeの値を設定
				}
				if (ly + lsize >= original_h) {		//描画範囲が下端を超えていた場合、
					lsize = original_h - ly - 1;	//下端まで描画するようにlsizeの値を設定
				}				
			}
			scaleRatio = (double) psize_w / (double) lsize;	//表示倍率を計算
			txtField_dist.setText(Integer.toString((int) (lsize / unit_dist)));
			draw();
			System.out.println(lx);
			System.out.println(ly);
			System.out.println(lsize);
		});
		// 配置
		StackPane canvasRoot = new StackPane();
		canvasRoot.setPadding(new Insets(50, 50, 10, 50));	//キャンバスの土台の余白
		canvasRoot.getChildren().addAll(rect_frame, canvas);
		root.getChildren().add(canvasRoot);
    }
    
    /**
     *  GUIの凡例を作成
     *  
     */
    private void guiGuide(VBox root) {
		//　凡例用ラベル
		Label lbl_guide = new Label("電波強度   ");
		Label lbl_rs0 = new Label("0");
		Label lbl_rs1 = new Label("1");
		Label lbl_rs2 = new Label("2");
		Label lbl_rs3 = new Label("3");
		Label lbl_rs4 = new Label("4");
		Label lbl_rs5 = new Label("5");
		//　凡例用の矩形
		Rectangle rect_0 = new Rectangle(10, 10);
		rect_0.setFill(Color.PURPLE);
		rect_0.setOpacity(0.5);
		Rectangle rect_1 = new Rectangle(10, 10);
		rect_1.setFill(Color.BLUE);
		rect_1.setOpacity(0.5);
		Rectangle rect_2 = new Rectangle(10, 10);
		rect_2.setFill(Color.LIGHTBLUE);
		rect_2.setOpacity(0.5);
		Rectangle rect_3 = new Rectangle(10, 10);
		rect_3.setFill(Color.GREEN);
		rect_3.setOpacity(0.5);
		Rectangle rect_4 = new Rectangle(10, 10);
		rect_4.setFill(Color.YELLOW);
		rect_4.setOpacity(0.5);
		Rectangle rect_5 = new Rectangle(10, 10);
		rect_5.setFill(Color.RED);
		rect_5.setOpacity(0.5);
		// 配置
		GridPane canvasGuideRoot = new GridPane();
		canvasGuideRoot.setPadding(new Insets(0, 50, 0, 50));
		canvasGuideRoot.add(rect_0, 1, 0);
		canvasGuideRoot.add(rect_1, 2, 0);
		canvasGuideRoot.add(rect_2, 3, 0);
		canvasGuideRoot.add(rect_3, 4, 0);
		canvasGuideRoot.add(rect_4, 5, 0);
		canvasGuideRoot.add(rect_5, 6, 0);
		canvasGuideRoot.add(lbl_guide, 0, 1);
		canvasGuideRoot.add(lbl_rs0, 1, 1);
		canvasGuideRoot.add(lbl_rs1, 2, 1);
		canvasGuideRoot.add(lbl_rs2, 3, 1);
		canvasGuideRoot.add(lbl_rs3, 4, 1);
		canvasGuideRoot.add(lbl_rs4, 5, 1);
		canvasGuideRoot.add(lbl_rs5, 6, 1);
		root.getChildren().add(canvasGuideRoot);	
    }
    
    /**
     *  GUIの「操作」を作成
     */
    private void guiHandle(HBox root) {
    	// ラベルを作成
		Label lbl_handle = new Label("操作");
		lbl_handle.setFont(font_index);
		Label lbl_sensitivity = new Label("ドラッグ感度");
		// チェックボックスを作成
 		cb_reverseX = new CheckBox("X方向反転");
		cb_reverseY = new CheckBox("Y方向反転");
		//　スライダーを作成
		slider = new Slider();
		slider.setMin(0.1);				//最小値
		slider.setMax(5.0);				//最大値
		slider.setValue(1.0);			//初期値
		slider.setMajorTickUnit(1.0);	//大目盛の間隔
		slider.setShowTickLabels(true);	//メモリ値の表示
		slider.setShowTickMarks(true);	//メモリ線の表示
		// 配置
    	VBox handleRoot = new VBox();
		handleRoot.setSpacing(10);
    	handleRoot.getChildren().addAll(lbl_handle, cb_reverseX, cb_reverseY, lbl_sensitivity, slider);
    	root.getChildren().add(handleRoot);
    }
    
    /**
     *  GUIの「表示」を作成
     */
    private void guiDisplay(HBox root) {
    	// ラベルを作成
		Label lbl_display = new Label("表示");
		lbl_display.setFont(font_index);
 		// チェックボックスを作成
		cb_2g = new CheckBox("2GHz帯");
		cb_2g.setSelected(true);	//デフォルトでチェックをつける
		cb_2g.setOnAction(ev -> {
			draw();
		});
		cb_800m = new CheckBox("800MHz帯");
		cb_800m.setSelected(true);	//デフォルトでチェックをつける
		cb_800m.setOnAction(ev -> {
			draw();
		});
		cb_virtual = new CheckBox("仮想基地局");
		cb_virtual.setSelected(true);
		cb_virtual.setOnAction(ev -> {
			draw();
		});
		//　配置
    	VBox displayRoot = new VBox();
		displayRoot.setSpacing(10);
    	displayRoot.getChildren().addAll(lbl_display, cb_2g, cb_800m, cb_virtual);
    	root.getChildren().add(displayRoot);
	}
    
    /**
     *  GUIの「距離」を作成
     */
    private void guiDistance(HBox root) {
    	// ラベルを作成
		Label lbl_dist = new Label("距離");
		lbl_dist.setFont(font_index);
		Label lbl_distInfo = new Label("マップの一辺の距離(m)");
		lbl_distError = new Label("");
		lbl_distError.setTextFill(Color.RED);
    	// テキストフィールドを作成
		txtField_dist = new TextField(Integer.toString(dist));
		txtField_dist.setAlignment(Pos.CENTER_RIGHT);
		// ボタンを作成
		Button btn_applyDist = new Button("適用");
		btn_applyDist.setOnAction(ev -> {
			try {
				dist = Integer.parseInt(txtField_dist.getText());
				lbl_distError.setText("");
			} catch (NumberFormatException e) {
				lbl_distError.setText("不適切な入力です。");
			}
			unit_dist = (double)lsize / (double)dist;
			draw();
		});
		// 配置
		VBox distRoot = new VBox();
		distRoot.setSpacing(10);
		distRoot.getChildren().addAll(lbl_dist, lbl_distInfo, txtField_dist, btn_applyDist, lbl_distError);
		root.getChildren().add(distRoot);
    }
    
    /**
     *  GUIの基地局情報を表示するリストビューを作成
     */
    private void guiListView(VBox root) {
		//　データベースから取得した基地局情報を表示するリストビューを作成
		listView = new ListView<String>();
		listView.setPrefSize(300, 500);
		list_baseStation = listView.getItems();
		listView.setOnMouseClicked(ev -> {
			String line = listView.getSelectionModel().getSelectedItem();
			if (line == null) {
				return;
			}
			String lineEnd = line.substring(line.length() - 3, line.length() - 1);
			switch (lineEnd) {
				case "仮想":
					//選択中の仮想基地局の情報を設定部分に反映
				    String splitLine[] = line.split(" ");
				    txtField_configX.setText(splitLine[1].substring(2, splitLine[1].length()));
				    txtField_configY.setText(splitLine[2].substring(2, splitLine[2].length()));
				    switch (splitLine[3].substring(4, 5)) {
					    case "2":
					    	comboBox_frequency.setValue("2GHz");
					    	break;
					    case "8":
					    	comboBox_frequency.setValue("800MHz");
					    	break;
					    }
				    comboBox_radioStrength.setValue(Integer.parseInt(splitLine[4].substring(5, 6)));
					btn_delete.setDisable(false);
					btn_edit.setDisable(false);
					break;
				default:
					btn_delete.setDisable(true);
					btn_edit.setDisable(true);
					break;
			}
		});
    	// 配置
		root.getChildren().add(listView);
    }
    
    /**
     *  GUIの基地局編集の部分を作成
     */
    private void guiEditBaseSta(VBox root) {
		// ラベルを作成
    	Label lbl_add = new Label("仮想基地局の追加・変更");
		lbl_add.setFont(font_index);
		Label lbl_addX = new Label("X座標");
		Label lbl_addY = new Label("Y座標");
		Label lbl_frequency = new Label("周波数");
		Label lbl_radioStrength = new Label("電波強度");		
		// テキストフィールドを作成
		txtField_configX = new TextField("0");
		txtField_configX.setAlignment(Pos.CENTER_RIGHT);
		txtField_configY = new TextField("0");
		txtField_configY.setAlignment(Pos.CENTER_RIGHT);
		// コンボボックスを作成
		comboBox_frequency = new ComboBox<String>();
		ObservableList<String> list_frequency = comboBox_frequency.getItems();
		list_frequency.addAll("2GHz", "800MHz");
		comboBox_frequency.setValue("2GHz");
		comboBox_radioStrength = new ComboBox<Integer>();
		ObservableList<Integer> list_radioStrength = comboBox_radioStrength.getItems();
		list_radioStrength.addAll(5, 4, 3, 2, 1, 0);
		comboBox_radioStrength.setValue(5);	
		// ボタンを作成
		//仮想基地局追加ボタン
		Button btn_add = new Button("追加");
		btn_add.setOnAction(ev -> add());
		//仮想基地局変更ボタン
		btn_edit = new Button("変更");
		btn_edit.setDisable(true);
		btn_edit.setOnAction(ev -> edit());
		//仮想基地局削除ボタン
		btn_delete = new Button("選択中の仮想地基地局を削除");
		btn_delete.setDisable(true);
		btn_delete.setOnAction(ev -> delete());
		// 配置
		GridPane editBaseStaRoot = new GridPane();
		editBaseStaRoot.setVgap(10);
		editBaseStaRoot.setHgap(10);
		editBaseStaRoot.setPadding(new Insets(30, 0, 0, 0));
		editBaseStaRoot.add(lbl_add, 0, 0, 5, 1);
		editBaseStaRoot.add(lbl_addX, 0, 1);
		editBaseStaRoot.add(txtField_configX, 1, 1);
		editBaseStaRoot.add(lbl_addY, 0, 2);
		editBaseStaRoot.add(txtField_configY, 1, 2);
		editBaseStaRoot.add(lbl_frequency, 2, 1);
		editBaseStaRoot.add(comboBox_frequency, 3, 1);
		editBaseStaRoot.add(lbl_radioStrength, 2, 2);
		editBaseStaRoot.add(comboBox_radioStrength, 3, 2);
		editBaseStaRoot.add(btn_add, 4, 2);		
		editBaseStaRoot.add(btn_edit, 5, 2);
		root.getChildren().addAll(btn_delete, editBaseStaRoot);
    }
    
    /**
     *  仮想基地局追加処理
     */
    private void add() {
        int addX = Integer.parseInt(txtField_configX.getText()) ;
        int addY = Integer.parseInt(txtField_configY.getText()) ;
        String addFrequency = comboBox_frequency.getValue();
        int addRadioStrength = comboBox_radioStrength.getValue();
        String sql = 
        		"insert into base_stations(x, y, frequency, radio_strength, isVirtual) "
        		+ "values(" + addX + ", " + addY + ", '" + addFrequency + "', " + addRadioStrength + ", TRUE)";
        try {
        	Connection con = DriverManager.getConnection ( url, user, password );
        	try {
                Statement stmt = con.createStatement();
                stmt.executeUpdate(sql);
        	} finally {
        		con.close();
        	}
        } catch ( SQLException e ) {
            e.printStackTrace() ;	//本番はコメントアウト
        }
        draw();    	
    }
    
    /**
     *  仮想基地局変更処理
     */
    private void edit() {
        int addX = Integer.parseInt(txtField_configX.getText()) ;
        int addY = Integer.parseInt(txtField_configY.getText()) ;
        String addFrequency = comboBox_frequency.getValue();
        int addRadioStrength = comboBox_radioStrength.getValue();
		String line = listView.getSelectionModel().getSelectedItem();
	    int id = Integer.parseInt(line.substring(3, line.indexOf(" ")));
        String sql =
        		"update base_stations "
        		+ "set x = " + addX + ", y = " + addY + ", frequency = '" + addFrequency + "', radio_strength = " + addRadioStrength + " "
        		+ "where id = " + id;
        try {
        	Connection con = DriverManager.getConnection (url, user, password);
        	try {
                Statement stmt = con.createStatement();
                stmt.executeUpdate(sql);
        	} finally {
        		con.close();
        	}
        } catch ( SQLException e ) {
            e.printStackTrace();	//本番はコメントアウト
        }
        btn_delete.setDisable(true);
		btn_edit.setDisable(true);
        draw();
    }
    
    /**
     *  仮想基地局削除処理
     */
    private void delete() {
		String line = listView.getSelectionModel().getSelectedItem();
		int id = Integer.parseInt(line.substring(3, line.indexOf(" ")));
		String sql =
		        "delete from base_stations where id = " + id;
	    try {
		    Connection con = DriverManager.getConnection (url, user, password);
		    try {
		        Statement stmt = con.createStatement();
		        stmt.executeUpdate(sql);
		    } finally {
		        con.close();
		    }
		} catch ( SQLException e ) {
		    e.printStackTrace();	//本番はコメントアウト
		}
		btn_delete.setDisable(true);
		btn_edit.setDisable(true);
		draw();
    }
}
