package com.sam.syncai;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Lightweight shared Sync Core visualizer for chat, appearance and voice. */
public final class SyncCoreView extends View {
    public enum State { IDLE, READY, LISTENING, THINKING, SPEAKING, EXECUTING, SUCCESS, ERROR }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private AppTheme theme;
    private State state = State.READY;
    private float rotation;
    private ValueAnimator animator;

    public SyncCoreView(Context context) {
        super(context);
        theme = AppTheme.from(context);
        setMinimumWidth(dp(24));
        setMinimumHeight(dp(24));
        setContentDescription("Sync Core, ready");
    }

    public void setState(State next) {
        if (next == null) return;
        if (state != next) {
            state = next;
            setContentDescription("Sync Core, " + next.name().toLowerCase(java.util.Locale.US));
            if (animator != null) animator.setDuration(duration());
            invalidate();
        }
        refreshMotionSettings();
    }

    public void setStateFromLabel(String value) {
        String s = value == null ? "" : value.toUpperCase(java.util.Locale.US);
        if (s.contains("LISTEN")) setState(State.LISTENING);
        else if (s.contains("THINK") || s.contains("LOADING MODEL")) setState(State.THINKING);
        else if (s.contains("RESPOND")) setState(State.SPEAKING);
        else if (s.contains("ACTION COMPLETE")) setState(State.SUCCESS);
        else if (s.contains("NEEDS ATTENTION") || s.contains("ERROR") || s.contains("FAILED")) setState(State.ERROR);
        else if (s.contains("ACTION") || s.contains("EXECUT")) setState(State.EXECUTING);
        else if (s.contains("READY")) setState(State.READY);
        else setState(State.IDLE);
    }

    public void refreshMotionSettings() {
        if (Motion.reduced(getContext())) stopAnimator();
        else if (isAttachedToWindow()) startAnimator();
        invalidate();
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); refreshMotionSettings(); }
    @Override protected void onDetachedFromWindow() { stopAnimator(); super.onDetachedFromWindow(); }

    private void startAnimator() {
        if (animator != null || Motion.reduced(getContext())) return;
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(duration());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> { rotation=(float)a.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    private long duration() {
        switch(state) {
            case LISTENING: return 900L;
            case SPEAKING: return 760L;
            case EXECUTING: return 650L;
            case THINKING: return 1200L;
            case ERROR: return 1800L;
            case SUCCESS: return 2200L;
            default: return 3200L;
        }
    }

    private void stopAnimator() {
        if(animator!=null){animator.cancel();animator=null;}
        rotation=0f;
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float d=Math.min(getWidth(),getHeight());
        if(d<=0f)return;
        float x=getWidth()/2f,y=getHeight()/2f,r=d*.31f;
        float pulse=Motion.reduced(getContext())?1f:1f+.035f*(float)Math.sin(Math.toRadians(rotation*2f));
        int primary=stateColor(), secondary=state==State.ERROR?0xFFFF718D:state==State.SUCCESS?0xFF34D399:theme.secondary;

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(x,y,r*2.05f,new int[]{alpha(primary,75),alpha(primary,0)},null,Shader.TileMode.CLAMP));
        c.drawCircle(x,y,r*2.05f,paint);paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(dp(1),d*.022f));
        RectF outer=new RectF(x-r*1.55f,y-r*1.55f,x+r*1.55f,y+r*1.55f);
        paint.setColor(alpha(secondary,95));c.drawArc(outer,0,360,false,paint);
        paint.setStrokeWidth(Math.max(dp(1.5f),d*.045f));
        RectF ring=new RectF(x-r*1.28f,y-r*1.28f,x+r*1.28f,y+r*1.28f);
        paint.setColor(secondary);c.drawArc(ring,rotation,105,false,paint);
        paint.setColor(alpha(primary,220));c.drawArc(ring,rotation+180,55,false,paint);

        double angle=Math.toRadians(rotation*1.15f-90);
        paint.setStyle(Paint.Style.FILL);paint.setColor(secondary);
        c.drawCircle(x+(float)Math.cos(angle)*r*1.55f,y+(float)Math.sin(angle)*r*1.55f,Math.max(dp(2),d*.045f),paint);
        paint.setShader(new RadialGradient(x-r*.25f,y-r*.29f,r*1.35f,
                new int[]{blend(primary,Color.WHITE,.42f),primary,blend(primary,Color.BLACK,.45f)},
                new float[]{0f,.58f,1f},Shader.TileMode.CLAMP));
        c.save();c.scale(pulse,pulse,x,y);c.drawCircle(x,y,r,paint);
        paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(dp(1),d*.025f));
        paint.setColor(alpha(Color.WHITE,145));c.drawCircle(x,y,r*.88f,paint);c.restore();
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(alpha(Color.WHITE,160));
        c.drawCircle(x-r*.26f,y-r*.29f,Math.max(dp(1),d*.034f),paint);
    }

    private int stateColor() {
        switch(state) {
            case LISTENING: case SPEAKING: return theme.secondary;
            case ERROR: return 0xFFFF718D;
            case SUCCESS: return 0xFF34D399;
            case THINKING: return blend(theme.primary,theme.secondary,.25f);
            default: return theme.primary;
        }
    }
    private int alpha(int c,int a){return Color.argb(Math.max(0,Math.min(255,a)),Color.red(c),Color.green(c),Color.blue(c));}
    private int blend(int a,int b,float r){
        float t=Math.max(0f,Math.min(1f,r));
        return Color.rgb(Math.round(Color.red(a)*(1-t)+Color.red(b)*t),
                Math.round(Color.green(a)*(1-t)+Color.green(b)*t),
                Math.round(Color.blue(a)*(1-t)+Color.blue(b)*t));
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
