package com.sam.syncai;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

public final class CanvasActivity extends Activity {
    private DrawView drawView;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7, 8, 14));

        LinearLayout bar = new LinearLayout(this);
        Button clear = new Button(this);
        clear.setText("CLEAR");
        clear.setOnClickListener(v -> drawView.clearCanvas());
        Button save = new Button(this);
        save.setText("SAVE PNG");
        save.setOnClickListener(v -> saveCanvas());
        Button close = new Button(this);
        close.setText("CLOSE");
        close.setOnClickListener(v -> finish());
        bar.addView(clear, new LinearLayout.LayoutParams(0, 52, 1));
        bar.addView(save, new LinearLayout.LayoutParams(0, 52, 1));
        bar.addView(close, new LinearLayout.LayoutParams(0, 52, 1));

        drawView = new DrawView();
        root.addView(bar);
        root.addView(drawView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void saveCanvas() {
        Bitmap bitmap = Bitmap.createBitmap(drawView.getWidth(), drawView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawView.draw(canvas);
        File out = new File(getFilesDir(), "workspace/canvas-" + System.currentTimeMillis() + ".png");
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            Toast.makeText(this, "Saved " + out.getName(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            bitmap.recycle();
        }
    }

    private final class DrawView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();

        DrawView() {
            super(CanvasActivity.this);
            setBackgroundColor(Color.rgb(12, 14, 22));
            paint.setColor(Color.rgb(154, 96, 255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(7f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawPath(path, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    path.moveTo(x, y);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    path.lineTo(x, y);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    path.lineTo(x, y);
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        void clearCanvas() {
            path.reset();
            invalidate();
        }
    }
}
