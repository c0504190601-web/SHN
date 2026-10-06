package com.kioskmdm;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small vector icons: no font-dependent emoji or external assets. */
final class ControlIcon extends View {
    static final int WIFI=0, BLUETOOTH=1, ROTATION=2, SETTINGS=3, CLOCK=4, SOUND=5, DISPLAY=6;
    private final int kind;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = UI.ACCENT;
    ControlIcon(Context c, int kind) { super(c); this.kind=kind; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    void tint(int value) { color=value; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float size=Math.min(getWidth(),getHeight());
        canvas.translate((getWidth()-size)/2f,(getHeight()-size)/2f);
        canvas.scale(size/32f,size/32f);
        pen.setColor(color); pen.setStyle(Paint.Style.STROKE); pen.setStrokeWidth(2.2f);
        pen.setStrokeCap(Paint.Cap.ROUND); pen.setStrokeJoin(Paint.Join.ROUND);
        if(kind==WIFI) {
            canvas.drawArc(3,6,29,31,224,92,false,pen);
            canvas.drawArc(8,12,24,30,224,92,false,pen);
            pen.setStyle(Paint.Style.FILL); canvas.drawCircle(16,25,2,pen);
        } else if(kind==BLUETOOTH) {
            Path p=new Path();p.moveTo(10,9);p.lineTo(23,22);p.lineTo(16,28);p.lineTo(16,4);p.lineTo(23,10);p.lineTo(10,23);canvas.drawPath(p,pen);
        } else if(kind==ROTATION) {
            canvas.save();canvas.rotate(-25,16,16);canvas.drawRoundRect(11,7,21,25,2,2,pen);canvas.restore();
            canvas.drawArc(3,3,29,29,185,102,false,pen);canvas.drawLine(19,3,23,4,pen);canvas.drawLine(23,4,21,8,pen);
            canvas.drawArc(3,3,29,29,5,102,false,pen);canvas.drawLine(13,29,9,28,pen);canvas.drawLine(9,28,11,24,pen);
        } else if(kind==SETTINGS) {
            canvas.drawCircle(16,16,8,pen);canvas.drawCircle(16,16,3,pen);
            for(int i=0;i<8;i++){canvas.save();canvas.rotate(i*45,16,16);canvas.drawLine(16,4,16,8,pen);canvas.restore();}
        } else if(kind==CLOCK) {
            canvas.drawCircle(16,16,11,pen);canvas.drawLine(16,9,16,16,pen);canvas.drawLine(16,16,22,19,pen);
        } else if(kind==SOUND) {
            Path p=new Path();p.moveTo(5,12);p.lineTo(10,12);p.lineTo(17,6);p.lineTo(17,26);p.lineTo(10,20);p.lineTo(5,20);p.close();canvas.drawPath(p,pen);
            canvas.drawArc(13,6,29,26,-60,120,false,pen);
        } else {
            canvas.drawCircle(16,16,6,pen);
            for(int i=0;i<8;i++){canvas.save();canvas.rotate(i*45,16,16);canvas.drawLine(16,3,16,6,pen);canvas.restore();}
        }
        canvas.restore();
    }
}
