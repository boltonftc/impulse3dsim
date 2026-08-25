package com.acmerobotics.dashboard.canvas;

// Minimal FTC Dashboard Canvas: a field-overlay drawing surface. On a real Control Hub this
// renders on the dashboard's live field view; here in the sim it is a genuine no-op (matches the
// real FtcDashboard's own behavior when no dashboard client is connected) -- every method just
// returns `this` so student code can chain calls without any null-check or behavior change.
public class Canvas {

    public Canvas setFill(String color) { return this; }
    public Canvas setStroke(String color) { return this; }
    public Canvas setStrokeWidth(double width) { return this; }
    public Canvas setAlpha(double alpha) { return this; }

    public Canvas fillCircle(double x, double y, double radius) { return this; }
    public Canvas strokeCircle(double x, double y, double radius) { return this; }
    public Canvas fillRect(double x, double y, double width, double height) { return this; }
    public Canvas strokeRect(double x, double y, double width, double height) { return this; }
    public Canvas strokeLine(double x1, double y1, double x2, double y2) { return this; }
    public Canvas strokePolyline(double[] xPoints, double[] yPoints) { return this; }
    public Canvas strokePolygon(double[] xPoints, double[] yPoints) { return this; }
    public Canvas fillPolygon(double[] xPoints, double[] yPoints) { return this; }
    public Canvas fillText(String text, double x, double y) { return this; }
}
